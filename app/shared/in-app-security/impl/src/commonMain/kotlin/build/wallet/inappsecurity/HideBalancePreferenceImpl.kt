package build.wallet.inappsecurity

import build.wallet.database.BitkeyDatabaseProvider
import build.wallet.db.DbError
import build.wallet.logging.logFailure
import build.wallet.sqldelight.asFlowOfOneOrNull
import build.wallet.sqldelight.awaitAsOneOrNullResult
import build.wallet.sqldelight.awaitTransactionWithResult
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.get
import com.github.michaelbull.result.map
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class HideBalancePreferenceImpl(
  private val databaseProvider: BitkeyDatabaseProvider,
) : HideBalancePreference {
  private val db by lazy {
    databaseProvider.database()
  }

  override suspend fun get(): Result<Boolean, DbError> {
    return db.hideBalancePreferenceQueries
      .getHideBalancePeference()
      .awaitAsOneOrNullResult()
      .logFailure { "Unable to get Lightning Preference Entity" }
      .map { it?.enabled ?: false } // if there is no preference set we assume false
  }

  override suspend fun set(enabled: Boolean): Result<Unit, DbError> {
    return db.hideBalancePreferenceQueries
      .awaitTransactionWithResult {
        setHideBalancePreference(enabled)
      }
  }

  override fun isEnabled(): Flow<Boolean> {
    return db.hideBalancePreferenceQueries
      .getHideBalancePeference()
      .asFlowOfOneOrNull()
      .map { it.get()?.enabled ?: false }
  }
}