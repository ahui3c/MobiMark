package tw.ahuimark.battery

import android.Manifest
import android.content.pm.ActivityInfo
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import tw.ahuimark.battery.model.BenchmarkPhase
import tw.ahuimark.battery.model.BenchmarkResult
import tw.ahuimark.battery.model.BenchmarkUiState
import tw.ahuimark.battery.model.WorkloadType
import tw.ahuimark.battery.ui.AhuimarkApp
import tw.ahuimark.battery.ui.theme.AhuimarkTheme
import tw.ahuimark.battery.data.ReportExporter

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    private var hasReachedResume = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { viewModel.refreshReadiness() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        applyHighestRefreshRate()

        lifecycle.addObserver(LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    hasReachedResume = true
                    viewModel.retryDndRecovery()
                    viewModel.onAppForegrounded()
                }
                Lifecycle.Event.ON_STOP -> if (hasReachedResume && !isChangingConfigurations) {
                    viewModel.onAppBackgrounded()
                }
                else -> Unit
            }
        })

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState
                    .map(::benchmarkOrientation)
                    .distinctUntilChanged()
                    .collect { orientation -> requestedOrientation = orientation }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState
                    .map { shouldKeepScreenAwake(it.phase) }
                    .distinctUntilChanged()
                    .collect { keepAwake ->
                        if (keepAwake) {
                            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        } else {
                            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        }
                    }
            }
        }

        setContent {
            AhuimarkTheme {
                AhuimarkApp(
                    viewModel = viewModel,
                    onRequestPermissions = ::requestPermissions,
                    onDownloadVideo = viewModel::downloadVideoMaterial,
                    onExportResult = ::exportResult,
                    onRequestDndAccess = ::requestDndAccess
                )
            }
        }
    }

    private fun exportResult(result: BenchmarkResult) {
        lifecycleScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching { ReportExporter(this@MainActivity).export(result) }
            }
            outcome.onSuccess { archive ->
                val uri = FileProvider.getUriForFile(
                    this@MainActivity,
                    "$packageName.fileprovider",
                    archive
                )
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/zip"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(intent, "匯出 MobiMark 成績報告"))
            }.onFailure { error ->
                Toast.makeText(this@MainActivity, "報告匯出失敗：${error.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun requestPermissions() {
        val permissions = buildList {
            add(Manifest.permission.CAMERA)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }

    private fun requestDndAccess() {
        runCatching {
            startActivity(Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
        }.onFailure {
            Toast.makeText(this, "請至系統設定 → 特殊應用程式存取權 → 勿擾模式，允許 MobiMark", Toast.LENGTH_LONG).show()
        }
    }

    @Suppress("DEPRECATION")
    private fun applyHighestRefreshRate() {
        val targetDisplay = windowManager.defaultDisplay
        val current = targetDisplay.mode
        val preferredModeId = selectPreferredDisplayMode(
            modes = targetDisplay.supportedModes.map {
                DisplayModeCandidate(it.modeId, it.physicalWidth, it.physicalHeight, it.refreshRate)
            },
            currentWidth = current.physicalWidth,
            currentHeight = current.physicalHeight
        )
        val preferred = targetDisplay.supportedModes.firstOrNull { it.modeId == preferredModeId } ?: return
        window.attributes = window.attributes.apply {
            this.preferredDisplayModeId = preferred.modeId
            preferredRefreshRate = preferred.refreshRate
        }
    }
}

internal fun shouldKeepScreenAwake(phase: BenchmarkPhase): Boolean =
    phase == BenchmarkPhase.PRECONDITIONING || phase == BenchmarkPhase.RUNNING

internal fun benchmarkOrientation(state: BenchmarkUiState): Int {
    val testing = state.phase == BenchmarkPhase.PRECONDITIONING ||
        state.phase == BenchmarkPhase.RUNNING
    val landscapeWorkload = state.currentWorkload == WorkloadType.SHOOTER ||
        state.currentWorkload == WorkloadType.VIDEO
    return if (testing && landscapeWorkload) {
        ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    } else {
        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }
}

internal data class DisplayModeCandidate(
    val id: Int,
    val width: Int,
    val height: Int,
    val refreshRate: Float
)

internal fun selectPreferredDisplayMode(
    modes: List<DisplayModeCandidate>,
    currentWidth: Int,
    currentHeight: Int
): Int? = modes
    .filter { it.width == currentWidth && it.height == currentHeight }
    .maxByOrNull(DisplayModeCandidate::refreshRate)
    ?.id
