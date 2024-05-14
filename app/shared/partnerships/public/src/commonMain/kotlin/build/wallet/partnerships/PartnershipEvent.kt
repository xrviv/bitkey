package build.wallet.partnerships

import kotlin.jvm.JvmInline

/**
 * Wraps an event ID used to differentiate external events between partners.
 *
 * Note: Since this information comes from an outside source, this is
 * intentionally not an enum to allow for future values.
 * Known values are included in the companion object, and unknown
 * values should be handled appropriately by the caller.
 */
@JvmInline
value class PartnershipEvent(val value: String) {
  companion object {
    /**
     * Event used when a partner has created a transaction that can
     * be expected to be seen in the future.
     */
    val TransactionCreated = PartnershipEvent("transaction_created")
  }
}