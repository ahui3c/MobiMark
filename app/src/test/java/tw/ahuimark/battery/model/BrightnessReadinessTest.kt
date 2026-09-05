package tw.ahuimark.battery.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrightnessReadinessTest {
    private val readyDevice = DeviceReadiness(
        batteryAbove80 = true,
        unplugged = true,
        cameraPermission = true,
        rearUhd30Supported = true,
        mediaAssetReady = false,
        storageReady = true
    )

    @Test
    fun externalCalibrationDoesNotRequireAnAppBrightnessValueOrConfirmation() {
        val state = BenchmarkUiState(
            dndAccessGranted = true,
            readiness = readyDevice,
            settings = BenchmarkSettings(videoSourceMode = VideoSourceMode.ONLINE)
        )
        BenchmarkMode.entries.forEach { mode -> assertTrue(state.copy(mode = mode).canStart) }
        assertFalse(state.copy(readiness = readyDevice.copy(batteryAbove80 = false)).canStart)
        assertFalse(state.copy(readiness = readyDevice.copy(unplugged = false)).canStart)
    }

    @Test
    fun automaticBrightnessIsAdvisoryNotAnExternalCalibrationGate() {
        val state = BenchmarkUiState(dndAccessGranted = true, readiness = readyDevice)
        assertTrue(state.copy(readiness = readyDevice.copy(automaticBrightnessDisabled = false)).canStart)
        assertTrue(state.copy(readiness = readyDevice.copy(automaticBrightnessDisabled = true)).canStart)
    }
}
