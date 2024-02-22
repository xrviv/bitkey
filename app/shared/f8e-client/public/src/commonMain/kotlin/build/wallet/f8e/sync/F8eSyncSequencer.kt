package build.wallet.f8e.sync

import build.wallet.bitkey.account.Account
import build.wallet.bitkey.f8e.AccountId
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class F8eSyncSequencer {
  private val syncLock = Mutex()
  private val dataLock = Mutex()
  private var currentAccount: AccountId? = null

  /**
   * Sequences multiple invocations of [task] from different coroutines. If a
   * previous invocation of [task] is still running, and it had a different [Account] as the
   * current invocation, an [IllegalStateException] will be thrown. Otherwise, this call
   * will suspend until the previous invocation is canceled.
   */
  suspend fun run(
    account: AccountId,
    task: suspend () -> Unit,
  ) {
    val lastAccount = dataLock.withLock { currentAccount }
    val acquired = syncLock.tryLock()

    if (acquired) {
      try {
        doLocked(account, task)
      } finally {
        syncLock.unlock()
      }
      return
    }

    if (lastAccount != null && lastAccount != account) {
      error("sync sequencer called with different account while previous coroutine was active")
    }
    syncLock.withLock { doLocked(account, task) }
  }

  private suspend fun doLocked(
    account: AccountId,
    task: suspend () -> Unit,
  ) {
    dataLock.withLock { currentAccount = account }
    task()
  }
}
