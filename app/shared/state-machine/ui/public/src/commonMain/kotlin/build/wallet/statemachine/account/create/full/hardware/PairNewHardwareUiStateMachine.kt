package build.wallet.statemachine.account.create.full.hardware

import build.wallet.analytics.events.screen.context.PairHardwareEventTrackerScreenIdContext
import build.wallet.bitkey.account.FullAccountConfig
import build.wallet.bitkey.app.AppGlobalAuthPublicKey
import build.wallet.nfc.transaction.PairingTransactionResponse.FingerprintEnrolled
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle
import build.wallet.statemachine.core.StateMachine

/** UI State Machine for navigating the pairing of a new hardware device. */
interface PairNewHardwareUiStateMachine :
  StateMachine<PairNewHardwareProps, ScreenModel>

data class PairNewHardwareProps(
  val request: Request,
  val onExit: () -> Unit,
  val eventTrackerContext: PairHardwareEventTrackerScreenIdContext,
  val screenPresentationStyle: ScreenPresentationStyle,
) {
  /**
   * Request data to pair a new hardware device.
   */
  sealed interface Request {
    /**
     * Allows parent state machine to prepare any async data before the hardware pairing process
     * begins. When in this state, the NFC command will not be sent to the hardware until
     * [Ready].
     */
    data object Preparing : Request

    /**
     * Indicates that the hardware is ready to be paired.
     *
     * @param [appGlobalAuthPublicKey] app global auth public key to be signed by the hardware.
     * Signature will be included in the response [FingerprintEnrolled].
     */
    data class Ready(
      val fullAccountConfig: FullAccountConfig,
      val appGlobalAuthPublicKey: AppGlobalAuthPublicKey,
      val onSuccess: (FingerprintEnrolled) -> Unit,
    ) : Request
  }
}
