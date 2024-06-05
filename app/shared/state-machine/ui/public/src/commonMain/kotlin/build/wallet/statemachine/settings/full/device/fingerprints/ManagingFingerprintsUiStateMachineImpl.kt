package build.wallet.statemachine.settings.full.device.fingerprints

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.analytics.events.EventTracker
import build.wallet.analytics.events.screen.EventTrackerCountInfo
import build.wallet.analytics.events.screen.context.NfcEventTrackerScreenIdContext
import build.wallet.firmware.EnrolledFingerprints
import build.wallet.firmware.FingerprintHandle
import build.wallet.firmware.FirmwareFeatureFlag
import build.wallet.home.GettingStartedTask
import build.wallet.home.GettingStartedTaskDao
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle
import build.wallet.statemachine.nfc.NfcSessionUIStateMachine
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps
import build.wallet.statemachine.settings.full.device.EnrolledFingerprintResult
import build.wallet.statemachine.settings.full.device.fingerprints.ManagingFingerprintsUiState.AddingNewFingerprintUiState
import build.wallet.statemachine.settings.full.device.fingerprints.ManagingFingerprintsUiState.CheckingFingerprintsUiState
import build.wallet.statemachine.settings.full.device.fingerprints.ManagingFingerprintsUiState.DeletingFingerprintUiState
import build.wallet.statemachine.settings.full.device.fingerprints.ManagingFingerprintsUiState.EditingFingerprintUiState
import build.wallet.statemachine.settings.full.device.fingerprints.ManagingFingerprintsUiState.ListingFingerprintsUiState
import build.wallet.statemachine.settings.full.device.fingerprints.ManagingFingerprintsUiState.RetrievingEnrolledFingerprintsUiState
import build.wallet.statemachine.settings.full.device.fingerprints.ManagingFingerprintsUiState.SavingFingerprintLabelUiState
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.icon.IconTint
import build.wallet.ui.model.toast.ToastModel

class ManagingFingerprintsUiStateMachineImpl(
  private val nfcSessionUIStateMachine: NfcSessionUIStateMachine,
  private val editingFingerprintUiStateMachine: EditingFingerprintUiStateMachine,
  private val enrollingFingerprintUiStateMachine: EnrollingFingerprintUiStateMachine,
  private val gettingStartedTaskDao: GettingStartedTaskDao,
  private val eventTracker: EventTracker,
) : ManagingFingerprintsUiStateMachine {
  @Composable
  override fun model(props: ManagingFingerprintsProps): ScreenModel {
    var uiState: ManagingFingerprintsUiState by remember {
      mutableStateOf(RetrievingEnrolledFingerprintsUiState())
    }

    return when (val state = uiState) {
      is ListingFingerprintsUiState -> ScreenModel(
        body = ListingFingerprintsBodyModel(
          enrolledFingerprints = state.enrolledFingerprints,
          onBack = props.onBack,
          onEditFingerprint = {
            uiState = EditingFingerprintUiState(
              enrolledFingerprints = state.enrolledFingerprints,
              isExistingFingerprint = true,
              fingerprintToEdit = EditingFingerprintHandle(
                index = it.index,
                label = it.label
              )
            )
          },
          onAddFingerprint = {
            uiState = EditingFingerprintUiState(
              enrolledFingerprints = state.enrolledFingerprints,
              isExistingFingerprint = false,
              fingerprintToEdit = EditingFingerprintHandle(index = it, label = "")
            )
          }
        ),
        presentationStyle = ScreenPresentationStyle.Root,
        toastModel = if (state.fingerprintDeleted) {
          ToastModel(
            title = "Fingerprint deleted",
            leadingIcon = IconModel(
              icon = Icon.SmallIconCheckFilled,
              iconTint = IconTint.Success,
              iconSize = IconSize.Accessory
            ),
            whiteIconStroke = true
          )
        } else if (state.fingerprintAdded) {
          ToastModel(
            title = "Fingerprint added",
            leadingIcon = IconModel(
              icon = Icon.SmallIconCheckFilled,
              iconTint = IconTint.Success,
              iconSize = IconSize.Accessory
            ),
            whiteIconStroke = true
          )
        } else {
          null
        }
      )
      is EditingFingerprintUiState -> ScreenModel(
        body = ListingFingerprintsBodyModel(
          enrolledFingerprints = state.enrolledFingerprints,
          onBack = props.onBack,
          onEditFingerprint = {
            uiState = EditingFingerprintUiState(
              enrolledFingerprints = state.enrolledFingerprints,
              isExistingFingerprint = true,
              fingerprintToEdit = EditingFingerprintHandle(
                index = it.index,
                label = it.label
              )
            )
          },
          onAddFingerprint = {
            uiState = EditingFingerprintUiState(
              enrolledFingerprints = state.enrolledFingerprints,
              isExistingFingerprint = false,
              fingerprintToEdit = EditingFingerprintHandle(index = it, label = "")
            )
          }
        ),
        bottomSheetModel = editingFingerprintUiStateMachine.model(
          EditingFingerprintProps(
            enrolledFingerprints = state.enrolledFingerprints,
            onBack = {
              uiState = ListingFingerprintsUiState(enrolledFingerprints = state.enrolledFingerprints)
            },
            onSave = {
              if (state.isExistingFingerprint) {
                uiState = SavingFingerprintLabelUiState(
                  enrolledFingerprints = state.enrolledFingerprints,
                  fingerprintToSave = EditingFingerprintHandle(
                    index = it.index,
                    originalLabel = state.fingerprintToEdit.originalLabel,
                    currentLabel = it.label
                  )
                )
              } else {
                uiState = AddingNewFingerprintUiState(
                  enrolledFingerprints = state.enrolledFingerprints,
                  fingerprintToAdd = EditingFingerprintHandle(
                    index = it.index,
                    originalLabel = state.fingerprintToEdit.originalLabel,
                    currentLabel = it.label
                  )
                )
              }
            },
            onDeleteFingerprint = {
              uiState = DeletingFingerprintUiState(
                enrolledFingerprints = state.enrolledFingerprints,
                fingerprintToDelete = state.fingerprintToEdit
              )
            },
            originalFingerprintLabel = state.fingerprintToEdit.originalLabel,
            fingerprintToEdit = FingerprintHandle(
              index = state.fingerprintToEdit.index,
              label = state.fingerprintToEdit.currentLabel
            ),
            isExistingFingerprint = state.isExistingFingerprint
          )
        )
      )
      is SavingFingerprintLabelUiState -> nfcSessionUIStateMachine.model(
        NfcSessionUIStateMachineProps(
          session = { session, commands ->
            // In the event the user backed out of enrollment and is now trying to save the label
            // for another fingerprint, cancel any ongoing enrollment.
            commands.cancelFingerprintEnrollment(session)
            commands.setFingerprintLabel(
              session,
              FingerprintHandle(
                index = state.fingerprintToSave.index,
                label = state.fingerprintToSave.currentLabel
              )
            )
            commands.getEnrolledFingerprints(session)
          },
          onSuccess = {
            uiState = ListingFingerprintsUiState(enrolledFingerprints = it)
          },
          onCancel = {
            uiState = EditingFingerprintUiState(
              enrolledFingerprints = state.enrolledFingerprints,
              isExistingFingerprint = true,
              fingerprintToEdit = state.fingerprintToSave
            )
          },
          isHardwareFake = props.account.config.isHardwareFake,
          screenPresentationStyle = ScreenPresentationStyle.Modal,
          eventTrackerContext = NfcEventTrackerScreenIdContext.SAVE_FINGERPRINT_LABEL
        )
      )
      is CheckingFingerprintsUiState -> TODO("W-6590")
      is AddingNewFingerprintUiState -> enrollingFingerprintUiStateMachine.model(
        EnrollingFingerprintProps(
          account = props.account,
          onCancel = {
            uiState = EditingFingerprintUiState(
              enrolledFingerprints = state.enrolledFingerprints,
              isExistingFingerprint = false,
              fingerprintToEdit = state.fingerprintToAdd
            )
          },
          onSuccess = {
            gettingStartedTaskDao.updateTask(
              id = GettingStartedTask.TaskId.AddAdditionalFingerprint,
              state = GettingStartedTask.TaskState.Complete
            )

            eventTracker.track(
              EventTrackerCountInfo(
                eventTrackerCounterId = FingerprintEventTrackerCounterId.FINGERPRINT_ADDED_COUNT,
                count = it.fingerprintHandles.size
              )
            )

            uiState = ListingFingerprintsUiState(it, fingerprintAdded = true)
          },
          fingerprintHandle = FingerprintHandle(
            index = state.fingerprintToAdd.index,
            label = state.fingerprintToAdd.currentLabel
          ),
          enrolledFingerprints = state.enrolledFingerprints
        )
      )
      is DeletingFingerprintUiState -> nfcSessionUIStateMachine.model(
        NfcSessionUIStateMachineProps(
          session = { session, commands ->
            // In the event the user backed out of enrollment and is now trying to delete a
            // different fingerprint, cancel any ongoing enrollment.
            commands.cancelFingerprintEnrollment(session)
            commands.deleteFingerprint(session, state.fingerprintToDelete.index)
            commands.getEnrolledFingerprints(session)
          },
          onSuccess = {
            eventTracker.track(
              EventTrackerCountInfo(
                eventTrackerCounterId = FingerprintEventTrackerCounterId.FINGERPRINT_DELETED_COUNT,
                count = it.fingerprintHandles.size
              )
            )
            uiState = ListingFingerprintsUiState(it, fingerprintDeleted = true)
          },
          onCancel = {
            uiState = EditingFingerprintUiState(
              enrolledFingerprints = state.enrolledFingerprints,
              isExistingFingerprint = true,
              fingerprintToEdit = state.fingerprintToDelete
            )
          },
          isHardwareFake = props.account.config.isHardwareFake,
          screenPresentationStyle = ScreenPresentationStyle.Modal,
          eventTrackerContext = NfcEventTrackerScreenIdContext.DELETE_FINGERPRINT
        )
      )
      is RetrievingEnrolledFingerprintsUiState -> {
        if (state.fwUpdateRequired) {
          LaunchedEffect("fwup-required-for-fingerprints") {
            props.onFwUpRequired()
          }
        }
        nfcSessionUIStateMachine.model(
          NfcSessionUIStateMachineProps(
            session = { session, commands ->
              // Check that the fw supports multiple fingerprints
              val enabled = commands.getFirmwareFeatureFlags(session)
                .find { it.flag == FirmwareFeatureFlag.MULTIPLE_FINGERPRINTS }
                ?.enabled

              when (enabled) {
                true -> {
                  // In the event the user backed out of enrollment, either through a crash or manually,
                  // cancel any ongoing enrollment.
                  commands.cancelFingerprintEnrollment(session)
                  EnrolledFingerprintResult.Success(commands.getEnrolledFingerprints(session))
                }
                else -> EnrolledFingerprintResult.FwUpRequired
              }
            },
            onSuccess = {
              uiState = when (it) {
                EnrolledFingerprintResult.FwUpRequired -> RetrievingEnrolledFingerprintsUiState(
                  fwUpdateRequired = true
                )
                is EnrolledFingerprintResult.Success -> when (props.entryPoint) {
                  EntryPoint.MONEY_HOME -> EditingFingerprintUiState(
                    enrolledFingerprints = it.enrolledFingerprints,
                    isExistingFingerprint = false,
                    fingerprintToEdit = EditingFingerprintHandle(index = 1, label = "")
                  )

                  EntryPoint.DEVICE_SETTINGS -> ListingFingerprintsUiState(it.enrolledFingerprints)
                }
              }
            },
            onCancel = props.onBack,
            isHardwareFake = props.account.config.isHardwareFake,
            screenPresentationStyle = ScreenPresentationStyle.Modal,
            eventTrackerContext = NfcEventTrackerScreenIdContext.GET_ENROLLED_FINGERPRINTS
          )
        )
      }
    }
  }
}

private sealed interface ManagingFingerprintsUiState {
  data class ListingFingerprintsUiState(
    val enrolledFingerprints: EnrolledFingerprints,
    val fingerprintDeleted: Boolean = false,
    val fingerprintAdded: Boolean = false,
  ) : ManagingFingerprintsUiState

  data class CheckingFingerprintsUiState(
    val enrolledFingerprints: EnrolledFingerprints,
  ) : ManagingFingerprintsUiState

  data class EditingFingerprintUiState(
    val enrolledFingerprints: EnrolledFingerprints,
    val isExistingFingerprint: Boolean,
    val fingerprintToEdit: EditingFingerprintHandle,
  ) : ManagingFingerprintsUiState

  data class AddingNewFingerprintUiState(
    val enrolledFingerprints: EnrolledFingerprints,
    val fingerprintToAdd: EditingFingerprintHandle,
  ) : ManagingFingerprintsUiState

  data class SavingFingerprintLabelUiState(
    val enrolledFingerprints: EnrolledFingerprints,
    val fingerprintToSave: EditingFingerprintHandle,
  ) : ManagingFingerprintsUiState

  data class DeletingFingerprintUiState(
    val enrolledFingerprints: EnrolledFingerprints,
    val fingerprintToDelete: EditingFingerprintHandle,
  ) : ManagingFingerprintsUiState

  data class RetrievingEnrolledFingerprintsUiState(
    val fwUpdateRequired: Boolean = false,
  ) : ManagingFingerprintsUiState
}

/**
 * A wrapper around [FingerprintHandle] that includes the original label of the
 * fingerprint to ensure that both the original label and label-in-flight are preserved,
 * such as if the user cancels saving a change and goes back to editing the handle.
 */
private data class EditingFingerprintHandle(
  val index: Int,
  /** The name of the fingerprint when it was read from hardware. */
  val originalLabel: String,
  /** The updated name of the fingerprint due to the user making edits. */
  val currentLabel: String,
) {
  constructor(index: Int, label: String) : this(
    index = index,
    originalLabel = label,
    currentLabel = label
  )
}
