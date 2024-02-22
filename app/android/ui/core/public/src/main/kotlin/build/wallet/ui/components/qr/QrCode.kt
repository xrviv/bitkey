package build.wallet.ui.components.qr

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import build.wallet.ui.components.loading.LoadingIndicator
import build.wallet.ui.components.qr.CellShape.Circle
import build.wallet.ui.components.qr.CellShape.RoundedSquare
import build.wallet.ui.components.qr.CellShape.Square
import build.wallet.ui.tooling.PreviewWalletTheme
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.encoder.ByteMatrix
import com.google.zxing.qrcode.encoder.Encoder

/**
 * @param [data] - content that will be encoded in the QR code. If `null`, loading spinner is shown.
 * @param [cellShape] - shape of the QR code cells
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun QrCode(
  modifier: Modifier = Modifier,
  data: String?,
  cellShape: CellShape = Square,
) {
  val matrix by remember(data) {
    mutableStateOf(
      when (data) {
        null -> null
        else ->
          Encoder.encode(
            data,
            ErrorCorrectionLevel.H,
            mapOf(
              EncodeHintType.CHARACTER_SET to "UTF-8",
              EncodeHintType.MARGIN to 16,
              EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H
            )
          ).matrix
      }
    )
  }

  // Use BoxWithConstraints so that we can derive size constraints from
  // parent layout node. We need those constraints to set appropriate
  // size of our QR code in pixels (while accounting for density).
  BoxWithConstraints(
    modifier = modifier.padding(top = 20.dp)
  ) {
    // Use the most narrow constraint available.
    val qrCodeSizeDp = min(maxWidth, maxHeight)

    when (val m = matrix) {
      null -> {
        Box(modifier = Modifier.size(qrCodeSizeDp)) {
          // Show loading spinner while we are waiting for data
          LoadingIndicator(
            modifier =
              Modifier.size(qrCodeSizeDp / 4)
                .align(Alignment.Center)
          )
        }
      }

      else -> {
        Canvas(
          modifier = Modifier.size(qrCodeSizeDp),
          contentDescription = ""
        ) {
          val cellSize = size.width / m.width
          // draw the individual cells of the qr code, excluding the finder cells
          drawCells(
            matrix = m,
            cellShape = cellShape
          )
          // top-left finder cell
          drawFinderCell(
            cellShape = cellShape,
            cellSize = cellSize,
            topLeft = Offset(0f, 0f)
          )
          // top-right finder cell
          drawFinderCell(
            cellShape = cellShape,
            cellSize = cellSize,
            topLeft = Offset(size.width - FINDER_CELL_SIZE * cellSize, 0f)
          )
          // bottom-left finder cell
          drawFinderCell(
            cellShape = cellShape,
            cellSize = cellSize,
            topLeft = Offset(0f, size.width - FINDER_CELL_SIZE * cellSize)
          )
        }
      }
    }
  }
}

/**
 * Enum describing how the QR code cell should be rendered
 */
enum class CellShape {
  Square,
  Circle,
  RoundedSquare,
}

private fun DrawScope.drawCells(
  matrix: ByteMatrix,
  cellShape: CellShape,
) {
  val cellSize = size.width / matrix.width

  for (cellRow in 0 until matrix.width) {
    for (cellColumn in 0 until matrix.height) {
      if (matrix[cellRow, cellColumn].isColoredCell() &&
        !isFinderCell(cellRow, cellColumn, matrix.width)
      ) {
        drawCell(
          color = Color.Black,
          topLeftOffset =
            Offset(
              x = cellRow * cellSize,
              y = cellColumn * cellSize
            ),
          cellSize = cellSize,
          cellShape = cellShape
        )
      }
    }
  }
}

private fun Byte.isColoredCell() = this == 1.toByte()

private const val FINDER_CELL_SIZE = 7

private fun isFinderCell(
  cellRow: Int,
  cellColumn: Int,
  gridSize: Int,
) = (cellRow < FINDER_CELL_SIZE && cellColumn < FINDER_CELL_SIZE) ||
  (cellRow < FINDER_CELL_SIZE && cellColumn > gridSize - 1 - FINDER_CELL_SIZE) ||
  (cellRow > gridSize - 1 - FINDER_CELL_SIZE && cellColumn < FINDER_CELL_SIZE)

private fun DrawScope.drawCell(
  color: Color,
  topLeftOffset: Offset,
  cellSize: Float,
  cellShape: CellShape,
) {
  when (cellShape) {
    Square ->
      drawRect(
        color = color,
        topLeft = topLeftOffset,
        size = Size(cellSize, cellSize)
      )

    Circle, RoundedSquare ->
      drawCircle(
        color = color,
        center = Offset(topLeftOffset.x + cellSize / 2, topLeftOffset.y + cellSize / 2),
        radius = cellSize / 2
      )
  }
}

private fun DrawScope.drawFinderCell(
  cellShape: CellShape,
  cellSize: Float,
  topLeft: Offset,
) {
  when (cellShape) {
    Square -> drawSquareFinderCell(cellSize, topLeft)
    Circle -> drawCircleFinderCell(cellSize, topLeft)
    RoundedSquare -> drawRoundedSquareFinderCell(cellSize, topLeft)
  }
}

private fun DrawScope.drawCircleFinderCell(
  cellSize: Float,
  topLeftOffset: Offset,
) {
  drawCircle(
    color = Color.Black,
    center =
      Offset(
        topLeftOffset.x + cellSize * FINDER_CELL_SIZE / 2,
        topLeftOffset.y + cellSize * FINDER_CELL_SIZE / 2
      ),
    radius = cellSize * FINDER_CELL_SIZE / 2
  )
  drawCircle(
    color = Color.White,
    center =
      Offset(
        topLeftOffset.x + cellSize * FINDER_CELL_SIZE / 2,
        topLeftOffset.y + cellSize * FINDER_CELL_SIZE / 2
      ),
    radius = cellSize * (FINDER_CELL_SIZE - 2) / 2
  )

  drawCircle(
    color = Color.Black,
    center =
      Offset(
        topLeftOffset.x + cellSize * FINDER_CELL_SIZE / 2,
        topLeftOffset.y + cellSize * FINDER_CELL_SIZE / 2
      ),
    radius = cellSize * (FINDER_CELL_SIZE - 4) / 2
  )
}

private fun DrawScope.drawRoundedSquareFinderCell(
  cellSize: Float,
  topLeft: Offset,
) {
  drawRoundRect(
    color = Color.Black,
    topLeft = topLeft,
    size = Size(cellSize * FINDER_CELL_SIZE, cellSize * FINDER_CELL_SIZE),
    cornerRadius = CornerRadius(cellSize * 2)
  )
  drawRoundRect(
    color = Color.White,
    topLeft = topLeft + Offset(cellSize, cellSize),
    size =
      Size(
        cellSize * (FINDER_CELL_SIZE - 2),
        cellSize * (FINDER_CELL_SIZE - 2)
      ),
    cornerRadius = CornerRadius(cellSize * 2)
  )

  drawRoundRect(
    color = Color.Black,
    topLeft = topLeft + Offset(cellSize * 2, cellSize * 2),
    size =
      Size(
        cellSize * (FINDER_CELL_SIZE - 4),
        cellSize * (FINDER_CELL_SIZE - 4)
      ),
    cornerRadius = CornerRadius(cellSize * 2)
  )
}

private fun DrawScope.drawSquareFinderCell(
  cellSize: Float,
  topLeft: Offset,
) {
  drawRect(
    color = Color.Black,
    topLeft = topLeft,
    size = Size(cellSize * FINDER_CELL_SIZE, cellSize * FINDER_CELL_SIZE)
  )
  drawRect(
    color = Color.White,
    topLeft = topLeft + Offset(cellSize, cellSize),
    size =
      Size(
        cellSize * (FINDER_CELL_SIZE - 2),
        cellSize * (FINDER_CELL_SIZE - 2)
      )
  )

  drawRect(
    color = Color.Black,
    topLeft = topLeft + Offset(cellSize * 2, cellSize * 2),
    size =
      Size(
        cellSize * (FINDER_CELL_SIZE - 4),
        cellSize * (FINDER_CELL_SIZE - 4)
      )
  )
}

@Preview
@Composable
internal fun QrCodePreview() {
  PreviewWalletTheme {
    Box(modifier = Modifier.size(300.dp)) {
      QrCode(data = "https://www.youtube.com/watch?v=dQw4w9WgXcQ")
    }
  }
}
