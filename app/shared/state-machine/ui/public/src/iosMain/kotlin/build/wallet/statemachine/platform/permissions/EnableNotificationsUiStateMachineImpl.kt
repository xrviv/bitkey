package build.wallet.statemachine.platform.permissions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.analytics.events.EventTracker
import build.wallet.analytics.events.screen.id.NotificationsEventTrackerScreenId
import build.wallet.analytics.v1.Action.ACTION_APP_PUSH_NOTIFICATIONS_DISABLED
import build.wallet.analytics.v1.Action.ACTION_APP_PUSH_NOTIFICATIONS_ENABLED
import build.wallet.platform.permissions.Permission.PushNotifications
import build.wallet.platform.permissions.PermissionChecker
import build.wallet.platform.permissions.PermissionStatus.Denied
import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.platform.permissions.EnableNotificationsUiStateMachineImpl.UiState.EnableNotificationsUiState
import build.wallet.statemachine.platform.permissions.EnableNotificationsUiStateMachineImpl.UiState.LoadingUiState
import build.wallet.statemachine.platform.permissions.EnableNotificationsUiStateMachineImpl.UiState.OpenSettingsUiState
import build.wallet.statemachine.platform.permissions.EnableNotificationsUiStateMachineImpl.UiState.ShowingSystemPermissionsUiState
import build.wallet.ui.model.Click
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.button.ButtonModel.Size.Footer
import build.wallet.ui.model.toolbar.ToolbarModel
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString

private const val ENABLE_STRING = "Enable"

actual class EnableNotificationsUiStateMachineImpl actual constructor(
  private val notificationPermissionRequester: NotificationPermissionRequester,
  private val permissionChecker: PermissionChecker,
  private val eventTracker: EventTracker,
) : EnableNotificationsUiStateMachine {
  @Composable
  override fun model(props: EnableNotificationsUiProps): BodyModel {
    var uiState: UiState by remember { mutableStateOf(LoadingUiState) }

    when (uiState) {
      LoadingUiState -> {
        uiState =
          when (permissionChecker.getPermissionStatus(PushNotifications)) {
            // this will only be for IOS
            Denied -> OpenSettingsUiState
            else -> EnableNotificationsUiState
          }
      }

      is ShowingSystemPermissionsUiState -> {
        notificationPermissionRequester.requestNotificationPermission(
          onGranted = {
            eventTracker.track(ACTION_APP_PUSH_NOTIFICATIONS_ENABLED)
            props.onComplete()
          },
          onDeclined = {
            eventTracker.track(ACTION_APP_PUSH_NOTIFICATIONS_DISABLED)
            props.onComplete()
          }
        )
      }

      else -> {}
    }

    return FormBodyModel(
      id = NotificationsEventTrackerScreenId.ENABLE_PUSH_NOTIFICATIONS,
      eventTrackerScreenIdContext = props.eventTrackerContext,
      onBack = props.retreat.onRetreat,
      toolbar =
        ToolbarModel(leadingAccessory = props.retreat.leadingToolbarAccessory),
      header =
        FormHeaderModel(
          headline = "Turn on notifications",
          subline =
            if (uiState == OpenSettingsUiState) {
              "Keep your wallet secure and stay updated. Open your settings to change notification permissions for the Bitkey app."
            } else {
              "Keep your wallet secure and stay updated."
            }
        ),
      primaryButton =
        when (uiState) {
          LoadingUiState ->
            ButtonModel(
              text = "",
              isEnabled = false,
              isLoading = true,
              size = Footer,
              onClick = Click.StandardClick { } // cannot click when `loading`
            )

          is ShowingSystemPermissionsUiState -> {
            ButtonModel(
              text = ENABLE_STRING,
              isEnabled = false,
              isLoading = false,
              size = Footer,
              onClick = Click.StandardClick { } // cannot click when disabled
            )
          }

          is EnableNotificationsUiState -> {
            ButtonModel(
              text = ENABLE_STRING,
              isEnabled = true,
              isLoading = false,
              size = Footer,
              onClick =
                Click.standardClick {
                  uiState = ShowingSystemPermissionsUiState
                }
            )
          }

          is OpenSettingsUiState -> {
            ButtonModel(
              text = "Open settings",
              isEnabled = true,
              isLoading = false,
              size = Footer,
              onClick =
                Click.standardClick {
                  NSURL.URLWithString(UIApplicationOpenSettingsURLString)?.let { settingsURL ->
                    UIApplication.sharedApplication.openURL(settingsURL)
                  }
                }
            )
          }
        }
    )
  }

  private sealed interface UiState {
    /** Indicates the button is loading */
    data object LoadingUiState : UiState

    /** Indicates the button will be used to Enable Notifications */
    data object EnableNotificationsUiState : UiState

    /** Indicates the button will be used to open IOS settings */
    data object OpenSettingsUiState : UiState

    /**
     * When the systems permissions alert is on the button is disabled
     * `showingSystemPermission` is true
     */
    data object ShowingSystemPermissionsUiState : UiState
  }
}