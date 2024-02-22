package build.wallet.sqldelight

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema

/**
 * Creates [SqlDriver] instance which will be used by generated SqlDelight APIs to interact
 * with underlying SQL database.
 */
interface SqlDriverFactory {
  /**
   * Creates a [SqlDriver] instance for a SqlDelight database.
   *
   * Note: We employ the SQLite driver, which does not support asynchronous interactions; all operations are blocking.
   * While SqlDelight allows for the generation of async APIs through [QueryResult.AsyncValue], we opt for
   * [QueryResult.Value] due to SQLite's lack of async support. Consequently, using [QueryResult.AsyncValue] is not
   * applicable to our use-case.
   *
   * [dataBaseName] - name of the database, for example "recipes.db".
   * [dataBaseSchema] - [SqlSchema] for this database, usually this is generated by SqlDelight.
   */
  fun createDriver(
    dataBaseName: String,
    dataBaseSchema: SqlSchema<QueryResult.Value<Unit>>,
  ): SqlDriver
}