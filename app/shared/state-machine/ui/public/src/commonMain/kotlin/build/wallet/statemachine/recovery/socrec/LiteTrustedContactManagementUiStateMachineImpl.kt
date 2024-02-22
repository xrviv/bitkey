package build.wallet.statemachine.recovery.socrec

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.bitkey.socrec.ProtectedCustomer
import build.wallet.statemachine.core.Retreat
import build.wallet.statemachine.core.RetreatStyle
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle
import build.wallet.statemachine.recovery.socrec.help.HelpingWithRecoveryUiProps
import build.wallet.statemachine.recovery.socrec.help.HelpingWithRecoveryUiStateMachine
import build.wallet.statemachine.recovery.socrec.list.lite.LiteListingTrustedContactsUiProps
import build.wallet.statemachine.recovery.socrec.list.lite.LiteListingTrustedContactsUiStateMachine
import build.wallet.statemachine.trustedcontact.TrustedContactEnrollmentUiProps
import build.wallet.statemachine.trustedcontact.TrustedContactEnrollmentUiStateMachine

class LiteTrustedContactManagementUiStateMachineImpl(
  private val liteListingTrustedContactsUiStateMachine: LiteListingTrustedContactsUiStateMachine,
  private val trustedContactEnrollmentUiStateMachine: TrustedContactEnrollmentUiStateMachine,
  private val helpingWithRecoveryUiStateMachine: HelpingWithRecoveryUiStateMachine,
) : LiteTrustedContactManagementUiStateMachine {
  @Composable
  override fun model(props: LiteTrustedContactManagementProps): ScreenModel {
    var state: State by remember {
      mutableStateOf(
        if (props.acceptInvite != null) {
          State.EnrollingAsTrustedContact(inviteCode = props.acceptInvite.inviteCode)
        } else {
          State.ShowingProtectedCustomersList
        }
      )
    }

    return when (val s = state) {
      State.ShowingProtectedCustomersList ->
        liteListingTrustedContactsUiStateMachine.model(
          LiteListingTrustedContactsUiProps(
            onExit = props.onExit,
            protectedCustomers = props.protectedCustomers,
            onRemoveProtectedCustomer = props.actions::removeProtectedCustomer,
            onHelpWithRecovery = { protectedCustomer ->
              state =
                State.HelpingWithRecovery(
                  protectedCustomer = protectedCustomer
                )
            },
            onAcceptInvitePressed = { state = State.EnrollingAsTrustedContact(inviteCode = null) }
          )
        )

      is State.EnrollingAsTrustedContact ->
        trustedContactEnrollmentUiStateMachine.model(
          props =
            TrustedContactEnrollmentUiProps(
              retreat =
                Retreat(
                  style = RetreatStyle.Close,
                  onRetreat = { state = State.ShowingProtectedCustomersList }
                ),
              account = props.accountData.account,
              inviteCode = s.inviteCode,
              acceptInvitation = props.actions::acceptInvitation,
              retrieveInvitation = props.actions::retrieveInvitation,
              onDone = { state = State.ShowingProtectedCustomersList },
              screenPresentationStyle = ScreenPresentationStyle.Modal
            )
        )

      is State.HelpingWithRecovery ->
        helpingWithRecoveryUiStateMachine.model(
          props =
            HelpingWithRecoveryUiProps(
              account = props.accountData.account,
              protectedCustomer = s.protectedCustomer,
              onExit = { state = State.ShowingProtectedCustomersList }
            )
        )
    }
  }
}

private sealed interface State {
  data object ShowingProtectedCustomersList : State

  data class EnrollingAsTrustedContact(val inviteCode: String?) : State

  data class HelpingWithRecovery(val protectedCustomer: ProtectedCustomer) : State
}
