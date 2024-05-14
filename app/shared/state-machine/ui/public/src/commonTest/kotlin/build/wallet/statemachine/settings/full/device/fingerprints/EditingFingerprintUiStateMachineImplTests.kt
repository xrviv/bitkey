package build.wallet.statemachine.settings.full.device.fingerprints

import app.cash.turbine.plusAssign
import build.wallet.coroutines.turbine.turbines
import build.wallet.firmware.EnrolledFingerprints
import build.wallet.firmware.FingerprintHandle
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.awaitSheetWithBody
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.ui.clickPrimaryButton
import build.wallet.statemachine.ui.clickSecondaryButton
import build.wallet.ui.model.icon.IconImage.LocalImage
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class EditingFingerprintUiStateMachineImplTests : FunSpec({
  val stateMachine = EditingFingerprintUiStateMachineImpl()
  val onBackCalls = turbines.create<Unit>("onBack calls")
  val onSaveCalls = turbines.create<FingerprintHandle>("onSave calls")
  val onDeleteFingerprintCalls = turbines.create<FingerprintHandle>("onDeleteFingerprint calls")
  val enrolledFingerprints = EnrolledFingerprints(
    maxCount = 3,
    fingerprintHandles = listOf(
      FingerprintHandle(index = 0, label = "Left Thumb"),
      FingerprintHandle(index = 1, label = "Right Thumb")
    )
  )

  val props = EditingFingerprintProps(
    enrolledFingerprints = enrolledFingerprints,
    onBack = { onBackCalls += Unit },
    onSave = { onSaveCalls += it },
    onDeleteFingerprint = { onDeleteFingerprintCalls += it },
    fingerprintToEdit = FingerprintHandle(index = 0, label = "Left Thumb"),
    isExistingFingerprint = true
  )

  test("edit fingerprint label") {
    stateMachine.test(props) {
      // Change the fingerprint label
      awaitSheetWithBody<FormBodyModel> {
        mainContentList[0]
          .shouldBeInstanceOf<FormMainContentModel.TextInput>()
          .fieldModel.onValueChange("Right index", 0..0)
      }

      // Click Save fingerprint
      awaitSheetWithBody<FormBodyModel> {
        clickSecondaryButton()
      }

      // The updated fingerprint handle should be emitted to onSave
      onSaveCalls.awaitItem()
        .shouldBe(FingerprintHandle(index = 0, label = "Right index"))
    }
  }

  test("delete fingerprint and confirm") {
    stateMachine.test(props) {
      // Click Delete fingerprint
      awaitSheetWithBody<FormBodyModel> {
        clickPrimaryButton()
      }

      // Confirm deletion
      awaitSheetWithBody<FormBodyModel> {
        clickPrimaryButton()
      }

      // Deleted fingerprint should be emitted to onDeleteFingerprint
      onDeleteFingerprintCalls.awaitItem()
        .shouldBe(FingerprintHandle(index = 0, label = "Left Thumb"))
    }
  }

  test("select delete fingerprint but cancel") {
    stateMachine.test(props) {
      // Click Delete fingerprint
      awaitSheetWithBody<FormBodyModel> {
        primaryButton.shouldNotBeNull()
          .text.shouldBe("Delete fingerprint")
        secondaryButton.shouldNotBeNull()
          .text.shouldBe("Save fingerprint")
        clickPrimaryButton()
      }

      // Cancel the deletion
      awaitSheetWithBody<FormBodyModel> {
        primaryButton.shouldNotBeNull()
          .text.shouldBe("Delete fingerprint")
        secondaryButton.shouldNotBeNull()
          .text.shouldBe("Cancel")
        clickSecondaryButton()
      }

      // Should go back to the first editing screen
      awaitSheetWithBody<FormBodyModel> {
        secondaryButton.shouldNotBeNull().text.shouldBe("Save fingerprint")
      }
    }
  }

  test("onBack calls") {
    stateMachine.test(props) {
      awaitSheetWithBody<FormBodyModel> {
        toolbar.shouldNotBeNull()
          .leadingAccessory.shouldBeInstanceOf<IconAccessory>().model.apply {
            iconModel.iconImage.shouldBe(LocalImage(icon = Icon.SmallIconX))
            onClick.shouldNotBeNull()
              .invoke()
          }
      }

      onBackCalls.awaitItem().shouldBe(Unit)
    }
  }

  test("edit fingerprint label for a new fingerprint") {
    stateMachine.test(props.copy(isExistingFingerprint = false)) {
      // Change the fingerprint label
      awaitSheetWithBody<FormBodyModel> {
        mainContentList[0]
          .shouldBeInstanceOf<FormMainContentModel.TextInput>()
          .fieldModel.onValueChange("Right thumb", 0..0)
      }

      awaitSheetWithBody<FormBodyModel> {
        // The delete button should not be available
        primaryButton.shouldBeNull()

        // Click Start fingerprint
        secondaryButton.shouldNotBeNull().apply {
          text.shouldBe("Start fingerprint")
          onClick.invoke()
        }
      }

      // The fingerprint to enroll should be emitted in onSave
      onSaveCalls.awaitItem()
        .shouldBe(FingerprintHandle(index = 0, label = "Right thumb"))
    }
  }
})