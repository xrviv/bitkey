package build.wallet.cloud.backup

import build.wallet.account.AccountRepositoryFake
import build.wallet.account.AccountStatus
import build.wallet.auth.AccessToken
import build.wallet.auth.AccountAuthTokens
import build.wallet.auth.AccountAuthenticatorMock
import build.wallet.auth.AuthStorageError
import build.wallet.auth.AuthTokenDaoMock
import build.wallet.auth.AuthTokenScope.Recovery
import build.wallet.auth.RefreshToken
import build.wallet.bitcoin.AppPrivateKeyDaoFake
import build.wallet.bitkey.auth.AppRecoveryAuthPrivateKeyMock
import build.wallet.bitkey.auth.AppRecoveryAuthPublicKeyMock
import build.wallet.bitkey.f8e.LiteAccountIdMock
import build.wallet.bitkey.keybox.LiteAccountMock
import build.wallet.cloud.backup.RestoreFromBackupError.AccountBackupRestorationError
import build.wallet.cloud.backup.local.BackupStorageError
import build.wallet.cloud.backup.local.CloudBackupDaoFake
import build.wallet.coroutines.turbine.turbines
import build.wallet.recovery.socrec.SocRecKeysDaoFake
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.shouldBe

class LiteAccountCloudBackupRestorerImplTests : FunSpec({

  val authTokenDao = AuthTokenDaoMock(turbines::create)
  val accountAuthenticator = AccountAuthenticatorMock(turbines::create)
  val appPrivateKeyDaoFake = AppPrivateKeyDaoFake()
  val cloudBackupDao = CloudBackupDaoFake()
  val accountRepository = AccountRepositoryFake()

  val restorer =
    LiteAccountCloudBackupRestorerImpl(
      appPrivateKeyDao = appPrivateKeyDaoFake,
      socRecKeysDao = SocRecKeysDaoFake(),
      accountAuthenticator = accountAuthenticator,
      authTokenDao = authTokenDao,
      cloudBackupDao = cloudBackupDao,
      accountRepository = accountRepository
    )

  beforeTest {
    accountAuthenticator.reset()
    authTokenDao.reset()
    appPrivateKeyDaoFake.reset()
    accountRepository.reset()
  }

  test("success") {
    restorer
      .restoreFromBackup(CloudBackupV2WithLiteAccountMock)
      .shouldBe(Ok(LiteAccountMock))
    accountAuthenticator.authCalls.awaitItem().shouldBe(
      CloudBackupV2WithLiteAccountMock.appRecoveryAuthKeypair.publicKey
    )
    authTokenDao.setTokensCalls.awaitItem().shouldBe(
      AuthTokenDaoMock.SetTokensParams(
        accountId = LiteAccountIdMock,
        tokens =
          AccountAuthTokens(
            accessToken = AccessToken(raw = "access-token-fake"),
            refreshToken = RefreshToken(raw = "refresh-token-fake")
          ),
        scope = Recovery
      )
    )
    appPrivateKeyDaoFake
      .appAuthKeys
      .shouldBe(
        mapOf(
          AppRecoveryAuthPublicKeyMock to AppRecoveryAuthPrivateKeyMock
        )
      )
    accountRepository.accountState.value.shouldBeEqual(
      Ok(
        AccountStatus.OnboardingAccount(
          LiteAccountMock
        )
      )
    )
  }

  test("failure - account authenticator error") {
    val authError = AuthStorageError()
    accountAuthenticator.authResults =
      mutableListOf(
        Err(AuthStorageError())
      )
    restorer
      .restoreFromBackup(CloudBackupV2WithLiteAccountMock)
      .shouldBe(Err(AccountBackupRestorationError(authError)))
    accountAuthenticator.authCalls.awaitItem().shouldBe(
      CloudBackupV2WithLiteAccountMock.appRecoveryAuthKeypair.publicKey
    )
  }

  test("failure - store app auth keypair failure") {
    val throwable = Throwable("foo")
    appPrivateKeyDaoFake.storeAppAuthKeyPairResult = Err(throwable)
    restorer
      .restoreFromBackup(CloudBackupV2WithLiteAccountMock)
      .shouldBe(Err(AccountBackupRestorationError(throwable)))
  }

  test("failure - cloud backup dao failure") {
    cloudBackupDao.returnError = true
    restorer
      .restoreFromBackup(CloudBackupV2WithLiteAccountMock)
      .shouldBe(Err(AccountBackupRestorationError(BackupStorageError())))
    accountAuthenticator.authCalls.awaitItem().shouldBe(
      CloudBackupV2WithLiteAccountMock.appRecoveryAuthKeypair.publicKey
    )
    authTokenDao.setTokensCalls.awaitItem().shouldBe(
      AuthTokenDaoMock.SetTokensParams(
        accountId = LiteAccountIdMock,
        tokens =
          AccountAuthTokens(
            accessToken = AccessToken(raw = "access-token-fake"),
            refreshToken = RefreshToken(raw = "refresh-token-fake")
          ),
        scope = Recovery
      )
    )
    appPrivateKeyDaoFake
      .appAuthKeys
      .shouldBe(
        mapOf(
          AppRecoveryAuthPublicKeyMock to AppRecoveryAuthPrivateKeyMock
        )
      )
  }
})
