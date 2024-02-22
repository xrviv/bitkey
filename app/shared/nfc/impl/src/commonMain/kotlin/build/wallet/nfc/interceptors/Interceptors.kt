package build.wallet.nfc.interceptors

import build.wallet.logging.LogLevel
import build.wallet.logging.NFC_TAG
import build.wallet.logging.log
import build.wallet.nfc.NfcException
import build.wallet.nfc.NfcSession
import build.wallet.nfc.haptics.NfcHaptics
import build.wallet.nfc.platform.NfcCommands
import com.github.michaelbull.result.coroutines.runSuspendCatching
import com.github.michaelbull.result.getOrThrow
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Sets the message to "Success" upon a successful transaction.
 */
fun iosMessages() =
  NfcTransactionInterceptor { next ->
    { session, commands ->
      session.message = "Connected"
      next(session, commands).also {
        session.message = "Success"
      }
    }
  }

/**
 * Logs the start of an NFC session.
 */
fun sessionLogger() =
  NfcTransactionInterceptor { next ->
    { session, commands ->
      runSuspendCatching { next(session, commands) }
        .onFailure { log(LogLevel.Info, tag = NFC_TAG, throwable = it) { "NFC Session Error" } }
        .getOrThrow()
    }
  }

/**
 * Vibrates the phone software upon a successful transaction,
 * and vibrates more violently upon a failed transaction.
 */
fun haptics(nfcHaptics: NfcHaptics) =
  NfcTransactionInterceptor { next ->
    { session, commands ->
      session.parameters.onTagConnectedObservers += { nfcHaptics.vibrateConnection() }
      runSuspendCatching { next(session, commands) }
        .onSuccess { nfcHaptics.vibrateSuccess() }
        .onFailure { nfcHaptics.vibrateFailure() }
        .getOrThrow()
    }
  }

/**
 * Adds a timeout to the NFC session.
 *
 * @param timeout The timeout to use. (defaults to 60 seconds)
 */
@Suppress("UnusedParameter")
fun timeoutSession(timeout: Duration = 60.seconds) =
  NfcTransactionInterceptor { next ->
    { session, commands ->
      // iOS both does its own timeout *and* blocks, despite claiming to be suspend
      // [W-5082]: Disabled due to toxic reaction with integration tests!
      // withTimeout(timeout) {
      next(session, commands)
      // }
    }
  }

/**
 * Locks the device after any transaction that wasn't cancelled or invalidated.
 */
fun lockDevice() =
  NfcTransactionInterceptor { next ->
    { session, commands ->
      runSuspendCatching { next(session, commands) }
        .onFailure {
          // An NfcException indicates the session is almost certainly invalidated
          if (it is NfcException) return@onFailure
          // Hello Future Us.
          // If this is throwing and impacting a successful transaction, put it in a finally.
          // It'll be fine.
          maybeLockDevice(session, commands)
        }.onSuccess { maybeLockDevice(session, commands) }
        .getOrThrow()
    }
  }

private suspend fun maybeLockDevice(
  session: NfcSession,
  commands: NfcCommands,
) {
  if (session.parameters.shouldLock) {
    commands.lockDevice(session)
  }
}
