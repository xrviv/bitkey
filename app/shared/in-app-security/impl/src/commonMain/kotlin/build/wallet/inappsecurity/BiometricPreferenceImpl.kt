package build.wallet.inappsecurity

import build.wallet.analytics.events.EventTracker
import build.wallet.analytics.v1.Action
import build.wallet.database.BitkeyDatabaseProvider
import build.wallet.db.DbError
import build.wallet.logging.logFailure
import build.wallet.sqldelight.asFlowOfOneOrNull
import build.wallet.sqldelight.awaitAsOneOrNullResult
import build.wallet.sqldelight.awaitTransactionWithResult
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.get
import com.github.michaelbull.result.map
import com.github.michaelbull.result.onSuccess
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class BiometricPreferenceImpl(
  private val databaseProvider: BitkeyDatabaseProvider,
  private val eventTracker: EventTracker,
) : BiometricPreference {
  private val db by lazy {
    databaseProvider.database()
  }

  override suspend fun get(): Result<Boolean, DbError> {
    return db.biometricPreferenceQueries
      .getBiometricPeference()
      .awaitAsOneOrNullResult()
      .logFailure { "Unable to get Lightning Preference Entity" }
      .map { it?.enabled ?: false } // if there is no preference set we assume false
  }

  override suspend fun set(enabled: Boolean): Result<Unit, DbError> {
    return db.biometricPreferenceQueries
      .awaitTransactionWithResult {
        setBiometricPreference(enabled)
      }
      .onSuccess {
        if (enabled) {
          eventTracker.track(Action.ACTION_APP_BIOMETRICS_ENABLED)
        } else {
          eventTracker.track(Action.ACTION_APP_BIOMETRICS_DISABLED)
        }
      }
  }

  override fun isEnabled(): Flow<Boolean> {
    return db.biometricPreferenceQueries
      .getBiometricPeference()
      .asFlowOfOneOrNull()
      .map { it.get()?.enabled ?: false }
  }

  override suspend fun clear(): Result<Unit, DbError> {
    return db.biometricPreferenceQueries
      .awaitTransactionWithResult {
        clear()
      }
  }
}
