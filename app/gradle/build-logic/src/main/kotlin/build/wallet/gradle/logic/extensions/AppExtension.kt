package build.wallet.gradle.logic.extensions

import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Action
import org.gradle.api.Project
import javax.inject.Inject

/**
 * DSL for app features.
 * Example:
 * ```kotlin
 * buildLogic {
 *   app {
 *     version(yyyy = 2023, version = 2, patch = 96, build = 1)
 *   }
 * }
 * ```
 */
open class AppExtension
  @Inject
  constructor(
    private val project: Project,
  ) {
    fun version(
      yyyy: Int,
      version: Int,
      patch: Int,
      build: Int,
    ) = project.run {
      val appVersion =
        AppVersion(
          yyyy = yyyy,
          version = version,
          patch = patch,
          build = build
        )

      android {
        defaultConfig {
          versionName = appVersion.name
          versionCode = appVersion.code
        }
      }
    }
  }

private data class AppVersion(
  val yyyy: Int,
  val version: Int,
  val patch: Int,
  val build: Int,
) {
  init {
    validateVersionGroup(yyyy, 9999)
    validateVersionGroup(version, 99)
    validateVersionGroup(patch, 99)
    validateVersionGroup(build, 99)
  }

  /**
   * Creates a version name, for example "2023.2.96 (1)".
   */
  val name by lazy { "$yyyy.$version.$patch ($build)" }

  /**
   * A positive integer used as an internal version number. This number is used only to determine
   * whether one version is more recent than another, with higher numbers indicating more recent
   * versions.
   *
   * For example, code for "2023.2.96 build 2" version is 2023029602.
   */
  val code by lazy { yyyy * 1_00_00_00 + version * 1_00_00 + patch * 1_00 + build }

  private fun validateVersionGroup(
    group: Int,
    maxVersion: Int,
  ) = require(
    group in (0..maxVersion)
  ) {
    "Version group has to between 0 and $maxVersion inclusively, but was $group."
  }
}

private fun Project.android(configure: Action<ApplicationExtension>): Unit =
  extensions.configure("android", configure)
