package build.wallet.statemachine.settings.full.feedback

import build.wallet.bitkey.f8e.AccountId
import build.wallet.f8e.F8eEnvironment
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine

/**
 * State machine for showing Feedback contact screen
 */
interface FeedbackUiStateMachine : StateMachine<FeedbackUiProps, ScreenModel>

/**
 * Feedback props
 *
 * @property onBack - invoked once a back action has occurred
 */
data class FeedbackUiProps(
  val f8eEnvironment: F8eEnvironment,
  val accountId: AccountId,
  val onBack: () -> Unit,
)
