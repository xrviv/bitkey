package build.wallet.statemachine.dev

import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine
import build.wallet.statemachine.data.firmware.FirmwareData
import build.wallet.statemachine.data.keybox.AccountData

/**
 * State machine with debug menu that allows configuring various options for development
 * and debugging purposes. Handles showing the main list as well as screens off of the
 * list. The list UI is managed by [DebugMenuListStateMachine].
 */
interface DebugMenuStateMachine : StateMachine<DebugMenuProps, ScreenModel>

/**
 * @property onClose callback that is called when the debug menu is closed.
 * context and the config has been modified via config options.
 */
data class DebugMenuProps(
  val accountData: AccountData,
  val firmwareData: FirmwareData?,
  val onClose: () -> Unit,
)
