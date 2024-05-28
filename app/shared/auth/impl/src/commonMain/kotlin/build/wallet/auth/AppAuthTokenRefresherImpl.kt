package build.wallet.auth

import build.wallet.availability.AuthSignatureStatus
import build.wallet.availability.F8eAuthSignatureStatusProvider
import build.wallet.bitkey.app.AppAuthKey
import build.wallet.bitkey.f8e.AccountId
import build.wallet.crypto.PublicKey
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.auth.AuthenticationService
import build.wallet.logging.LogLevel
import build.wallet.logging.log
import build.wallet.logging.logFailure
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.binding.binding
import com.github.michaelbull.result.map
import com.github.michaelbull.result.mapError
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import com.github.michaelbull.result.orElse
import com.github.michaelbull.result.recoverIf
import com.github.michaelbull.result.toErrorIfNull

class AppAuthTokenRefresherImpl(
  private val authTokenDao: AuthTokenDao,
  private val accountAuthenticator: AccountAuthenticator,
  private val authenticationService: AuthenticationService,
  private val appAuthPublicKeyProvider: AppAuthPublicKeyProvider,
  private val f8eAuthSignatureStatusProvider: F8eAuthSignatureStatusProvider,
) : AppAuthTokenRefresher {
  override suspend fun refreshAccessTokenForAccount(
    f8eEnvironment: F8eEnvironment,
    accountId: AccountId,
    tokenScope: AuthTokenScope,
  ): Result<AccountAuthTokens, AuthError> =
    binding {
      log {
        "Attempting to refresh access token for active, onboarding or recovering account for $accountId"
      }

      val appAuthPublicKey =
        appAuthPublicKeyProvider.getAppAuthPublicKeyFromAccountOrRecovery(
          f8eEnvironment = f8eEnvironment,
          accountId = accountId,
          tokenScope = tokenScope
        )
          // Allow access token to be refreshed using the refresh token even if we don't have an
          // account or recovery. If the refresh token is expired, we will fail later in this flow.
          .recoverIf(
            predicate = { it is AccountMissing },
            transform = { null }
          )
          .bind()

      refreshAccessToken(
        f8eEnvironment = f8eEnvironment,
        accountId = accountId,
        authTokenScope = tokenScope,
        appAuthKey = appAuthPublicKey
      ).bind()
    }.logFailure { "Error refreshing access token for active or onboarding keybox for $accountId" }

  private suspend fun refreshAccessToken(
    f8eEnvironment: F8eEnvironment,
    accountId: AccountId,
    authTokenScope: AuthTokenScope,
    appAuthKey: PublicKey<out AppAuthKey>?,
  ): Result<AccountAuthTokens, AuthError> =
    binding {
      log(level = LogLevel.Debug) {
        "Attempting to refresh access token using app auth key $appAuthKey for $accountId"
      }

      // Retrieve current auth tokens from local storage
      val authTokens =
        authTokenDao
          .getTokensOfScope(accountId, authTokenScope)
          .toErrorIfNull {
            AuthStorageError(message = "Unable to find authToken for account: $accountId")
          }
          .mapError { AuthStorageError(it) }
          .bind()

      // Refresh auth tokens with f8e
      val newTokens =
        refresh(f8eEnvironment, authTokens.refreshToken)
          .orElse {
            // The refresh token we passed to f8e is likely expired, so we need to re-authenticate.
            if (appAuthKey == null) {
              // If we couldn't find an account or pending recovery, we won't be able to authenticate
              Err(AccountMissing)
            } else {
              accountAuthenticator
                .appAuth(f8eEnvironment, appAuthKey, authTokenScope)
                .logAuthFailure { "Error when re-authenticating with app key" }
                .map { it.authTokens }
                .onSuccess { _ ->
                  // If able to authenticate with app key, mark the auth signature as authenticated
                  f8eAuthSignatureStatusProvider.updateAuthSignatureStatus(
                    authSignatureStatus = AuthSignatureStatus.Authenticated
                  )
                }
                .onFailure { error ->
                  // If unable to authenticate with app key due to a signature mismatch, mark the auth
                  // status as unauthenticated
                  if (error is AuthSignatureMismatch) {
                    f8eAuthSignatureStatusProvider.updateAuthSignatureStatus(
                      authSignatureStatus = AuthSignatureStatus.Unauthenticated
                    )
                  }
                }
            }
          }
          .bind()

      // Update auth tokens in local storage
      authTokenDao
        .setTokensOfScope(accountId, newTokens, authTokenScope)
        .mapError(::AuthStorageError)
        .bind()

      newTokens
    }.logFailure { "Error refreshing access token using app auth key for $accountId" }

  private suspend fun refresh(
    f8eEnvironment: F8eEnvironment,
    refreshToken: RefreshToken,
  ): Result<AccountAuthTokens, AuthError> {
    return authenticationService
      .refreshToken(f8eEnvironment, refreshToken)
      .mapError { AuthNetworkError(cause = it) }
  }
}
