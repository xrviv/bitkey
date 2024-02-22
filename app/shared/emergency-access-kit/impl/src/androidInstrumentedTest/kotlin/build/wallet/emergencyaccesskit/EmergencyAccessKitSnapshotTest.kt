package build.wallet.emergencyaccesskit

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import build.wallet.bitkey.keybox.KeyboxMock
import build.wallet.cloud.backup.csek.SealedCsekFake
import build.wallet.platform.PlatformContext
import build.wallet.platform.pdf.PdfAnnotatorFactoryImpl
import build.wallet.time.DateTimeFormatterImpl
import com.github.michaelbull.result.get
import com.github.michaelbull.result.map
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EmergencyAccessKitSnapshotTest {
  private val platformContext = PlatformContext(ApplicationProvider.getApplicationContext())

  private val snapshotGenerator =
    EmergencyAccessKitPdfGeneratorImpl(
      apkParametersProvider = EmergencyAccessKitApkParametersProviderFake(),
      mobileKeyParametersProvider = EmergencyAccessKitMobileKeyParametersProviderFake(),
      pdfAnnotatorFactory =
        PdfAnnotatorFactoryImpl(
          platformContext.appContext.applicationContext
        ),
      templateProvider = EmergencyAccessKitTemplateProviderImpl(platformContext),
      backupDateProvider = EmergencyAccessKitBackupDateProviderFake(),
      dateTimeFormatter = DateTimeFormatterImpl()
    )

  @Test
  fun emergencyAccessKitPDFSnapshot() {
    runBlocking {
      @Suppress("UnsafeCallOnNullableType")
      val pdfBytes =
        snapshotGenerator
          .generate(KeyboxMock, SealedCsekFake)
          .map { it.pdfData.toByteArray() }
          .get()!!

      val context = platformContext.appContext.applicationContext

      // Writes PDF in `data/data/build.wallet.shared.emergency.access.kit.impl.test/files`,
      // which can be viewed in IntelliJ Device Explorer.
      val fileOutput = context.openFileOutput("Emergency Access Kit.pdf", Context.MODE_PRIVATE)
      fileOutput.write(pdfBytes)
      fileOutput.close()
    }
  }
}
