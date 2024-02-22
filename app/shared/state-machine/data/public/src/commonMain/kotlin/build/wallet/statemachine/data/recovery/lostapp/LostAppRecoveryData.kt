package build.wallet.statemachine.data.recovery.lostapp

import build.wallet.auth.AccountAuthTokens
import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.factor.PhysicalFactor
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.bitkey.hardware.HwSpendingPublicKey
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.f8e.recovery.InitiateHardwareAuthService.AuthChallenge
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressData
import build.wallet.statemachine.data.recovery.lostapp.cloud.RecoveringKeyboxFromCloudBackupData
import build.wallet.statemachine.data.recovery.verification.RecoveryNotificationVerificationData

/**
 * Describes Lost App DN recovery state.
 */
sealed interface LostAppRecoveryData {
  /**
   * Indicates that there is no active server recovery present, therefore we will present
   * all options available to the customer to start one.
   */
  sealed interface LostAppRecoveryHaveNotStartedData : LostAppRecoveryData {
    /**
     * Indicates that we are attempting to start Lost App recovery
     */
    sealed interface StartingLostAppRecoveryData : LostAppRecoveryHaveNotStartedData {
      data class AttemptingCloudRecoveryLostAppRecoveryDataData(
        val data: RecoveringKeyboxFromCloudBackupData,
      ) : StartingLostAppRecoveryData

      /**
       * Indicates that we are in process of initiating a Lost App recovery.
       */
      sealed interface InitiatingLostAppRecoveryData : StartingLostAppRecoveryData {
        /**
         * Indicates that we are waiting for hardware to generate and share its keys.
         *
         * @property addHardwareAuthKey should move to [InitiatingAppAuthWithF8eData].
         */
        data class AwaitingAppKeysData(
          val addHardwareAuthKey: (hardwareKeys: HwAuthPublicKey) -> Unit,
          val rollback: () -> Unit,
        ) : InitiatingLostAppRecoveryData

        /**
         * Indicates that we are awaiting approval of push notifications
         */
        data class AwaitingPushNotificationPermissionData(
          val onComplete: () -> Unit,
          val onRetreat: () -> Unit,
        ) : InitiatingLostAppRecoveryData

        /**
         * Indicates that we are initiating hardware authentication with f8e by requesting
         * a challenge to be signed by hardware. Should move to [AwaitingAppSignedAuthChallengeData].
         */
        data class InitiatingAppAuthWithF8eData(
          val rollback: () -> Unit,
        ) : InitiatingLostAppRecoveryData

        /**
         * Indicates that we failed to initiate hardware authentication with f8e
         * in order to initiate recovery.
         */
        data class FailedToInitiateAppAuthWithF8eData(
          val retry: () -> Unit,
          val rollback: () -> Unit,
        ) : InitiatingLostAppRecoveryData

        /**
         * Indicates that we are waiting for hardware to sign authentication challenge for f8e.
         *
         * @property addSignedChallenge uses provided hardware signed challenge to move to
         * [InitiatingLostAppRecoveryWithF8eData].
         */
        data class AwaitingAppSignedAuthChallengeData(
          val challenge: AuthChallenge,
          val addSignedChallenge: (String) -> Unit,
          val rollback: () -> Unit,
        ) : InitiatingLostAppRecoveryData

        /**
         * Indicates that we are waiting for hardware to sign authentication challenge for f8e.
         */
        data class AuthenticatingWithF8EViaAppData(
          val rollback: () -> Unit,
        ) : InitiatingLostAppRecoveryData

        data class ListingKeysetsFromF8eData(
          val rollback: () -> Unit,
        ) : InitiatingLostAppRecoveryData

        /**
         * Indicates that we failed to authenticate with f8e using hardware in order to initiate
         * recovery.
         */
        data class FailedToAuthenticateWithF8EViaAppData(
          val retry: () -> Unit,
          val rollback: () -> Unit,
        ) : InitiatingLostAppRecoveryData

        /**
         * Indicates that we are waiting for hardware to sign for proof of possession
         * so that we can be properly authenticated for the initiate delay and notify call.
         *
         * @property onComplete should move to [InitiatingAppAuthWithF8eData].
         */
        data class AwaitingHardwareProofOfPossessionAndSpendingKeyData(
          val authTokens: AccountAuthTokens,
          val fullAccountId: FullAccountId,
          val network: BitcoinNetworkType,
          val existingHwSpendingKeys: List<HwSpendingPublicKey>,
          val onComplete: (HwFactorProofOfPossession, HwSpendingPublicKey) -> Unit,
          val rollback: () -> Unit,
        ) : InitiatingLostAppRecoveryData

        /**
         * Indicates that we are in process of initiating recovery with f8e.
         */
        data class InitiatingLostAppRecoveryWithF8eData(
          val rollback: () -> Unit,
        ) : InitiatingLostAppRecoveryData

        /**
         * Indicates that we failed to perform the last step initiating recovery process with f8e.
         */
        data class FailedToInitiateLostAppWithF8eData(
          val error: Error,
          val retry: () -> Unit,
          val rollback: () -> Unit,
        ) : InitiatingLostAppRecoveryData

        /**
         * Indicates that we are showing the notification verification flow for additional security
         * before initiating the recovery. Only required in some circumstances, and the server will
         * let us know it is necessary via [COMMS_VERIFICATION_REQUIRED] 4xx error code
         */
        data class VerifyingNotificationCommsData(
          val data: RecoveryNotificationVerificationData,
          val lostFactor: PhysicalFactor,
        ) : InitiatingLostAppRecoveryData

        data class DisplayingConflictingRecoveryData(
          val onCancelRecovery: () -> Unit,
          val onRetreat: () -> Unit,
        ) : InitiatingLostAppRecoveryData

        data object CancellingConflictingRecoveryData : InitiatingLostAppRecoveryData

        data class FailedToCancelConflictingRecoveryData(
          val onAcknowledge: () -> Unit,
        ) : InitiatingLostAppRecoveryData
      }
    }
  }

  /**
   * Indicates that we are undergoing a lost-app recovery that we started on the server.
   */
  data class LostAppRecoveryInProgressData(
    val recoveryInProgressData: RecoveryInProgressData,
  ) : LostAppRecoveryData
}
