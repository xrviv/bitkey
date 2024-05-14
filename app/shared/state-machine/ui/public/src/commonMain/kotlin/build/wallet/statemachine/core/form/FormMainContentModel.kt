package build.wallet.statemachine.core.form

import build.wallet.Progress
import build.wallet.compose.collections.emptyImmutableList
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.LabelModel
import build.wallet.statemachine.core.LabelModel.StringModel
import build.wallet.statemachine.core.TimerDirection
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.datetime.DatePickerModel
import build.wallet.ui.model.icon.IconButtonModel
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.input.TextFieldModel
import build.wallet.ui.model.list.ListGroupModel
import build.wallet.ui.model.picker.ItemPickerModel
import dev.zacsweers.redacted.annotations.Redacted
import kotlinx.collections.immutable.ImmutableList

sealed interface FormMainContentModel {
  /**
   * A content object used to add space between elements as needed.
   */
  data class Spacer(
    /** The amount of space, or null if it should try to fill as much space as possible. */
    val height: Float? = null,
  ) : FormMainContentModel

  /**
   * A display list of text items with a title and subtext with a leading icon aligned to the
   * top-left of the title and text.
   */
  data class Explainer(
    val items: ImmutableList<Statement>,
  ) : FormMainContentModel {
    data class Statement(
      val leadingIcon: Icon? = null,
      val title: String?,
      val body: LabelModel,
      val treatment: Treatment = Treatment.PRIMARY,
    ) {
      enum class Treatment {
        PRIMARY,
        WARNING,
      }

      constructor(
        leadingIcon: Icon? = null,
        title: String?,
        body: String,
        treatment: Treatment = Treatment.PRIMARY,
      ) :
        this(leadingIcon, title, StringModel(body), treatment)
    }
  }

  /**
   * A display list of data with a left-aligned label and a right-aligned primary and secondary
   * data and an optional "total" row that will be displayed at the bottom.
   */
  @Redacted
  data class DataList(
    val hero: DataHero? = null,
    val items: ImmutableList<Data>,
    val total: Data? = null,
    val buttons: ImmutableList<ButtonModel> = emptyImmutableList(),
  ) : FormMainContentModel {
    init {
      require(items.isNotEmpty())
    }

    /**
     * A section of a data list that is shown at the top of the data set
     *
     * @property image - the image shown in the hero
     * @property title - optional title of the hero, null when there is none
     * @property subtitle - optional subtitle of the hero, null when there is none
     * @property button - an optional action button in the hero
     */
    data class DataHero(
      val image: IconModel?,
      val title: String?,
      val subtitle: String?,
      val button: ButtonModel?,
    )

    data class Data(
      val title: String,
      val titleIcon: IconModel? = null,
      val onTitle: (() -> Unit)? = null,
      val sideText: String,
      val sideTextType: SideTextType = SideTextType.MEDIUM,
      val sideTextTreatment: SideTextTreatment = SideTextTreatment.PRIMARY,
      val secondarySideText: String? = null,
      val secondarySideTextType: SideTextType = SideTextType.REGULAR,
      val secondarySideTextTreatment: SideTextTreatment = SideTextTreatment.SECONDARY,
      val showBottomDivider: Boolean = false,
      val explainer: Explainer? = null,
      val onClick: (() -> Unit)? = null,
    ) {
      enum class SideTextType { REGULAR, MEDIUM, BOLD, BODY2BOLD }

      enum class SideTextTreatment { PRIMARY, SECONDARY, WARNING, STRIKETHROUGH }

      data class Explainer(val title: String, val subtitle: String, val iconButton: IconButtonModel? = null)
    }
  }

  /**
   * A selectable list of fee options.
   * Only one option can be selected at a time, like a radio button.
   */
  @Redacted
  data class FeeOptionList(
    val options: ImmutableList<FeeOption>,
  ) : FormMainContentModel {
    init {
      require(options.isNotEmpty())
    }

    /**
     * UI Model for showing a fee option in a [FeeOptionList]
     *
     * @property optionName - the title text of the option
     * @property transactionTime - the estimated settle time of the option
     * @property transactionFee - the cost of the selected option
     * @property selected - whether the option is currently selected or not
     * @property enabled - whether the option is enabled and able to be selected
     * @property infoText - the text to be shown in the info box of an option, null when none available
     * @property onClick - click handler for an option, null when there is none
     */
    data class FeeOption(
      val optionName: String,
      val transactionTime: String,
      val transactionFee: String,
      val selected: Boolean,
      val enabled: Boolean,
      val infoText: String? = null,
      val onClick: (() -> Unit)?,
    )
  }

  /**
   * An input field specifically for verification codes
   * This is distinct from [TextInput] because verification code inputs display text beneath
   * the input.
   * TODO (W-2828): Enable "text" as a main content to remove this custom main content type
   */
  data class VerificationCodeInput(
    val fieldModel: TextFieldModel,
    val resendCodeContent: ResendCodeContent,
    val skipForNowContent: SkipForNowContent,
  ) : FormMainContentModel {
    sealed interface ResendCodeContent {
      data class Text(val value: String) : ResendCodeContent

      data class Button(val value: ButtonModel) : ResendCodeContent {
        constructor(onSendCodeAgain: () -> Unit, isLoading: Boolean) : this(
          value =
            ButtonModel(
              text = "Send code again",
              isLoading = isLoading,
              treatment = ButtonModel.Treatment.Tertiary,
              size = ButtonModel.Size.Compact,
              onClick = StandardClick(onSendCodeAgain)
            )
        )
      }
    }

    sealed interface SkipForNowContent {
      data object Hidden : SkipForNowContent

      data class Showing(
        val text: String,
        val button: ButtonModel,
      ) : SkipForNowContent {
        constructor(
          text: String,
          onSkipForNow: () -> Unit,
        ) : this(
          text = text,
          button =
            ButtonModel(
              text = "Skip for now",
              treatment = ButtonModel.Treatment.Tertiary,
              size = ButtonModel.Size.Compact,
              onClick = StandardClick(onSkipForNow)
            )
        )
      }
    }
  }

  /**
   * A text input field.
   * @property title - Optional text shown above the input field to describe what it is for
   */
  @Redacted
  data class TextInput(
    val title: String? = null,
    val fieldModel: TextFieldModel,
  ) : FormMainContentModel

  /**
   * A multiline input field.
   *
   * @property title - Optional text shown above the text area to describe what it is for
   */
  @Redacted
  data class TextArea(
    val title: String? = null,
    val fieldModel: TextFieldModel,
  ) : FormMainContentModel

  /**
   * An input field with an optional trailing button contained inside
   * for pasting clipboard contents
   */
  @Redacted
  data class AddressInput(
    val fieldModel: TextFieldModel,
    val trailingButtonModel: ButtonModel?,
  ) : FormMainContentModel

  /**
   * A field allowing user to pick a date
   */
  @Redacted
  data class DatePicker(
    val title: String? = null,
    val fieldModel: DatePickerModel,
  ) : FormMainContentModel

  data class Picker(
    val title: String? = null,
    val fieldModel: ItemPickerModel<*>,
  ) : FormMainContentModel

  /**
   * A circular progress indicator to display a countdown by showing progress
   * along the circle. The title and subtitle are displayed inside the circle.
   * @param timerProgress: The progress as a percentage
   * @param timerRemainingSeconds: The progress as the remaining time in seconds.
   * @param direction: The direction of the timer (clockwise, filling - counter-clockwise, emptying)
   * iOS uses this to create an animation for the progress, rather that 1 second
   * delayed model updates.
   */
  data class Timer(
    val title: String,
    val subtitle: String,
    val timerProgress: Progress,
    val direction: TimerDirection,
    val timerRemainingSeconds: Long,
  ) : FormMainContentModel

  /**
   * Used to embed a Web View within a form
   */
  data class WebView(
    val url: String,
  ) : FormMainContentModel

  /**
   * Will display a list using the [ListGroupModel]
   */
  data class ListGroup(
    val listGroupModel: ListGroupModel,
  ) : FormMainContentModel

  /**
   * Allows a [ButtonModel] to be rendered in the [FormMainContentModel] list
   * @property item - the [ButtonModel] to be rendered
   */
  data class Button(
    val item: ButtonModel,
  ) : FormMainContentModel

  /**
   * A linear progress indicator with multiple labels, evenly spaced along
   * the bottom of the bar.
   */
  data class StepperIndicator(
    // TODO(W-8034): use Progress type.
    val progress: Float,
    val labels: ImmutableList<String>,
  ) : FormMainContentModel

  data object Loader : FormMainContentModel

  /**
   * Shows a hero of the Money Home screen with the given primary and secondary amount
   * display strings.
   */
  data class MoneyHomeHero(
    val primaryAmount: String,
    val secondaryAmount: String,
  ) : FormMainContentModel
}
