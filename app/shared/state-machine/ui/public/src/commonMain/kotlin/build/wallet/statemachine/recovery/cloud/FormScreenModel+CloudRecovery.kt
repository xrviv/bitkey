package build.wallet.statemachine.recovery.cloud

import build.wallet.analytics.events.screen.id.CloudEventTrackerScreenId.CLOUD_BACKUP_FOUND
import build.wallet.analytics.events.screen.id.CloudEventTrackerScreenId.CLOUD_BACKUP_NOT_FOUND_TROUBLESHOOTING
import build.wallet.analytics.events.screen.id.CloudEventTrackerScreenId.SOCIAL_RECOVERY_EXPLANATION
import build.wallet.analytics.events.screen.id.EventTrackerScreenId
import build.wallet.compose.collections.immutableListOf
import build.wallet.compose.collections.immutableListOfNotNull
import build.wallet.platform.device.DevicePlatform
import build.wallet.platform.device.DevicePlatform.Android
import build.wallet.platform.device.DevicePlatform.IOS
import build.wallet.platform.device.DevicePlatform.Jvm
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.button.ButtonModel.Companion.BitkeyInteractionButtonModel
import build.wallet.ui.model.button.ButtonModel.Size.Footer
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.icon.IconTint
import build.wallet.ui.model.list.ListGroupModel
import build.wallet.ui.model.list.ListGroupStyle
import build.wallet.ui.model.list.ListItemAccessory
import build.wallet.ui.model.list.ListItemAccessoryAlignment
import build.wallet.ui.model.list.ListItemModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.BackAccessory
import build.wallet.ui.model.toolbar.ToolbarModel

expect fun CloudBackupNotFoundBodyModel(
  onBack: () -> Unit,
  onCheckCloudAgain: () -> Unit,
  onCannotAccessCloud: () -> Unit,
  onImportEmergencyAccessKit: (() -> Unit)?,
  onShowTroubleshootingSteps: () -> Unit,
): FormBodyModel

expect fun CloudNotSignedInBodyModel(
  onBack: () -> Unit,
  onCheckCloudAgain: () -> Unit,
  onCannotAccessCloud: () -> Unit,
  onImportEmergencyAccessKit: (() -> Unit)?,
  onShowTroubleshootingSteps: () -> Unit,
): FormBodyModel

fun CloudWarningBodyModel(
  devicePlatform: DevicePlatform,
  id: EventTrackerScreenId,
  headerHeadline: String,
  headerSubline: String,
  onBack: () -> Unit,
  onCheckCloudAgain: () -> Unit,
  onCannotAccessCloud: () -> Unit,
  onImportEmergencyAccessKit: (() -> Unit)?,
) = FormBodyModel(
  id = id,
  onBack = onBack,
  toolbar =
    ToolbarModel(
      leadingAccessory =
        BackAccessory(onClick = onBack)
    ),
  header =
    FormHeaderModel(
      headline = headerHeadline,
      subline = headerSubline
    ),
  mainContentList =
    immutableListOf(
      FormMainContentModel.ListGroup(
        listGroupModel =
          ListGroupModel(
            items =
              immutableListOfNotNull(
                ListItemModel(
                  leadingAccessory =
                    ListItemAccessory.IconAccessory(
                      iconPadding = 12,
                      model =
                        IconModel(
                          icon = Icon.SmallIconAccount,
                          iconSize = IconSize.Small
                        )
                    ),
                  title = when (devicePlatform) {
                    Android, Jvm -> "Sign in to Google Drive"
                    IOS -> "Troubleshoot iCloud"
                  },
                  secondaryText = when (devicePlatform) {
                    Android, Jvm -> null
                    IOS -> "Check why your iCloud account isn’t showing your wallet"
                  },
                  onClick = onCheckCloudAgain,
                  trailingAccessory = ListItemAccessory.drillIcon(IconTint.On30)
                ),
                ListItemModel(
                  leadingAccessory =
                    ListItemAccessory.IconAccessory(
                      iconPadding = 12,
                      model =
                        IconModel(
                          icon = Icon.SmallIconWallet,
                          iconSize = IconSize.Small
                        )
                    ),
                  title = "Recover your Wallet",
                  secondaryText = "Replace a lost Mobile Key by creating a new one",
                  onClick = onCannotAccessCloud,
                  trailingAccessory = ListItemAccessory.drillIcon(IconTint.On30)
                ),
                onImportEmergencyAccessKit?.let {
                  ListItemModel(
                    leadingAccessory =
                      ListItemAccessory.IconAccessory(
                        iconPadding = 12,
                        model =
                          IconModel(
                            icon = Icon.SmallIconRecovery,
                            iconSize = IconSize.Small
                          )
                      ),
                    title = "Import your wallet",
                    secondaryText = "(Advanced) Use your Emergency Access Kit to import wallet.",
                    onClick = it,
                    trailingAccessory = ListItemAccessory.drillIcon(IconTint.On30)
                  )
                }
              ),
            style = ListGroupStyle.DIVIDER
          )
      )
    ),
  primaryButton = null
)

fun CloudBackupTroubleshootingStepsModel(
  onBack: () -> Unit,
  onTryAgain: () -> Unit,
) = FormBodyModel(
  onBack = onBack,
  toolbar =
    ToolbarModel(
      leadingAccessory =
        IconAccessory.CloseAccessory(
          onClick = onBack
        )
    ),
  header = FormHeaderModel(
    headline = "Check your iCloud settings"
  ),
  mainContentList = iCloudTroubleshootingStepsMainContentList(),
  primaryButton = ButtonModel(
    text = "Check again",
    leadingIcon = Icon.SmallIconRefresh,
    onClick = StandardClick(onTryAgain),
    size = Footer
  ),
  id = CLOUD_BACKUP_NOT_FOUND_TROUBLESHOOTING
)

fun iCloudTroubleshootingStepsMainContentList() =
  immutableListOf(
    FormMainContentModel.ListGroup(
      listGroupModel =
        ListGroupModel(
          items =
            immutableListOf(
              ListItemModel(
                leadingAccessory = ListItemAccessory.CircularCharacterAccessory(character = '1'),
                title = "Open iPhone Settings",
                onClick = null
              ),
              ListItemModel(
                leadingAccessory = ListItemAccessory.CircularCharacterAccessory(character = '2'),
                title = "Tap on your Apple ID at the top of the screen",
                onClick = null
              ),
              ListItemModel(
                leadingAccessory = ListItemAccessory.CircularCharacterAccessory(character = '3'),
                title = "Tap “iCloud” and make sure “iCloud Drive” is ON",
                onClick = null
              )
            ),
          style = ListGroupStyle.DIVIDER
        )
    )
  )

fun CloudBackupFoundModel(
  devicePlatform: DevicePlatform,
  onBack: () -> Unit,
  onRestore: () -> Unit,
  showSocRecButton: Boolean,
  onLostBitkeyClick: () -> Unit,
) = FormBodyModel(
  onBack = onBack,
  toolbar =
    ToolbarModel(
      leadingAccessory =
        IconAccessory.BackAccessory(
          onClick = onBack
        ),
      trailingAccessory =
        ToolbarAccessoryModel.ButtonAccessory(
          model =
            ButtonModel(
              text = "I’ve lost my Bitkey device",
              size = ButtonModel.Size.Compact,
              treatment = ButtonModel.Treatment.Tertiary,
              onClick = StandardClick(onLostBitkeyClick)
            )
        ).takeIf { showSocRecButton }
    ),
  header =
    FormHeaderModel(
      headline = "Restore your wallet",
      subline =
        when (devicePlatform) {
          Android, Jvm -> "Access your wallet on this phone with your Google Drive backup of your mobile key and Bitkey device."
          IOS -> "Access your wallet on this phone using the iCloud backup of your mobile key, with approval from your Bitkey device."
        }
    ),
  primaryButton =
    BitkeyInteractionButtonModel(
      text = "Restore Bitkey Wallet",
      onClick = StandardClick(onRestore),
      size = Footer,
      testTag = "restore-bitkey-wallet"
    ),
  id = CLOUD_BACKUP_FOUND
)

fun SocialRecoveryExplanationModel(
  onBack: () -> Unit,
  onContinue: () -> Unit,
) = FormBodyModel(
  id = SOCIAL_RECOVERY_EXPLANATION,
  onBack = onBack,
  toolbar = ToolbarModel(leadingAccessory = BackAccessory(onClick = onBack)),
  header = FormHeaderModel(
    headline = "Recover your wallet using Trusted Contacts"
  ),
  mainContentList =
    immutableListOf(
      FormMainContentModel.ListGroup(
        listGroupModel =
          ListGroupModel(
            header = "What you need to do",
            headerTreatment = ListGroupModel.HeaderTreatment.PRIMARY,
            items =
              immutableListOf(
                ListItemModel(
                  leadingAccessory = ListItemAccessory.CircularCharacterAccessory(character = '1'),
                  leadingAccessoryAlignment = ListItemAccessoryAlignment.TOP,
                  title = "Verify via a Trusted Contact",
                  secondaryText = "You’ll provide a recovery code to one of your Trusted Contacts to enter into their Bitkey app. Once they verify you’re really you, your wallet will be restored to this device."
                ),
                ListItemModel(
                  leadingAccessory = ListItemAccessory.CircularCharacterAccessory(character = '2'),
                  leadingAccessoryAlignment = ListItemAccessoryAlignment.TOP,
                  title = "Pair a new Bitkey device",
                  secondaryText = "Once paired you’ll have a 7-day security waiting period. You can cancel this process anytime and continue using your existing Bitkey device."
                )
              ),
            style = ListGroupStyle.NONE
          )
      )
    ),
  primaryButton =
    ButtonModel(
      text = "Continue",
      onClick = StandardClick(onContinue),
      size = Footer
    )
)
