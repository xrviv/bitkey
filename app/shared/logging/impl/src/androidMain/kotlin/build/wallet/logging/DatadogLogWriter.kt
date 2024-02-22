package build.wallet.logging

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Severity
import com.datadog.android.Datadog
import com.datadog.android.log.Logger

class DatadogLogWriter(
  private val logWriterContextStore: LogWriterContextStore,
  private val minSeverity: Severity = Severity.Info,
) : LogWriter() {
  private val datadogLogger: Logger by lazy {
    val logWriterContext = logWriterContextStore.get()
    Datadog.addUserProperties(
      mapOf(
        "app_installation_id" to logWriterContext.appInstallationId,
        "hardware_serial_number" to logWriterContext.hardwareSerialNumber
      )
    )
    Logger.Builder()
      .setNetworkInfoEnabled(enabled = true)
      .setBundleWithTraceEnabled(enabled = true)
      .setBundleWithRumEnabled(enabled = true)
      .setRemoteSampleRate(sampleRate = 100f)
      .build()
  }

  override fun isLoggable(
    tag: String,
    severity: Severity,
  ): Boolean = severity >= minSeverity

  override fun log(
    severity: Severity,
    message: String,
    tag: String,
    throwable: Throwable?,
  ) {
    val defaultAttributes =
      mapOf(
        "tag" to tag
      )
    when (severity) {
      Severity.Verbose ->
        datadogLogger.v(
          message = message,
          throwable = throwable,
          attributes = defaultAttributes
        )
      Severity.Debug ->
        datadogLogger.d(
          message = message,
          throwable = throwable,
          attributes = defaultAttributes
        )
      Severity.Info ->
        datadogLogger.i(
          message = message,
          throwable = throwable,
          attributes = defaultAttributes
        )
      Severity.Warn ->
        datadogLogger.w(
          message = message,
          throwable = throwable,
          attributes = defaultAttributes
        )
      Severity.Error ->
        datadogLogger.e(
          message = message,
          throwable = throwable,
          attributes = defaultAttributes
        )
      Severity.Assert ->
        datadogLogger.wtf(
          message = message,
          throwable = throwable,
          attributes = defaultAttributes
        )
    }
  }
}
