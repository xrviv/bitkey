package build.wallet.sqldelight

import build.wallet.sqldelight.dummy.DummyDatabase
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class DatabaseContentsRendererTests : FunSpec({
  val sqlDriverFactory = inMemorySqlDriver().factory

  val sqlDriver =
    sqlDriverFactory.createDriver(
      dataBaseName = "test.db",
      dataBaseSchema = DummyDatabase.Schema
    )
  val testDataQueries = DummyDatabase(sqlDriver).dummyDataQueries

  test("empty database") {
    sqlDriver.databaseContents().renderText()
      .shouldBe(
        """
        |┌────────┐
        |│ dummy  │
        |├──┬─────┤
        |│id│value│
        |├──┴─────┤
        |│ Empty  │
        |└────────┘
        """.trimMargin()
      )
  }

  test("database with contents") {
    testDataQueries.insertDummyData(id = 0, "a")
    testDataQueries.insertDummyData(id = 1, "b")
    testDataQueries.insertDummyData(id = 2, "c")

    sqlDriver.databaseContents().renderText().shouldBe(
      """
      |┌────────┐
      |│ dummy  │
      |├──┬─────┤
      |│id│value│
      |├──┼─────┤
      |│0 │  a  │
      |├──┼─────┤
      |│1 │  b  │
      |├──┼─────┤
      |│2 │  c  │
      |└──┴─────┘
      """.trimMargin()
    )
  }
})
