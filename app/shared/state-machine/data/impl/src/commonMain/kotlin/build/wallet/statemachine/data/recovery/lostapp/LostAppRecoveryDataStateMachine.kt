package build.wallet.statemachine.data.recovery.lostapp

import androidx.compose.runtime.Composable
import build.wallet.bitkey.account.FullAccountConfig
import build.wallet.cloud.backup.CloudBackup
import build.wallet.recovery.Recovery.StillRecovering
import build.wallet.statemachine.core.StateMachine
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressDataStateMachine
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressProps
import build.wallet.statemachine.data.recovery.lostapp.LostAppRecoveryData.LostAppRecoveryInProgressData

/**
 * Manages state of Lost App Recovery:
 * - initiation of Delay & Notify recovery
 * - waiting for Delay period
 * - completion of Delay & Notify recovery
 */
interface LostAppRecoveryDataStateMachine : StateMachine<LostAppRecoveryProps, LostAppRecoveryData>

/**
 * @property fullAccountConfig keybox configuration to use for Lost App recovery.
 * @property account existing account if any. TODO(W-3072): move into state machine as implementation detail.
 */
data class LostAppRecoveryProps(
  val cloudBackup: CloudBackup?,
  val fullAccountConfig: FullAccountConfig,
  val activeRecovery: StillRecovering?,
  val onRollback: () -> Unit,
  val onRetryCloudRecovery: () -> Unit,
)

class LostAppRecoveryDataStateMachineImpl(
  private val recoveryInProgressDataStateMachine: RecoveryInProgressDataStateMachine,
  private val lostAppRecoveryHaveNotStartedDataStateMachine:
    LostAppRecoveryHaveNotStartedDataStateMachine,
) : LostAppRecoveryDataStateMachine {
  @Composable
  override fun model(props: LostAppRecoveryProps): LostAppRecoveryData {
    return when (props.activeRecovery) {
      null ->
        lostAppRecoveryHaveNotStartedDataStateMachine.model(
          LostAppRecoveryHaveNotStartedProps(
            cloudBackup = props.cloudBackup,
            fullAccountConfig = props.fullAccountConfig,
            onRollback = props.onRollback
          )
        )

      else ->
        LostAppRecoveryInProgressData(
          recoveryInProgressData =
            recoveryInProgressDataStateMachine.model(
              RecoveryInProgressProps(
                fullAccountConfig = props.fullAccountConfig,
                recovery = props.activeRecovery,
                oldAppGlobalAuthKey = null,
                onRetryCloudRecovery = props.onRetryCloudRecovery
              )
            )
        )
    }
  }
}
