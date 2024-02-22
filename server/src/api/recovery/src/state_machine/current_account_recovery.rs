use account::{
    entities::{
        CommsVerificationScope, Factor, FullAccount, FullAccountAuthKeys,
        FullAccountAuthKeysPayload,
    },
    service::{FetchAccountByAuthKeyInput, FetchAndUpdateSpendingLimitInput},
};
use async_trait::async_trait;

use ::metrics::KeyValue;

use account::spend_limit::SpendingLimit;
use comms_verification::ConsumeVerificationForScopeInput;
use notification::{
    payloads::{
        recovery_canceled_delay_period::RecoveryCanceledDelayPeriodPayload,
        recovery_completed_delay_period::RecoveryCompletedDelayPeriodPayload,
        recovery_pending_delay_period::RecoveryPendingDelayPeriodPayload,
    },
    schedule::ScheduleNotificationType,
    service::{ScheduleNotificationsInput, SendNotificationInput},
    NotificationPayloadBuilder, NotificationPayloadType,
};
use time::{format_description::well_known::Rfc3339, Duration};
use types::account::identifiers::AccountId;

use crate::{
    entities::{
        DelayNotifyRecoveryAction, DelayNotifyRequirements, RecoveryAction, RecoveryDestination,
        RecoveryRequirements, RecoveryStatus, RecoveryType, RecoveryValuesPerAccountType, ToActor,
        ToActorStrategy, WalletRecovery,
    },
    helpers::validate_signatures,
    metrics,
    state_machine::{PendingDelayNotifyRecovery, RecoveryResponse},
};

use super::{
    cancel_recovery::CanceledRecoveryState, completable_recovery::CompletableRecoveryState,
    pending_recovery::PendingRecoveryState, rotated_keyset::RotatedKeysetState, RecoveryError,
    RecoveryEvent, RecoveryServices, RecoveryStateResponse, Transition, TransitionTo,
    TransitioningRecoveryState,
};

pub(crate) struct CurrentAccountRecoveryState {
    pub(crate) account: FullAccount,
    pub(crate) recovery: Option<WalletRecovery>,
    pub(crate) active_contest: bool,
}

#[async_trait]
impl RecoveryStateResponse for CurrentAccountRecoveryState {
    fn response(self: Box<Self>) -> serde_json::Value {
        let Some(recovery) = self.recovery else {
            return serde_json::json!(RecoveryResponse {
                pending_delay_notify: None,
                active_contest: self.active_contest,
            });
        };
        let requirements = recovery.requirements.delay_notify_requirements.unwrap();
        let destination = recovery
            .recovery_action
            .delay_notify_action
            .unwrap()
            .destination;
        serde_json::json!(RecoveryResponse {
            pending_delay_notify: Some(PendingDelayNotifyRecovery {
                delay_start_time: recovery.created_at,
                delay_end_time: requirements.delay_end_time,
                lost_factor: requirements.lost_factor,
                auth_keys: FullAccountAuthKeysPayload {
                    app: destination.app_auth_pubkey,
                    hardware: destination.hardware_auth_pubkey,
                    recovery: destination.recovery_auth_pubkey
                }
            }),
            active_contest: self.active_contest,
        })
    }
}

#[async_trait]
impl TransitioningRecoveryState for CurrentAccountRecoveryState {
    async fn next_transition_or_err(
        self: Box<Self>,
        event: RecoveryEvent,
        services: &RecoveryServices,
    ) -> Result<Transition, RecoveryError> {
        if let RecoveryEvent::CreateRecovery {
            account,
            lost_factor,
            destination,
            key_proof,
        } = event
        {
            let actor = key_proof.to_actor(ToActorStrategy::ExclusiveOr)?;
            if actor == lost_factor {
                return Err(RecoveryError::UnexpectedKeyProof);
            }

            // If there's an existing recovery with the same keys, return it
            if let Some(existing_recovery) = self.recovery.clone() {
                let Some(action) = existing_recovery
                    .recovery_action
                    .delay_notify_action
                    .as_ref()
                else {
                    return Err(RecoveryError::StartRecoveryForAccount);
                };

                if action.destination == destination {
                    return Ok(Transition::next(
                        self,
                        PendingRecoveryState {
                            recovery: existing_recovery,
                        },
                    ));
                } else {
                    return Err(RecoveryError::StartRecoveryForAccount);
                }
            }

            // Source needs to match account's active auth keys
            if account.common_fields.active_auth_keys_id != destination.source_auth_keys_id {
                return Err(RecoveryError::InvalidRecoverySource);
            }

            // Validate destination
            let account_auth_keys = account
                .active_auth_keys()
                .ok_or(RecoveryError::NoActiveAuthKeysError)?;
            validate_destination(
                services,
                &account.id,
                account_auth_keys,
                lost_factor,
                &destination,
            )
            .await?;

            let account_id = self.account.clone().id;
            let now = services.recovery.cur_time();

            // If there's been a contested recovery within 30 days, require comms verification
            if self.active_contest {
                let scope = CommsVerificationScope::DelayNotifyActor(actor);
                services
                    .comms_verification
                    .consume_verification_for_scope(ConsumeVerificationForScopeInput {
                        account_id: account_id.clone(),
                        scope: scope.clone(),
                    })
                    .await?;
            }

            let recovery_type = RecoveryType::DelayAndNotify;
            let delay_period = account.recovery_delay_period();
            let requirements = RecoveryRequirements {
                delay_notify_requirements: Some(DelayNotifyRequirements {
                    lost_factor,
                    delay_end_time: now + delay_period,
                }),
            };

            let recovery_action = RecoveryAction {
                delay_notify_action: Some(DelayNotifyRecoveryAction {
                    destination: destination.clone(),
                }),
            };

            let new_recovery = WalletRecovery {
                account_id: account_id.clone(),
                created_at: now,
                recovery_status: RecoveryStatus::Pending,
                recovery_type,
                recovery_type_time: format!("{}:{}", recovery_type, now.format(&Rfc3339).unwrap()),
                requirements,
                recovery_action,
                destination_app_auth_pubkey: Some(destination.app_auth_pubkey),
                destination_hardware_auth_pubkey: Some(destination.hardware_auth_pubkey),
                destination_recovery_auth_pubkey: destination.recovery_auth_pubkey,
                updated_at: now,
            };
            let recovery_service = services.recovery;
            recovery_service.create(&new_recovery).await?;

            // If this recovery is for a lost App, turn off Mobile Pay
            if let Factor::App = lost_factor {
                services
                    .account
                    .fetch_and_update_spend_limit(FetchAndUpdateSpendingLimitInput {
                        account_id: &account_id,
                        new_spending_limit: account.spending_limit.map_or_else(
                            || None,
                            |old_limit| {
                                Some(SpendingLimit {
                                    active: false,
                                    ..old_limit
                                })
                            },
                        ),
                    })
                    .await?;
            }

            let payload = NotificationPayloadBuilder::default()
                .recovery_pending_delay_period_payload(Some(RecoveryPendingDelayPeriodPayload {
                    initiation_time: now,
                    delay_end_time: now + delay_period,
                    lost_factor,
                }))
                .recovery_completed_delay_period_payload(Some(
                    RecoveryCompletedDelayPeriodPayload {
                        initiation_time: now,
                        lost_factor,
                    },
                ))
                .build()
                .map_err(|_| RecoveryError::GenerateNotificationPayloadError)?;

            services
                .notification
                .schedule_notifications(ScheduleNotificationsInput {
                    account_id: account_id.clone(),
                    notification_type: ScheduleNotificationType::RecoveryPendingDelayNotify(
                        now + delay_period,
                    ),
                    payload,
                })
                .await
                .map_err(|_| RecoveryError::ScheduleNotificationPersistanceError)?;

            metrics::DELAY_NOTIFY_CREATED.add(
                1,
                &[
                    KeyValue::new(metrics::LOST_FACTOR_KEY, lost_factor.to_string()),
                    KeyValue::new(metrics::CREATED_DURING_CONTEST_KEY, self.active_contest),
                ],
            );

            return Ok(Transition::next(
                self,
                PendingRecoveryState {
                    recovery: new_recovery,
                },
            ));
        } else if let RecoveryEvent::CheckEligibleForCompletion {
            challenge,
            app_signature,
            hardware_signature,
        } = event
        {
            let account = self.account.clone();
            let Some(recovery) = self.recovery.clone() else {
                let active_auth = account
                    .active_auth_keys()
                    .ok_or(RecoveryError::NoActiveAuthKeysError)?;

                // For Idempotency:
                // If the signatures match up with active keys, we'll return a success
                // Otherwise, we should return an error to indicate no such recovery
                validate_signatures(
                    active_auth.app_pubkey,
                    active_auth.hardware_pubkey,
                    active_auth.recovery_pubkey,
                    &challenge,
                    &app_signature,
                    &hardware_signature,
                )
                .map_err(|_| RecoveryError::NoExistingRecovery)?;
                return Ok(Transition::next(
                    self,
                    RotatedKeysetState {
                        active_keyset_id: account.active_keyset_id,
                    },
                ));
            };

            let (Some(requirements), Some(action)) = (
                recovery.requirements.delay_notify_requirements.as_ref(),
                recovery.recovery_action.delay_notify_action.as_ref(),
            ) else {
                return Err(RecoveryError::InvalidRecoveryDestination);
            };

            let (app_auth_pubkey, hardware_auth_pubkey, recovery_auth_pubkey) = (
                action.destination.app_auth_pubkey,
                action.destination.hardware_auth_pubkey,
                action.destination.recovery_auth_pubkey,
            );
            validate_signatures(
                app_auth_pubkey,
                hardware_auth_pubkey,
                recovery_auth_pubkey,
                &challenge,
                &app_signature,
                &hardware_signature,
            )?;

            let delay_end = requirements.delay_end_time;
            if services.recovery.cur_time() < delay_end {
                return Err(RecoveryError::DelayPeriodNotFinished);
            }

            let active_contest = self.active_contest;
            Ok(Transition::next(
                self,
                CompletableRecoveryState {
                    account,
                    recovery,
                    active_contest,
                },
            ))
        } else if let RecoveryEvent::CancelRecovery { key_proof } = event {
            let recovery = self
                .recovery
                .as_ref()
                .ok_or(RecoveryError::NoExistingRecovery)?;

            let requirements = recovery
                .requirements
                .delay_notify_requirements
                .as_ref()
                .unwrap();

            let actor = key_proof.to_actor(ToActorStrategy::PreferNonLostFactor(
                requirements.lost_factor,
            ))?;
            let is_contesting_recovery = actor == requirements.lost_factor;
            let account_id = self.account.clone().id;

            if is_contesting_recovery && self.active_contest {
                // If this recovery is being contested and it was created at a point in time
                //   when there was a contested recovery within 30 days, verify comms
                let scope = CommsVerificationScope::DelayNotifyActor(actor);
                services
                    .comms_verification
                    .consume_verification_for_scope(ConsumeVerificationForScopeInput {
                        account_id: account_id.clone(),
                        scope: scope.clone(),
                    })
                    .await?;
            }

            let status = if is_contesting_recovery {
                RecoveryStatus::CanceledInContest
            } else {
                RecoveryStatus::Canceled
            };

            services
                .recovery
                .complete((recovery.account_id.clone(), recovery.created_at), status)
                .await?;

            // If this recovery is being contested, turn off Mobile Pay
            if is_contesting_recovery {
                services
                    .account
                    .fetch_and_update_spend_limit(FetchAndUpdateSpendingLimitInput {
                        account_id: &account_id,
                        new_spending_limit: self.account.spending_limit.clone().map_or_else(
                            || None,
                            |old_limit| {
                                Some(SpendingLimit {
                                    active: false,
                                    ..old_limit
                                })
                            },
                        ),
                    })
                    .await?;
            }

            let payload = NotificationPayloadBuilder::default()
                .recovery_canceled_delay_period_payload(Some(RecoveryCanceledDelayPeriodPayload {
                    initiation_time: recovery.created_at,
                    lost_factor: requirements.lost_factor,
                }))
                .build()
                .map_err(|_| RecoveryError::GenerateNotificationPayloadError)?;

            services
                .notification
                .send_notification(SendNotificationInput {
                    account_id: &account_id,
                    payload_type: NotificationPayloadType::RecoveryCanceledDelayPeriod,
                    payload: &payload,
                    only_touchpoints: None,
                })
                .await
                .map_err(|_| RecoveryError::SendNotificationError)?;

            let attributes = &[
                KeyValue::new(
                    metrics::LOST_FACTOR_KEY,
                    requirements.lost_factor.to_string(),
                ),
                KeyValue::new(metrics::CREATED_DURING_CONTEST_KEY, self.active_contest),
                KeyValue::new(metrics::CANCELED_IN_CONTEST_KEY, is_contesting_recovery),
            ];
            metrics::DELAY_NOTIFY_CANCELED.add(1, attributes);
            metrics::DELAY_NOTIFY_TIME_TO_CANCEL.record(
                (services.recovery.cur_time() - recovery.created_at).whole_minutes() as u64,
                attributes,
            );

            Ok(Transition::next(self, CanceledRecoveryState {}))
        } else if let RecoveryEvent::UpdateDelayForTestAccountRecovery {
            delay_period_num_sec,
        } = event
        {
            if !self.account.common_fields.properties.is_test_account {
                return Err(RecoveryError::InvalidUpdateForNonTestAccount);
            }

            let recovery = self
                .recovery
                .as_ref()
                .ok_or(RecoveryError::NoExistingRecovery)?;

            let requirements = recovery
                .requirements
                .delay_notify_requirements
                .to_owned()
                .ok_or(RecoveryError::MalformedRecoveryRequirements)?;

            let delay_end_time = if let Some(delay_period) = delay_period_num_sec {
                recovery.created_at + Duration::seconds(delay_period)
            } else {
                requirements.delay_end_time.to_owned()
            };

            let updated_requirements = RecoveryRequirements {
                delay_notify_requirements: Some(DelayNotifyRequirements {
                    delay_end_time,
                    ..requirements
                }),
            };

            services
                .recovery
                .update_recovery_requirements(
                    (recovery.account_id.clone(), recovery.created_at),
                    updated_requirements,
                )
                .await?;

            let updated_recovery = services
                .recovery
                .fetch_pending(&self.account.id, RecoveryType::DelayAndNotify)
                .await?
                .ok_or(RecoveryError::NoExistingRecovery)?;
            return Ok(Transition::next(
                self,
                PendingRecoveryState {
                    recovery: updated_recovery,
                },
            ));
        } else {
            Err(RecoveryError::InvalidTransition)
        }
    }
}

async fn validate_destination(
    services: &RecoveryServices<'_>,
    account_id: &AccountId,
    account_auth_keys: &FullAccountAuthKeys,
    lost_factor: Factor,
    destination: &RecoveryDestination,
) -> Result<(), RecoveryError> {
    // TODO: We should abstract these and make reusable between recovery & onboarding

    // If there's an existing account with the same app auth pubkey, error
    if services
        .account
        .fetch_account_by_app_pubkey(FetchAccountByAuthKeyInput {
            pubkey: destination.app_auth_pubkey,
        })
        .await?
        .is_some()
    {
        return Err(RecoveryError::AppAuthPubkeyReuseAccount);
    }

    // If there's an existing pending recovery with the same destination app auth pubkey, error
    if services
        .recovery
        .fetch_optional_recovery_by_app_auth_pubkey(
            destination.app_auth_pubkey,
            RecoveryStatus::Pending,
        )
        .await?
        .is_some()
    {
        return Err(RecoveryError::AppAuthPubkeyReuseRecovery);
    }

    if let Some(recovery_auth_pubkey) = destination.recovery_auth_pubkey {
        // If there's an existing account with the same recovery auth pubkey, error
        if services
            .account
            .fetch_account_by_recovery_pubkey(FetchAccountByAuthKeyInput {
                pubkey: recovery_auth_pubkey,
            })
            .await?
            .is_some()
        {
            return Err(RecoveryError::RecoveryAuthPubkeyReuseAccount);
        }

        // If there's an existing pending recovery with the same destination recovery auth pubkey, error
        if services
            .recovery
            .fetch_optional_recovery_by_recovery_auth_pubkey(
                recovery_auth_pubkey,
                RecoveryStatus::Pending,
            )
            .await?
            .is_some()
        {
            return Err(RecoveryError::RecoveryAuthPubkeyReuseRecovery);
        }
    }

    // Hardware key shouldn't change for lost App
    if lost_factor == Factor::App
        && account_auth_keys.hardware_pubkey != destination.hardware_auth_pubkey
    {
        return Err(RecoveryError::InvalidRecoveryDestination);
    }

    // If there's an existing account with the same hardware auth pubkey, error
    //   unless it's the creator's account and this is a lost App recovery, in
    //   which case reuse of the hardware auth pubkey is expected
    if let Some(existing_account) = services
        .account
        .fetch_account_by_hw_pubkey(FetchAccountByAuthKeyInput {
            pubkey: destination.hardware_auth_pubkey,
        })
        .await?
    {
        if !(existing_account.get_id() == account_id && lost_factor == Factor::App) {
            return Err(RecoveryError::HwAuthPubkeyReuseAccount);
        }
    }

    // If there's an existing pending recovery with the same destination hardware auth pubkey, error
    if services
        .recovery
        .fetch_optional_recovery_by_hardware_auth_pubkey(
            destination.hardware_auth_pubkey,
            RecoveryStatus::Pending,
        )
        .await?
        .is_some()
    {
        return Err(RecoveryError::HwAuthPubkeyReuseRecovery);
    }

    Ok(())
}

impl TransitionTo<CanceledRecoveryState> for CurrentAccountRecoveryState {}
impl TransitionTo<PendingRecoveryState> for CurrentAccountRecoveryState {}
impl TransitionTo<RotatedKeysetState> for CurrentAccountRecoveryState {}
impl TransitionTo<CompletableRecoveryState> for CurrentAccountRecoveryState {}