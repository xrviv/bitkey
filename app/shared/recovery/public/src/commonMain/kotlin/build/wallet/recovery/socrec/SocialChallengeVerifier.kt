package build.wallet.recovery.socrec

import build.wallet.bitkey.account.Account
import build.wallet.bitkey.socrec.TrustedContactIdentityKey
import com.github.michaelbull.result.Result

/**
 * Utility for verifying a social challenge as a trusted contact, encrypting the shared secret, and responding to the challenge.
 */
interface SocialChallengeVerifier {
  /**
   * Completes all the steps of the trusted contact social recovery process.
   *
   * @param account - the account of the trusted contact
   * @param recoveryRelationshipId - the id of the recovery relationship between the protected customer and the trusted contact
   * @param code - the code sent by the protected customer to the trusted contact
   */
  suspend fun verifyChallenge(
    account: Account,
    trustedContactIdentityKey: TrustedContactIdentityKey,
    recoveryRelationshipId: String,
    code: String,
  ): Result<Unit, SocialChallengeError>
}

/**
 * Errors that can occur during the social challenge verification process as the trusted contact.
 */
sealed class SocialChallengeError(
  cause: Throwable,
  message: String? = null,
) : Error(message, cause) {
  data class UnableToVerifyChallengeError(
    override val cause: Throwable,
  ) : SocialChallengeError(cause = cause)

  data class UnableToEncryptSharedSecretError(override val cause: Throwable) : SocialChallengeError(
    cause = cause
  )

  data class UnableToRespondToChallengeError(override val cause: Throwable) : SocialChallengeError(
    cause = cause
  )
}
