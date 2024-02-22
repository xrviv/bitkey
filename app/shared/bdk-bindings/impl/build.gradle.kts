import build.wallet.gradle.logic.extensions.allTargets
import build.wallet.gradle.logic.gradle.exclude

plugins {
  id("build.wallet.kmp")
}

kotlin {
  allTargets()

  sourceSets {
    commonMain {
      dependencies {
        implementation(projects.shared.loggingPublic)
        implementation(projects.shared.stdlibPublic)
      }
    }

    commonTest {
      dependencies {
        implementation(projects.shared.bdkBindingsFake) {
          exclude(projects.shared.bdkBindingsPublic)
        }
      }
    }

    val jvmMain by getting {
      dependencies {
        implementation(libs.jvm.bdk)
      }
    }

    val androidMain by getting {
      dependencies {
        implementation(libs.android.bdk)
      }
    }
  }
}
