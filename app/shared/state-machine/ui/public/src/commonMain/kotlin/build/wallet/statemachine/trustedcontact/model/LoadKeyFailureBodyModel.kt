package build.wallet.statemachine.trustedcontact.model

import build.wallet.analytics.events.screen.id.SocialRecoveryEventTrackerScreenId
import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.core.ButtonDataModel
import build.wallet.statemachine.core.ErrorFormBodyModel

fun LoadKeyFailureBodyModel(
  onBack: () -> Unit,
  onRetry: () -> Unit,
): BodyModel {
  return ErrorFormBodyModel(
    title = "We couldn’t complete your enrollment as a Trusted Contact",
    subline = "Please try again.",
    secondaryButton = ButtonDataModel(text = "Back", onClick = onBack),
    primaryButton = ButtonDataModel(text = "Retry", onClick = onRetry),
    eventTrackerScreenId = SocialRecoveryEventTrackerScreenId.TC_ENROLLMENT_LOAD_KEY_FAILURE
  )
}