package build.wallet.recovery

import build.wallet.analytics.events.AppSessionManagerFake
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.f8e.FullAccountIdMock
import build.wallet.bitkey.keybox.KeyboxMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.f8e.F8eEnvironment.Development
import build.wallet.f8e.recovery.GetDelayNotifyRecoveryStatusServiceMock
import build.wallet.f8e.recovery.LostHardwareServerRecoveryMock
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.time.Duration.Companion.seconds

class RecoverSyncerImplTests : FunSpec({

  val recoveryDao = RecoveryDaoMock(turbines::create)
  val getRecoveryStatusService = GetDelayNotifyRecoveryStatusServiceMock()
  val sessionProvider = AppSessionManagerFake()
  val recoverySyncer =
    RecoverySyncerImpl(
      recoveryDao = recoveryDao,
      getRecoveryStatusService = getRecoveryStatusService,
      appSessionManager = sessionProvider
    )

  beforeTest {
    recoveryDao.reset()
    getRecoveryStatusService.reset()
    sessionProvider.reset()
  }

  test(
    "server polling sync recovery is retrieved (non-null) and cached in dao."
  ) {
    runTest {
      backgroundScope.launch {
        recoverySyncer.launchSync(
          fullAccountId = FullAccountId(""),
          f8eEnvironment = Development,
          scope = this,
          syncFrequency = 3.seconds
        )
      }

      getRecoveryStatusService.activeRecovery = LostHardwareServerRecoveryMock
      runCurrent()
      recoveryDao.setActiveServerRecoveryCalls.awaitItem().shouldBe(Unit)
      advanceTimeBy(4.seconds)
      // emit again after the polling duration
      recoveryDao.setActiveServerRecoveryCalls.awaitItem().shouldBe(Unit)
    }
  }

  test(
    "server unary recovery sync is retrieved (non-null) and cached in dao."
  ) {
    runTest {
      getRecoveryStatusService.activeRecovery = LostHardwareServerRecoveryMock

      recoverySyncer.performSync(
        fullAccountId = FullAccountId(""),
        f8eEnvironment = Development
      )

      recoveryDao.setActiveServerRecoveryCalls.awaitItem().shouldBe(Unit)
    }
  }

  test(
    "set local recovery progress"
  ) {
    recoverySyncer.setLocalRecoveryProgress(LocalRecoveryAttemptProgress.SweptFunds(KeyboxMock))
    recoveryDao.setLocalRecoveryProgressCalls.awaitItem().shouldBe(Unit)
  }

  test("clear") {
    recoverySyncer.clear()
    recoveryDao.clearCalls.awaitItem().shouldBe(Unit)
  }

  test("recovery syncer doesn't run in the background") {
    runTest {
      sessionProvider.appDidEnterBackground()

      recoverySyncer.launchSync(
        scope = backgroundScope,
        syncFrequency = 3.seconds,
        fullAccountId = FullAccountIdMock,
        f8eEnvironment = Development
      )
      advanceTimeBy(3.seconds)
      recoveryDao.setLocalRecoveryProgressCalls.expectNoEvents()

      sessionProvider.appDidEnterForeground()
      advanceTimeBy(3.seconds)
      recoveryDao.setActiveServerRecoveryCalls.awaitItem().shouldBe(Unit)
    }
  }
})
