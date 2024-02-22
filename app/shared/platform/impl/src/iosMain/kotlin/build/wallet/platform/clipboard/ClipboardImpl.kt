package build.wallet.platform.clipboard

import build.wallet.platform.PlatformContext
import build.wallet.platform.clipboard.ClipItem.PlainText
import platform.UIKit.UIPasteboard

actual class ClipboardImpl actual constructor(
  platformContext: PlatformContext,
) : Clipboard {
  override fun setItem(item: ClipItem) {
    val pasteboard = UIPasteboard.generalPasteboard
    when (item) {
      is PlainText -> pasteboard.string = item.data
    }
  }

  override fun getPlainTextItem(): PlainText? {
    val pasteboard = UIPasteboard.generalPasteboard
    return pasteboard.string?.let {
      PlainText(it)
    }
  }

  override fun getPlainTextItemAndroid(): PlainText? {
    // no-op
    return null
  }
}
