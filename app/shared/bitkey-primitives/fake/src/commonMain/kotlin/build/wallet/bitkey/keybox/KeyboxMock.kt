package build.wallet.bitkey.keybox

import build.wallet.bitcoin.BitcoinNetworkType.SIGNET
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.account.FullAccountConfig
import build.wallet.bitkey.account.LiteAccount
import build.wallet.bitkey.account.LiteAccountConfig
import build.wallet.bitkey.auth.AppGlobalAuthKeyHwSignatureMock
import build.wallet.bitkey.auth.AppRecoveryAuthPublicKeyMock
import build.wallet.bitkey.f8e.FullAccountIdMock
import build.wallet.bitkey.f8e.LiteAccountId
import build.wallet.bitkey.spending.SpendingKeysetMock
import build.wallet.bitkey.spending.SpendingKeysetMock2
import build.wallet.compose.collections.emptyImmutableList
import build.wallet.f8e.F8eEnvironment.Development

val KeyboxMock =
  Keybox(
    localId = "keybox-fake-id",
    fullAccountId = FullAccountIdMock,
    activeSpendingKeyset = SpendingKeysetMock,
    activeAppKeyBundle = AppKeyBundleMock,
    activeHwKeyBundle = HwKeyBundleMock,
    inactiveKeysets = emptyImmutableList(),
    appGlobalAuthKeyHwSignature = AppGlobalAuthKeyHwSignatureMock,
    config =
      FullAccountConfig(
        bitcoinNetworkType = SIGNET,
        isHardwareFake = false,
        f8eEnvironment = Development,
        isUsingSocRecFakes = false,
        isTestAccount = true
      )
  )

val KeyboxMock2 =
  Keybox(
    localId = "keybox-fake-id",
    fullAccountId = FullAccountIdMock,
    activeSpendingKeyset = SpendingKeysetMock2,
    activeAppKeyBundle = AppKeyBundleMock2,
    activeHwKeyBundle = HwKeyBundleMock,
    inactiveKeysets = emptyImmutableList(),
    appGlobalAuthKeyHwSignature = AppGlobalAuthKeyHwSignatureMock,
    config =
      FullAccountConfig(
        bitcoinNetworkType = SIGNET,
        isHardwareFake = false,
        f8eEnvironment = Development,
        isUsingSocRecFakes = true,
        isTestAccount = true
      )
  )

val FullAccountMock =
  FullAccount(
    accountId = FullAccountIdMock,
    config = KeyboxMock.config,
    keybox = KeyboxMock
  )

val LiteAccountConfigMock =
  LiteAccountConfig(
    bitcoinNetworkType = SIGNET,
    f8eEnvironment = Development,
    isTestAccount = true,
    isUsingSocRecFakes = true
  )

val LiteAccountMock =
  LiteAccount(
    accountId = LiteAccountId("server-id"),
    config = LiteAccountConfigMock,
    recoveryAuthKey = AppRecoveryAuthPublicKeyMock
  )
