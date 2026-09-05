package tw.ahuimark.battery

import android.content.pm.ActivityInfo
import org.junit.Assert.assertEquals
import org.junit.Test
import tw.ahuimark.battery.model.BenchmarkPhase
import tw.ahuimark.battery.model.BenchmarkUiState
import tw.ahuimark.battery.model.WorkloadType

class BenchmarkOrientationTest {
    @Test
    fun shooterAndVideoAreLandscapeAndAllOtherScreensArePortrait() {
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
            benchmarkOrientation(BenchmarkUiState(phase = BenchmarkPhase.PRECONDITIONING))
        )
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
            benchmarkOrientation(BenchmarkUiState(phase = BenchmarkPhase.RUNNING))
        )
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
            benchmarkOrientation(
                BenchmarkUiState(
                    phase = BenchmarkPhase.RUNNING,
                    currentWorkload = WorkloadType.WEB
                )
            )
        )
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
            benchmarkOrientation(
                BenchmarkUiState(
                    phase = BenchmarkPhase.RUNNING,
                    currentWorkload = WorkloadType.VIDEO
                )
            )
        )
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
            benchmarkOrientation(BenchmarkUiState(phase = BenchmarkPhase.SETUP))
        )
    }

    @Test
    fun selectsHighestRefreshRateWithoutChangingResolution() {
        val modes = listOf(
            DisplayModeCandidate(1, 1080, 2400, 60f),
            DisplayModeCandidate(2, 1080, 2400, 90f),
            DisplayModeCandidate(3, 1080, 2400, 120f),
            DisplayModeCandidate(4, 1440, 3200, 144f)
        )

        assertEquals(3, selectPreferredDisplayMode(modes, 1080, 2400))
    }
}
