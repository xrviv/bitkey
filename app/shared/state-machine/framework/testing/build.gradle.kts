import build.wallet.gradle.logic.extensions.targets

plugins {
  id("build.wallet.kmp")
  alias(libs.plugins.compose.runtime)
  alias(libs.plugins.kotlin.coroutines.native)
}

kotlin {
  targets(ios = true, jvm = true)

  sourceSets {
    commonMain {
      dependencies {
        api(compose.runtime)
        api(libs.kmp.test.kotest.assertions)
        api(libs.kmp.test.turbine)
        api(projects.shared.coroutinesTesting)
        implementation(libs.kmp.molecule.runtime)
      }
    }
  }
}
