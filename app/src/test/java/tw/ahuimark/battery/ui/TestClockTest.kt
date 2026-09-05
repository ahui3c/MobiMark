package tw.ahuimark.battery.ui

import org.junit.Assert.*
import org.junit.Test
import tw.ahuimark.battery.model.*

class TestClockTest {
    @Test fun fullModeHasElapsedButNoEstimatedRemaining() {
        val clock = testClock(BenchmarkUiState(phase = BenchmarkPhase.RUNNING, elapsedOfficialMs = 3_661_000))
        assertEquals("已測試時間", clock.elapsedLabel)
        assertEquals("01:01:01", clock.elapsed)
        assertNull(clock.remaining)
    }
    @Test fun quickCountdownUsesOfficialTime() {
        val clock = testClock(BenchmarkUiState(mode = BenchmarkMode.QUICK,
            phase = BenchmarkPhase.RUNNING, elapsedOfficialMs = 3_661_000))
        assertEquals("02:58:59", clock.remaining)
    }
    @Test fun preconditioningDoesNotConsumeQuickDuration() {
        val clock = testClock(BenchmarkUiState(mode = BenchmarkMode.QUICK,
            phase = BenchmarkPhase.PRECONDITIONING, elapsedPreconditioningMs = 600_000))
        assertEquals("00:10:00", clock.elapsed)
        assertEquals("預備已進行 · 不計分", clock.elapsedLabel)
        assertEquals("04:00:00", clock.remaining)
    }
    @Test fun allWorkloadsKeepTheSameClock() {
        val state = BenchmarkUiState(mode = BenchmarkMode.QUICK, phase = BenchmarkPhase.RUNNING,
            elapsedOfficialMs = 180_000)
        ACTIVE_WORKLOADS.forEach { workload ->
            assertEquals(testClock(state), testClock(state.copy(currentWorkload = workload, workloadElapsedMs = 0)))
        }
    }
    @Test fun countdownNeverGoesNegative() {
        assertEquals("00:00:00", testClock(BenchmarkUiState(mode = BenchmarkMode.QUICK,
            phase = BenchmarkPhase.RUNNING, elapsedOfficialMs = QUICK_TEST_DURATION_MS + 1000)).remaining)
    }
    @Test fun clockHasFixedHoursAndHandlesLongTests() {
        assertEquals("00:00:00", formatTestClock(-1))
        assertEquals("00:00:01", formatTestClock(1999))
        assertEquals("25:00:00", formatTestClock(90_000_000))
    }
}
