package build.wallet.ui.framework

import androidx.compose.runtime.Composable
import build.wallet.statemachine.NavigatingBodyModelFake
import build.wallet.statemachine.core.ScreenModel

class ScreenPresenterFake : ScreenPresenter<ScreenFake> {
  @Composable
  override fun model(
    navigator: Navigator,
    screen: ScreenFake,
  ): ScreenModel {
    return NavigatingBodyModelFake(id = screen.id, goTo = navigator::goTo).asRootScreen()
  }
}