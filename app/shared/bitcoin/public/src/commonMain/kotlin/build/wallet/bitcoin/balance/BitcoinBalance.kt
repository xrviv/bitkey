package build.wallet.bitcoin.balance

import build.wallet.money.BitcoinMoney

/**
 * Represents the balance of a Bitcoin wallet.
 *
 * @property immature All coinbase outputs not yet matured. Not spendable.
 * @property trustedPending Unconfirmed UTXOs generated by a wallet transaction. Not spendable.
 * @property untrustedPending Unconfirmed UTXOs received from an external wallet. Not spendable.
 * @property confirmed Confirmed and immediately spendable balance.
 * @property spendable Sum of [trustedPending] and [confirmed] coins.
 * @property total The total balance visible to the wallet.
 *
 * @throws IllegalArgumentException if any of the balances are not in Bitcoin.
 * @throws IllegalArgumentException if the [total] balance is not the sum of [immature],
 * [trustedPending], [untrustedPending], and [confirmed].
 * @throws IllegalArgumentException if the [spendable] is not the sum of [trustedPending] and [confirmed].
 */
data class BitcoinBalance(
  val immature: BitcoinMoney,
  val trustedPending: BitcoinMoney,
  val untrustedPending: BitcoinMoney,
  val confirmed: BitcoinMoney,
  val spendable: BitcoinMoney,
  val total: BitcoinMoney,
) {
  init {
    require(!immature.isNegative)
    require(!confirmed.isNegative)
    require(!spendable.isNegative)
    require(!total.isNegative)
    // [trustedPending] and [untrustedPending] can be negative when outgoing tnxs are pending

    require(spendable == trustedPending + confirmed) {
      "Spendable balance must be the sum of trustedPending and confirmed"
    }
  }

  companion object {
    val ZeroBalance =
      BitcoinBalance(
        immature = BitcoinMoney.zero(),
        trustedPending = BitcoinMoney.zero(),
        untrustedPending = BitcoinMoney.zero(),
        confirmed = BitcoinMoney.zero(),
        spendable = BitcoinMoney.zero(),
        total = BitcoinMoney.zero()
      )
  }
}
