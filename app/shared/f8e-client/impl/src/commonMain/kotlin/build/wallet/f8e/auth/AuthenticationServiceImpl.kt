package build.wallet.f8e.auth

import build.wallet.auth.AccessToken
import build.wallet.auth.AccountAuthTokens
import build.wallet.auth.AuthTokenScope
import build.wallet.auth.RefreshToken
import build.wallet.bitkey.app.AppAuthKey
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.crypto.PublicKey
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.auth.AuthenticationService.InitiateAuthenticationSuccess
import build.wallet.f8e.client.UnauthenticatedF8eHttpClient
import build.wallet.ktor.result.NetworkingError
import build.wallet.ktor.result.RedactedRequestBody
import build.wallet.ktor.result.RedactedResponseBody
import build.wallet.ktor.result.bodyResult
import build.wallet.ktor.result.setRedactedBody
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import io.ktor.client.request.post
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class AuthenticationServiceImpl(
  private val f8eHttpClient: UnauthenticatedF8eHttpClient, // only require unauthenticated calls
) : AuthenticationService {
  override suspend fun initiateAuthentication(
    f8eEnvironment: F8eEnvironment,
    authPublicKey: HwAuthPublicKey,
  ): Result<InitiateAuthenticationSuccess, NetworkingError> =
    authenticate(f8eEnvironment, AuthenticationRequest(authPublicKey))

  override suspend fun initiateAuthentication(
    f8eEnvironment: F8eEnvironment,
    authPublicKey: PublicKey<out AppAuthKey>,
    tokenScope: AuthTokenScope,
  ): Result<InitiateAuthenticationSuccess, NetworkingError> =
    authenticate(f8eEnvironment, AuthenticationRequest(authPublicKey, tokenScope))

  private suspend fun authenticate(
    f8eEnvironment: F8eEnvironment,
    req: AuthenticationRequest,
  ) = f8eHttpClient.unauthenticated(f8eEnvironment)
    .bodyResult<InitiateAuthenticationSuccess> {
      post("/api/authenticate") {
        setRedactedBody(req)
      }
    }

  @Serializable
  private data class AuthenticationRequest(
    @SerialName("auth_request_key")
    val authRequestKey: Map<String, String>,
  ) : RedactedRequestBody {
    constructor(
      authPublicKey: HwAuthPublicKey,
    ) : this(mapOf("HwPubkey" to authPublicKey.pubKey.value))

    constructor(authPublicKey: PublicKey<out AppAuthKey>, tokenScope: AuthTokenScope) : this(
      when (tokenScope) {
        AuthTokenScope.Global -> mapOf("AppPubkey" to authPublicKey.value)
        AuthTokenScope.Recovery -> mapOf("RecoveryPubkey" to authPublicKey.value)
      }
    )
  }

  override suspend fun completeAuthentication(
    f8eEnvironment: F8eEnvironment,
    username: String,
    challengeResponse: String,
    session: String,
  ): Result<AccountAuthTokens, NetworkingError> {
    val tokenRequest =
      GetTokensRequest(
        challenge = ChallengeResponseParameters(username, challengeResponse, session)
      )
    return f8eHttpClient.unauthenticated(f8eEnvironment)
      .bodyResult<AuthTokensSuccess> {
        post("/api/authenticate/tokens") {
          setRedactedBody(tokenRequest)
        }
      }.map { AccountAuthTokens(AccessToken(it.accessToken), RefreshToken(it.refreshToken)) }
  }

  override suspend fun refreshToken(
    f8eEnvironment: F8eEnvironment,
    refreshToken: RefreshToken,
  ): Result<AccountAuthTokens, NetworkingError> {
    val tokenRequest = GetTokensRequest(refreshToken = refreshToken.raw)
    return f8eHttpClient.unauthenticated(f8eEnvironment)
      .bodyResult<AuthTokensSuccess> {
        post("/api/authenticate/tokens") {
          setRedactedBody(tokenRequest)
        }
      }.map { AccountAuthTokens(AccessToken(it.accessToken), RefreshToken(it.refreshToken)) }
  }

  @Serializable
  private data class ChallengeResponseParameters(
    val username: String,
    @SerialName("challenge_response")
    val challengeResponse: String,
    val session: String,
  )

  @Serializable
  private data class GetTokensRequest(
    @SerialName("challenge")
    val challenge: ChallengeResponseParameters? = null,
    @SerialName("refresh_token")
    val refreshToken: String? = null,
  ) : RedactedRequestBody

  @Serializable
  private data class AuthTokensSuccess(
    @SerialName("access_token")
    val accessToken: String,
    @SerialName("refresh_token")
    val refreshToken: String,
  ) : RedactedResponseBody
}
