package build.wallet.ui.components.toast

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import build.wallet.android.ui.core.R
import build.wallet.statemachine.core.Icon
import build.wallet.ui.components.icon.IconImage
import build.wallet.ui.components.icon.dp
import build.wallet.ui.components.label.Label
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.icon.IconTint
import build.wallet.ui.model.toast.ToastModel
import build.wallet.ui.tooling.PreviewWalletTheme
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

/*
 A toast is a component that has a leading icon and title text
 It displays temporarily at the bottom of the screen with an animated appearance and dismissal
 */
@Composable
fun Toast(model: ToastModel?) {
  val isVisible by produceState(false, model) {
    value = model != null
    delay(2.5.seconds)
    value = false
  }

  AnimatedVisibility(
    visible = isVisible,
    enter = slideInVertically(
      initialOffsetY = { fullHeight -> fullHeight },
      animationSpec = tween(durationMillis = 300)
    ),
    exit = slideOutVertically(
      targetOffsetY = { fullHeight -> fullHeight },
      animationSpec = tween(durationMillis = 300)
    )
  ) {
    ToastComposable(model)
  }
}

@Composable
private fun ToastComposable(model: ToastModel?) {
  Column(
    modifier = Modifier
      .fillMaxSize(),
    verticalArrangement = Arrangement.Bottom
  ) {
    // Curved left and right corners on the top of the toast
    Row(
      modifier = Modifier
        .offset(0.dp, 0.5.dp)
        .fillMaxWidth()
    ) {
      IconImage(
        model = IconModel(
          icon = Icon.SubtractLeft,
          iconSize = IconSize.Subtract
        ),
        color = Color.Black
      )

      Spacer(modifier = Modifier.weight(1f))

      IconImage(
        model = IconModel(
          icon = Icon.SubtractRight,
          iconSize = IconSize.Subtract
        ),
        color = Color.Black
      )
    }

    Row(
      modifier = Modifier
        .background(Color.Black)
        .padding(start = 24.dp, end = 24.dp)
        .fillMaxWidth()
    ) {
      model?.leadingIcon?.let { icon ->
        Box {
          Box(
            modifier = Modifier
              .padding(top = 20.dp, start = 2.dp)
              .background(Color.White, shape = CircleShape)
              .size(icon.iconSize.dp - 4.dp, icon.iconSize.dp - 4.dp)
          ).takeIf { model.whiteIconStroke }
          IconImage(
            modifier = Modifier
              .padding(top = 18.dp, bottom = 34.dp, end = 8.dp),
            model = icon
          )
        }
      }
      Label(
        modifier = Modifier.padding(top = 18.dp, bottom = 34.dp),
        text = model?.title.orEmpty(),
        style = TextStyle(
          fontSize = 16.sp,
          lineHeight = 24.sp,
          fontFamily = FontFamily(Font(R.font.inter_medium)),
          fontWeight = FontWeight(500),
          color = Color.White
        )
      )
    }
  }
}

@Preview
@Composable
internal fun ToastPreview() {
  PreviewWalletTheme {
    Column {
      ToastComposable(
        model = ToastModel(
          leadingIcon = IconModel(
            icon = Icon.SmallIconCheckFilled,
            iconSize = IconSize.Accessory,
            iconTint = IconTint.Success
          ),
          title = "This is a toast",
          whiteIconStroke = true
        )
      )
    }
  }
}
