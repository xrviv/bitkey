package build.wallet.keybox

import build.wallet.account.AccountRepositoryFake
import build.wallet.account.AccountStatus.NoAccount
import build.wallet.auth.AuthKeyRotationAttemptDaoMock
import build.wallet.auth.AuthTokenDaoMock
import build.wallet.availability.AuthSignatureStatus
import build.wallet.availability.F8eAuthSignatureStatusProviderImpl
import build.wallet.bitcoin.AppPrivateKeyDaoFake
import build.wallet.bitcoin.BitcoinNetworkType.SIGNET
import build.wallet.bitcoin.transactions.EstimatedTransactionPriority.FASTEST
import build.wallet.bitcoin.transactions.OutgoingTransactionDetailDaoMock
import build.wallet.bitcoin.transactions.TransactionPriorityPreferenceFake
import build.wallet.bitkey.auth.AppGlobalAuthKeyHwSignatureMock
import build.wallet.bitkey.auth.AppGlobalAuthKeypairMock
import build.wallet.bitkey.auth.AppGlobalAuthPublicKeyMock
import build.wallet.bitkey.auth.AppRecoveryAuthPublicKeyMock
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.spending.AppSpendingKeypair
import build.wallet.bitkey.spending.AppSpendingPublicKeyMock
import build.wallet.cloud.backup.csek.CsekDaoFake
import build.wallet.cloud.backup.local.CloudBackupDaoFake
import build.wallet.cloud.backup.local.shouldBeEmpty
import build.wallet.coroutines.turbine.turbines
import build.wallet.encrypt.Secp256k1PublicKey
import build.wallet.firmware.FirmwareDeviceInfoDaoMock
import build.wallet.firmware.FirmwareMetadataDaoMock
import build.wallet.fwup.FwupDataDaoMock
import build.wallet.home.GettingStartedTaskDaoMock
import build.wallet.home.HomeUiBottomSheetDaoMock
import build.wallet.inappsecurity.BiometricPreferenceFake
import build.wallet.inappsecurity.HideBalancePreferenceFake
import build.wallet.keybox.keys.OnboardingAppKeyKeystoreFake
import build.wallet.limit.MobilePayServiceMock
import build.wallet.money.display.BitcoinDisplayPreferenceRepositoryMock
import build.wallet.money.display.FiatCurrencyPreferenceRepositoryMock
import build.wallet.notifications.NotificationTouchpointDaoMock
import build.wallet.onboarding.OnboardingKeyboxHardwareKeys
import build.wallet.onboarding.OnboardingKeyboxHardwareKeysDaoFake
import build.wallet.onboarding.OnboardingKeyboxSealedCsekDaoMock
import build.wallet.onboarding.OnboardingKeyboxStepStateDaoMock
import build.wallet.platform.config.AppVariant
import build.wallet.recovery.RecoveryDaoMock
import build.wallet.recovery.socrec.SocRecKeysDaoFake
import build.wallet.recovery.socrec.SocRecRelationshipsRepositoryMock
import build.wallet.recovery.socrec.SocRecStartedChallengeDaoFake
import build.wallet.testing.shouldBeOk
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class AppDataDeleterImplTests : FunSpec({

  val gettingStartedTaskDao = GettingStartedTaskDaoMock(turbines::create)
  val onboardingKeyboxSealedCsekDao = OnboardingKeyboxSealedCsekDaoMock()
  val onboardingKeyboxStepStateDao =
    OnboardingKeyboxStepStateDaoMock(turbines::create)
  val notificationTouchpointDao = NotificationTouchpointDaoMock(turbines::create)

  val accountRepository = AccountRepositoryFake()
  val authTokenDao = AuthTokenDaoMock(turbines::create)
  val keyboxDao = KeyboxDaoMock(turbines::create)
  val mobilePayService = MobilePayServiceMock(turbines::create)
  val transactionDetailDao = OutgoingTransactionDetailDaoMock(turbines::create)
  val fwupDataDao = FwupDataDaoMock(turbines::create)
  val firmwareDeviceIdentifiersDao =
    FirmwareDeviceInfoDaoMock(turbines::create)
  val firmwareMetadataDao = FirmwareMetadataDaoMock(turbines::create)
  val transactionPriorityPreference = TransactionPriorityPreferenceFake()
  val onboardingAppKeyKeystoreFake = OnboardingAppKeyKeystoreFake()
  val onboardingKeyboxHwAuthPublicKeyDao = OnboardingKeyboxHardwareKeysDaoFake()
  val fiatCurrencyPreferenceRepository = FiatCurrencyPreferenceRepositoryMock(turbines::create)
  val homeUiBottomSheetDao = HomeUiBottomSheetDaoMock(turbines::create)
  val bitcoinDisplayPreferenceRepository = BitcoinDisplayPreferenceRepositoryMock(turbines::create)
  val appPrivateKeyDao = AppPrivateKeyDaoFake()
  val cloudBackupDao = CloudBackupDaoFake()
  val socRecKeysDaoFake = SocRecKeysDaoFake()
  val socRecStartedChallengeDao = SocRecStartedChallengeDaoFake()
  val socRecRelationshipsRepository = SocRecRelationshipsRepositoryMock(turbines::create)
  val authKeyRotationAttemptMock = AuthKeyRotationAttemptDaoMock(turbines::create)
  val recoveryDaoMock = RecoveryDaoMock(turbines::create)
  val authSignatureStatusProvider = F8eAuthSignatureStatusProviderImpl()

  fun appDataDeleter(appVariant: AppVariant) =
    AppDataDeleterImpl(
      appVariant = appVariant,
      accountRepository = accountRepository,
      authTokenDao = authTokenDao,
      gettingStartedTaskDao = gettingStartedTaskDao,
      keyboxDao = keyboxDao,
      notificationTouchpointDao = notificationTouchpointDao,
      onboardingKeyboxSealedCsekDao = onboardingKeyboxSealedCsekDao,
      onboardingKeyboxStepStateDao = onboardingKeyboxStepStateDao,
      onboardingKeyboxHardwareKeysDao = onboardingKeyboxHwAuthPublicKeyDao,
      mobilePayService = mobilePayService,
      outgoingTransactionDetailDao = transactionDetailDao,
      fwupDataDao = fwupDataDao,
      firmwareDeviceInfoDao = firmwareDeviceIdentifiersDao,
      firmwareMetadataDao = firmwareMetadataDao,
      transactionPriorityPreference = transactionPriorityPreference,
      onboardingAppKeyKeystore = onboardingAppKeyKeystoreFake,
      fiatCurrencyPreferenceRepository = fiatCurrencyPreferenceRepository,
      homeUiBottomSheetDao = homeUiBottomSheetDao,
      bitcoinDisplayPreferenceRepository = bitcoinDisplayPreferenceRepository,
      appPrivateKeyDao = appPrivateKeyDao,
      cloudBackupDao = cloudBackupDao,
      socRecKeysDao = socRecKeysDaoFake,
      socRecRelationshipsRepository = socRecRelationshipsRepository,
      socRecStartedChallengeDao = socRecStartedChallengeDao,
      csekDao = CsekDaoFake(),
      authKeyRotationAttemptDao = authKeyRotationAttemptMock,
      recoveryDao = recoveryDaoMock,
      authSignatureStatusProvider = authSignatureStatusProvider,
      hideBalancePreference = HideBalancePreferenceFake(),
      biometricPreference = BiometricPreferenceFake()
    )

  beforeTest {
    accountRepository.reset()
    onboardingKeyboxSealedCsekDao.reset()
    gettingStartedTaskDao.reset()
    transactionPriorityPreference.reset()
    appPrivateKeyDao.reset()
    cloudBackupDao.reset()
  }

  test("not allowed to delete app data in Customer builds") {
    shouldThrow<IllegalStateException> {
      appDataDeleter(AppVariant.Customer).deleteAll()
    }
  }

  listOf(AppVariant.Development, AppVariant.Team).forEach { variant ->
    test("delete app data for $variant") {
      accountRepository.setActiveAccount(FullAccountMock)
      appPrivateKeyDao.storeAppKeyPair(AppGlobalAuthKeypairMock)
      appPrivateKeyDao.storeAppSpendingKeyPair(AppSpendingKeypair)
      onboardingAppKeyKeystoreFake
        .persistAppKeys(
          AppSpendingPublicKeyMock,
          AppGlobalAuthPublicKeyMock,
          AppRecoveryAuthPublicKeyMock,
          SIGNET
        )
      onboardingKeyboxHwAuthPublicKeyDao.set(
        OnboardingKeyboxHardwareKeys(
          hwAuthPublicKey = HwAuthPublicKey(Secp256k1PublicKey("fake-hw")),
          appGlobalAuthKeyHwSignature = AppGlobalAuthKeyHwSignatureMock
        )
      )
      transactionPriorityPreference.set(FASTEST)
      socRecStartedChallengeDao.set("fake")
      authSignatureStatusProvider.updateAuthSignatureStatus(AuthSignatureStatus.Unauthenticated)

      appDataDeleter(variant).deleteAll()

      accountRepository.accountState.value.shouldBeOk(NoAccount)
      appPrivateKeyDao.asymmetricKeys.shouldBeEmpty()
      authTokenDao.clearCalls.awaitItem()
      gettingStartedTaskDao.clearTasksCalls.awaitItem()
      keyboxDao.clearCalls.awaitItem()
      mobilePayService.deleteLocalCalls.awaitItem()
      transactionDetailDao.clearCalls.awaitItem()
      notificationTouchpointDao.clearCalls.awaitItem()
      onboardingKeyboxStepStateDao.clearCalls.awaitItem()
      onboardingKeyboxSealedCsekDao.sealedCsek.shouldBeNull()
      fwupDataDao.clearCalls.awaitItem()
      firmwareDeviceIdentifiersDao.clearCalls.awaitItem()
      firmwareMetadataDao.clearCalls.awaitItem()
      transactionPriorityPreference.preference.shouldBeNull()
      onboardingAppKeyKeystoreFake.appKeys.shouldBeNull()
      fiatCurrencyPreferenceRepository.clearCalls.awaitItem()
      homeUiBottomSheetDao.clearHomeUiBottomSheetCalls.awaitItem()
      bitcoinDisplayPreferenceRepository.clearCalls?.awaitItem()
      authKeyRotationAttemptMock.clearCalls.awaitItem()
      onboardingKeyboxHwAuthPublicKeyDao.keys?.hwAuthPublicKey.shouldBeNull()
      socRecStartedChallengeDao.pendingChallengeId.shouldBeNull()
      recoveryDaoMock.clearCalls.awaitItem()
      authSignatureStatusProvider.authSignatureStatus().value.shouldBe(AuthSignatureStatus.Authenticated)

      cloudBackupDao.shouldBeEmpty()
    }
  }
})
