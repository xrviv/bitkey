package build.wallet.bitkey.keybox

import build.wallet.bitcoin.BitcoinNetworkType.SIGNET
import build.wallet.bitkey.account.FullAccountConfig
import build.wallet.f8e.F8eEnvironment.Development

val FullAccountConfigMock =
  FullAccountConfig(
    bitcoinNetworkType = SIGNET,
    isHardwareFake = true,
    f8eEnvironment = Development,
    isUsingSocRecFakes = true,
    isTestAccount = true
  )