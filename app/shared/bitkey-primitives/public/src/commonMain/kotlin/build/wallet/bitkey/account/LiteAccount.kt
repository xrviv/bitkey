package build.wallet.bitkey.account

import build.wallet.bitkey.app.AppRecoveryAuthPublicKey
import build.wallet.bitkey.f8e.LiteAccountId

/**
 * Represents Lite Account.
 *
 * Primarily used to assist [FullAccount]s with Social Recovery.
 * Only contains app authentication key in order to communicate with
 * the F8e service and authorize SocRec operations.
 *
 * Social Recovery roles:
 * Lite Account can only have a role of a Trusted Contact - be a Trusted
 * Contact for other [FullAccount]s.
 *
 * @property config determines environment configuration of this account.
 */
data class LiteAccount(
  override val accountId: LiteAccountId,
  override val config: AccountConfig,
  val recoveryAuthKey: AppRecoveryAuthPublicKey,
) : Account
