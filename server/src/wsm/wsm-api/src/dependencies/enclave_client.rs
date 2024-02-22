use anyhow::{anyhow, bail};
use aws_credential_types::provider::{ProvideCredentials, SharedCredentialsProvider};
use aws_credential_types::Credentials;
use aws_types::region::Region;
use aws_types::SdkConfig;
use base64::{engine::general_purpose::STANDARD as BASE64, Engine as _};
use log::{log, Level};
use reqwest::Response;
use serde::Serialize;
use serde_json;
use thiserror::Error;
use tracing::{event, instrument};

use wsm_common::enclave_log::LogBuffer;
use wsm_common::messages::enclave::{DerivedKey, EnclaveCreateKeyRequest, EnclaveDeriveKeyRequest};
use wsm_common::messages::{
    api::SignedPsbt,
    enclave::{CreatedKey, EnclaveSignRequest, LoadSecretRequest},
    SecretRequest,
};

use crate::dependencies::enclave_client::EnclaveClientError::CredentialProviderError;
use crate::{DekStore, Settings};

#[derive(Error, Debug)]
pub enum EnclaveClientError {
    #[error("Could not load kms credentials: {0}")]
    CredentialProviderError(String),
}

#[derive(Clone, Debug)]
pub struct KmsConfig {
    proxy_port: u32,
    region: Region,
    credentials_provider: SharedCredentialsProvider,
    cmk_id: String,
}

impl KmsConfig {
    pub async fn try_new(
        settings: &Settings,
        aws_config: &SdkConfig,
        cmk_id: String,
    ) -> Result<Self, EnclaveClientError> {
        Ok(Self {
            proxy_port: settings.kms_proxy_port,
            region: aws_config
                .region()
                .expect("No region provided through KMS config")
                .clone(),
            credentials_provider: aws_config.credentials_provider().ok_or(
                CredentialProviderError(
                    "No credentials provider provided through KMS config".to_string(),
                ),
            )?,
            cmk_id,
        })
    }

    pub async fn creds(&self) -> Result<Credentials, EnclaveClientError> {
        self.credentials_provider
            .provide_credentials()
            .await
            .map_err(|err| CredentialProviderError(err.to_string()))
    }
}

#[derive(Debug)]
pub struct EnclaveClient {
    endpoint: reqwest::Url,
    client: reqwest::Client,
    kms_config: Option<KmsConfig>,
    dek_store: DekStore,
}

impl EnclaveClient {
    pub fn new(dek_store: DekStore, kms_config: Option<KmsConfig>, settings: &Settings) -> Self {
        EnclaveClient {
            endpoint: reqwest::Url::try_from(settings.enclave_endpoint.as_str()).unwrap(),
            client: reqwest::Client::new(),
            kms_config,
            dek_store,
        }
    }

    pub async fn health_check(&self) -> anyhow::Result<()> {
        self.client
            .get(self.endpoint.join("health-check")?)
            .send()
            .await?;
        Ok(())
    }

    #[instrument(skip(self))]
    pub async fn create_key(&self, req: EnclaveCreateKeyRequest) -> anyhow::Result<CreatedKey> {
        let result = self
            .post_request_with_dek(SecretRequest::new("create-key", req.dek_id.clone(), req))
            .await?;
        Ok(result.json().await?)
    }

    #[instrument(skip(self))]
    pub async fn derive_key(&self, req: EnclaveDeriveKeyRequest) -> anyhow::Result<DerivedKey> {
        let result = self
            .post_request_with_dek(SecretRequest::new("derive-key", req.dek_id.clone(), req))
            .await?;
        Ok(result.json().await?)
    }

    #[instrument(skip(self))]
    pub async fn sign_psbt(&self, req: EnclaveSignRequest) -> anyhow::Result<SignedPsbt> {
        let result = self
            .post_request_with_dek(SecretRequest::new("sign-psbt", req.dek_id.clone(), req))
            .await?;
        Ok(result.json().await?)
    }

    /// This method first tries an "optimistic" call to the enclave with the user's provided
    /// data-encryption key. Upon first failure, which is usually due to the DEK not being loaded
    /// onto the enclave, we "force" the load. Then, we try again.
    #[instrument(skip(self))]
    async fn post_request_with_dek<T: Serialize>(
        &self,
        req: SecretRequest<'_, T>,
    ) -> anyhow::Result<Response> {
        for _attempt in 0..2 {
            let res = self
                .client
                .post(self.endpoint.join(req.endpoint)?)
                .json(&req.data)
                .send()
                .await?;
            // get the wsm-enclave logs from the response header and include them in a trace
            if let Some(logs) = get_enclave_logs_from_header(&res) {
                // the logs are a base64-encoded LogBuffer. We decode them and log them as a trace
                // If decoding fails, we log that but don't fail the whole call
                match unpack_enclave_logs(&logs) {
                    Ok(logs) => {
                        event!(tracing::Level::WARN, "Enclave error logs: {}", logs);
                        log!(Level::Warn, "Enclave error logs: {}", logs);
                    }
                    Err(e) => {
                        event!(tracing::Level::WARN, "Could not decode enclave logs: {}", e);
                        log!(Level::Warn, "Could not decode enclave logs: {}", e);
                    }
                }
            }
            if res.status() == 404 {
                // DEK not loaded into enclave
                event!(
                    tracing::Level::DEBUG,
                    "404 from enclave, loading DEK into enclave"
                );
                self.load_wrapped_dek(&req.dek_id).await?;
                // now that the dek is loaded, try making the call again
                continue;
            } else if res.status() == 200 {
                return Ok(res);
            } else {
                match res.text().await {
                    Ok(v) => bail!("Error from the enclave: {}", v),
                    Err(e) => bail!("Error from the enclave: {}", e),
                }
            }
        }
        bail!("Could not get result from server")
    }

    #[instrument(skip(self))]
    pub async fn get_available_dek_id(&self) -> anyhow::Result<String> {
        match self.kms_config {
            Some(_) => self.dek_store.get_availabile_dek_id().await,
            None => Ok("FAKE_DEK_ID".to_string()),
        }
    }

    #[instrument(skip(self))]
    async fn load_wrapped_dek(&self, dek_id: &str) -> anyhow::Result<()> {
        let req = match &self.kms_config {
            None => {
                event!(tracing::Level::WARN, "Loading fake secret into enclave");
                LoadSecretRequest {
                    region: "FAKE_REGION".to_string(),
                    proxy_port: "FAKE_PROXY_PORT".to_string(),
                    akid: "FAKE_AKID".to_string(),
                    skid: "FAKE_SKID".to_string(),
                    session_token: "FAKE_SESSION_TOKEN".to_string(),
                    dek_id: "FAKE_DEK_ID".to_string(),
                    ciphertext: "FAKE_DEK_CIPHERTEXT".to_string(),
                    cmk_id: "FAKE_CMK_ID".to_string(),
                }
            }
            Some(kms_config) => {
                let dek = self
                    .dek_store
                    .get_wrapped_key(dek_id)
                    .await
                    .map_err(|e| anyhow!("Could not load DEK: {}", e))?;
                let region = kms_config.region.to_string();
                let creds = kms_config.creds().await?;
                let akid = creds.access_key_id().to_string();
                let skid = creds.secret_access_key().to_string();
                let session_token = creds.session_token().unwrap_or("").to_string();
                LoadSecretRequest {
                    region,
                    proxy_port: kms_config.proxy_port.to_string(),
                    akid,
                    skid,
                    session_token,
                    dek_id: dek_id.to_string(),
                    ciphertext: dek,
                    cmk_id: kms_config.cmk_id.to_string(),
                }
            }
        };
        let res = self
            .client
            .post(self.endpoint.join("load-secret")?)
            .json(&req)
            .send()
            .await?;
        // get the wsm-enclave logs from the response header and include them in a trace
        if let Some(logs) = get_enclave_logs_from_header(&res) {
            // the logs are a base64-encoded LogBuffer. We decode them and log them as a trace
            // If decoding fails, we log that but don't fail the whole call
            match unpack_enclave_logs(&logs) {
                Ok(logs) => {
                    event!(tracing::Level::WARN, "Enclave error logs: {}", logs);
                    log!(Level::Warn, "Enclave error logs: {}", logs);
                }
                Err(e) => {
                    event!(tracing::Level::WARN, "Could not decode enclave logs: {}", e);
                    log!(Level::Warn, "Could not decode enclave logs: {}", e);
                }
            }
        }
        Ok(())
    }
}

// Get the X-WSM-Logs header from the response, if it exists
fn get_enclave_logs_from_header(res: &Response) -> Option<String> {
    res.headers()
        .get("X-WSM-Logs")
        .and_then(|v| v.to_str().ok())
        .map(|v| v.to_string())
}

fn unpack_enclave_logs(logs: &str) -> anyhow::Result<LogBuffer> {
    let logs = BASE64.decode(logs)?;
    let logs = serde_json::from_str(&String::from_utf8(logs)?)?;
    Ok(logs)
}
