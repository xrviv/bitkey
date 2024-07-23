package build.wallet.statemachine.partnerships

import build.wallet.analytics.events.EventTrackerMock
import build.wallet.analytics.v1.Action
import build.wallet.bitkey.keybox.KeyboxMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.f8e.partnerships.GetTransferPartnerListF8eClientMock
import build.wallet.f8e.partnerships.GetTransferRedirectF8eClientMock
import build.wallet.partnerships.*
import build.wallet.statemachine.core.awaitSheetWithBody
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.core.form.FormMainContentModel.Loader
import build.wallet.statemachine.core.test
import build.wallet.statemachine.data.keybox.address.KeyboxAddressDataMock
import build.wallet.statemachine.partnerships.transfer.PartnershipsTransferUiProps
import build.wallet.statemachine.partnerships.transfer.PartnershipsTransferUiStateMachineImpl
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf

class PartnershipsTransferUiStateMachineImplTests : FunSpec({
  // turbines
  val onBack = turbines.create<Unit>("on back calls")
  val onAnotherWalletOrExchange = turbines.create<Unit>("on another wallet or exchange calls")
  val onExitCalls = turbines.create<Unit>("on exit calls")
  val onPartnerRedirectedCalls =
    turbines.create<PartnerRedirectionMethod>(
      "on partner redirected calls"
    )
  val getTransferPartnerListF8eClient = GetTransferPartnerListF8eClientMock(turbines::create)
  val getTransferRedirectF8eClient = GetTransferRedirectF8eClientMock(turbines::create)
  val partnershipRepositoryMock = PartnershipTransactionStatusRepositoryMock(
    clearCalls = turbines.create("clear calls"),
    syncCalls = turbines.create("sync calls"),
    createCalls = turbines.create("create calls"),
    fetchMostRecentCalls = turbines.create("fetch most recent calls"),
    updateRecentTransactionStatusCalls = turbines.create("update recent transaction status calls")
  )
  val eventTracker = EventTrackerMock(turbines::create)

  // state machine
  val stateMachine =
    PartnershipsTransferUiStateMachineImpl(
      getTransferPartnerListF8eClient = getTransferPartnerListF8eClient,
      getTransferRedirectF8eClient = getTransferRedirectF8eClient,
      partnershipsRepository = partnershipRepositoryMock,
      eventTracker = eventTracker
    )

  fun props() =
    PartnershipsTransferUiProps(
      keybox = KeyboxMock,
      generateAddress = KeyboxAddressDataMock.generateAddress,
      onBack = {
        onBack.add(Unit)
      },
      onAnotherWalletOrExchange = {
        onAnotherWalletOrExchange.add(Unit)
      },
      onPartnerRedirected = { method, _ ->
        onPartnerRedirectedCalls.add(method)
      },
      onExit = {
        onExitCalls.add(Unit)
      }
    )

  // tests

  test("redirect partner") {
    stateMachine.test(props()) {
      getTransferPartnerListF8eClient.getTransferPartnersCall.awaitItem()

      awaitSheetWithBody<FormBodyModel> {
        mainContentList[0].shouldBeTypeOf<Loader>()
      }

      awaitSheetWithBody<FormBodyModel> {
        with(mainContentList[0].shouldBeTypeOf<FormMainContentModel.ListGroup>()) {
          listGroupModel.items.count().shouldBe(3)
          listGroupModel.items[0].title.shouldBe("Partner 1")
          listGroupModel.items[1].title.shouldBe("Partner 2")
          listGroupModel.items[2].title.shouldBe("Another exchange or wallet")

          listGroupModel.items[1].onClick.shouldNotBeNull().invoke()

          getTransferRedirectF8eClient.getTransferPartnersRedirectCall.awaitItem()
          awaitSheetWithBody<FormBodyModel> {
            mainContentList[0].shouldBeTypeOf<Loader>()
          }

          val partner1 = PartnerInfo(
            logoUrl = null,
            name = "Partner 1",
            partnerId = PartnerId("Partner1")
          )
          val partner2 = PartnerInfo(
            logoUrl = null,
            name = "Partner 2",
            partnerId = PartnerId("Partner2")
          )
          awaitSheetWithBody<FormBodyModel> {
            mainContentList[0].shouldBeTypeOf<Loader>()
            partnershipRepositoryMock.createCalls.awaitItem().should { (partnerInfo, type) ->
              type.shouldBe(PartnershipTransactionType.TRANSFER)
              partnerInfo.shouldBe(partner2)
            }
            onPartnerRedirectedCalls.awaitItem().shouldBe(
              PartnerRedirectionMethod.Web(
                "http://example.com/redirect_url",
                partnerInfo = partner2
              )
            )
          }

          eventTracker.eventCalls.awaitItem().should {
            it.action.shouldBe(Action.ACTION_APP_PARTNERSHIPS_VIEWED_TRANSFER_PARTNER)
            it.context.should { context ->
              context.shouldBeTypeOf<PartnerEventTrackerScreenIdContext>()
              context.name.shouldBe(partner1.partnerId.value)
            }
          }
          eventTracker.eventCalls.awaitItem().should {
            it.action.shouldBe(Action.ACTION_APP_PARTNERSHIPS_VIEWED_TRANSFER_PARTNER)
            it.context.should { context ->
              context.shouldBeTypeOf<PartnerEventTrackerScreenIdContext>()
              context.name.shouldBe(partner2.partnerId.value)
            }
          }
        }
      }
    }
  }

  test("another exchange or wallet clicked") {
    stateMachine.test(props()) {
      getTransferPartnerListF8eClient.getTransferPartnersCall.awaitItem()
      awaitSheetWithBody<FormBodyModel>()

      awaitSheetWithBody<FormBodyModel> {
        with(mainContentList[0].shouldBeTypeOf<FormMainContentModel.ListGroup>()) {
          listGroupModel.items[2].title.shouldBe("Another exchange or wallet")
          listGroupModel.items[2].onClick.shouldNotBeNull().invoke()

          onAnotherWalletOrExchange.awaitItem()
        }
      }
      repeat(2) {
        eventTracker.eventCalls.awaitItem().action.shouldBe(Action.ACTION_APP_PARTNERSHIPS_VIEWED_TRANSFER_PARTNER)
      }
    }
  }
})
