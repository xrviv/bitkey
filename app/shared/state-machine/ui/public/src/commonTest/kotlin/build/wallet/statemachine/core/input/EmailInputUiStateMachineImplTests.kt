package build.wallet.statemachine.core.input

import build.wallet.coroutines.turbine.turbines
import build.wallet.email.Email
import build.wallet.email.EmailValidatorMock
import build.wallet.f8e.error.F8eError
import build.wallet.f8e.error.SpecificClientErrorMock
import build.wallet.f8e.error.code.AddTouchpointClientErrorCode
import build.wallet.statemachine.core.awaitScreenWithBody
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormMainContentModel.TextInput
import build.wallet.statemachine.core.input.DataInputStyle.Edit
import build.wallet.statemachine.core.input.DataInputStyle.Enter
import build.wallet.statemachine.core.test
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.ButtonAccessory
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldBeEmpty
import io.kotest.matchers.types.shouldBeInstanceOf

class EmailInputUiStateMachineImplTests : FunSpec({
  val emailValidator = EmailValidatorMock()

  val stateMachine =
    EmailInputUiStateMachineImpl(
      emailValidator = emailValidator
    )

  data class EmailEnteredParams(
    val email: String,
    val errorCallback: (F8eError<AddTouchpointClientErrorCode>) -> Unit,
  )

  val onCloseCalls = turbines.create<Unit>("on close calls")
  val onEmailEnteredCalls = turbines.create<EmailEnteredParams>("on email entered calls")

  val props =
    EmailInputUiProps(
      dataInputStyle = Enter,
      onClose = {
        onCloseCalls.add(Unit)
      },
      onEmailEntered = { email, onError ->
        onEmailEnteredCalls.add(EmailEnteredParams(email.value, onError))
      },
      previousEmail = null,
      skipBottomSheetProvider = { SheetModelMock(it) }
    )

  afterTest {
    emailValidator.reset()
  }

  test("initial email input state") {
    stateMachine.test(props = props) {
      val newValue = "jcole@dreamville.com"
      emailValidator.isValid = true

      awaitScreenWithBody<FormBodyModel> {
        header.shouldNotBeNull().headline.shouldBe("Enter your email address")
        with(mainContentList.first().shouldBeInstanceOf<TextInput>()) {
          fieldModel.value.shouldBeEmpty()
          primaryButton.shouldNotBeNull().isEnabled.shouldBeFalse()
          fieldModel.onValueChange(newValue)
        }
      }

      awaitScreenWithBody<FormBodyModel> {
        with(mainContentList.first().shouldBeInstanceOf<TextInput>()) {
          fieldModel.value.shouldBe(newValue)
          primaryButton.shouldNotBeNull().onClick()
        }
      }

      awaitScreenWithBody<FormBodyModel> {
        with(mainContentList.first().shouldBeInstanceOf<TextInput>()) {
          fieldModel.value.shouldBe(newValue)
          primaryButton.shouldNotBeNull().isLoading.shouldBeTrue()
        }
      }

      onEmailEnteredCalls.awaitItem().email.shouldBe("jcole@dreamville.com")
    }
  }

  test("email is properly initialized when a previous email exists") {
    stateMachine.test(
      props =
        props.copy(
          dataInputStyle = Edit,
          previousEmail = Email("kendrick@tde.com")
        )
    ) {
      awaitScreenWithBody<FormBodyModel> {
        header.shouldNotBeNull().headline.shouldBe("Edit your email address")
        with(mainContentList.first().shouldBeInstanceOf<TextInput>()) {
          fieldModel.value.shouldBe("kendrick@tde.com")
        }
      }
    }
  }

  test("primary button should be enabled with valid email") {
    emailValidator.isValid = true
    stateMachine.test(props) {
      awaitScreenWithBody<FormBodyModel> {
        primaryButton.shouldNotBeNull().isEnabled.shouldBeTrue()
      }
    }
  }

  test("skip flow - onClosed of the errorOverlay should close the sheet") {
    stateMachine.test(props) {
      awaitScreenWithBody<FormBodyModel> {
        toolbar?.trailingAccessory.shouldBeInstanceOf<ButtonAccessory>()
          .model.onClick()
      }

      awaitItem().bottomSheetModel
        .shouldNotBeNull()
        .onClosed()

      awaitItem().bottomSheetModel
        .shouldBeNull()
    }
  }

  test("skipBottomSheetProvider passed as null") {
    stateMachine.test(props.copy(skipBottomSheetProvider = null)) {
      awaitScreenWithBody<FormBodyModel> {
        toolbar.shouldNotBeNull()
          .trailingAccessory.shouldBeNull()
      }
    }
  }

  test("email is trimmed before submitting") {
    stateMachine.test(props.copy(previousEmail = Email("   ex@mple.com   "))) {
      awaitScreenWithBody<FormBodyModel> {
        primaryButton.shouldNotBeNull().onClick()
      }

      onEmailEnteredCalls.awaitItem().email.shouldBe("ex@mple.com")
    }
  }

  test("error - touchpoint already active") {
    emailValidator.isValid = true
    stateMachine.test(props) {
      // Ready to submit
      with(awaitItem()) {
        bottomSheetModel.shouldBeNull()
        body.shouldBeInstanceOf<FormBodyModel>()
          .primaryButton.shouldNotBeNull().onClick()
      }

      // Submitting
      awaitScreenWithBody<FormBodyModel>()

      // Send error in the callback
      val enterEmail = onEmailEnteredCalls.awaitItem()
      enterEmail.errorCallback(
        SpecificClientErrorMock(AddTouchpointClientErrorCode.TOUCHPOINT_ALREADY_ACTIVE)
      )

      // Showing error sheet
      val errorModel =
        awaitItem().bottomSheetModel.shouldNotBeNull()
          .body.shouldBeInstanceOf<FormBodyModel>()
      with(errorModel) {
        with(header.shouldNotBeNull()) {
          headline.shouldBe(
            "The entered email is already registered for notifications on this account."
          )
          sublineModel.shouldNotBeNull().string.shouldBe("Please provide a different email.")
        }
        onBack.shouldNotBeNull().invoke()
      }

      // Error sheet closed
      awaitItem().bottomSheetModel.shouldBeNull()
    }
  }

  test("error - invalid email") {
    emailValidator.isValid = true
    stateMachine.test(props) {
      // Ready to submit
      with(awaitItem()) {
        bottomSheetModel.shouldBeNull()
        body.shouldBeInstanceOf<FormBodyModel>()
          .primaryButton.shouldNotBeNull().onClick()
      }

      // Submitting
      awaitScreenWithBody<FormBodyModel>()

      // Send error in the callback
      val enterEmail = onEmailEnteredCalls.awaitItem()
      enterEmail.errorCallback(
        SpecificClientErrorMock(AddTouchpointClientErrorCode.INVALID_EMAIL_ADDRESS)
      )

      // Showing error sheet
      val errorModel =
        awaitItem().bottomSheetModel.shouldNotBeNull()
          .body.shouldBeInstanceOf<FormBodyModel>()
      with(errorModel) {
        with(header.shouldNotBeNull()) {
          headline.shouldBe(
            "The entered email is not valid."
          )
          sublineModel.shouldNotBeNull().string.shouldBe("Please provide a different email.")
        }
        onBack.shouldNotBeNull().invoke()
      }

      // Error sheet closed
      awaitItem().bottomSheetModel.shouldBeNull()
    }
  }
})
