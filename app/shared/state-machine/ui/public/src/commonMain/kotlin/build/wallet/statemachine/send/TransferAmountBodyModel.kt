package build.wallet.statemachine.send

import build.wallet.analytics.events.screen.EventTrackerScreenInfo
import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.core.Icon.SmallIconBitkey
import build.wallet.statemachine.core.LabelModel
import build.wallet.statemachine.core.LabelModel.StringWithStyledSubstringModel.Color.ON60
import build.wallet.statemachine.keypad.KeypadModel
import build.wallet.statemachine.money.amount.MoneyAmountEntryModel
import build.wallet.statemachine.moneyhome.card.CardModel
import build.wallet.statemachine.moneyhome.card.CardModel.CardStyle.Outline
import build.wallet.statemachine.send.TransferScreenBannerModel.AmountEqualOrAboveBalanceBannerModel
import build.wallet.statemachine.send.TransferScreenBannerModel.HardwareRequiredBannerModel
import build.wallet.ui.model.Click
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.button.ButtonModel.Size.Footer
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.BackAccessory
import build.wallet.ui.model.toolbar.ToolbarMiddleAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarModel

data class TransferAmountBodyModel(
  override val onBack: () -> Unit,
  val toolbar: ToolbarModel,
  val amountModel: MoneyAmountEntryModel,
  val keypadModel: KeypadModel,
  val primaryButton: ButtonModel,
  val cardModel: CardModel?,
  val amountDisabled: Boolean,
  val onSwapCurrencyClick: () -> Unit,
  // We don't want to track this for privacy reasons
  override val eventTrackerScreenInfo: EventTrackerScreenInfo? = null,
) : BodyModel() {
  constructor(
    onBack: () -> Unit,
    balanceTitle: String,
    amountModel: MoneyAmountEntryModel,
    keypadModel: KeypadModel,
    bannerModel: TransferScreenBannerModel?,
    continueButtonEnabled: Boolean,
    amountDisabled: Boolean,
    onSendMaxClick: () -> Unit,
    onContinueClick: () -> Unit,
    onSwapCurrencyClick: () -> Unit,
    onHardwareRequiredClick: () -> Unit,
  ) : this(
    onBack = onBack,
    toolbar =
      ToolbarModel(
        leadingAccessory = BackAccessory(onClick = onBack),
        middleAccessory =
          ToolbarMiddleAccessoryModel(
            title = "Amount",
            subtitle = balanceTitle
          )
      ),
    amountModel = amountModel,
    keypadModel = keypadModel,
    primaryButton =
      ButtonModel(
        text = "Continue",
        isEnabled = continueButtonEnabled,
        size = Footer,
        onClick = Click.standardClick { onContinueClick() }
      ),
    cardModel =
      when (bannerModel) {
        is AmountEqualOrAboveBalanceBannerModel ->
          CardModel(
            title =
              LabelModel.StringWithStyledSubstringModel.from(
                string = "Send Max (balance minus fees)",
                substringToColor =
                  mapOf(
                    "(balance minus fees)" to ON60
                  )
              ),
            subtitle = null,
            leadingImage = null,
            content = null,
            style = Outline,
            onClick = onSendMaxClick
          )
        is HardwareRequiredBannerModel ->
          CardModel(
            title =
              LabelModel.StringWithStyledSubstringModel.from(
                string = "Bitkey approval required",
                substringToColor = emptyMap()
              ),
            subtitle = null,
            leadingImage = CardModel.CardImage.StaticImage(SmallIconBitkey),
            content = null,
            style = Outline
          )

        TransferScreenBannerModel.F8eUnavailableBannerModel ->
          CardModel(
            title =
              LabelModel.StringWithStyledSubstringModel.from(
                string = "Mobile Pay Unavailable",
                substringToColor = emptyMap()
              ),
            subtitle = null,
            leadingImage = CardModel.CardImage.StaticImage(SmallIconBitkey),
            content = null,
            style = Outline,
            onClick = onHardwareRequiredClick
          )
        null -> null
      },
    amountDisabled = amountDisabled,
    onSwapCurrencyClick = onSwapCurrencyClick
  )
}
