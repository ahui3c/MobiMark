package tw.ahuimark.battery.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import tw.ahuimark.battery.R
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import tw.ahuimark.battery.MainViewModel
import tw.ahuimark.battery.model.BenchmarkMode
import tw.ahuimark.battery.model.BenchmarkPhase
import tw.ahuimark.battery.model.BenchmarkResult
import tw.ahuimark.battery.model.BenchmarkUiState
import tw.ahuimark.battery.model.BenchmarkSettings
import tw.ahuimark.battery.model.DeviceReadiness
import tw.ahuimark.battery.model.ACTIVE_WORKLOADS
import tw.ahuimark.battery.model.WorkloadType
import tw.ahuimark.battery.model.VideoSourceMode
import tw.ahuimark.battery.model.WebSourceMode
import tw.ahuimark.battery.core.BenchmarkSchedule
import tw.ahuimark.battery.core.ScoreCalculator
import tw.ahuimark.battery.model.InterruptedSession
import tw.ahuimark.battery.ui.theme.BatteryAmber
import tw.ahuimark.battery.ui.theme.FaultRed
import tw.ahuimark.battery.ui.theme.LabInk
import tw.ahuimark.battery.ui.theme.LabPanel
import tw.ahuimark.battery.ui.theme.LabPanelRaised
import tw.ahuimark.battery.ui.theme.MutedSteel
import tw.ahuimark.battery.ui.theme.PaperMist
import tw.ahuimark.battery.ui.theme.SignalCyan
import tw.ahuimark.battery.workload.WorkloadHost
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

@Composable
fun AhuimarkApp(
    viewModel: MainViewModel,
    onRequestPermissions: () -> Unit,
    onDownloadVideo: () -> Unit,
    onExportResult: (BenchmarkResult) -> Unit,
    onRequestDndAccess: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    var showBrightnessCalibration by rememberSaveable { mutableStateOf(false) }
    if (showBrightnessCalibration && state.phase == BenchmarkPhase.SETUP) {
        BrightnessCalibrationScreen(onClose = { showBrightnessCalibration = false })
        return
    }
    Surface(modifier = Modifier.fillMaxSize(), color = LabInk) {
        when (state.phase) {
            BenchmarkPhase.SETUP -> SetupScreen(
                state = state,
                onSelectMode = viewModel::selectMode,
                onOpenBrightnessCalibration = { showBrightnessCalibration = true },
                onRecoverInterrupted = viewModel::recoverInterruptedBenchmark,
                onDiscardInterrupted = viewModel::discardInterruptedBenchmark,
                onRefresh = viewModel::refreshReadiness,
                onRequestPermissions = onRequestPermissions,
                onRequestDndAccess = onRequestDndAccess,
                onSetAutomaticDnd = viewModel::setAutomaticDnd,
                onRetryDndRecovery = viewModel::retryDndRecovery,
                onDownloadVideo = onDownloadVideo,
                onUpdateWebUrl = viewModel::updateWebUrl,
                onSelectWebSource = viewModel::selectWebSource,
                onSelectVideoSource = viewModel::selectVideoSource,
                onUpdateOnlineVideoUrl = viewModel::updateOnlineVideoUrl,
                onOpenSavedResult = viewModel::openSavedResult,
                onDeleteSavedResult = viewModel::deleteSavedResult,
                onStart = viewModel::startBenchmark
            )
            BenchmarkPhase.PRECONDITIONING, BenchmarkPhase.RUNNING -> RunningScreen(
                state = state,
                onStop = { viewModel.stopBenchmark() }
            )
            BenchmarkPhase.COMPLETED -> ResultScreen(
                result = requireNotNull(state.result),
                dndWarning = listOfNotNull(state.dndError, state.volumeError).joinToString("\n").ifBlank { null },
                onExport = { onExportResult(requireNotNull(state.result)) },
                onReset = viewModel::reset
            )
            BenchmarkPhase.ABORTED -> AbortScreen(
                reason = (state.abortReason ?: "測試未完成") + (state.dndError?.let { "\n$it" } ?: ""),
                onReset = viewModel::reset
            )
        }
    }
}

@Composable
private fun SetupScreen(
    state: BenchmarkUiState,
    onSelectMode: (BenchmarkMode) -> Unit,
    onOpenBrightnessCalibration: () -> Unit,
    onRecoverInterrupted: () -> Unit,
    onDiscardInterrupted: () -> Unit,
    onRefresh: () -> Unit,
    onRequestPermissions: () -> Unit,
    onRequestDndAccess: () -> Unit,
    onSetAutomaticDnd: (Boolean) -> Unit,
    onRetryDndRecovery: () -> Unit,
    onDownloadVideo: () -> Unit,
    onUpdateWebUrl: (Int, String) -> Unit,
    onSelectWebSource: (WebSourceMode) -> Unit,
    onSelectVideoSource: (VideoSourceMode) -> Unit,
    onUpdateOnlineVideoUrl: (String) -> Unit,
    onOpenSavedResult: (BenchmarkResult) -> Unit,
    onDeleteSavedResult: (BenchmarkResult) -> Unit,
    onStart: () -> Unit
) {
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(start = 18.dp, top = 18.dp, end = 18.dp, bottom = 126.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item { BrandHeader(state.battery?.levelPercent) }
            item {
                Text("準備續航測試", color = PaperMist, fontSize = 26.sp,
                    lineHeight = 31.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(5.dp))
                Text("統一亮度與五種工作負載，取得可重現的等效 100% 續航。",
                    color = MutedSteel, fontSize = 13.sp, lineHeight = 19.sp)
                Spacer(Modifier.height(14.dp))
                WorkloadRoute()
            }
            state.interruptedSession?.let { snapshot ->
                item {
                    RecoveryPanel(
                        snapshot = snapshot,
                        error = state.recoveryError,
                        onRecover = onRecoverInterrupted,
                        onDiscard = onDiscardInterrupted
                    )
                }
            }
            item { ModeSelector(state.mode, onSelectMode) }
            item {
                DndSettingsPanel(state, onSetAutomaticDnd, onRequestDndAccess, onRetryDndRecovery)
            }
            item {
                BrightnessCalibrationPanel(
                    automaticBrightnessDisabled = state.readiness.automaticBrightnessDisabled,
                    onOpen = onOpenBrightnessCalibration
                )
            }
            item {
                ContentSettingsPanel(
                    settings = state.settings,
                    onUpdateWebUrl = onUpdateWebUrl,
                    onSelectWebSource = onSelectWebSource,
                    onSelectVideoSource = onSelectVideoSource,
                    onUpdateOnlineVideoUrl = onUpdateOnlineVideoUrl
                )
            }
            item {
                ReadinessPanel(
                    readiness = state.readiness,
                    settings = state.settings,
                    canStart = state.canStart,
                    dndReady = (!state.settings.automaticDnd || state.dndAccessGranted) && !state.dndRecoveryPending,
                    mediaDownloadProgress = state.mediaDownloadProgress,
                    mediaDownloadError = state.mediaDownloadError,
                    onRequestPermissions = onRequestPermissions,
                    onDownloadVideo = onDownloadVideo
                )
            }
            if (state.history.isNotEmpty()) {
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        SectionLabel("歷史成績")
                        Text("${state.history.size} 筆 · 已自動儲存", color = SignalCyan, fontSize = 11.sp)
                    }
                }
                itemsIndexed(state.history, key = { _, result -> result.id }) { _, result ->
                    HistoryRow(
                        result = result,
                        onOpen = { onOpenSavedResult(result) },
                        onDelete = { onDeleteSavedResult(result) }
                    )
                }
            }
        }
        SetupActionDock(
            canStart = state.canStart,
            mode = state.mode,
            onRefresh = onRefresh,
            onStart = onStart,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun DndSettingsPanel(state: BenchmarkUiState, onEnabled: (Boolean) -> Unit,
    onAuthorize: () -> Unit, onRetry: () -> Unit) {
    Surface(color = LabPanel, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("測試期間自動勿擾", color = PaperMist, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Text(if (!state.settings.automaticDnd) "已關閉 · 通知可能影響測試"
                        else if (state.dndAccessGranted) "已授權 · 開始測試時自動啟用" else "需要系統勿擾存取權",
                        color = if (state.settings.automaticDnd && state.dndAccessGranted) SignalCyan else BatteryAmber,
                        fontSize = 12.sp)
                }
                Switch(checked = state.settings.automaticDnd, onCheckedChange = onEnabled)
            }
            Text("抑制通知橫幅、聲音與震動。進入 App 自動將媒體音量降至最低；離開或測試結束時恢復，不改動鈴聲與鬧鐘。",
                color = MutedSteel, fontSize = 12.sp, lineHeight = 18.sp)
            if ((state.settings.automaticDnd || state.dndRecoveryPending) && !state.dndAccessGranted) {
                OutlinedButton(onClick = onAuthorize) { Text("前往系統授權勿擾") }
            }
            state.dndError?.let { Text(it, color = FaultRed, fontSize = 12.sp) }
            state.volumeError?.let { Text(it, color = FaultRed, fontSize = 12.sp) }
            if (state.dndRecoveryPending) {
                OutlinedButton(onClick = onRetry) { Text("重試解除測試勿擾") }
            }
            Text("來電與鬧鐘也會受抑制；緊急警示、權限視窗不保證攔截。閃退後請重新開啟 App 復原。",
                color = MutedSteel, fontSize = 11.sp, lineHeight = 16.sp)
        }
    }
}

@Composable
private fun WorkloadRoute() {
    val labels = listOf("3D", "網頁", "影片", "錄影", "文書")
    Surface(color = LabPanel, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 12.dp)) {
            labels.forEachIndexed { index, label ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(24.dp).background(
                            if (index == 0) BatteryAmber else LabPanelRaised,
                            RoundedCornerShape(8.dp)
                        ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("${index + 1}", color = if (index == 0) LabInk else SignalCyan,
                            fontFamily = FontFamily.Monospace, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(5.dp))
                    Text(label, color = if (index == 0) PaperMist else MutedSteel, fontSize = 9.sp)
                }
            }
        }
    }
}

@Composable
private fun SetupActionDock(
    canStart: Boolean,
    mode: BenchmarkMode,
    onRefresh: () -> Unit,
    onStart: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(modifier.fillMaxWidth(), color = Color(0xF20D171F), tonalElevation = 12.dp) {
        Row(
            Modifier.navigationBarsPadding().padding(horizontal = 18.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = onRefresh,
                modifier = Modifier.height(50.dp),
                contentPadding = PaddingValues(horizontal = 15.dp),
                border = BorderStroke(1.dp, MutedSteel.copy(alpha = .45f))
            ) {
                Icon(Icons.Default.Refresh, "重新檢查", modifier = Modifier.size(19.dp))
            }
            Button(
                onClick = onStart,
                enabled = canStart,
                modifier = Modifier.weight(1f).height(50.dp),
                shape = RoundedCornerShape(15.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = BatteryAmber,
                    contentColor = LabInk,
                    disabledContainerColor = LabPanelRaised,
                    disabledContentColor = MutedSteel
                )
            ) {
                Text(if (canStart) "開始${mode.title}" else "完成檢查後開始", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun BrightnessCalibrationPanel(
    automaticBrightnessDisabled: Boolean,
    onOpen: () -> Unit
) {
    Surface(color = LabPanel, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("200 nits 量測白畫面", color = PaperMist, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Text("亮度由外部設備與 ADB 控制", color = MutedSteel, fontSize = 12.sp)
                }
                OutlinedButton(onClick = onOpen) { Text("顯示全白", fontSize = 12.sp) }
            }
            Text("App 不調整或鎖定亮度，也不判定是否達到 200 nits。以外部儀器量測；輕觸白畫面或按返回即可離開。",
                color = MutedSteel, fontSize = 12.sp, lineHeight = 18.sp)
            if (!automaticBrightnessDisabled) Text("建議關閉系統自動亮度，維持一致測試條件。",
                color = BatteryAmber, fontSize = 12.sp)
        }
    }
}

@Composable
private fun BrightnessCalibrationScreen(onClose: () -> Unit) {
    val view = LocalView.current
    androidx.activity.compose.BackHandler(onBack = onClose)
    DisposableEffect(view) {
        val wasKeepingScreenOn = view.keepScreenOn
        view.keepScreenOn = true
        val window = view.context.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            view.keepScreenOn = wasKeepingScreenOn
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }
    Box(Modifier.fillMaxSize().background(Color.White).clickable(
        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
        indication = null,
        onClick = onClose
    ))
}

@Composable
private fun RecoveryPanel(
    snapshot: InterruptedSession,
    error: String?,
    onRecover: () -> Unit,
    onDiscard: () -> Unit
) {
    val consumed = ScoreCalculator.recoveredConsumedPercent(
        snapshot.startLevel,
        snapshot.lastLevel,
        snapshot.startChargeCounterMicroAh,
        snapshot.lastChargeCounterMicroAh
    )
    val sixtyPercentEstimate = if (snapshot.measuredDurationMs > 0L && consumed > 0.0) {
        ScoreCalculator.equivalentSixtyPercentDurationMs(snapshot.measuredDurationMs, consumed)
    } else null
    Surface(color = Color(0xFF3A2D18), shape = RoundedCornerShape(8.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, null, tint = BatteryAmber, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(9.dp))
                Text("發現未正常結束的測試", color = PaperMist, fontWeight = FontWeight.Bold)
            }
            Text(
                "${snapshot.mode.title} · 已記錄 ${formatDuration(snapshot.measuredDurationMs)} · ${snapshot.startLevel}% → ${snapshot.lastLevel}%",
                color = MutedSteel,
                fontSize = 13.sp
            )
            Text(
                if (sixtyPercentEstimate != null) {
                    "依最後 checkpoint 推算 80% → 20%：${formatDuration(sixtyPercentEstimate)}"
                } else {
                    "目前紀錄尚無足夠耗電變化可供推算"
                },
                color = BatteryAmber,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp
            )
            Text(
                "最後保存：${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM).format(Date(snapshot.lastSavedAtMs))}",
                color = MutedSteel,
                fontSize = 11.sp
            )
            if (error != null) Text(error, color = FaultRed, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onRecover, modifier = Modifier.weight(1.3f)) { Text("用紀錄推算成績") }
                OutlinedButton(onClick = onDiscard, modifier = Modifier.weight(1f)) { Text("捨棄紀錄") }
            }
        }
    }
}

@Composable
private fun ContentSettingsPanel(
    settings: BenchmarkSettings,
    onUpdateWebUrl: (Int, String) -> Unit,
    onSelectWebSource: (WebSourceMode) -> Unit,
    onSelectVideoSource: (VideoSourceMode) -> Unit,
    onUpdateOnlineVideoUrl: (String) -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Surface(color = LabPanel, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                Modifier.fillMaxWidth().clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("測試內容", color = PaperMist, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Text(
                        "網頁：${settings.webSourceMode.title}  ·  影片：${settings.videoSourceMode.title}",
                        color = SignalCyan,
                        fontSize = 11.sp
                    )
                }
                Text(if (expanded) "收合 ︿" else "編輯 ﹀", color = BatteryAmber, fontSize = 12.sp)
            }
            if (expanded) {
                HorizontalDivider(color = LabPanelRaised)
                SettingCaption("網頁來源", "至少填 1 個，最多 3 個；每分鐘輪替，總共 3 分鐘")
                SourceSelector(
                    options = WebSourceMode.entries.map { it.title },
                    selectedIndex = WebSourceMode.entries.indexOf(settings.webSourceMode),
                    onSelect = { onSelectWebSource(WebSourceMode.entries[it]) }
                )
                if (settings.webSourceMode == WebSourceMode.ONLINE) {
                    List(3) { settings.webUrls.getOrElse(it) { "" } }.forEachIndexed { index, url ->
                        OutlinedTextField(
                            value = url,
                            onValueChange = { onUpdateWebUrl(index, it) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            isError = url.isNotBlank() && !tw.ahuimark.battery.model.isHttpUrl(
                                tw.ahuimark.battery.model.normalizeHttpUrl(url)),
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, fontFamily = FontFamily.Monospace),
                            label = { Text("網站 ${index + 1}（可留空）", fontSize = 11.sp) },
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                } else {
                    Text("內建大量文字、54 張圖片與 18 組表格，捲動到底後重新載入。",
                        color = MutedSteel, fontSize = 11.sp, lineHeight = 16.sp)
                }
                Spacer(Modifier.height(2.dp))
                SettingCaption("影片來源", "1080p · 30fps · H.264")
                SourceSelector(
                    options = VideoSourceMode.entries.map { it.title },
                    selectedIndex = VideoSourceMode.entries.indexOf(settings.videoSourceMode),
                    onSelect = { onSelectVideoSource(VideoSourceMode.entries[it]) }
                )
                if (settings.videoSourceMode == VideoSourceMode.ONLINE) {
                    OutlinedTextField(
                        value = settings.onlineVideoUrl,
                        onValueChange = onUpdateOnlineVideoUrl,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        isError = !settings.onlineVideoUrlValid,
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, fontFamily = FontFamily.Monospace),
                        label = { Text("YouTube 或影片網址", fontSize = 11.sp) },
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingCaption(title: String, detail: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = PaperMist, fontWeight = FontWeight.Medium, fontSize = 13.sp)
        Spacer(Modifier.width(8.dp))
        Text(detail, color = MutedSteel, fontSize = 10.sp)
    }
}

@Composable
private fun SourceSelector(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(LabInk.copy(alpha = .62f), RoundedCornerShape(12.dp)).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEachIndexed { index, label ->
            Surface(
                onClick = { onSelect(index) },
                color = if (index == selectedIndex) SignalCyan else Color.Transparent,
                contentColor = if (index == selectedIndex) LabInk else MutedSteel,
                shape = RoundedCornerShape(9.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(Modifier.padding(vertical = 9.dp), contentAlignment = Alignment.Center) {
                    Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun BrandHeader(level: Int?) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Image(painterResource(R.drawable.ic_launcher), contentDescription = "MobiMark",
            modifier = Modifier.size(42.dp))
        Spacer(Modifier.width(10.dp))
        Column {
            Text("MobiMark", fontFamily = FontFamily.Monospace, letterSpacing = 1.sp,
                color = PaperMist, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Text(tw.ahuimark.battery.model.WORKLOAD_VERSION.substringBefore(" · ").removePrefix("Ahuimark ").uppercase() + " · ANDROID", fontFamily = FontFamily.Monospace,
                color = SignalCyan, fontSize = 8.sp, letterSpacing = .8.sp)
        }
        Spacer(Modifier.weight(1f))
        Surface(color = LabPanel, shape = RoundedCornerShape(12.dp)) {
            Text(
                "${level ?: "--"}%",
                color = if ((level ?: 0) > 80) SignalCyan else BatteryAmber,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp)
            )
        }
    }
}

@Composable
private fun ModeSelector(selected: BenchmarkMode, onSelect: (BenchmarkMode) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        SectionLabel("測試模式")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BenchmarkMode.entries.forEach { mode ->
                val active = mode == selected
                Surface(
                    onClick = { onSelect(mode) },
                    color = if (active) LabPanelRaised else LabPanel,
                    shape = RoundedCornerShape(16.dp),
                    border = if (active) BorderStroke(1.dp, BatteryAmber.copy(alpha = .72f)) else null,
                    modifier = Modifier.weight(1f)
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StatusMark(active, activeColor = BatteryAmber)
                            Spacer(Modifier.width(8.dp))
                            Text(mode.title, color = PaperMist, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        Spacer(Modifier.height(7.dp))
                        Text(
                            if (mode == BenchmarkMode.FULL) "80% → 20%\n完整循環" else "固定 4 小時\n斜率換算",
                            color = if (active) PaperMist.copy(alpha = .82f) else MutedSteel,
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusMark(ok: Boolean, activeColor: Color = SignalCyan) {
    Box(
        Modifier.size(18.dp).background(
            if (ok) activeColor.copy(alpha = .18f) else MutedSteel.copy(alpha = .12f),
            RoundedCornerShape(6.dp)
        ),
        contentAlignment = Alignment.Center
    ) {
        if (ok) Icon(Icons.Default.Check, null, tint = activeColor, modifier = Modifier.size(13.dp))
        else Box(Modifier.size(5.dp).background(MutedSteel, RoundedCornerShape(2.dp)))
    }
}

@Composable
private fun ReadinessPanel(
    readiness: DeviceReadiness,
    settings: BenchmarkSettings,
    canStart: Boolean,
    dndReady: Boolean,
    mediaDownloadProgress: Float?,
    mediaDownloadError: String?,
    onRequestPermissions: () -> Unit,
    onDownloadVideo: () -> Unit
) {
    var showAll by rememberSaveable { mutableStateOf(false) }
    val videoReady = settings.videoSourceMode == VideoSourceMode.ONLINE || readiness.mediaAssetReady
    val checks = listOf(
        Triple("測試勿擾防護", dndReady, "請完成上方勿擾授權，或解除殘留測試規則"),
        Triple("電量高於 80%", readiness.batteryAbove80, "目前必須為 81% 以上"),
        Triple("未連接充電器", readiness.unplugged, "拔除 USB、充電線或無線充電"),
        Triple("相機權限", readiness.cameraPermission, "需要前後鏡頭執行工作負載"),
        Triple("後鏡頭 4K30", readiness.rearUhd30Supported, "需支援 UHD 3840×2160、30fps 錄影"),
        Triple(
            if (settings.webSourceMode == WebSourceMode.ONLINE) "線上網頁網址（至少 1 個）" else "離線模擬網頁",
            settings.webSourceMode == WebSourceMode.OFFLINE || settings.webUrlsValid,
            "至少填入 1 個有效的 http/https 網址，其餘可留空；已填欄位都必須有效"
        ),
        Triple(if (settings.videoSourceMode == VideoSourceMode.ONLINE) "線上影片網址" else "1080p 本地測試影片", videoReady,
            mediaDownloadError ?: if (mediaDownloadProgress != null) "下載中 ${(mediaDownloadProgress * 100).roundToInt()}%" else "從指定 Dropbox 下載並校驗"),
        Triple("可用空間 3 GB", readiness.storageReady, "目前 ${formatBytes(readiness.availableStorageBytes)}")
    )
    val visibleChecks = if (showAll) checks else checks.filterNot { it.second }.ifEmpty { checks.take(0) }
    Surface(color = LabPanel, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(15.dp)) {
            Row(
                Modifier.fillMaxWidth().clickable { showAll = !showAll },
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusMark(canStart)
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text("開始前檢查", color = PaperMist, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Text(
                        if (canStart) "所有條件已就緒" else "尚有 ${checks.count { !it.second }} 項需要處理",
                        color = if (canStart) SignalCyan else BatteryAmber,
                        fontSize = 11.sp
                    )
                }
                Text(if (showAll) "收合 ︿" else "${checks.count { it.second }}/${checks.size}  ﹀",
                    color = MutedSteel, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            }
            if (visibleChecks.isNotEmpty()) {
                Spacer(Modifier.height(11.dp))
                HorizontalDivider(color = LabPanelRaised)
                visibleChecks.forEachIndexed { index, item ->
                    Row(Modifier.padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (item.second) Icons.Default.Check else Icons.Default.Close, null,
                            tint = if (item.second) SignalCyan else FaultRed, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.first, color = PaperMist, fontSize = 12.sp)
                            if (!item.second) Text(item.third, color = MutedSteel, fontSize = 10.sp, maxLines = 2)
                        }
                        if (item.first == "相機權限" && !item.second) {
                            OutlinedButton(onClick = onRequestPermissions,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)) {
                                Text("授權", fontSize = 11.sp)
                            }
                        } else if (item.first == "1080p 本地測試影片" && !item.second) {
                            OutlinedButton(onClick = onDownloadVideo, enabled = mediaDownloadProgress == null,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)) {
                                Text(if (mediaDownloadProgress == null) "下載" else "${(mediaDownloadProgress * 100).roundToInt()}%", fontSize = 11.sp)
                            }
                        }
                    }
                    if (index != visibleChecks.lastIndex) HorizontalDivider(color = LabPanelRaised.copy(alpha = .7f))
                }
            }
        }
    }
}

@Composable
internal fun RunningScreen(state: BenchmarkUiState, onStop: () -> Unit) {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = view.context.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    val batteryLevel = state.battery?.levelPercent ?: 80
    val workloadDurationMs = BenchmarkSchedule.durationFor(state.currentWorkload)
    val remainingMs = (workloadDurationMs - state.workloadElapsedMs).coerceAtLeast(0L)
    val chromeAlpha = runningChromeAlpha(state.currentWorkload)
    val clock = testClock(state)
    val density = LocalDensity.current
    var headerHeight by remember { mutableStateOf(112.dp) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val landscape = maxWidth > maxHeight
        WorkloadHost(state.currentWorkload, state.workloadElapsedMs, settings = state.settings,
            modifier = if (state.currentWorkload == WorkloadType.OFFICE) Modifier.padding(top = headerHeight, bottom = 76.dp) else Modifier)

        Column(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .testTag("running-header")
                .onSizeChanged { headerHeight = with(density) { it.height.toDp() } }
                .background(Color.Black.copy(alpha = chromeAlpha))
                .padding(horizontal = 14.dp, vertical = if (landscape) 6.dp else 9.dp)
        ) {
            if (landscape) {
                CompactRunningHeader(state, clock, remainingMs)
            } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (state.phase == BenchmarkPhase.PRECONDITIONING) {
                    Text(
                        "預備至 80%",
                        color = LabInk,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        modifier = Modifier.background(SignalCyan, RoundedCornerShape(3.dp))
                            .padding(horizontal = 7.dp, vertical = 4.dp)
                    )
                } else {
                    Text("LIVE", color = LabInk, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                        fontSize = 10.sp, modifier = Modifier.background(BatteryAmber, RoundedCornerShape(3.dp))
                            .padding(horizontal = 7.dp, vertical = 4.dp))
                }
                Spacer(Modifier.width(10.dp))
                Text(state.currentWorkload.title, color = PaperMist, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Spacer(Modifier.weight(1f))
                Text("$batteryLevel%", color = BatteryAmber, fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TestClockDisplay(clock.elapsedLabel, clock.elapsed, BatteryAmber, Modifier.weight(1f))
                clock.remaining?.let {
                    TestClockDisplay(clock.remainingLabel.orEmpty(), it, SignalCyan, Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("本項剩餘 ${formatDuration(remainingMs)}", color = PaperMist,
                    fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                Text("${state.battery?.temperatureCelsius ?: 0f}°C", color = PaperMist,
                    fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            }
            }
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = chromeAlpha))
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            WorkloadStrip(state.workloadIndex)
            Spacer(Modifier.height(5.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${state.workloadIndex + 1}/${ACTIVE_WORKLOADS.size} · 本項 ${formatDuration(workloadDurationMs)}", color = PaperMist,
                    fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                Spacer(Modifier.width(10.dp))
                Text(
                    if (state.phase == BenchmarkPhase.PRECONDITIONING) {
                        "80% 後建立備援"
                    } else {
                        state.checkpointError ?: if (state.lastCheckpointAtMs != null) "備援已保存" else "建立備援中"
                    },
                    color = if (state.checkpointError == null) SignalCyan else FaultRed,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp
                )
                Spacer(Modifier.weight(1f))
                OutlinedButton(onClick = onStop, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)) {
                    Text("停止", fontSize = 11.sp)
                }
            }
        }
    }
}

internal fun runningChromeAlpha(workload: WorkloadType): Float =
    if (workload == WorkloadType.SHOOTER) .20f else .48f

@Composable
private fun TestClockDisplay(label: String, value: String, color: Color, modifier: Modifier, compact: Boolean = false) {
    BoxWithConstraints(modifier) {
        // Keep HH:MM:SS on one line even on narrow phones with enlarged system text.
        val size = (maxWidth.value / 5.2f / LocalDensity.current.fontScale).coerceAtMost(if (compact) 26f else 30f).sp
        val shadow = Shadow(Color.Black, Offset(0f, 2f), 5f)
        Column {
            Text(label, color = PaperMist, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                style = TextStyle(shadow = shadow))
            Text(value, color = color, fontSize = size, fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold, maxLines = 1, style = TextStyle(shadow = shadow))
        }
    }
}

@Composable
private fun CompactRunningHeader(state: BenchmarkUiState, clock: TestClock, remainingMs: Long) {
    val preparing = state.phase == BenchmarkPhase.PRECONDITIONING
    val shadow = TextStyle(shadow = Shadow(Color.Black, Offset(0f, 2f), 5f))
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(Modifier.weight(1.3f)) {
            Text("${if (preparing) "預備" else "LIVE"} · ${state.currentWorkload.title}",
                color = PaperMist, fontWeight = FontWeight.Bold, fontSize = 15.sp, style = shadow)
            Text("本項剩餘 ${formatDuration(remainingMs)}", color = PaperMist,
                fontFamily = FontFamily.Monospace, fontSize = 11.sp, style = shadow)
        }
        TestClockDisplay(if (preparing) "預備時間 · 不計分" else clock.elapsedLabel,
            clock.elapsed, BatteryAmber, Modifier.weight(1f), compact = true)
        clock.remaining?.let {
            TestClockDisplay(if (preparing) "正式剩餘 · 80% 起" else clock.remainingLabel.orEmpty(),
                it, SignalCyan, Modifier.weight(1f), compact = true)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("${state.battery?.levelPercent ?: 80}%", color = BatteryAmber,
                fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 18.sp, style = shadow)
            Text("${state.battery?.temperatureCelsius ?: 0f}°C", color = PaperMist,
                fontFamily = FontFamily.Monospace, fontSize = 11.sp, style = shadow)
        }
    }
}

@Composable
private fun WorkloadStrip(activeIndex: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        ACTIVE_WORKLOADS.forEachIndexed { index, _ ->
            Box(Modifier.weight(1f).height(5.dp).background(
                when {
                    index == activeIndex -> BatteryAmber
                    index < activeIndex -> SignalCyan
                    else -> LabPanelRaised
                }, RoundedCornerShape(2.dp)
            ))
        }
    }
}

@Composable
private fun ResultScreen(result: BenchmarkResult, dndWarning: String?, onExport: () -> Unit, onReset: () -> Unit) {
    val sixtyPercentDuration = ScoreCalculator.equivalentSixtyPercentDurationMs(
        result.measuredDurationMs,
        result.consumedPercent
    )
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp, 56.dp, 24.dp, 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item { BrandHeader(result.endLevel) }
        dndWarning?.let { warning ->
            item { Text("系統設定復原提醒：$warning。請確認系統設定。", color = FaultRed, fontSize = 14.sp) }
        }
        item {
            Text("等效 100% 續航", color = MutedSteel, fontFamily = FontFamily.Monospace, fontSize = 12.sp, letterSpacing = 1.sp)
            Text(formatDuration(result.equivalentFullDurationMs), color = BatteryAmber,
                fontFamily = FontFamily.Monospace, fontSize = 45.sp, fontWeight = FontWeight.Bold)
            Text(result.confidence.label, color = SignalCyan, fontWeight = FontWeight.Bold)
            if (result.recoveredFromCheckpoint) {
                Text("由當機前定時紀錄推算", color = BatteryAmber, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
        item {
            Surface(color = LabPanel, shape = RoundedCornerShape(8.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                    ResultLine("模式", result.mode.title)
                    ResultLine("實際測試", formatDuration(result.measuredDurationMs))
                    ResultLine("實際電量", "${result.startLevel}% → ${result.endLevel}%")
                    ResultLine("耗電", "${result.consumedPercent.roundToInt()} 個百分點")
                    ResultLine("推算 80% → 20%", formatDuration(sixtyPercentDuration))
                    ResultLine("完成循環", "${result.completedLoops} 次")
                    ResultLine("最高電池溫度", "${result.maxTemperatureCelsius}°C")
                    ResultLine("工作負載", result.workloadVersion)
                    ResultLine("通知防護", result.interruptionProtection)
                }
            }
        }
        if (result.workloadStats.isNotEmpty()) {
            item { SectionLabel("分項耗電成績") }
            itemsIndexed(result.workloadStats) { _, stat ->
                Surface(color = LabPanel, shape = RoundedCornerShape(7.dp)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stat.workload.title, color = PaperMist, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.weight(1f))
                            Text("%.2f mAh".format(stat.consumedMilliAmpHours), color = BatteryAmber, fontFamily = FontFamily.Monospace)
                        }
                        Text(
                            "耗電 %.3f%% · 平均 %.0f mA · 最高 %.1f°C · ${formatDuration(stat.durationMs)}".format(
                                stat.estimatedConsumedPercent,
                                stat.averageCurrentMilliAmps,
                                stat.maxTemperatureCelsius
                            ),
                            color = MutedSteel,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onReset, modifier = Modifier.weight(1f)) { Text("返回首頁") }
                Button(onClick = onExport, modifier = Modifier.weight(1.3f)) { Text("匯出完整報告") }
            }
        }
    }
}

@Composable
private fun AbortScreen(reason: String, onReset: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Icon(Icons.Default.Warning, null, tint = FaultRed, modifier = Modifier.size(36.dp))
        Spacer(Modifier.height(16.dp))
        Text("本次測試已作廢", color = PaperMist, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(reason, color = FaultRed, fontSize = 16.sp)
        Spacer(Modifier.height(26.dp))
        Button(onClick = onReset, modifier = Modifier.fillMaxWidth()) { Text("重新檢查環境") }
    }
}

@Composable private fun SectionLabel(text: String) = Text(text.uppercase(), color = MutedSteel,
    fontFamily = FontFamily.Monospace, fontSize = 11.sp, letterSpacing = 1.sp)

@Composable private fun ResultLine(label: String, value: String) = Row(Modifier.fillMaxWidth()) {
    Text(label, color = MutedSteel, modifier = Modifier.weight(1f))
    Text(value, color = PaperMist, fontFamily = FontFamily.Monospace)
}

@Composable private fun HistoryRow(
    result: BenchmarkResult,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    var confirmDelete by rememberSaveable(result.id) { mutableStateOf(false) }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("刪除此筆成績？") },
            text = { Text("成績與對應的原始遙測紀錄將一併刪除，且無法復原。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text("刪除", color = FaultRed) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("取消") }
            }
        )
    }
    Row(Modifier.fillMaxWidth().background(LabPanel, RoundedCornerShape(12.dp)).clickable(onClick = onOpen).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                result.mode.title + if (result.recoveredFromCheckpoint) " · 備援推算" else "",
                color = PaperMist,
                fontWeight = FontWeight.Bold
            )
            Text(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(result.endedAtMs)), color = MutedSteel, fontSize = 11.sp)
        }
        Text(formatDuration(result.equivalentFullDurationMs), color = BatteryAmber,
            fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(4.dp))
        IconButton(onClick = { confirmDelete = true }) {
            Icon(Icons.Default.Delete, contentDescription = "刪除成績", tint = MutedSteel)
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalMinutes = ms / 60_000L
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    val seconds = (ms / 1_000L) % 60L
    return if (hours > 0) "%02d:%02d:%02d".format(hours, minutes, seconds)
    else "%02d:%02d".format(minutes, seconds)
}

private fun formatBytes(bytes: Long): String = "%.1f GB".format(bytes / 1024.0 / 1024.0 / 1024.0)

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
