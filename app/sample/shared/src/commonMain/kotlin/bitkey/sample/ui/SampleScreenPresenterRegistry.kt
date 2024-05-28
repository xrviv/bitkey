package bitkey.sample.ui

import bitkey.sample.ui.settings.account.AccountSettingsScreen
import bitkey.sample.ui.settings.account.AccountSettingsScreenPresenter
import build.wallet.ui.framework.Screen
import build.wallet.ui.framework.ScreenPresenter
import build.wallet.ui.framework.ScreenPresenterRegistry

class SampleScreenPresenterRegistry(
  private val accountSettingsScreenPresenter: AccountSettingsScreenPresenter,
) : ScreenPresenterRegistry {
  override fun <ScreenT : Screen> get(screen: ScreenT): ScreenPresenter<ScreenT> {
    @Suppress("UNCHECKED_CAST")
    return when (screen) {
      is AccountSettingsScreen -> accountSettingsScreenPresenter
      else -> error("Unknown screen: ${screen::class.simpleName}")
    } as ScreenPresenter<ScreenT>
  }
}
