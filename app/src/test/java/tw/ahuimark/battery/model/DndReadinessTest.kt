package tw.ahuimark.battery.model

import org.junit.Assert.*
import org.junit.Test

class DndReadinessTest {
    private fun ready() = BenchmarkUiState(
        readiness = DeviceReadiness(batteryAbove80 = true, unplugged = true,
            cameraPermission = true, rearUhd30Supported = true,
            mediaAssetReady = true, storageReady = true),
        settings = BenchmarkSettings())

    @Test fun enabledByDefaultRequiresPermission() {
        val state = ready()
        assertTrue(state.settings.automaticDnd)
        assertFalse(state.canStart)
        assertTrue(state.copy(dndAccessGranted = true).canStart)
    }
    @Test fun explicitOptOutAllowsTestWithoutPermission() {
        val state = ready()
        assertTrue(state.copy(settings = state.settings.copy(automaticDnd = false)).canStart)
    }
    @Test fun pendingRecoveryBlocksBothModesEvenAfterOptOut() {
        for (mode in BenchmarkMode.entries) {
            val state = ready().copy(mode = mode, dndAccessGranted = true, dndRecoveryPending = true)
            assertFalse(state.canStart)
            assertFalse(state.copy(settings = state.settings.copy(automaticDnd = false)).canStart)
        }
    }
}
