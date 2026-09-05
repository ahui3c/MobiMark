package tw.ahuimark.battery.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val LabInk = Color(0xFF101820)
val LabPanel = Color(0xFF17242D)
val LabPanelRaised = Color(0xFF1E2D37)
val BatteryAmber = Color(0xFFF5B544)
val SignalCyan = Color(0xFF59D4C7)
val PaperMist = Color(0xFFE7EEF1)
val MutedSteel = Color(0xFF91A4AD)
val FaultRed = Color(0xFFFF706A)

private val AhuimarkColors = darkColorScheme(
    primary = BatteryAmber,
    onPrimary = LabInk,
    secondary = SignalCyan,
    onSecondary = LabInk,
    background = LabInk,
    onBackground = PaperMist,
    surface = LabPanel,
    onSurface = PaperMist,
    surfaceVariant = LabPanelRaised,
    onSurfaceVariant = MutedSteel,
    error = FaultRed,
    onError = LabInk
)

@Composable
fun AhuimarkTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = AhuimarkColors, content = content)
}

