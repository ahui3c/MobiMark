package tw.ahuimark.battery.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import tw.ahuimark.battery.model.BatterySample
import tw.ahuimark.battery.model.BenchmarkPhase
import tw.ahuimark.battery.model.BenchmarkUiState
import tw.ahuimark.battery.model.WorkloadType
import tw.ahuimark.battery.ui.theme.AhuimarkTheme

class RunningScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun preconditioningDisplaysTheActiveWorkloadContent() {
        composeRule.mainClock.autoAdvance = false
        val state = BenchmarkUiState(
            phase = BenchmarkPhase.PRECONDITIONING,
            currentWorkload = WorkloadType.OFFICE,
            workloadIndex = WorkloadType.OFFICE.ordinal,
            battery = BatterySample(
                elapsedRealtimeMs = 1_000L,
                wallTimeMs = 1_000L,
                levelPercent = 81,
                isCharging = false,
                temperatureCelsius = 31.5f,
                voltageMv = 4_100,
                currentMicroAmps = -450_000,
                chargeCounterMicroAh = 3_200_000
            )
        )

        composeRule.setContent {
            AhuimarkTheme { RunningScreen(state = state, onStop = {}) }
        }
        composeRule.mainClock.advanceTimeByFrame()

        composeRule.onNodeWithText("文書工作").assertIsDisplayed()
        composeRule.onNodeWithText("季度裝置續航報告").assertIsDisplayed()
        composeRule.onNodeWithText("預備至 80%").assertIsDisplayed()
        composeRule.onNodeWithText("預備已進行 · 不計分").assertIsDisplayed()
    }

    @Test
    fun shooterCreatesItsGlSurfaceAndKeepsTheScreenAlive() {
        val state = BenchmarkUiState(
            phase = BenchmarkPhase.RUNNING,
            currentWorkload = WorkloadType.SHOOTER,
            workloadIndex = WorkloadType.SHOOTER.ordinal,
            battery = BatterySample(
                elapsedRealtimeMs = 1_000L,
                wallTimeMs = 1_000L,
                levelPercent = 80,
                isCharging = false,
                temperatureCelsius = 31.5f,
                voltageMv = 4_100,
                currentMicroAmps = -450_000,
                chargeCounterMicroAh = 3_200_000
            )
        )

        composeRule.setContent {
            AhuimarkTheme { RunningScreen(state = state, onStop = {}) }
        }

        composeRule.onNodeWithText("3D 射擊").assertIsDisplayed()
        composeRule.onNodeWithText("LIVE").assertIsDisplayed()
        composeRule.onNodeWithText("已測試時間").assertIsDisplayed()
        composeRule.onNodeWithText("00:00:00").assertIsDisplayed()
        // Renderer animation is driven by System.nanoTime. Keep the real GL
        // surface alive through rifle, rocket launcher and light-saber phases.
        Thread.sleep(26_000L)
        composeRule.waitForIdle()
    }
}
