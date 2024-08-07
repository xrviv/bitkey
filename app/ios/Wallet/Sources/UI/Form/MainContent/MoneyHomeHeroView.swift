import Foundation
import Shared
import SwiftUI

// MARK: -

struct MoneyHomeHeroView: View {

    // MARK: - Private Properties

    private let viewModel: FormMainContentModel.MoneyHomeHero

    // MARK: - Life Cycle

    init(viewModel: FormMainContentModel.MoneyHomeHero) {
        self.viewModel = viewModel
    }

    // MARK: - View

    var body: some View {
        ZStack(alignment: Alignment(horizontal: .center, vertical: .top)) {
            Image(uiImage: .moneyHomeHero)
                .resizable()
                .aspectRatio(contentMode: .fill)
                .frame(width: 210, height: 224, alignment: .top)
                .mask(Rectangle())

            VStack(spacing: 0) {
                Spacer()
                    .frame(height: 64)
                CollapsibleLabelContainer(
                    collapsed: viewModel.isHidden,
                    topContent: HStack {
                        Spacer()
                        ModeledText(
                            model: .standard(
                                viewModel.primaryAmount,
                                font: .title1,
                                textAlignment: nil
                            )
                        )
                        .numericTextAnimation(numericText: viewModel.primaryAmount)
                        Spacer()
                    },
                    bottomContent: ModeledText(
                        model: .standard(
                            viewModel.secondaryAmount,
                            font: .body4Medium,
                            textAlignment: .center,
                            textColor: .foreground60
                        )
                    ),
                    collapsedContent: CollapsedMoneyView(height: 16)
                )
                Spacer()
            }
        }
        .mask {
            LinearGradient(
                gradient: .init(
                    stops: [
                        .init(color: Color.background, location: 0),
                        .init(color: Color.background, location: 0.7),
                        .init(color: .clear, location: 1),
                    ]
                ),
                startPoint: .top,
                endPoint: .bottom
            )
        }
    }

}
