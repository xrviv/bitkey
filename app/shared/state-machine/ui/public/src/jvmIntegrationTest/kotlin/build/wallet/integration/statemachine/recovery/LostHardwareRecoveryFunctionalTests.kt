@file:OptIn(DelicateCoroutinesApi::class)

package build.wallet.integration.statemachine.recovery

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import app.cash.turbine.ReceiveTurbine
import build.wallet.analytics.events.screen.id.CloudEventTrackerScreenId.CLOUD_SIGN_IN_LOADING
import build.wallet.analytics.events.screen.id.CloudEventTrackerScreenId.SAVE_CLOUD_BACKUP_INSTRUCTIONS
import build.wallet.analytics.events.screen.id.HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_INITIATION_INSTRUCTIONS
import build.wallet.analytics.events.screen.id.HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_INITIATION_NEW_DEVICE_READY
import build.wallet.analytics.events.screen.id.HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_PENDING
import build.wallet.analytics.events.screen.id.HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_READY
import build.wallet.analytics.events.screen.id.HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_ROTATING_AUTH_KEYS
import build.wallet.analytics.events.screen.id.HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_SWEEP_BROADCASTING
import build.wallet.analytics.events.screen.id.HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_SWEEP_GENERATING_PSBTS
import build.wallet.analytics.events.screen.id.HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_SWEEP_SIGN_PSBTS_PROMPT
import build.wallet.analytics.events.screen.id.HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_SWEEP_SUCCESS
import build.wallet.analytics.events.screen.id.HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_SWEEP_ZERO_BALANCE
import build.wallet.analytics.events.screen.id.PairHardwareEventTrackerScreenId.HW_ACTIVATION_INSTRUCTIONS
import build.wallet.analytics.events.screen.id.PairHardwareEventTrackerScreenId.HW_PAIR_INSTRUCTIONS
import build.wallet.analytics.events.screen.id.PairHardwareEventTrackerScreenId.HW_SAVE_FINGERPRINT_INSTRUCTIONS
import build.wallet.bitkey.keybox.KeyboxConfig
import build.wallet.cloud.store.CloudStoreAccountFake
import build.wallet.cloud.store.CloudStoreAccountFake.Companion
import build.wallet.coroutines.actualDelay
import build.wallet.di.ActivityComponentImpl
import build.wallet.integration.statemachine.recovery.RecoveryTestingTrackerScreenId.RECOVERY_ABORTED
import build.wallet.integration.statemachine.recovery.RecoveryTestingTrackerScreenId.RECOVERY_COMPLETED
import build.wallet.integration.statemachine.recovery.RecoveryTestingTrackerScreenId.RECOVERY_NOT_STARTED
import build.wallet.keybox.KeyboxDao
import build.wallet.keybox.wallet.AppSpendingWalletProvider
import build.wallet.money.BitcoinMoney
import build.wallet.money.currency.USD
import build.wallet.money.display.BitcoinDisplayUnit
import build.wallet.money.display.CurrencyPreferenceData
import build.wallet.money.matchers.shouldBeGreaterThan
import build.wallet.recovery.Recovery.Loading
import build.wallet.recovery.Recovery.NoActiveRecovery
import build.wallet.recovery.RecoverySyncer
import build.wallet.statemachine.account.create.full.hardware.PairNewHardwareBodyModel
import build.wallet.statemachine.cloud.CloudSignInModelFake
import build.wallet.statemachine.core.LoadingBodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle.Root
import build.wallet.statemachine.core.StateMachine
import build.wallet.statemachine.core.SuccessBodyModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.data.keybox.AccountData.CheckingActiveAccountData
import build.wallet.statemachine.data.keybox.AccountData.HasActiveFullAccountData.ActiveFullAccountLoadedData
import build.wallet.statemachine.data.keybox.AccountData.HasActiveFullAccountData.LoadingActiveFullAccountData
import build.wallet.statemachine.data.keybox.AccountDataProps
import build.wallet.statemachine.data.keybox.AccountDataStateMachineImpl
import build.wallet.statemachine.data.keybox.config.TemplateKeyboxConfigData.LoadedTemplateKeyboxConfigData
import build.wallet.statemachine.nfc.NfcBodyModel
import build.wallet.statemachine.recovery.losthardware.LostHardwareRecoveryProps
import build.wallet.statemachine.recovery.losthardware.LostHardwareRecoveryUiStateMachineImpl
import build.wallet.statemachine.recovery.losthardware.initiate.InstructionsStyle
import build.wallet.statemachine.ui.awaitUntilScreenWithBody
import build.wallet.statemachine.ui.clickPrimaryButton
import build.wallet.testing.AppTester
import build.wallet.testing.launchNewApp
import build.wallet.testing.relaunchApp
import build.wallet.testing.shouldBeLoaded
import build.wallet.testing.shouldBeOk
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.getOrThrow
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.assertions.nondeterministic.eventuallyConfig
import io.kotest.core.spec.style.FunSpec
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlin.time.Duration.Companion.seconds

class LostHardwareRecoveryFunctionalTests : FunSpec({
  data class Props(val keyboxConfig: KeyboxConfig, val originalKeyboxId: String)

  class TestingStateMachine(
    val dsm: AccountDataStateMachineImpl,
    val usm: LostHardwareRecoveryUiStateMachineImpl,
    val keyboxDao: KeyboxDao,
    val recoverySyncer: RecoverySyncer,
  ) : StateMachine<Props, ScreenModel> {
    @Composable
    override fun model(props: Props): ScreenModel {
      val activeKeybox =
        remember {
          keyboxDao.activeKeybox()
        }.collectAsState(null).value?.getOrThrow()
      val activeRecovery =
        remember { recoverySyncer.recoveryStatus() }.collectAsState(Ok(Loading))
          .value.getOrThrow()
      val (aborted, updateAborted) = remember { mutableStateOf(false) }
      if (aborted) {
        preStartOrPostRecoveryCompletionScreen(RECOVERY_ABORTED)
      }
      if (props.originalKeyboxId != activeKeybox?.localId && activeRecovery == NoActiveRecovery) {
        return preStartOrPostRecoveryCompletionScreen(RECOVERY_COMPLETED)
      }
      val accountData =
        dsm.model(
          AccountDataProps(
            templateKeyboxConfigData = LoadedTemplateKeyboxConfigData(props.keyboxConfig) {},
            currencyPreferenceData = CurrencyPreferenceData(BitcoinDisplayUnit.Satoshi, {}, USD) {}
          )
        )
      return when (accountData) {
        is LoadingActiveFullAccountData,
        is CheckingActiveAccountData,
        -> preStartOrPostRecoveryCompletionScreen(RECOVERY_NOT_STARTED)
        is ActiveFullAccountLoadedData -> {
          usm.model(
            LostHardwareRecoveryProps(
              keyboxConfig = props.keyboxConfig,
              fullAccountId = accountData.account.accountId,
              lostHardwareRecoveryData = accountData.lostHardwareRecoveryData,
              fiatCurrency = USD,
              screenPresentationStyle = Root,
              onFoundHardware = {},
              instructionsStyle = InstructionsStyle.Independent
            ) { updateAborted(true) }
          )
        }
        else -> error("Unexpected KeyboxData state $accountData")
      }
    }
  }

  lateinit var appTester: AppTester
  lateinit var app: ActivityComponentImpl
  lateinit var recoveryStateMachine: TestingStateMachine
  lateinit var appSpendingWalletProvider: AppSpendingWalletProvider

  beforeTest {
    appTester = launchNewApp()
    app = appTester.app
    appSpendingWalletProvider = appTester.app.appComponent.appSpendingWalletProvider
    appTester.onboardFullAccountWithFakeHardware()
    appTester.fakeNfcCommands.clearHardwareKeys()
    recoveryStateMachine =
      TestingStateMachine(
        app.accountDataStateMachine,
        app.lostHardwareRecoveryUiStateMachine,
        app.appComponent.keyboxDao,
        app.recoverySyncer
      )
  }

  fun resetApp() {
    appTester = appTester.relaunchApp()
    app = appTester.app
    recoveryStateMachine =
      TestingStateMachine(
        app.accountDataStateMachine,
        app.lostHardwareRecoveryUiStateMachine,
        app.appComponent.keyboxDao,
        app.recoverySyncer
      )
  }

  test("lost hardware recovery - happy path") {
    val keybox = appTester.getActiveFullAccount().keybox
    val props = Props(keybox.config, keybox.localId)
    recoveryStateMachine.test(
      props = props,
      useVirtualTime = false,
      testTimeout = 60.seconds,
      turbineTimeout = 30.seconds
    ) {
      startRecoveryAndAdvanceToDelayNotify()
      appTester.completeServerDelayNotifyPeriodForTesting(keybox.config.f8eEnvironment)

      awaitUntilScreenWithBody<FormBodyModel>(LOST_HW_DELAY_NOTIFY_READY)
        .clickPrimaryButton()
      awaitUntilScreenWithBody<LoadingBodyModel>(LOST_HW_DELAY_NOTIFY_ROTATING_AUTH_KEYS)
      awaitUntilScreenWithBody<FormBodyModel>(SAVE_CLOUD_BACKUP_INSTRUCTIONS)
        .clickPrimaryButton()
      awaitUntilScreenWithBody<CloudSignInModelFake>(CLOUD_SIGN_IN_LOADING)
        .signInSuccess(CloudStoreAccountFake.CloudStoreAccount1Fake)
      awaitUntilScreenWithBody<LoadingBodyModel>(LOST_HW_DELAY_NOTIFY_SWEEP_GENERATING_PSBTS)
      awaitUntilScreenWithBody<FormBodyModel>(LOST_HW_DELAY_NOTIFY_SWEEP_ZERO_BALANCE)
        .clickPrimaryButton()
      awaitUntilScreenWithBody<SuccessBodyModel>(RECOVERY_COMPLETED)
      cancelAndIgnoreRemainingEvents()
    }
  }

  test(
    "recovery lost hardware - force exiting before cloud backup takes you back to icloud backup"
  ) {
    val keybox = appTester.getActiveFullAccount().keybox
    val props = Props(keybox.config, keybox.localId)
    app.apply {
      recoveryStateMachine.test(
        props = props,
        useVirtualTime = false,
        testTimeout = 20.seconds,
        turbineTimeout = 5.seconds
      ) {
        startRecoveryAndAdvanceToDelayNotify()
        appTester.completeServerDelayNotifyPeriodForTesting(keybox.config.f8eEnvironment)

        awaitUntilScreenWithBody<FormBodyModel>(LOST_HW_DELAY_NOTIFY_READY)
          .clickPrimaryButton()
        awaitUntilScreenWithBody<LoadingBodyModel>(LOST_HW_DELAY_NOTIFY_ROTATING_AUTH_KEYS)
        awaitUntilScreenWithBody<FormBodyModel>(SAVE_CLOUD_BACKUP_INSTRUCTIONS)
      }

      // Force quit!
      resetApp()

      recoveryStateMachine.test(
        props = props,
        useVirtualTime = false,
        testTimeout = 20.seconds,
        turbineTimeout = 5.seconds
      ) {
        awaitUntilScreenWithBody<FormBodyModel>(SAVE_CLOUD_BACKUP_INSTRUCTIONS)
          .clickPrimaryButton()
        awaitUntilScreenWithBody<CloudSignInModelFake>(CLOUD_SIGN_IN_LOADING)
          .signInSuccess(Companion.CloudStoreAccount1Fake)
        awaitUntilScreenWithBody<LoadingBodyModel>(LOST_HW_DELAY_NOTIFY_SWEEP_GENERATING_PSBTS)
        awaitUntilScreenWithBody<FormBodyModel>(LOST_HW_DELAY_NOTIFY_SWEEP_ZERO_BALANCE)
          .clickPrimaryButton()
        awaitUntilScreenWithBody<SuccessBodyModel>(RECOVERY_COMPLETED)
        cancelAndIgnoreRemainingEvents()
      }
    }
  }

  test(
    "recovery lost hardware - force exiting in the middle of initiation"
  ) {
    val keybox = appTester.getActiveFullAccount().keybox
    val props = Props(keybox.config, keybox.localId)
    app.apply {
      recoveryStateMachine.test(
        props = props,
        useVirtualTime = false,
        testTimeout = 20.seconds,
        turbineTimeout = 5.seconds
      ) {
        awaitUntilScreenWithBody<SuccessBodyModel>(RECOVERY_NOT_STARTED)
        awaitUntilScreenWithBody<FormBodyModel>(LOST_HW_DELAY_NOTIFY_INITIATION_INSTRUCTIONS)
          .clickPrimaryButton()
        awaitUntilScreenWithBody<FormBodyModel>(LOST_HW_DELAY_NOTIFY_INITIATION_NEW_DEVICE_READY)
          .clickPrimaryButton()
        awaitUntilScreenWithBody<PairNewHardwareBodyModel>(HW_ACTIVATION_INSTRUCTIONS)
          .clickPrimaryButton()
        awaitUntilScreenWithBody<PairNewHardwareBodyModel>(HW_PAIR_INSTRUCTIONS)
          .clickPrimaryButton()
        awaitUntilScreenWithBody<PairNewHardwareBodyModel>(HW_SAVE_FINGERPRINT_INSTRUCTIONS)
          .clickPrimaryButton()
        // Initiating NFC
        awaitUntilScreenWithBody<NfcBodyModel>()
        // Detected NFC
        awaitUntilScreenWithBody<NfcBodyModel>()
        // Success NFC
        awaitUntilScreenWithBody<NfcBodyModel>()

        cancelAndIgnoreRemainingEvents()
      }

      resetApp()

      recoveryStateMachine.test(
        props = props,
        useVirtualTime = false,
        testTimeout = 20.seconds,
        turbineTimeout = 5.seconds
      ) {
        awaitUntilScreenWithBody<SuccessBodyModel>(RECOVERY_NOT_STARTED)
        awaitUntilScreenWithBody<FormBodyModel>(LOST_HW_DELAY_NOTIFY_INITIATION_INSTRUCTIONS)
          .clickPrimaryButton()
        awaitUntilScreenWithBody<FormBodyModel>(LOST_HW_DELAY_NOTIFY_INITIATION_NEW_DEVICE_READY)
          .clickPrimaryButton()
        awaitUntilScreenWithBody<PairNewHardwareBodyModel>(HW_ACTIVATION_INSTRUCTIONS)
          .clickPrimaryButton()
        awaitUntilScreenWithBody<PairNewHardwareBodyModel>(HW_PAIR_INSTRUCTIONS)
          .clickPrimaryButton()
        awaitUntilScreenWithBody<PairNewHardwareBodyModel>(HW_SAVE_FINGERPRINT_INSTRUCTIONS)
          .clickPrimaryButton()
        awaitUntilScreenWithBody<FormBodyModel>(LOST_HW_DELAY_NOTIFY_PENDING)
        awaitUntilScreenWithBody<FormBodyModel>(LOST_HW_DELAY_NOTIFY_READY) {
          actualDelay(3.seconds) {
            // TODO(W-3916) App is not using server recovery status
            "Allow for a teeny bit of clock skew"
          }
        }
          .clickPrimaryButton()
        awaitUntilScreenWithBody<LoadingBodyModel>(LOST_HW_DELAY_NOTIFY_ROTATING_AUTH_KEYS)
        awaitUntilScreenWithBody<FormBodyModel>(SAVE_CLOUD_BACKUP_INSTRUCTIONS)
          .clickPrimaryButton()
        awaitUntilScreenWithBody<CloudSignInModelFake>(CLOUD_SIGN_IN_LOADING)
          .signInSuccess(Companion.CloudStoreAccount1Fake)
        awaitUntilScreenWithBody<LoadingBodyModel>(LOST_HW_DELAY_NOTIFY_SWEEP_GENERATING_PSBTS)
        awaitUntilScreenWithBody<FormBodyModel>(LOST_HW_DELAY_NOTIFY_SWEEP_ZERO_BALANCE)
          .clickPrimaryButton()
        awaitUntilScreenWithBody<SuccessBodyModel>(RECOVERY_COMPLETED)
        cancelAndIgnoreRemainingEvents()
      }
    }
  }

  test(
    "recovery lost hardware - force exiting after cloud backup & before sweep takes you back to sweep"
  ) {
    val keybox = appTester.getActiveFullAccount().keybox
    val props = Props(keybox.config, keybox.localId)
    app.apply {
      recoveryStateMachine.test(
        props = props,
        useVirtualTime = false,
        testTimeout = 20.seconds,
        turbineTimeout = 5.seconds
      ) {
        startRecoveryAndAdvanceToDelayNotify()
        appTester.completeServerDelayNotifyPeriodForTesting(keybox.config.f8eEnvironment)

        awaitUntilScreenWithBody<FormBodyModel>(LOST_HW_DELAY_NOTIFY_READY)
          .clickPrimaryButton()
        awaitUntilScreenWithBody<LoadingBodyModel>(LOST_HW_DELAY_NOTIFY_ROTATING_AUTH_KEYS)
        awaitUntilScreenWithBody<FormBodyModel>(SAVE_CLOUD_BACKUP_INSTRUCTIONS)
          .clickPrimaryButton()
        awaitUntilScreenWithBody<CloudSignInModelFake>(CLOUD_SIGN_IN_LOADING)
          .signInSuccess(Companion.CloudStoreAccount1Fake)
        awaitUntilScreenWithBody<LoadingBodyModel>(LOST_HW_DELAY_NOTIFY_SWEEP_GENERATING_PSBTS)
        cancelAndIgnoreRemainingEvents()
      }

      resetApp()

      recoveryStateMachine.test(
        props = props,
        useVirtualTime = false,
        testTimeout = 20.seconds,
        turbineTimeout = 5.seconds
      ) {
        awaitUntilScreenWithBody<LoadingBodyModel>(LOST_HW_DELAY_NOTIFY_SWEEP_GENERATING_PSBTS)
        awaitUntilScreenWithBody<FormBodyModel>(LOST_HW_DELAY_NOTIFY_SWEEP_ZERO_BALANCE)
          .clickPrimaryButton()
        awaitUntilScreenWithBody<SuccessBodyModel>(RECOVERY_COMPLETED)
        cancelAndIgnoreRemainingEvents()
      }
    }
  }

  test("recovery lost hardware - force exiting during D&N wait") {
    val keybox = appTester.getActiveFullAccount().keybox
    val props = Props(keybox.config, keybox.localId)
    app.apply {
      recoveryStateMachine.test(
        props = props,
        useVirtualTime = false,
        testTimeout = 20.seconds,
        turbineTimeout = 5.seconds
      ) {
        startRecoveryAndAdvanceToDelayNotify()
        appTester.completeServerDelayNotifyPeriodForTesting(keybox.config.f8eEnvironment)

        awaitUntilScreenWithBody<FormBodyModel>(LOST_HW_DELAY_NOTIFY_READY)
          .clickPrimaryButton()
        awaitUntilScreenWithBody<LoadingBodyModel>(LOST_HW_DELAY_NOTIFY_ROTATING_AUTH_KEYS)
        awaitUntilScreenWithBody<FormBodyModel>(SAVE_CLOUD_BACKUP_INSTRUCTIONS)
        cancelAndIgnoreRemainingEvents()
      }
    }
  }

  test("recover lost hardware - sweep real funds") {
    val keybox = appTester.getActiveFullAccount().keybox
    val wallet =
      appSpendingWalletProvider.getSpendingWallet(keybox.activeSpendingKeyset)
        .getOrThrow()
    appTester.treasuryWallet.fund(wallet, BitcoinMoney.sats(10_000L))

    val props = Props(keybox.config, keybox.localId)
    app.apply {
      recoveryStateMachine.test(
        props = props,
        useVirtualTime = false,
        testTimeout = 20.seconds,
        turbineTimeout = 5.seconds
      ) {
        startRecoveryAndAdvanceToDelayNotify()
        appTester.completeServerDelayNotifyPeriodForTesting(keybox.config.f8eEnvironment)

        awaitUntilScreenWithBody<FormBodyModel>(LOST_HW_DELAY_NOTIFY_READY)
          .clickPrimaryButton()
        awaitUntilScreenWithBody<LoadingBodyModel>(LOST_HW_DELAY_NOTIFY_ROTATING_AUTH_KEYS)
        awaitUntilScreenWithBody<FormBodyModel>(SAVE_CLOUD_BACKUP_INSTRUCTIONS)
          .clickPrimaryButton()
        awaitUntilScreenWithBody<CloudSignInModelFake>(CLOUD_SIGN_IN_LOADING)
          .signInSuccess(Companion.CloudStoreAccount1Fake)

        awaitUntilScreenWithBody<LoadingBodyModel>(LOST_HW_DELAY_NOTIFY_SWEEP_GENERATING_PSBTS)
        awaitUntilScreenWithBody<FormBodyModel>(LOST_HW_DELAY_NOTIFY_SWEEP_SIGN_PSBTS_PROMPT)
          .clickPrimaryButton()
        awaitUntilScreenWithBody<LoadingBodyModel>(LOST_HW_DELAY_NOTIFY_SWEEP_BROADCASTING)
        awaitUntilScreenWithBody<FormBodyModel>(LOST_HW_DELAY_NOTIFY_SWEEP_SUCCESS)
          .clickPrimaryButton()

        awaitUntilScreenWithBody<SuccessBodyModel>(RECOVERY_COMPLETED)
      }
    }

    val activeAccount = appTester.getActiveFullAccount()
    val activeWallet =
      appSpendingWalletProvider.getSpendingWallet(
        activeAccount.keybox.activeSpendingKeyset
      ).getOrThrow()
    eventually(
      eventuallyConfig {
        duration = 60.seconds
        interval = 1.seconds
        initialDelay = 1.seconds
      }
    ) {
      activeWallet.sync().shouldBeOk()
      val balance = activeWallet.balance().first().shouldBeLoaded()
      balance.total.shouldBeGreaterThan(BitcoinMoney.sats(0))
      // Eventually could iterate to calculate and subtract psbtsGeneratedData.totalFeeAmount)
    }
    appTester.returnFundsToTreasury(activeAccount)
  }
})

private suspend fun ReceiveTurbine<ScreenModel>.startRecoveryAndAdvanceToDelayNotify() {
  awaitUntilScreenWithBody<SuccessBodyModel>(RECOVERY_NOT_STARTED)
  awaitUntilScreenWithBody<FormBodyModel>(LOST_HW_DELAY_NOTIFY_INITIATION_INSTRUCTIONS)
    .clickPrimaryButton()
  awaitUntilScreenWithBody<FormBodyModel>(LOST_HW_DELAY_NOTIFY_INITIATION_NEW_DEVICE_READY)
    .clickPrimaryButton()
  awaitUntilScreenWithBody<PairNewHardwareBodyModel>(HW_ACTIVATION_INSTRUCTIONS)
    .clickPrimaryButton()
  awaitUntilScreenWithBody<PairNewHardwareBodyModel>(HW_PAIR_INSTRUCTIONS)
    .clickPrimaryButton()
  awaitUntilScreenWithBody<PairNewHardwareBodyModel>(HW_SAVE_FINGERPRINT_INSTRUCTIONS)
    .clickPrimaryButton()
  awaitUntilScreenWithBody<FormBodyModel>(LOST_HW_DELAY_NOTIFY_PENDING)
}