package build.wallet.platform.random

/**
 * Fake implementation of [Uuid] that increments [seed] value on each uuid generation:
 *
 * ```
 * uuidFake.random() // "uuid-0"
 * uuidFake.random() // "uuid-1"
 * uuidFake.random() // "uuid-2"
 * ```
 *
 * Note that this implementation is not thread safe and prone to race conditions.
 */
class UuidFake(
  private val initialSeed: Int = 0,
) : Uuid {
  private var currentSeed: Int = initialSeed

  override fun random(): String {
    return "uuid-$currentSeed".also {
      // Make the next uuid "unique".
      currentSeed += 1
    }
  }

  fun reset() {
    currentSeed = initialSeed
  }
}
