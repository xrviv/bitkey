package build.wallet.emergencyaccesskit

/**
 * DO NOT EDIT THIS FILE. IT IS UPDATED AS PART OF THE BUILD AND RELEASE PROCESS.
 *
 * The Emergency Access Kit app version, hash, and URL that is associated with the current release.
 * Inserted into the Emergency Access PDF when it is generated.
 *
 * The release process will build the EAK version of the app, then update this file with the
 * version number, hash, and URL where it is hosted.
 *
 * See [Emergency Access Engineering Design - Release Pipeline](https://docs.google.com/document/d/1frmVxpUnbswWBJRh57u7GiUL_enHUV3wgHckR49q50E/edit#heading=h.klvvm1l9wksq)
 */
data object EmergencyAccessKitAppInformation {
  /** Version number of the Emergency Access Kit app */
  const val ApkVersion: String = ""

  /** SHA-256 of built Apk */
  const val ApkHash: String = ""

  /** URL to the location where the Emergency Access Kit app is hosted */
  const val ApkUrl: String = ""
}
