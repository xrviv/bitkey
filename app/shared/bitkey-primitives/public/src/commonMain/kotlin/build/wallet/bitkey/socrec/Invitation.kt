package build.wallet.bitkey.socrec

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * Represents the Protected Customer's view of an invitation to become a Trusted Contact.
 *
 * Used in the Protected Customer experience to represent the invite being created / pending / expired
 * as well as in the Trusted Contact experience to represent the invite being retrieved / accepted.
 *
 * @param trustedContactAlias The alias of the contact being requested to offer protection with
 *  this invitation. Note: unused and just a blank string when [Invitation] is used in the context
 *  of the Trusted Contact experience
 *  @param code the code generated by f8e to be concatenated with the PAKE part to form
 *  the full invite code. The TC will use this code to accept the invitation. The code is always
 *  in hex and left justified.
 *  @param codeBitLength [code] length in bits.
 */
data class Invitation(
  override val recoveryRelationshipId: String,
  override val trustedContactAlias: TrustedContactAlias,
  val code: String,
  val codeBitLength: Int,
  val expiresAt: Instant,
) : RecoveryContact {
  fun isExpired(clock: Clock) = expiresAt < clock.now()

  fun isExpired(now: Long) = expiresAt.toEpochMilliseconds() < now
}
