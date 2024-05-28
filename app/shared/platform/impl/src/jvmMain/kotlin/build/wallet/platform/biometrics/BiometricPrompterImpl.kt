package build.wallet.platform.biometrics

class BiometricPrompterImpl : BiometricPrompter {
  override fun biometricsAvailability(): BiometricsResult<Boolean> {
    // no-op on jvm, just return error
    return BiometricsResult.Err(BiometricError.HardwareUnavailable())
  }

  override suspend fun enrollBiometrics(): BiometricsResult<Unit> {
    // no-op on jvm, just return ok
    return BiometricsResult.Ok(Unit)
  }

  override fun promptForAuth() {
    // no-op on jvm
  }
}