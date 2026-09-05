package tw.ahuimark.battery.workload

internal data class OfficeMoment(val section: Int, val second: Float, val action: String) {
    val typing get() = second in 5f..17f
    val picker get() = second in 25f..29f
    val saving get() = second >= 52f
    val chartFocus get() = when (section) { 0 -> second in 32f..44f; 1 -> second in 30f..50f; else -> second in 34f..52f }
}

internal fun officeMoment(elapsedMs: Long): OfficeMoment {
    val time = elapsedMs.coerceAtLeast(0) % 180_000L
    val section = (time / 60_000L).toInt()
    val second = (time % 60_000L) / 1000f
    val actions = listOf(
        listOf("開啟活動企劃", "輸入企劃摘要", "選取文字・設定標題", "插入成效圖片", "滑動閱讀與校稿", "儲存並重新開啟 DOCX"),
        listOf("開啟活動數據", "輸入 SUM 公式", "向下填滿公式", "插入分析圖表", "捲動檢查計算結果", "重算・儲存 XLSX"),
        listOf("開啟提案簡報", "編輯投影片標題", "調整文字與版面", "插入成效圖片", "切換投影片與預覽", "壓縮並重新開啟驗證")
    )
    val step = when { second < 5 -> 0; second < 18 -> 1; second < 25 -> 2; second < 34 -> 3; second < 52 -> 4; else -> 5 }
    return OfficeMoment(section, second, actions[section][step])
}

internal const val OFFICE_TITLE = "秋季品牌活動企劃"
internal const val OFFICE_SUMMARY = "本季以體驗活動與數位內容拓展品牌觸及，整合通路資源、活動成效及執行時程，持續追蹤每個階段的成果。"

internal fun verifyOfficeFormulaRows(xml: String): Int {
    val rows = Regex("<row r=\"(\\d+)\">(.*?)</row>", RegexOption.DOT_MATCHES_ALL)
    var count = 0
    for (match in rows.findAll(xml)) {
        val row = match.groupValues[1].toInt()
        if (row == 1) continue
        val body = match.groupValues[2]
        fun value(column: String): Double = Regex("<c r=\"$column$row\">(?:<f>.*?</f>)?<v>(.*?)</v></c>")
            .find(body)?.groupValues?.get(1)?.toDoubleOrNull() ?: error("Missing $column$row")
        check(body.contains("<f>SUM(B$row:C$row)</f>")) { "Invalid formula D$row" }
        check(kotlin.math.abs(value("B") + value("C") - value("D")) <= .00011) { "Invalid cache D$row" }
        count++
    }
    check(count == 1500) { "Expected 1500 formulas, got $count" }
    return count
}
