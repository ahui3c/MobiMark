package tw.ahuimark.battery.ui

import java.util.Locale
import tw.ahuimark.battery.model.BenchmarkMode
import tw.ahuimark.battery.model.BenchmarkPhase
import tw.ahuimark.battery.model.BenchmarkUiState
import tw.ahuimark.battery.model.QUICK_TEST_DURATION_MS

internal data class TestClock(val elapsedLabel: String, val elapsed: String,
    val remainingLabel: String?, val remaining: String?)

internal fun testClock(state: BenchmarkUiState): TestClock {
    val preparing = state.phase == BenchmarkPhase.PRECONDITIONING
    val official = if (preparing) 0L else state.elapsedOfficialMs.coerceAtLeast(0L)
    return TestClock(
        if (preparing) "預備已進行 · 不計分" else "已測試時間",
        formatTestClock(if (preparing) state.elapsedPreconditioningMs else official),
        if (state.mode == BenchmarkMode.QUICK) {
            if (preparing) "正式測試剩餘 · 80% 後開始" else "快速測試剩餘"
        } else null,
        if (state.mode == BenchmarkMode.QUICK)
            formatTestClock((QUICK_TEST_DURATION_MS - official).coerceAtLeast(0L)) else null
    )
}

internal fun formatTestClock(ms: Long): String {
    val seconds = ms.coerceAtLeast(0L) / 1000L
    return String.format(Locale.ROOT, "%02d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
}
