package build.wallet.bitcoin.bdk

import build.wallet.bdk.bindings.BdkAddressBuilderMock
import build.wallet.bdk.bindings.BdkAddressMock
import build.wallet.bdk.bindings.BdkBlockTime
import build.wallet.bdk.bindings.BdkNetwork
import build.wallet.bdk.bindings.BdkScriptMock
import build.wallet.bdk.bindings.BdkTransaction
import build.wallet.bdk.bindings.BdkTransactionDetails
import build.wallet.bdk.bindings.BdkTransactionMock
import build.wallet.bdk.bindings.BdkTxOutMock
import build.wallet.bitcoin.BlockTime
import build.wallet.bitcoin.address.someBitcoinAddress
import build.wallet.bitcoin.transactions.BitcoinTransaction
import build.wallet.bitcoin.transactions.TransactionDetailDaoMock
import build.wallet.bitcoin.wallet.shouldBePending
import build.wallet.coroutines.turbine.turbines
import build.wallet.money.BitcoinMoney
import build.wallet.time.someInstant
import com.ionspin.kotlin.bignum.integer.BigInteger
import com.ionspin.kotlin.bignum.integer.toBigInteger
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.datetime.Instant
import kotlin.time.DurationUnit
import kotlin.time.toDuration

class BdkTransactionMapperImplTests : FunSpec({

  val bdkAddressBuilder = BdkAddressBuilderMock(turbines::create)
  val transactionDetailDao = TransactionDetailDaoMock(turbines::create)
  val bdkWallet = BdkWalletMock(turbines::create)
  val mapper =
    BdkTransactionMapperImpl(
      bdkAddressBuilder = bdkAddressBuilder,
      transactionDetailDao = transactionDetailDao
    )
  val timestamp = someInstant
  val estimatedConfirmationTime = someInstant.plus(10.toDuration(DurationUnit.MINUTES))

  beforeTest {
    transactionDetailDao.reset()
    bdkAddressBuilder.reset()
    bdkWallet.reset()
  }

  suspend fun BdkTransactionMapper.createTransaction(
    bdkTransaction: BdkTransactionDetails,
  ): BitcoinTransaction {
    return createTransaction(bdkTransaction, BdkNetwork.SIGNET, bdkWallet)
  }

  test("Confirmation status when confirmation time is null") {
    mapper.createTransaction(makeTransactionDetails(confirmationTimestamp = null))
      .shouldBePending()
  }

  test("Confirmation status when confirmation time is nonnull") {
    mapper.createTransaction(makeTransactionDetails(confirmationTimestamp = timestamp))
      .confirmationStatus.shouldBe(
        BitcoinTransaction.ConfirmationStatus.Confirmed(
          blockTime = BlockTime(height = 1, timestamp = timestamp)
        )
      )
  }

  test("Broadcast time when time is not in dao") {
    mapper.createTransaction(makeTransactionDetails())
      .broadcastTime.shouldBeNull()
  }

  test("Broadcast time when time is in dao") {
    val transactionId = "123"
    transactionDetailDao.insert(
      broadcastTime = someInstant,
      transactionId = transactionId,
      estimatedConfirmationTime = estimatedConfirmationTime
    )
    transactionDetailDao.insertCalls.awaitItem()

    val transaction = mapper.createTransaction(makeTransactionDetails(id = transactionId))
    transaction.broadcastTime.shouldBe(someInstant)
  }

  test("Transaction is incoming when sent is zero") {
    mapper.createTransaction(
      bdkTransaction =
        makeTransactionDetails(
          received = 100,
          sent = 0
        )
    ).incoming.shouldBeTrue()
  }

  test("Transaction is not incoming when sent is not zero") {
    mapper.createTransaction(
      bdkTransaction =
        makeTransactionDetails(
          received = 100,
          sent = 1000
        )
    ).incoming.shouldBeFalse()
  }

  test("Transaction math when sent is zero") {
    val txn =
      mapper.createTransaction(
        bdkTransaction =
          makeTransactionDetails(
            received = 100,
            sent = 0
          )
      )

    txn.subtotal.fractionalUnitValue.shouldBe(100.toBigInteger())
    txn.total.fractionalUnitValue.shouldBe(100.toBigInteger())
  }

  test("Transaction math when sent is not zero") {
    val txn =
      mapper.createTransaction(
        bdkTransaction =
          makeTransactionDetails(
            received = 100,
            sent = 1000,
            fee = 100
          )
      )

    txn.subtotal.fractionalUnitValue.shouldBe(800.toBigInteger())
    txn.total.fractionalUnitValue.shouldBe(900.toBigInteger())
  }

  test("To address extracted and built for incoming") {
    val expectedAddress = someBitcoinAddress.address
    bdkAddressBuilder.buildFromScriptReturn = BdkAddressMock(expectedAddress)

    val script1 = BdkScriptMock()
    val script2 = BdkScriptMock()
    val txOut =
      listOf(
        BdkTxOutMock.copy(scriptPubkey = script1),
        BdkTxOutMock.copy(scriptPubkey = script2)
      )
    val incomingTxn =
      makeTransactionDetails(
        received = 100,
        sent = 0,
        transaction = BdkTransactionMock(output = txOut)
      )

    bdkWallet.isMineResultMap =
      mapOf(
        script1 to false,
        script2 to true
      )
    val transaction = mapper.createTransaction(incomingTxn)

    // We check the scripts to see which is isMine
    bdkWallet.isMineCalls.awaitItem().shouldBe(script1)
    bdkWallet.isMineCalls.awaitItem().shouldBe(script2)

    bdkAddressBuilder.buildFromScriptCalls.awaitItem().shouldBe(script2)

    transaction.recipientAddress.shouldNotBeNull().address.shouldBe(expectedAddress)
  }

  test("To address extracted and built for outgoing") {
    val expectedAddress = someBitcoinAddress.address
    bdkAddressBuilder.buildFromScriptReturn = BdkAddressMock(expectedAddress)

    val script1 = BdkScriptMock()
    val script2 = BdkScriptMock()
    val txOut =
      listOf(
        BdkTxOutMock.copy(scriptPubkey = script1),
        BdkTxOutMock.copy(scriptPubkey = script2)
      )
    val outgoingTxn =
      makeTransactionDetails(
        received = 0,
        sent = 100,
        transaction = BdkTransactionMock(output = txOut)
      )

    bdkWallet.isMineResultMap =
      mapOf(
        script1 to true,
        script2 to false
      )

    val transaction = mapper.createTransaction(outgoingTxn)

    // We check the scripts to see which is !isMine
    bdkWallet.isMineCalls.awaitItem().shouldBe(script1)
    bdkWallet.isMineCalls.awaitItem().shouldBe(script2)

    bdkAddressBuilder.buildFromScriptCalls.awaitItem().shouldBe(script2)

    transaction.recipientAddress.shouldNotBeNull().address.shouldBe(expectedAddress)
  }

  test("To address not determined when isMine is null") {
    val expectedAddress = someBitcoinAddress.address
    bdkAddressBuilder.buildFromScriptReturn = BdkAddressMock(expectedAddress)

    val script1 = BdkScriptMock()
    val script2 = BdkScriptMock()
    val txOut =
      listOf(
        BdkTxOutMock.copy(scriptPubkey = script1),
        BdkTxOutMock.copy(scriptPubkey = script2)
      )
    val outgoingTxn =
      makeTransactionDetails(
        received = 0,
        sent = 100,
        transaction = BdkTransactionMock(output = txOut)
      )

    bdkWallet.isMineResultMap = mapOf()
    val transaction = mapper.createTransaction(outgoingTxn)

    // We check the scripts to see which is !isMine, but since it errors at script1 it
    // short circuits and returns null
    bdkWallet.isMineCalls.awaitItem().shouldBe(script1)
    transaction.recipientAddress.shouldBeNull()
  }

  test("Receive-only transaction") {
    val netReceiveValue = BigInteger(400)
    val fees = BigInteger(21)
    val bdkTransaction =
      BdkTransactionDetails(
        transaction = BdkTransactionMock(output = listOf(BdkTxOutMock)),
        fee = fees,
        received = netReceiveValue,
        sent = BigInteger.ZERO,
        txid = "",
        confirmationTime = null
      )
    val tx = mapper.createTransaction(bdkTransaction)
    bdkWallet.isMineCalls.awaitItem()

    tx.incoming.shouldBeTrue()
    tx.total.shouldBe(BitcoinMoney.sats(netReceiveValue + fees))
    tx.subtotal.shouldBe(BitcoinMoney.sats(netReceiveValue))
  }

  test("Send transaction") {
    // Sum of our inputs
    val inputValue = BigInteger(1000)
    val fee = BigInteger(20)
    // How much we expect to get back, after send 580 sats + 20 sats fee (1000 - 580 - 20)
    val changeValue = inputValue - BigInteger(580) - fee
    val amountToSend = BigInteger(580) + fee

    val bdkTransaction =
      BdkTransactionDetails(
        transaction = BdkTransactionMock(output = listOf(BdkTxOutMock)),
        fee = fee,
        received = changeValue,
        sent = inputValue,
        txid = "",
        confirmationTime = null
      )
    val tx = mapper.createTransaction(bdkTransaction)
    bdkWallet.isMineCalls.awaitItem()

    tx.incoming.shouldBeFalse()
    tx.total.shouldBe(BitcoinMoney.sats(amountToSend))
    tx.subtotal.shouldBe(BitcoinMoney.sats(amountToSend - fee))
  }
})

private fun makeTransactionDetails(
  id: String = "4a5e1e4baab89f3a32518a88c31bc87f618f76673e2cc77ab2127b7afdeda33b",
  received: Long = 0,
  sent: Long = 2_000,
  fee: Long? = null,
  confirmationTimestamp: Instant? = someInstant,
  transaction: BdkTransaction = BdkTransactionMock(),
) = BdkTransactionDetails(
  transaction = transaction,
  fee = fee?.toBigInteger(),
  received = received.toBigInteger(),
  sent = sent.toBigInteger(),
  txid = id,
  confirmationTime =
    confirmationTimestamp?.let {
      BdkBlockTime(
        height = 1,
        timestamp = it
      )
    }
)
