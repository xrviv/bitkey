use std::collections::HashSet;

use anyhow::Result;
use rustify::blocking::clients::reqwest::Client;
use sled::Db;

use crate::{
    db::transactions::{FromDatabase, ToDatabase},
    entities::{Account, AuthenticationToken, DescriptorKeyset, Keyset},
    requests::{helper::EndpointExt, KeysetsRequest},
};

pub(crate) trait FromCache {
    fn from_cache(client: &Client, db: &Db) -> Result<Self>
    where
        Self: Sized;
}

impl FromCache for Account {
    fn from_cache(client: &Client, db: &Db) -> Result<Self>
    where
        Self: Sized,
    {
        let account = Account::from_database(db)?;
        let request = KeysetsRequest {
            account_id: account.id.clone(),
        }
        .exec_authenticated(client, &AuthenticationToken::from_database(db)?);

        if let Ok(response) = request {
            let keysets_client = account.keysets;
            let keysets_server = response.keysets.into_iter().map(|k| Keyset {
                id: k.keyset_id,
                network: k.network,
                keys: DescriptorKeyset {
                    application: k.app_dpub,
                    hardware: k.hardware_dpub,
                    server: k.server_dpub,
                },
            });

            let mut keysets = HashSet::new();
            keysets.extend(keysets_client);
            keysets.extend(keysets_server);

            Account {
                id: account.id,
                keysets: Vec::from_iter(keysets),
            }
            .to_database(db)?;

            Account::from_database(db)
        } else {
            Ok(account)
        }
    }
}
