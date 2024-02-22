use bdk_utils::bdk::bitcoin::secp256k1::PublicKey;
use database::aws_sdk_dynamodb::error::ProvideErrorMetadata;
use database::ddb::{try_from_item, try_to_attribute_val, DDBService, DatabaseError};

use tracing::{event, instrument, Level};
use types::account::identifiers::AccountId;

use crate::entities::AuthFactor;
use crate::repository::{
    APPLICATION_IDX_PARTITION_KEY, APPLICATION_TO_ACCOUNT_IDX, HW_IDX_PARTITION_KEY,
    HW_TO_ACCOUNT_IDX,
};
use crate::{entities::Account, repository::PARTITION_KEY};

use super::{Repository, RECOVERY_AUTHKEY_IDX_PARTITION_KEY, RECOVERY_AUTHKEY_TO_ACCOUNT_IDX};

impl Repository {
    #[instrument(skip(self))]
    pub async fn fetch(&self, id: &AccountId) -> Result<Account, DatabaseError> {
        let table_name = self.get_table_name().await?;
        let database_object = self.get_database_object();

        let item_output = self
            .connection
            .client
            .get_item()
            .table_name(table_name)
            .key(PARTITION_KEY, try_to_attribute_val(id, database_object)?)
            .consistent_read(true)
            .send()
            .await
            .map_err(|err| {
                let service_err = err.into_service_error();
                event!(
                    Level::ERROR,
                    "Could not query database: {service_err:?} with message: {:?}",
                    service_err.message()
                );
                DatabaseError::FetchError(database_object)
            })?;

        let item = item_output.item.ok_or_else(|| {
            event!(Level::WARN, "account {id} not found in the database");
            DatabaseError::ObjectNotFound(database_object)
        })?;
        try_from_item(item, database_object)
    }

    pub(crate) async fn fetch_optional_account_by_auth_pubkey(
        &self,
        pubkey: PublicKey,
        key_factor: AuthFactor,
    ) -> Result<Option<Account>, DatabaseError> {
        let table_name = self.get_table_name().await?;
        let database_object = self.get_database_object();

        let (attr_name, index) = match key_factor {
            AuthFactor::App => (APPLICATION_IDX_PARTITION_KEY, APPLICATION_TO_ACCOUNT_IDX),
            AuthFactor::Hw => (HW_IDX_PARTITION_KEY, HW_TO_ACCOUNT_IDX),
            AuthFactor::Recovery => (
                RECOVERY_AUTHKEY_IDX_PARTITION_KEY,
                RECOVERY_AUTHKEY_TO_ACCOUNT_IDX,
            ),
        };

        let item_output = self
            .connection
            .client
            .query()
            .table_name(table_name)
            .index_name(index)
            .key_condition_expression("#P = :pubkey")
            .expression_attribute_names("#P", attr_name)
            .expression_attribute_values(
                ":pubkey",
                try_to_attribute_val(pubkey.to_string(), database_object)?,
            )
            .send()
            .await
            .map_err(|err| {
                let service_err = err.into_service_error();
                event!(
                    Level::ERROR,
                    "Could not fetch account: {service_err:?} with message: {:?}",
                    service_err.message()
                );
                DatabaseError::FetchError(database_object)
            })?;

        let item = item_output.items().first();
        if let Some(account_item) = item {
            try_from_item(account_item.clone(), database_object)
        } else {
            Ok(None)
        }
    }

    pub(crate) async fn fetch_account_by_auth_pubkey(
        &self,
        pubkey: PublicKey,
        key_factor: AuthFactor,
    ) -> Result<Account, DatabaseError> {
        let database_object = self.get_database_object();
        self.fetch_optional_account_by_auth_pubkey(pubkey, key_factor)
            .await?
            .ok_or(DatabaseError::ObjectNotFound(database_object))
    }

    #[instrument(skip(self))]
    pub(crate) async fn fetch_all_accounts(&self) -> Result<Vec<Account>, DatabaseError> {
        let table_name = self.get_table_name().await?;
        let database_object = self.get_database_object();

        let mut accounts = vec![];
        let mut exclusive_start_key = None;

        loop {
            let item_output = self
                .connection
                .client
                .scan()
                .set_exclusive_start_key(exclusive_start_key)
                .table_name(table_name.clone())
                .consistent_read(true)
                .send()
                .await
                .map_err(|err| {
                    let service_err = err.into_service_error();
                    event!(
                        Level::ERROR,
                        "Could not query database: {service_err:?} with message: {:?}",
                        service_err.message()
                    );
                    DatabaseError::FetchError(database_object)
                })?;

            let items = item_output.items();
            if !items.is_empty() {
                let mut fetched_accounts = items
                    .iter()
                    .filter_map(|item| {
                        let account = try_from_item(item.clone(), database_object);
                        match account {
                            Ok(account) => Some(account),
                            Err(e) => {
                                event!(
                                    Level::ERROR,
                                    "Skipping account that failed to deserialize with error: {e:?}",
                                );
                                None
                            }
                        }
                    })
                    .collect();

                accounts.append(&mut fetched_accounts);
            }

            match item_output.last_evaluated_key() {
                Some(last_evaluated_key) => {
                    exclusive_start_key = Some(last_evaluated_key.clone());
                }
                None => {
                    break;
                }
            }
        }
        Ok(accounts)
    }
}
