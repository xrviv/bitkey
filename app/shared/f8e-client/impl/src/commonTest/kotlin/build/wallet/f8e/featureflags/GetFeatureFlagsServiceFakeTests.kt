package build.wallet.f8e.featureflags

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

class GetFeatureFlagsServiceFakeTests : FunSpec({
  val jsonDecoder = Json { ignoreUnknownKeys = true }

  test("deserialize boolean feature flag JSON") {
    val trueFlagJSON =
      """
      {
      "key":"key1",
      "name":"Feature Enabled",
      "description":"The feature is enabled",
      "value":{"boolean": true}
      }
      """.trimIndent()
    val trueFlag: GetFeatureFlagsService.F8eFeatureFlag = jsonDecoder.decodeFromString(trueFlagJSON)
    trueFlag.key.shouldBe("key1")
    trueFlag.name.shouldBe("Feature Enabled")
    trueFlag.description.shouldBe("The feature is enabled")
    trueFlag.value.shouldBe(F8eFeatureFlagValue.BooleanValue(true))

    val falseFlagJSON =
      """
      {
      "key":"key1",
      "name":"Feature Enabled",
      "description":"The feature is enabled",
      "value":{"boolean": false}
      }
      """.trimIndent()
    val falseFlag: GetFeatureFlagsService.F8eFeatureFlag =
      jsonDecoder.decodeFromString(falseFlagJSON)
    falseFlag.value.shouldBe(F8eFeatureFlagValue.BooleanValue(false))
  }

  test("deserialize string feature flag JSON") {
    val flagJSON =
      """
      {
      "key":"key1",
      "name":"Feature Enabled",
      "description":"The feature is enabled",
      "value":{"string": "string value"}
      }
      """.trimIndent()
    val flag: GetFeatureFlagsService.F8eFeatureFlag = jsonDecoder.decodeFromString(flagJSON)
    flag.key.shouldBe("key1")
    flag.name.shouldBe("Feature Enabled")
    flag.description.shouldBe("The feature is enabled")
    flag.value.shouldBe(F8eFeatureFlagValue.StringValue("string value"))
  }

  test("deserialize double feature flag JSON") {
    val flagJSON =
      """
      {
      "key":"key1",
      "name":"Feature Enabled",
      "description":"The feature is enabled",
      "value":{"double": 3.14}
      }
      """.trimIndent()
    val flag: GetFeatureFlagsService.F8eFeatureFlag = jsonDecoder.decodeFromString(flagJSON)
    flag.key.shouldBe("key1")
    flag.name.shouldBe("Feature Enabled")
    flag.description.shouldBe("The feature is enabled")
    flag.value.shouldBe(F8eFeatureFlagValue.DoubleValue(3.14))
  }

  test("wrong value type feature flag JSON throws") {
    val flagJSON =
      """
      {
      "key":"key1",
      "name":"Feature Enabled",
      "description":"The feature is enabled",
      "value":{"string": 123}
      }
      """.trimIndent()
    shouldThrow<SerializationException> {
      jsonDecoder.decodeFromString(flagJSON)
    }
  }

  test("empty value JSON throws") {
    val flagJSON =
      """
      {
      "key":"key1",
      "name":"Feature Enabled",
      "description":"The feature is enabled",
      "value":{}
      }
      """.trimIndent()

    shouldThrow<SerializationException> {
      jsonDecoder.decodeFromString(flagJSON)
    }
  }

  test("unknown value JSON throws") {
    val flagJSON =
      """
      {
      "key":"key1",
      "name":"Feature Enabled",
      "description":"The feature is enabled",
      "value":{color: "blue"}
      }
      """.trimIndent()

    shouldThrow<SerializationException> {
      jsonDecoder.decodeFromString(flagJSON)
    }
  }

  test("missing value JSON throws") {
    val flagJSON =
      """
      {
      "key":"key1",
      "name":"Feature Enabled",
      "description":"The feature is enabled",
      }
      """.trimIndent()

    shouldThrow<SerializationException> {
      jsonDecoder.decodeFromString(flagJSON)
    }
  }

  test("multiple value JSON throws") {
    val flagJSON =
      """
      {
      "key":"key1",
      "name":"Feature Enabled",
      "description":"The feature is enabled",
      "value":{boolean: true, string: "string value"}
      }
      """.trimIndent()

    shouldThrow<SerializationException> {
      jsonDecoder.decodeFromString(flagJSON)
    }
  }
})
