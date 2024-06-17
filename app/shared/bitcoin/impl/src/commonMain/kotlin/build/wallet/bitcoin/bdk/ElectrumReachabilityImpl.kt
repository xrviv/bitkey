package build.wallet.bitcoin.bdk

import build.wallet.bdk.bindings.getBlockHash
import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitcoin.BitcoinNetworkType.REGTEST
import build.wallet.bitcoin.sync.ElectrumReachability
import build.wallet.bitcoin.sync.ElectrumReachability.ElectrumReachabilityError
import build.wallet.bitcoin.sync.ElectrumServer
import build.wallet.bitcoin.sync.chainHash
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.mapError

class ElectrumReachabilityImpl(
  private val bdkBlockchainProvider: BdkBlockchainProvider,
) : ElectrumReachability {
  override suspend fun reachable(
    electrumServer: ElectrumServer,
    network: BitcoinNetworkType,
  ): Result<Unit, ElectrumReachabilityError> {
    if (network == REGTEST) {
      // We don't have a hard-coded genesis block to check reachability for Regtest
      return Ok(Unit)
    }
    return coroutineBinding {
      val blockchain =
        bdkBlockchainProvider.getBlockchain(electrumServer)
          .result.mapError { ElectrumReachabilityError.Unreachable(it) }.bind()

      // Get genesis block hash and verify it matches user expectations
      val chainHash =
        blockchain.bdkBlockchain.getBlockHash(height = 0)
          .result
          .mapError { ElectrumReachabilityError.Unreachable(it) }
          .bind()

      if (chainHash == network.chainHash()) {
        Ok(Unit)
      } else {
        Err(ElectrumReachabilityError.IncompatibleNetwork)
      }.bind()
    }
  }
}
