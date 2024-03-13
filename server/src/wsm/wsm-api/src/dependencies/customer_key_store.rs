use anyhow::Context;
use aws_sdk_dynamodb::client::Client as DdbClient;
use aws_sdk_dynamodb::types::AttributeValue;
use serde::{Deserialize, Serialize};
use serde_dynamo::{from_item, to_item};
use tracing::instrument;
use wsm_common::bitcoin::Network;
use wsm_common::messages::DomainFactoredXpub;

/// Struct representing the customer's root key. We use the data in this struct to derive subsequent
/// child BIP32 xprvs. Customers and `CustomerKey` have a bijective relationship.
#[derive(Serialize, Deserialize, Debug)]
pub struct CustomerKey {
    /// The partition key used to uniquely identify a `CustomerKey`.
    pub root_key_id: String,
    /// Base64-encoded ciphertext of the customer's root key. Encrypted/decrypted using a
    /// data encryption key (DEK) whose `id` is also stored in this struct.
    pub key_ciphertext: String,
    /// Base64-encoded nonce used to encrypt/decrypt the customer's root key
    pub key_nonce: String,
    /// Xpub wrapped in a `DescriptorKey` format -- it includes information about the xpub itself,
    /// as well as information about its origin like derivation path.
    pub xpub_descriptor: String,
    /// ID of the Data Encryption Key (DEK) used to decrypt/encrypt the customer's root key
    pub dek_id: String,
    /// A list of cached Xpubs with their intended domain.
    pub xpubs: Vec<DomainFactoredXpub>,
    /// The bitcoin network type to be used with the customer's root key
    #[serde(default)]
    pub network: Option<Network>,
    #[serde(default)]
    /// Signature over the server public key using the WSM integrity key
    pub integrity_signature: Option<String>,
}

impl CustomerKey {
    pub fn new(
        root_key_id: String,
        key_ciphertext: String,
        key_nonce: String,
        xpub_descriptor: String,
        dek_id: String,
        xpubs: Vec<DomainFactoredXpub>,
        network: Network,
        integrity_signature: String,
    ) -> Self {
        Self {
            root_key_id,
            key_ciphertext,
            key_nonce,
            xpub_descriptor,
            dek_id,
            xpubs,
            network: Some(network),
            integrity_signature: Some(integrity_signature),
        }
    }
}

#[derive(Debug, Clone)]
pub struct CustomerKeyStore {
    ck_table_name: String,
    client: DdbClient,
}

impl CustomerKeyStore {
    pub fn new(client: DdbClient, customer_keys_table_name: &str) -> Self {
        CustomerKeyStore {
            ck_table_name: customer_keys_table_name.to_string(),
            client,
        }
    }

    #[instrument(skip(self))]
    pub async fn get_customer_key(&self, root_key_id: &str) -> anyhow::Result<Option<CustomerKey>> {
        let item_output = self
            .client
            .get_item()
            .table_name(&self.ck_table_name)
            .key("root_key_id", AttributeValue::S(root_key_id.to_string()))
            .send()
            .await?
            .item;

        match item_output {
            Some(item) => from_item(item).context("Unable to parse database object to CustomerKey"),
            None => Ok(None),
        }
    }

    #[instrument(skip(self))]
    pub async fn put_customer_key(&self, customer_key: &CustomerKey) -> anyhow::Result<()> {
        let customer_key_item = to_item(customer_key)?;

        self.client
            .put_item()
            .table_name(self.ck_table_name.clone())
            .set_item(Some(customer_key_item))
            .send()
            .await
            .context("could not write new customer key to ddb")?;
        Ok(())
    }

    #[instrument(skip(self))]
    /// TODO(W-5872) Remove after backfill. This loads all the keys into memory, but
    /// should be fine for now as we have a small number of keys.
    pub async fn get_all_customer_keys(&self) -> anyhow::Result<Vec<CustomerKey>> {
        let mut customer_keys = Vec::new();
        let mut last_evaluated_key = None;

        loop {
            let scan_output = self
                .client
                .scan()
                .table_name(&self.ck_table_name)
                .set_exclusive_start_key(last_evaluated_key)
                .send()
                .await?;

            if let Some(items) = scan_output.items {
                for item in items {
                    if let Ok(customer_key) = from_item(item) {
                        customer_keys.push(customer_key);
                    }
                }
            }

            if scan_output.last_evaluated_key.is_none() {
                break;
            }

            last_evaluated_key = scan_output.last_evaluated_key;
        }

        Ok(customer_keys)
    }

    pub async fn update_integrity_signature(
        &self,
        root_key_id: &str,
        new_signature: &str,
    ) -> anyhow::Result<()> {
        let update_expression = "SET integrity_signature = :new_signature";

        let expression_attribute_values = std::collections::HashMap::from([(
            ":new_signature".to_string(),
            AttributeValue::S(new_signature.to_string()),
        )]);

        self.client
            .update_item()
            .table_name(&self.ck_table_name)
            .key("root_key_id", AttributeValue::S(root_key_id.to_string()))
            .update_expression(update_expression)
            .set_expression_attribute_values(Some(expression_attribute_values))
            .send()
            .await
            .context("Failed to update customer key integrity signature in DynamoDB")?;

        Ok(())
    }
}
