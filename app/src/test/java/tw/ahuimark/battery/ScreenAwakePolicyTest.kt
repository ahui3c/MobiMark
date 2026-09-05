package tw.ahuimark.battery

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tw.ahuimark.battery.model.BenchmarkPhase

class ScreenAwakePolicyTest {
    @Test
    fun `only active test phases keep the screen awake`() {
        assertFalse(shouldKeepScreenAwake(BenchmarkPhase.SETUP))
        assertTrue(shouldKeepScreenAwake(BenchmarkPhase.PRECONDITIONING))
        assertTrue(shouldKeepScreenAwake(BenchmarkPhase.RUNNING))
        assertFalse(shouldKeepScreenAwake(BenchmarkPhase.COMPLETED))
        assertFalse(shouldKeepScreenAwake(BenchmarkPhase.ABORTED))
    }
}
