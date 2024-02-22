package build.wallet.ui.app.qrcode

import android.Manifest.permission
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview.Builder
import androidx.camera.core.UseCaseGroup
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import build.wallet.logging.LogLevel.Error
import build.wallet.logging.log
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.send.BitcoinQrCodeScanBodyModel
import build.wallet.statemachine.send.QrCodeScanBodyModel
import build.wallet.ui.components.button.Button
import build.wallet.ui.components.icon.IconButton
import build.wallet.ui.components.icon.iconStyle
import build.wallet.ui.components.label.Label
import build.wallet.ui.components.label.LabelTreatment.Unspecified
import build.wallet.ui.components.label.labelStyle
import build.wallet.ui.components.toolbar.Toolbar
import build.wallet.ui.model.icon.IconBackgroundType
import build.wallet.ui.model.icon.IconImage
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.icon.IconTint
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tokens.LabelType
import build.wallet.ui.tooling.PreviewWalletTheme

private val qrCodeViewfinderMargin = 48.dp
private val qrCodeViewfinderBorderRadius = 40.dp

@Composable
fun QrCodeScanScreen(model: QrCodeScanBodyModel) {
  BackHandler(
    onBack = model.onClose
  )
  Box(
    modifier = Modifier.fillMaxSize()
  ) {
    QrCodeScanner(model = model)
    QrCodeScanViewFinder()
    QrCodeScanWidgets(model = model)
  }
}

@Composable
private fun QrCodeScanner(model: QrCodeScanBodyModel) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current

  val hasCameraPermission by remember {
    mutableStateOf(
      value =
        ContextCompat.checkSelfPermission(
          context,
          permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    )
  }

  val previewView =
    remember {
      PreviewView(context)
    }

  val cameraProvider =
    remember {
      ProcessCameraProvider.getInstance(context).get()
    }

  DisposableEffect(cameraProvider) {
    onDispose {
      // when this composable leaves composition, close all instances of camera
      cameraProvider.unbindAll()
    }
  }

  Box(modifier = Modifier.fillMaxSize()) {
    if (hasCameraPermission) {
      AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
          context.createQrCodeImageAnalysisUseCase(
            lifecycleOwner = lifecycleOwner,
            cameraProvider = cameraProvider,
            previewView = previewView,
            onQrCodeDetected = model.onQrCodeScanned
          )

          previewView
        }
      )
    }
  }
}

@Composable
private fun QrCodeScanViewFinder() {
  Canvas(modifier = Modifier.fillMaxSize()) {
    // the width of the view finder is the width of the canvas minus two sides of margin
    val viewFinderWidth = size.width - qrCodeViewfinderMargin.toPx() * 2

    with(drawContext.canvas.nativeCanvas) {
      val checkPoint = saveLayer(null, null)

      // Draw the transparent-black background
      drawRect(Color.Black.copy(alpha = 0.6f))

      // Cut out the QR code view finder from the black region
      drawRoundRect(
        color = Color.Transparent,
        blendMode = BlendMode.Clear,
        cornerRadius = CornerRadius(qrCodeViewfinderBorderRadius.toPx()),
        size =
          Size(
            width = viewFinderWidth,
            // the height is calculated to be the same as the width
            height = viewFinderWidth
          ),
        // offset the top left of the viewport
        topLeft =
          Offset(
            // offset by the intended margin on the x-axis
            x = qrCodeViewfinderMargin.toPx(),
            // offset by half the height minus half the height of the view finder
            y = size.height / 2 - viewFinderWidth / 2
          )
      )
      restoreToCount(checkPoint)
    }

    // Draw the white border on the cut out
    drawRoundRect(
      color = Color.White,
      style = Stroke(width = 3.dp.toPx()),
      cornerRadius = CornerRadius(qrCodeViewfinderBorderRadius.toPx()),
      // same size as the cutout
      size =
        Size(
          width = viewFinderWidth,
          height = viewFinderWidth
        ),
      // same offset as the cutout
      topLeft =
        Offset(
          x = qrCodeViewfinderMargin.toPx(),
          y = size.height / 2 - viewFinderWidth / 2
        )
    )
  }
}

@Composable
private fun QrCodeScanWidgets(model: QrCodeScanBodyModel) {
  BoxWithConstraints(
    modifier =
      Modifier
        .fillMaxSize()
        .systemBarsPadding()
        .padding(20.dp)
  ) {
    Toolbar(
      modifier =
        Modifier
          .background(color = Color.Transparent)
          .align(Alignment.TopCenter),
      leadingContent = {
        IconButton(
          iconModel =
            IconModel(
              icon = Icon.SmallIconX,
              iconSize = IconSize.Accessory,
              iconTint = IconTint.OnTranslucent,
              iconBackgroundType =
                IconBackgroundType.Circle(
                  circleSize = IconSize.Regular,
                  color = IconBackgroundType.Circle.CircleColor.TranslucentBlack
                )
            ),
          color =
            WalletTheme.iconStyle(
              icon = IconImage.LocalImage(Icon.SmallIconX),
              color = Color.Unspecified,
              tint = IconTint.OnTranslucent
            ).color,
          onClick = model.onClose
        )
      },
      middleContent =
        model.headline?.let { headline ->
          {
            Label(
              text = headline,
              style =
                WalletTheme.labelStyle(
                  type = LabelType.Title2,
                  textColor = WalletTheme.colors.translucentForeground,
                  treatment = Unspecified
                )
            )
          }
        }
    )
    model.reticleLabel?.let { caption ->
      Label(
        // adjust label to lower text below view finder
        modifier =
          Modifier
            .align(Alignment.Center)
            .padding(top = maxWidth),
        text = caption,
        style =
          WalletTheme.labelStyle(
            type = LabelType.Body2Bold,
            textColor = WalletTheme.colors.translucentForeground,
            treatment = Unspecified
          )
      )
    }

    Column(
      modifier =
        Modifier
          .align(Alignment.BottomCenter)
    ) {
      model.primaryButton?.let { Button(it) }
      model.secondaryButton?.let {
        Spacer(Modifier.size(16.dp))
        Button(it)
      }
    }
  }
}

private fun Context.createQrCodeImageAnalysisUseCase(
  lifecycleOwner: LifecycleOwner,
  cameraProvider: ProcessCameraProvider,
  previewView: PreviewView,
  onQrCodeDetected: (String) -> Unit,
) {
  val preview =
    Builder()
      .build()
      .apply {
        setSurfaceProvider(previewView.surfaceProvider)
      }
  val selector =
    CameraSelector.Builder()
      .requireLensFacing(CameraSelector.LENS_FACING_BACK)
      .build()

  val imageAnalysis =
    ImageAnalysis.Builder()
      .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
      .build()

  imageAnalysis.setAnalyzer(
    ContextCompat.getMainExecutor(this),
    QrCodeImageAnalyzer(
      onQrCodeDetected = onQrCodeDetected
    )
  )

  val useCaseGroup =
    UseCaseGroup.Builder()
      .addUseCase(preview)
      .addUseCase(imageAnalysis)
      .build()

  try {
    cameraProvider.bindToLifecycle(
      lifecycleOwner,
      selector,
      useCaseGroup
    )
  } catch (e: IllegalStateException) {
    log(Error, throwable = e) { "Unable to bind camera because ${e.localizedMessage}" }
  } catch (e: IllegalArgumentException) {
    log(Error, throwable = e) { "Unable to resolve camera because ${e.localizedMessage}" }
  }
}

@Preview
@Composable
internal fun PreviewQrCodeScanScreenWithoutPasteAddressButton() {
  PreviewWalletTheme(backgroundColor = Color.Transparent) {
    QrCodeScanViewFinder()
    QrCodeScanWidgets(
      model =
        BitcoinQrCodeScanBodyModel(
          showSendToCopiedAddressButton = false,
          onQrCodeScanned = {},
          onEnterAddressClick = {},
          onClose = {},
          onSendToCopiedAddressClick = {}
        )
    )
  }
}

@Preview
@Composable
internal fun PreviewQrCodeScanScreenWithPasteAddressButton() {
  PreviewWalletTheme(backgroundColor = Color.Transparent) {
    QrCodeScanViewFinder()
    QrCodeScanWidgets(
      model =
        BitcoinQrCodeScanBodyModel(
          showSendToCopiedAddressButton = true,
          onQrCodeScanned = {},
          onEnterAddressClick = {},
          onClose = {},
          onSendToCopiedAddressClick = {}
        )
    )
  }
}
