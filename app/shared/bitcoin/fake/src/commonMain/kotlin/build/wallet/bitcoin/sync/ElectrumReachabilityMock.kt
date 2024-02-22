package build.wallet.bitcoin.sync

import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitcoin.sync.ElectrumReachability.ElectrumReachabilityError
import com.github.michaelbull.result.Result

data class ElectrumReachabilityMock(
  var reachableResult: Result<Unit, ElectrumReachabilityError>,
) : ElectrumReachability {
  override suspend fun reachable(
    electrumServer: ElectrumServer,
    network: BitcoinNetworkType,
  ): Result<Unit, ElectrumReachabilityError> {
    return reachableResult
  }
}
