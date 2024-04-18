package build.wallet.limit

import build.wallet.bitkey.account.FullAccount
import build.wallet.f8e.auth.HwFactorProofOfPossession
import com.github.michaelbull.result.Result

interface MobilePayLimitSetter {
  suspend fun setLimit(
    account: FullAccount,
    spendingLimit: SpendingLimit,
    hwFactorProofOfPossession: HwFactorProofOfPossession,
  ): Result<Unit, SetMobilePayLimitError>

  sealed class SetMobilePayLimitError : Error() {
    data class AccountMissing(override val cause: Throwable?) : SetMobilePayLimitError()

    data class F8eFailedToSaveLimit(override val cause: Throwable) : SetMobilePayLimitError()

    data class FailedToUpdateLocalLimitState(
      override val cause: Throwable,
    ) : SetMobilePayLimitError()
  }
}
