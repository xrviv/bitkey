package build.wallet.f8e.partnerships

import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.client.F8eHttpClient
import build.wallet.ktor.result.NetworkingError
import build.wallet.ktor.result.bodyResult
import build.wallet.logging.logNetworkFailure
import build.wallet.partnerships.PartnershipTransactionId
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import io.ktor.client.request.get
import kotlinx.serialization.Serializable

class GetPartnershipTransactionServiceImpl(
  private val client: F8eHttpClient,
) : GetPartnershipTransactionService {
  override suspend fun getPartnershipTransaction(
    fullAccountId: FullAccountId,
    f8eEnvironment: F8eEnvironment,
    partner: String,
    partnershipTransactionId: PartnershipTransactionId,
  ): Result<F8ePartnershipTransaction, NetworkingError> {
    return client
      .authenticated(
        accountId = fullAccountId,
        f8eEnvironment = f8eEnvironment
      )
      .bodyResult<PartnershipTransactionResponse> {
        get("/api/partnerships/partners/$partner/transactions/${partnershipTransactionId.value}")
      }
      .logNetworkFailure {
        "Failed to get partnership transaction"
      }
      .map {
        it.transaction
      }
  }

  @Serializable
  internal data class PartnershipTransactionResponse(
    val transaction: F8ePartnershipTransaction,
  )
}