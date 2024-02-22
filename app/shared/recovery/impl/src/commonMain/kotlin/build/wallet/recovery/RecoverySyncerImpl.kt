package build.wallet.recovery

import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.db.DbError
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.recovery.GetDelayNotifyRecoveryStatusService
import build.wallet.logging.log
import build.wallet.logging.logNetworkFailure
import build.wallet.recovery.RecoverySyncer.SyncError
import build.wallet.recovery.RecoverySyncer.SyncError.CouldNotFetchServerRecovery
import build.wallet.recovery.RecoverySyncer.SyncError.SyncDbError
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.binding.binding
import com.github.michaelbull.result.mapError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration

class RecoverySyncerImpl(
  val recoveryDao: RecoveryDao,
  val getRecoveryStatusService: GetDelayNotifyRecoveryStatusService,
) : RecoverySyncer {
  /**
   * A mutex used to ensure only one call to sync is in flight at a time
   * and to record unusually long syncs.
   */
  private val syncLock = Mutex(locked = false)

  override fun launchSync(
    scope: CoroutineScope,
    syncFrequency: Duration,
    fullAccountId: FullAccountId,
    f8eEnvironment: F8eEnvironment,
  ) {
    log { "Starting recovery sync polling" }
    scope.launch {
      while (true) {
        performSync(
          fullAccountId = fullAccountId,
          f8eEnvironment = f8eEnvironment
        )
        delay(syncFrequency)
      }
    }
  }

  override suspend fun performSync(
    fullAccountId: FullAccountId,
    f8eEnvironment: F8eEnvironment,
  ): Result<Unit, SyncError> =
    binding {
      syncLock.withLock {
        log { "Syncing recovery status" }
        val serverRecovery =
          getRecoveryStatusService.getStatus(f8eEnvironment, fullAccountId)
            .logNetworkFailure { "Could not fetch server recovery when syncing" }
            .mapError { CouldNotFetchServerRecovery(it) }
            .bind()

        recoveryDao
          .setActiveServerRecovery(serverRecovery)
          .mapError { SyncDbError(it) }
          .bind()
      }
    }

  override fun recoveryStatus(): Flow<Result<Recovery, DbError>> {
    // Return a flow that emits whenever the recovery status changes. This could be an advancement
    // of a local recovery to a new phase, or entering a state where our local recovery attempt
    // was canceled on the server. We do this by listening to changes to both the cached active
    // serer recovery and any local ongoing recovery attempt we have and running comparisons
    // against the two to calculate the recovery status.
    return recoveryDao.activeRecovery().distinctUntilChanged()
  }

  override suspend fun setLocalRecoveryProgress(
    progress: LocalRecoveryAttemptProgress,
  ): Result<Unit, DbError> {
    return recoveryDao.setLocalRecoveryProgress(progress)
  }

  override suspend fun clear(): Result<Unit, DbError> {
    return recoveryDao.clear()
  }
}