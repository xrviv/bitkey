package build.wallet.statemachine.account.create.full.onboard.notifications

import build.wallet.analytics.events.EventTrackerMock
import build.wallet.bitkey.f8e.FullAccountIdMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.f8e.F8eEnvironment
import build.wallet.platform.permissions.PermissionCheckerMock
import build.wallet.platform.settings.SystemSettingsLauncherMock
import build.wallet.platform.web.InAppBrowserNavigatorMock
import build.wallet.statemachine.account.notifications.NotificationPermissionRequesterMock
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.awaitScreenWithBody
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.notifications.NotificationPreferencesProps
import build.wallet.statemachine.notifications.NotificationPreferencesUiStateMachineImpl
import build.wallet.statemachine.notifications.NotificationsPreferencesCachedProviderMock
import build.wallet.ui.model.icon.IconImage
import build.wallet.ui.model.list.ListItemAccessory
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class NotificationPreferencesUiStateMachineImplTests : FunSpec({
  val stateMachine = NotificationPreferencesUiStateMachineImpl(
    permissionChecker = PermissionCheckerMock(),
    notificationsPreferencesCachedProvider = NotificationsPreferencesCachedProviderMock(),
    systemSettingsLauncher = SystemSettingsLauncherMock(),
    notificationPermissionRequester = NotificationPermissionRequesterMock(turbines::create),
    inAppBrowserNavigator = InAppBrowserNavigatorMock(turbines::create),
    eventTracker = EventTrackerMock(turbines::create)
  )

  val props = NotificationPreferencesProps(
    fullAccountId = FullAccountIdMock,
    f8eEnvironment = F8eEnvironment.Production,
    source = NotificationPreferencesProps.Source.Onboarding,
    onBack = {},
    onComplete = {}
  )

  test("show tos if terms not accepted") {
    stateMachine.test(props) {
      // Try and hit "Continue" right away
      awaitScreenWithBody<FormBodyModel> {
        ctaWarning.shouldBeNull()
        primaryButton.shouldNotBeNull().onClick.shouldNotBeNull().invoke()
      }

      // Assert that we show some terms
      awaitScreenWithBody<FormBodyModel> {
        ctaWarning.shouldNotBeNull().text.shouldBe("Agree to our Terms and Privacy Policy to continue.")

        // Simulate tapping the ToS button
        val tosListGroup = mainContentList[4].shouldBeInstanceOf<FormMainContentModel.ListGroup>()
        tosListGroup.listGroupModel.items.first().trailingAccessory.shouldNotBeNull()
          .shouldBeInstanceOf<ListItemAccessory.IconAccessory>().onClick.shouldNotBeNull().invoke()
      }

      // Terms warning should go away
      awaitScreenWithBody<FormBodyModel> {
        ctaWarning.shouldBeNull()
      }

      // Icon should be filled
      awaitScreenWithBody<FormBodyModel> {
        val tosListGroup = mainContentList[4].shouldBeInstanceOf<FormMainContentModel.ListGroup>()
        tosListGroup.listGroupModel.items.first().trailingAccessory.shouldNotBeNull()
          .shouldBeInstanceOf<ListItemAccessory.IconAccessory>()
          .model.iconImage.shouldBe(IconImage.LocalImage(Icon.SmallIconCheckFilled))
      }
    }
  }
})
