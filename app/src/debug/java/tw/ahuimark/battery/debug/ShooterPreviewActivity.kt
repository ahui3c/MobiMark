package tw.ahuimark.battery.debug

import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.delay
import tw.ahuimark.battery.model.WorkloadType
import tw.ahuimark.battery.ui.theme.AhuimarkTheme
import tw.ahuimark.battery.workload.WorkloadHost

/** Debug-only entry point for visual QA when an emulator fails camera preflight. */
class ShooterPreviewActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        @Suppress("DEPRECATION")
        val panel = windowManager.defaultDisplay
        val current = panel.mode
        panel.supportedModes.filter {
            it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight
        }.maxByOrNull { it.refreshRate }?.let { mode ->
            window.attributes = window.attributes.apply {
                preferredDisplayModeId = mode.modeId
                preferredRefreshRate = mode.refreshRate
            }
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        val offset = intent.getLongExtra("elapsed_ms", 0L)
        val cycle = intent.getBooleanExtra("cycle", false)
        val cycleDuration = intent.getLongExtra("cycle_duration_ms", 180_000L).coerceAtLeast(10_000L)
        setContent {
            AhuimarkTheme {
                val start = remember { SystemClock.elapsedRealtime() }
                var elapsed by remember { mutableLongStateOf(offset) }
                LaunchedEffect(Unit) {
                    while (true) {
                        elapsed = offset + SystemClock.elapsedRealtime() - start
                        delay(100)
                    }
                }
                val phase = if (cycle) (elapsed / cycleDuration).toInt() % 2 else 0
                WorkloadHost(if (phase == 0) WorkloadType.SHOOTER else WorkloadType.WEB, elapsed % cycleDuration)
            }
        }
    }
}
