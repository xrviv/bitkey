package build.wallet.testing

import androidx.compose.runtime.Composable
import build.wallet.bdk.BdkAddressBuilderImpl
import build.wallet.bdk.BdkBlockchainFactoryImpl
import build.wallet.bdk.BdkBumpFeeTxBuilderFactoryImpl
import build.wallet.bdk.BdkDescriptorFactoryImpl
import build.wallet.bdk.BdkDescriptorSecretKeyFactoryImpl
import build.wallet.bdk.BdkDescriptorSecretKeyGeneratorImpl
import build.wallet.bdk.BdkMnemonicGeneratorImpl
import build.wallet.bdk.BdkPartiallySignedTransactionBuilderImpl
import build.wallet.bdk.BdkTxBuilderFactoryImpl
import build.wallet.bdk.BdkWalletFactoryImpl
import build.wallet.bdk.bindings.BdkBlockchainFactory
import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitcoin.BitcoinNetworkType.REGTEST
import build.wallet.bitcoin.blockchain.BlockchainControl
import build.wallet.bitcoin.blockchain.NoopBlockchainControl
import build.wallet.bitcoin.blockchain.RegtestControl
import build.wallet.bitcoin.lightning.LightningInvoiceParserImpl
import build.wallet.bitcoin.treasury.TreasuryWallet
import build.wallet.bitcoin.treasury.TreasuryWalletFactory
import build.wallet.bitkey.account.FullAccountConfig
import build.wallet.cloud.store.CloudFileStoreFake
import build.wallet.cloud.store.CloudKeyValueStore
import build.wallet.cloud.store.CloudKeyValueStoreImpl
import build.wallet.cloud.store.CloudStoreAccountRepository
import build.wallet.cloud.store.CloudStoreAccountRepositoryImpl
import build.wallet.cloud.store.CloudStoreServiceProviderFake
import build.wallet.cloud.store.WritableCloudStoreAccountRepository
import build.wallet.crypto.Spake2Impl
import build.wallet.datadog.DatadogRumMonitorImpl
import build.wallet.di.ActivityComponentImpl
import build.wallet.di.AppComponentImpl
import build.wallet.di.makeAppComponent
import build.wallet.encrypt.CryptoBoxImpl
import build.wallet.encrypt.MessageSignerImpl
import build.wallet.encrypt.Secp256k1KeyGeneratorImpl
import build.wallet.encrypt.SignatureVerifierImpl
import build.wallet.encrypt.SymmetricKeyEncryptorImpl
import build.wallet.encrypt.SymmetricKeyGeneratorImpl
import build.wallet.encrypt.XChaCha20Poly1305Impl
import build.wallet.encrypt.XNonceGeneratorImpl
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.F8eEnvironment.Local
import build.wallet.firmware.TeltraMock
import build.wallet.logging.log
import build.wallet.money.exchange.ExchangeRateF8eClientMock
import build.wallet.nfc.FakeHardwareKeyStore
import build.wallet.nfc.FakeHardwareKeyStoreImpl
import build.wallet.nfc.FakeHardwareSpendingWalletProvider
import build.wallet.nfc.NfcCommandsFake
import build.wallet.nfc.NfcSessionFake
import build.wallet.nfc.platform.NfcCommands
import build.wallet.nfc.platform.NfcCommandsProvider
import build.wallet.phonenumber.PhoneNumberLibBindingsImpl
import build.wallet.platform.PlatformContext
import build.wallet.platform.biometrics.BiometricPrompterImpl
import build.wallet.platform.config.DeviceTokenConfig
import build.wallet.platform.config.DeviceTokenConfigProviderImpl
import build.wallet.platform.config.TouchpointPlatform.FcmTeam
import build.wallet.platform.data.File.join
import build.wallet.platform.data.FileDirectoryProviderImpl
import build.wallet.platform.pdf.PdfAnnotatorFactoryImpl
import build.wallet.platform.settings.SystemSettingsLauncher
import build.wallet.platform.sharing.SharingManager
import build.wallet.platform.web.InAppBrowserNavigator
import build.wallet.statemachine.cloud.CloudSignInUiStateMachineFake
import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.dev.cloud.CloudDevOptionsProps
import build.wallet.statemachine.dev.cloud.CloudDevOptionsStateMachine
import build.wallet.store.EncryptedKeyValueStoreFactory
import build.wallet.time.ControlledDelayer
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Duration.Companion.seconds

const val BITCOIN_NETWORK_ENV_VAR_NAME = "BITCOIN_NETWORK"
const val F8E_ENV_ENV_VAR_NAME = "F8E_ENVIRONMENT"

@Suppress("TooManyFunctions")
class AppTester(
  val app: ActivityComponentImpl,
  val fakeHardwareKeyStore: FakeHardwareKeyStore,
  val fakeNfcCommands: NfcCommandsFake,
  internal val sharingManager: SharingManagerFake,
  internal val blockchainControl: BlockchainControl,
  val treasuryWallet: TreasuryWallet,
  internal val initialF8eEnvironment: F8eEnvironment,
  val initialBitcoinNetworkType: BitcoinNetworkType,
  val isUsingSocRecFakes: Boolean,
) {
  /**
   * Creates a new [AppTester] that share data with an existing app instance.
   * It is not safe to continue using the previous [AppTester] instance after calling this method.
   */
  suspend fun relaunchApp(
    bdkBlockchainFactory: BdkBlockchainFactory? = null,
    f8eEnvironment: F8eEnvironment? = null,
  ): AppTester =
    launchApp(
      existingAppDir = app.appComponent.fileDirectoryProvider.appDir(),
      bdkBlockchainFactory = bdkBlockchainFactory,
      f8eEnvironment = f8eEnvironment,
      cloudStoreAccountRepository = app.cloudStoreAccountRepository,
      cloudKeyValueStore = app.cloudKeyValueStore,
      isUsingSocRecFakes = isUsingSocRecFakes,
      hardwareSeed = fakeHardwareKeyStore.getSeed()
    )

  companion object {
    /**
     * Creates a brand new [AppTester].
     */
    fun launchNewApp(
      bdkBlockchainFactory: BdkBlockchainFactory? = null,
      f8eEnvironment: F8eEnvironment? = null,
      bitcoinNetworkType: BitcoinNetworkType? = null,
      cloudStoreAccountRepository: CloudStoreAccountRepository? = null,
      cloudKeyValueStore: CloudKeyValueStore? = null,
      hardwareSeed: FakeHardwareKeyStore.Seed? = null,
      isUsingSocRecFakes: Boolean = false,
    ): AppTester =
      launchApp(
        existingAppDir = null,
        bdkBlockchainFactory,
        f8eEnvironment,
        bitcoinNetworkType,
        cloudStoreAccountRepository,
        cloudKeyValueStore,
        hardwareSeed,
        isUsingSocRecFakes
      )

    /**
     * Creates an [AppTester] instance
     * @param existingAppDir Specify where application data (databases) should be saved.
     * If there is existing data in the directory, it will be used by the new app.
     */
    @Suppress("NAME_SHADOWING")
    private fun launchApp(
      existingAppDir: String? = null,
      bdkBlockchainFactory: BdkBlockchainFactory? = null,
      f8eEnvironment: F8eEnvironment? = null,
      bitcoinNetworkType: BitcoinNetworkType? = null,
      cloudStoreAccountRepository: CloudStoreAccountRepository? = null,
      cloudKeyValueStore: CloudKeyValueStore? = null,
      hardwareSeed: FakeHardwareKeyStore.Seed? = null,
      isUsingSocRecFakes: Boolean,
    ): AppTester {
      /**
       * Get the `F8eEnvironment` from the environment variables, falling back to local.
       * Should only be used when first setting up the keybox. Once the keybox is set up,
       * callers should use the environment from the keybox's config.
       */
      val f8eEnvironment =
        f8eEnvironment ?: System.getenv(F8E_ENV_ENV_VAR_NAME)?.let {
          F8eEnvironment.parseString(it)
        } ?: Local
      val bitcoinNetworkType =
        bitcoinNetworkType ?: System.getenv(BITCOIN_NETWORK_ENV_VAR_NAME)?.let {
          BitcoinNetworkType.valueOf(it.uppercase())
        } ?: REGTEST
      val bdkBlockchainFactory = bdkBlockchainFactory ?: BdkBlockchainFactoryImpl()

      val platformContext = initPlatform(existingAppDir)
      val appComponent = createAppComponent(platformContext, bdkBlockchainFactory)
      val blockchainControl = createBlockchainControl(bitcoinNetworkType)
      val fakeHardwareKeyStore =
        createFakeHardwareKeyStore(appComponent.secureStoreFactory, hardwareSeed)
      val fakeHardwareSpendingWalletProvider =
        FakeHardwareSpendingWalletProvider(
          spendingWalletProvider = appComponent.spendingWalletProvider,
          descriptorBuilder = appComponent.bitcoinMultiSigDescriptorBuilder,
          fakeHardwareKeyStore = fakeHardwareKeyStore
        )
      val fakeNfcCommands =
        NfcCommandsFake(
          messageSigner = MessageSignerImpl(),
          fakeHardwareKeyStore = fakeHardwareKeyStore,
          fakeHardwareSpendingWalletProvider = fakeHardwareSpendingWalletProvider
        )
      val fakeSharingManager = SharingManagerFake()
      val activityComponent =
        createActivityComponent(
          appComponent = appComponent,
          fakeNfcCommands = fakeNfcCommands,
          sharingManager = fakeSharingManager,
          cloudStoreAccRepository = cloudStoreAccountRepository,
          cloudKeyValueStore = cloudKeyValueStore
        )

      val treasury =
        runBlocking {
          TreasuryWalletFactory(
            activityComponent.bitcoinBlockchain,
            blockchainControl,
            activityComponent.appComponent.spendingWalletProvider,
            BdkDescriptorSecretKeyFactoryImpl(),
            BdkDescriptorFactoryImpl()
          ).create(bitcoinNetworkType)
        }

      val fullAccountConfig =
        FullAccountConfig(
          bitcoinNetworkType = bitcoinNetworkType,
          isHardwareFake = true,
          f8eEnvironment = f8eEnvironment,
          isTestAccount = true,
          isUsingSocRecFakes = isUsingSocRecFakes,
          delayNotifyDuration = 0.seconds
        )
      runBlocking {
        appComponent.templateFullAccountConfigDao.set(fullAccountConfig)
      }

      return AppTester(
        activityComponent,
        fakeHardwareKeyStore,
        fakeNfcCommands,
        fakeSharingManager,
        blockchainControl,
        treasury,
        f8eEnvironment,
        bitcoinNetworkType,
        isUsingSocRecFakes
      )
    }
  }
}

private fun initPlatform(existingAppDir: String?): PlatformContext {
  val appDir =
    if (existingAppDir != null) {
      existingAppDir
    } else {
      val rootDir = (System.getProperty("user.dir") + "/_build/bitkey/appdata")
      val now = Clock.System.now().toString()
      rootDir.join(now)
    }
  log { "App data directory is $appDir" }
  val platformContext = PlatformContext(appDirOverride = appDir)
  val fileDirectoryProvider = FileDirectoryProviderImpl(platformContext)
  Files.createDirectories(Path.of(fileDirectoryProvider.databasesDir()))
  Files.createDirectories(Path.of(fileDirectoryProvider.filesDir()))
  return platformContext
}

private fun createAppComponent(
  platformContext: PlatformContext,
  bdkBlockchainFactory: BdkBlockchainFactory,
): AppComponentImpl {
  return makeAppComponent(
    bdkAddressBuilder = BdkAddressBuilderImpl(),
    bdkBlockchainFactory = bdkBlockchainFactory,
    bdkBumpFeeTxBuilderFactory = BdkBumpFeeTxBuilderFactoryImpl(),
    bdkPartiallySignedTransactionBuilder = BdkPartiallySignedTransactionBuilderImpl(),
    bdkTxBuilderFactory = BdkTxBuilderFactoryImpl(),
    bdkWalletFactory = BdkWalletFactoryImpl(),
    datadogRumMonitor = DatadogRumMonitorImpl(),
    delayer = ControlledDelayer(),
    deviceTokenConfigProvider =
      DeviceTokenConfigProviderImpl(
        DeviceTokenConfig("fake-device-token", FcmTeam)
      ),
    // we pass a mock exchange rate service to avoid calls to 3rd party exchange rate services
    // during tests
    exchangeRateF8eClient = ExchangeRateF8eClientMock(),
    messageSigner = MessageSignerImpl(),
    signatureVerifier = SignatureVerifierImpl(),
    platformContext = platformContext,
    teltra = TeltraMock()
  )
}

private fun createActivityComponent(
  appComponent: AppComponentImpl,
  fakeNfcCommands: NfcCommands,
  sharingManager: SharingManager,
  cloudStoreAccRepository: CloudStoreAccountRepository? = null,
  cloudKeyValueStore: CloudKeyValueStore? = null,
): ActivityComponentImpl {
  val cloudStoreAccountRepository = cloudStoreAccRepository
    ?: CloudStoreAccountRepositoryImpl(
      appComponent.keyValueStoreFactory
    )

  return ActivityComponentImpl(
    appComponent = appComponent,
    cloudKeyValueStore = cloudKeyValueStore
      ?: CloudKeyValueStoreImpl(appComponent.keyValueStoreFactory),
    cloudFileStore = CloudFileStoreFake(
      parentDir = appComponent.fileDirectoryProvider.filesDir(),
      fileManager = appComponent.fileManager
    ),
    cloudSignInUiStateMachine =
      CloudSignInUiStateMachineFake(
        cloudStoreAccountRepository as WritableCloudStoreAccountRepository,
        CloudStoreServiceProviderFake
      ),
    cloudDevOptionsStateMachine = cloudDevOptionsStateMachineNoop,
    cloudStoreAccountRepository = cloudStoreAccountRepository,
    datadogRumMonitor = DatadogRumMonitorImpl(),
    phoneNumberLibBindings = PhoneNumberLibBindingsImpl(),
    symmetricKeyEncryptor = SymmetricKeyEncryptorImpl(),
    symmetricKeyGenerator = SymmetricKeyGeneratorImpl(),
    lightningInvoiceParser = LightningInvoiceParserImpl(),
    nfcCommandsProvider = NfcCommandsProvider(fake = fakeNfcCommands, real = fakeNfcCommands),
    nfcSessionProvider = NfcSessionFake,
    sharingManager = sharingManager,
    systemSettingsLauncher = systemSettingsLauncher,
    inAppBrowserNavigator = inAppBrowserNavigator,
    xChaCha20Poly1305 = XChaCha20Poly1305Impl(),
    xNonceGenerator = XNonceGeneratorImpl(),
    pdfAnnotatorFactory = PdfAnnotatorFactoryImpl(),
    spake2 = Spake2Impl(),
    cryptoBox = CryptoBoxImpl(),
    biometricPrompter = BiometricPrompterImpl()
  )
}

private fun createBlockchainControl(networkType: BitcoinNetworkType): BlockchainControl =
  runBlocking {
    when (networkType) {
      REGTEST -> {
        val electrumUrl = System.getenv("ELECTRUM_HTTP_URL") ?: "http://localhost:8100"
        val bitcoindDomain = System.getenv("BITCOIND_DOMAIN") ?: "localhost:18443"
        val bitcoindUser = System.getenv("BITCOIND_USER") ?: "test"
        val bitcoindPassword = System.getenv("BITCOIND_PASSWORD") ?: "test"
        RegtestControl.create(
          bitcoindDomain = bitcoindDomain,
          bitcoindUser = bitcoindUser,
          bitcoindPassword = bitcoindPassword,
          electrumHttpApiUrl = electrumUrl
        )
      }

      else -> NoopBlockchainControl()
    }
  }

private fun createFakeHardwareKeyStore(
  secureStoreFactory: EncryptedKeyValueStoreFactory,
  hardwareSeed: FakeHardwareKeyStore.Seed?,
): FakeHardwareKeyStoreImpl {
  val bdkMnemonicGenerator = BdkMnemonicGeneratorImpl()
  val bdkDescriptorSecretKeyGenerator = BdkDescriptorSecretKeyGeneratorImpl()
  val publicKeyGenerator = Secp256k1KeyGeneratorImpl()
  val fakeHardwareKeyStore =
    FakeHardwareKeyStoreImpl(
      bdkMnemonicGenerator = bdkMnemonicGenerator,
      bdkDescriptorSecretKeyGenerator = bdkDescriptorSecretKeyGenerator,
      secp256k1KeyGenerator = publicKeyGenerator,
      encryptedKeyValueStoreFactory = secureStoreFactory
    )
  if (hardwareSeed != null) {
    runBlocking {
      fakeHardwareKeyStore.setSeed(hardwareSeed)
    }
  }
  return fakeHardwareKeyStore
}

private val inAppBrowserNavigator =
  object : InAppBrowserNavigator {
    override fun open(
      url: String,
      onClose: () -> Unit,
    ) {
      log { "Opened URL: $url " }
      onClose()
    }

    override fun onClose() = Unit

    override fun close() {
      onClose()
    }
  }

private val systemSettingsLauncher =
  object : SystemSettingsLauncher {
    override fun launchAppSettings() {
      log { "Launch App Settings" }
    }

    override fun launchSecuritySettings() {
      log { "Launch Security Settings" }
    }
  }

private val cloudDevOptionsStateMachineNoop = object : CloudDevOptionsStateMachine {
  @Composable
  override fun model(props: CloudDevOptionsProps): BodyModel {
    error("Not implemented")
  }
}
