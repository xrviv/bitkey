package build.wallet.recovery

import build.wallet.bitkey.app.AppGlobalAuthPublicKey
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.cloud.backup.csek.SealedCsek
import build.wallet.f8e.F8eEnvironment
import com.github.michaelbull.result.Result

interface RecoveryAuthCompleter {
  /**
   * Complete rotation of auth keys for recovery.
   *
   * @param fullAccountId account retrieved from f8e during recovery initiation phase.
   * @param challenge challenge to be signed by [destinationAppGlobalAuthPubKey]'s
   * private key. The signed challenge will be used by f8e to approve completion of recovery.
   * @param destinationAppGlobalAuthPubKey app's new [AppGlobalAuthPublicKey].
   *
   * @return f8e tokens after successful authentication.
   */
  suspend fun rotateAuthKeys(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    challenge: ChallengeToCompleteRecovery,
    hardwareSignedChallenge: SignedChallengeToCompleteRecovery,
    destinationAppGlobalAuthPubKey: AppGlobalAuthPublicKey,
    sealedCsek: SealedCsek,
  ): Result<Unit, Throwable>
}