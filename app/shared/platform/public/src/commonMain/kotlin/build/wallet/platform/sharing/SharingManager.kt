package build.wallet.platform.sharing

import build.wallet.platform.data.MimeType
import okio.ByteString

/** Provides functions for sharing content, e.g. from a "share" button. */
interface SharingManager {
  /**
   * Shared code that triggers the platform-specific share sheet for text.
   *
   *  @param text text to share
   *  @param title title of the share sheet
   *  @param completion callback for success or failure
   */
  fun shareText(
    text: String,
    title: String,
    completion: ((Boolean) -> Unit)?,
  )

  /**
   * Shared code that triggers the platform-specific share sheet for some file.
   *
   * @param data data of the file to share
   * @param mimeType type of data
   * @param title title of the share sheet
   */
  fun shareData(
    data: ByteString,
    mimeType: MimeType,
    title: String,
    completion: ((Boolean) -> Unit)?,
  )

  /**
   * Used to call the optional completion block when a share sheet action
   * has been selected.
   */
  fun completed()
}

fun SharingManager.shareInvitation(
  inviteCode: String,
  onCompletion: () -> Unit = {},
  onFailure: () -> Unit = {},
) {
  shareText(
    """Hey! I'm setting you up as a Trusted Contact in my Bitkey bitcoin wallet. If my device ever gets lost, you'd be a huge help with the recovery. 
                
Could you download the app and enter this invite code when you sign up?

INVITE CODE: $inviteCode

https://bitkey.world/links/downloads/trusted-contact#$inviteCode""",
    "BitKey Trusted Contact"
  ) { success ->
    if (success) {
      onCompletion()
    } else {
      onFailure()
    }
  }
}
