package build.wallet.money

import build.wallet.money.currency.BTC
import build.wallet.money.currency.CryptoCurrency
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.decimal.toBigDecimal
import com.ionspin.kotlin.bignum.integer.BigInteger
import com.ionspin.kotlin.bignum.integer.toBigInteger

data class BitcoinMoney(
  /** The currency, always [BTC] for [BitcoinMoney]. */
  override val currency: CryptoCurrency = BTC,
  /** The amount of Bitcoin. */
  override val value: BigDecimal,
) : Money {
  /** Creates a [BitcoinMoney] object by specifying the amount in the fractional unit, Satoshis. */
  constructor(fractionalUnitAmount: BigInteger) : this(
    value = BTC.unitValueFromFractionalUnitValue(fractionalUnitAmount)
  )

  override fun toString() = "Money(${currency.textCode.code},${value.toPlainString()})"

  operator fun plus(other: BitcoinMoney): BitcoinMoney {
    require(this.currency == other.currency)
    return copy(value = this.value + other.value)
  }

  operator fun minus(other: BitcoinMoney): BitcoinMoney {
    require(this.currency == other.currency)
    return copy(value = this.value - other.value)
  }

  companion object {
    fun zero() = btc(0.0)

    /**
     * Constructor for creating bitcoin Money given BTC amount.
     */
    fun btc(amount: BigDecimal) = BitcoinMoney(value = amount)

    fun btc(amount: Double) = btc(amount = amount.toBigDecimal())

    /**
     * Constructor for creating bitcoin Money given satoshis amount.
     */
    fun sats(amount: BigInteger) = BitcoinMoney(fractionalUnitAmount = amount)

    fun sats(amount: Long) = sats(amount = amount.toBigInteger())

    fun sats(amount: Int) = sats(amount = amount.toBigInteger())
  }
}

/**
 * Returns the sum of all [BitcoinMoney] values produced by [selector] function
 * applied to each element in the collection.
 *
 * @throws UnsupportedOperationException if this iterable is empty.
 */
inline fun <T> Iterable<T>.sumOf(selector: (T) -> BitcoinMoney): BitcoinMoney {
  return map(selector).reduce { acc, money -> acc + money }
}

/**
 * Negate the amount value of the [BitcoinMoney] by inverting the sign.
 */
fun BitcoinMoney.negate(): BitcoinMoney = copy(value = value.negate())
