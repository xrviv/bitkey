import build.wallet.gradle.logic.extensions.targets

plugins {
  id("build.wallet.kmp")
  alias(libs.plugins.compose.runtime)
}

kotlin {
  targets(android = true, jvm = true)

  sourceSets {
    commonMain {
      dependencies {
        api(projects.shared.platformPublic)
        implementation(projects.shared.loggingPublic)
      }
    }

    val androidMain by getting {
      dependencies {
        implementation(compose.runtime)
        implementation(libs.android.compose.ui.activity)
        implementation(libs.android.google.play.services.coroutines)
      }
    }
  }
}
