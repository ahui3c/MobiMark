package tw.ahuimark.battery.ui

import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Rule
import org.junit.Test
import tw.ahuimark.battery.model.*
import tw.ahuimark.battery.ui.theme.AhuimarkTheme

/** Diagnostic screenshots: real Compose UI with a fixed, explicitly simulated timer. */
class TimerReferenceCaptureTest {
    @get:Rule val compose = createComposeRule()

    @Test fun capture() {
        val args = InstrumentationRegistry.getArguments()
        val quick = args.getString("timerMode") != "full"
        compose.mainClock.autoAdvance = false
        compose.setContent {
            AhuimarkTheme {
                RunningScreen(BenchmarkUiState(
                    mode = if (quick) BenchmarkMode.QUICK else BenchmarkMode.FULL,
                    phase = BenchmarkPhase.RUNNING,
                    currentWorkload = WorkloadType.OFFICE,
                    workloadIndex = 4,
                    elapsedOfficialMs = 5_025_000L,
                    workloadElapsedMs = 45_000L,
                    lastCheckpointAtMs = 1L,
                    battery = BatterySample(1L, 1L, 68, false, 33.2f, 4100, -450000, 3200000)
                ), onStop = {})
            }
        }
        repeat(8) { compose.mainClock.advanceTimeByFrame() }
        compose.onNodeWithText("已測試時間").assertIsDisplayed()
        compose.onNodeWithText("01:23:45").assertIsDisplayed()
        if (quick) compose.onNodeWithText("02:36:15").assertIsDisplayed()
        if (args.getString("compact") == "true") {
            val node = compose.onNodeWithTag("running-header").fetchSemanticsNode()
            val density = InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.density
            check(node.boundsInRoot.height / density <= 72f) { "Landscape header must stay compact" }
        }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        val output = File(instrumentation.targetContext.getExternalFilesDir(null),
            if (quick) "timer-quick.png" else "timer-full.png")
        output.outputStream().use {
            check(instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }
}
