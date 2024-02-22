package build.wallet.bitkey.keys.app

import build.wallet.crypto.AsymmetricKey
import build.wallet.crypto.CurveType
import build.wallet.crypto.PublicKey

/**
 * An app key is a key that is generated by the app and stored in the app's keystore.
 */
interface AppKey : AsymmetricKey {
  companion object {
    fun fromPublicKey(value: String) = AppKeyImpl(CurveType.SECP256K1, PublicKey(value), null)

    fun fromPublicKey(
      value: String,
      curveType: CurveType,
    ) = AppKeyImpl(curveType, PublicKey(value), null)
  }
}