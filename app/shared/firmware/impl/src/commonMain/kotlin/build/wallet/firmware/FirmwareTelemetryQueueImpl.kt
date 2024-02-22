package build.wallet.firmware

import build.wallet.database.BitkeyDatabaseProvider
import build.wallet.queueprocessor.Queue
import build.wallet.sqldelight.awaitAsListResult
import build.wallet.sqldelight.awaitTransaction
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import okio.ByteString.Companion.toByteString

class FirmwareTelemetryQueueImpl(
  private val databaseProvider: BitkeyDatabaseProvider,
) : Queue<FirmwareTelemetryEvent> {
  override suspend fun append(item: FirmwareTelemetryEvent): Result<Unit, Error> {
    return databaseProvider.database().awaitTransaction {
      databaseProvider.database().firmwareTelemetryQueries.append(
        serial = item.serial,
        event = item.event.toByteArray()
      )
    }
  }

  override suspend fun take(num: Int): Result<List<FirmwareTelemetryEvent>, Error> {
    require(num >= 0) { "Requested element count $num is less than zero." }

    return databaseProvider.database().firmwareTelemetryQueries.take(num.toLong())
      .awaitAsListResult()
      .map { items ->
        items.map { item -> FirmwareTelemetryEvent(item.serial, item.event.toByteString()) }
      }
  }

  override suspend fun removeFirst(num: Int): Result<Unit, Error> {
    require(num >= 0) { "Requested element count $num is less than zero." }

    return databaseProvider.database().firmwareTelemetryQueries.awaitTransaction {
      removeFirst(num.toLong())
    }
  }
}
