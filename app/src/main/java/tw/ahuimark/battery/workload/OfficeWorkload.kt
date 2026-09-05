package tw.ahuimark.battery.workload

import android.graphics.BitmapFactory
import android.os.SystemClock
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.util.Locale

private val OfficeInk = Color(0xFF23334D)
private val OfficeMuted = Color(0xFF6E7C91)
private val OfficeDesk = Color(0xFFF2F5F9)
private val OfficeLine = Color(0xFFDDE4EE)
private val OfficeAccents = listOf(Color(0xFF3869D8), Color(0xFF23805E), Color(0xFFCB674F))

/** Native, deterministic editor simulation. Does not launch or impersonate an
 * installed office suite. File work is serial and independent of display FPS. */
@Composable
internal fun OfficeWorkload(elapsedMs: Long) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val hostTime by rememberUpdatedState(elapsedMs)
    val anchor by rememberUpdatedState(elapsedMs to SystemClock.elapsedRealtime())
    var visualMs by remember { mutableLongStateOf(elapsedMs) }
    var result by remember { mutableStateOf<OfficeDocumentGenerator.Result?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }
    var writing by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        while (isActive) withFrameNanos {
            visualMs = anchor.first + (SystemClock.elapsedRealtime() - anchor.second).coerceIn(0, 500)
        }
    }
    LaunchedEffect(Unit) {
        snapshotFlow { hostTime / 15_000L }.collect { batch ->
            writing = true
            failure = null
            if (batch < (result?.batch ?: -1)) result = null
            try {
                result = withContext(Dispatchers.IO) { OfficeDocumentGenerator.generate(context, batch) }
                android.util.Log.i("MobiMarkOffice", "batch=$batch formulas=${result?.formulaCount} archive=${result?.archiveBytes} verified=true")
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (error: Exception) {
                failure = error.message ?: "無法寫入 Office 檔案"
                android.util.Log.e("MobiMarkOffice", "Office workload failed", error)
            } finally { writing = false }
        }
    }
    val moment = officeMoment(visualMs)
    val accent = OfficeAccents[moment.section]
    val deviceDensity = LocalDensity.current
    // Exactly 2x the previous sp scale, including utility labels and keyboard.
    CompositionLocalProvider(LocalDensity provides Density(deviceDensity.density, deviceDensity.fontScale * 2f)) {
    Column(Modifier.fillMaxSize().background(OfficeDesk)) {
        Row(Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 16.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("‹", color = OfficeInk, fontSize = 30.sp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(listOf("活動企劃.docx", "活動數據.xlsx", "活動提案.pptx")[moment.section], color = OfficeInk, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text("裝置上的檔案  /  自動操作模擬", color = OfficeMuted, fontSize = 10.sp)
            }
            Text("↶   ⋮", color = OfficeMuted, fontSize = 22.sp)
        }
        if (!moment.typing) Row(Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            listOf("文件", "試算表", "簡報").forEachIndexed { index, title ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(title, color = if (moment.section == index) accent else OfficeMuted, fontSize = 12.sp,
                        modifier = Modifier.padding(vertical = 9.dp))
                    Box(Modifier.width(42.dp).height(2.dp).background(if (moment.section == index) accent else Color.Transparent))
                }
            }
        }
        Row(Modifier.fillMaxWidth().background(accent.copy(alpha = .08f)).padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).background(accent, RoundedCornerShape(3.dp)))
            Spacer(Modifier.width(8.dp))
            Text(if (moment.chartFocus) "展開圖表・檢視成效" else moment.action, color = accent, fontSize = 11.sp, modifier = Modifier.weight(1f))
            Text("${moment.section + 1}/3", color = accent, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        }
        Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(0.dp))) {
            if (moment.second < 4) RecentFiles(moment, accent)
            else if (moment.chartFocus) AnimatedOfficeChart(moment, result, accent)
            else when (moment.section) {
                0 -> DocumentEditor(moment, result)
                1 -> SheetEditor(moment, result)
                else -> SlideEditor(moment, result)
            }
            if (moment.picker) PicturePicker(result, accent)
            if (moment.second >= 56) {
                Box(Modifier.align(Alignment.BottomCenter).padding(14.dp).background(Color.White, RoundedCornerShape(12.dp)).border(1.dp, OfficeLine, RoundedCornerShape(12.dp)).padding(14.dp)) {
                    Text(when { failure != null -> "檔案處理失敗：$failure"; writing -> "正在寫入與驗證檔案…"; result != null -> "✓  已儲存並重新開啟\nDOCX · XLSX · PPTX · ZIP"; else -> "等待檔案產生" }, color = if (failure != null) Color(0xFFB42318) else accent, fontSize = 12.sp)
                }
            }
            TouchGesture(moment, accent)
        }
        EditorToolbar(moment, accent)
        if (moment.typing) SimulatedKeyboard(moment, accent)
        Row(Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 14.dp, vertical = 7.dp)) {
            Text(when { failure != null -> "處理失敗：$failure"; writing -> "正在建立檔案與重算…"; result != null -> "✓ 第 ${result!!.batch + 1} 批已驗證 · ${result!!.formulaCount} 組公式"; else -> "準備文件中" },
                color = if (failure != null) Color(0xFFB42318) else OfficeMuted, fontSize = 9.sp, maxLines = 2)
        }
    }
    }
}

@Composable
private fun RecentFiles(moment: OfficeMoment, accent: Color) {
    Column(Modifier.padding(22.dp)) {
        Text("最近使用", color = OfficeInk, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text("品牌活動  /  工作資料夾", color = OfficeMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp, bottom = 22.dp))
        listOf("活動企劃.docx", "活動數據.xlsx", "活動提案.pptx").forEachIndexed { index, title ->
            Row(Modifier.fillMaxWidth().padding(bottom = 12.dp).background(if (index == moment.section) accent.copy(alpha = .09f) else Color.White, RoundedCornerShape(10.dp)).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(listOf("W", "X", "P")[index], color = OfficeAccents[index], fontSize = 23.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(18.dp))
                Column { Text(title, color = OfficeInk, fontSize = 14.sp); Text("儲存在本機 · 今天", color = OfficeMuted, fontSize = 10.sp) }
            }
        }
    }
}

@Composable
private fun DocumentEditor(moment: OfficeMoment, result: OfficeDocumentGenerator.Result?) {
    val scroll = rememberScrollState()
    val density = LocalDensity.current.density
    LaunchedEffect(moment.second) {
        val fraction = ((moment.second - 44f) / 8f).coerceIn(0f, 1f)
        val target = when { moment.typing -> (180 * density).toInt(); moment.second in 29f..32f -> (270 * density).toInt(); else -> (scroll.maxValue * fraction).toInt() }
        scroll.scrollTo(target.coerceAtMost(scroll.maxValue))
    }
    Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(12.dp)) {
        Column(Modifier.fillMaxWidth().background(Color.White).padding(22.dp)) {
            Text("MOBIMARK  /  BRAND STUDIO", color = OfficeAccents[0], fontSize = 9.sp, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.height(18.dp))
            Text(OFFICE_TITLE, color = OfficeInk, fontSize = 25.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif,
                modifier = Modifier.background(if (moment.second in 18f..24f) OfficeAccents[0].copy(alpha = .16f) else Color.Transparent))
            Text("企劃摘要   ·   工作草稿", color = OfficeMuted, fontSize = 11.sp, modifier = Modifier.padding(vertical = 12.dp))
            val count = if (moment.second < 18f) ((moment.second - 5f).coerceAtLeast(0f) / 12f * OFFICE_SUMMARY.length).toInt().coerceAtMost(OFFICE_SUMMARY.length) else OFFICE_SUMMARY.length
            Text(OFFICE_SUMMARY.take(count) + if (moment.typing && (moment.second * 2).toInt() % 2 == 0) "│" else "", color = OfficeInk, fontSize = 14.sp, lineHeight = 25.sp)
            if (moment.second >= 29) {
                Spacer(Modifier.height(16.dp))
                ReportImage(result, Modifier.fillMaxWidth().aspectRatio(16f / 9f).border(if (moment.second < 34) 2.dp else 0.dp, OfficeAccents[0]))
                Text("圖 1  活動成效分析", color = OfficeMuted, fontSize = 10.sp, modifier = Modifier.padding(top = 5.dp))
            }
            repeat(6) { index ->
                Text(listOf("活動目標", "執行策略", "通路規劃", "成效追蹤", "團隊分工", "下一步")[index], color = OfficeInk, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 24.dp, bottom = 8.dp))
                Text(OFFICE_SUMMARY, color = OfficeInk, fontSize = 13.sp, lineHeight = 24.sp)
            }
            Text("1 / 3", color = OfficeMuted, fontSize = 10.sp, modifier = Modifier.align(Alignment.End).padding(top = 24.dp))
        }
    }
}

@Composable
private fun SheetEditor(moment: OfficeMoment, result: OfficeDocumentGenerator.Result?) {
    val accent = OfficeAccents[1]
    val rowStart = 0
    val sheetScroll = rememberScrollState()
    LaunchedEffect(moment.second) {
        sheetScroll.scrollTo((sheetScroll.maxValue * ((moment.second - 34f) / 17f).coerceIn(0f, 1f)).toInt())
    }
    val activeRow = if (moment.second in 18f..25f) ((moment.second - 18) * 1.7f).toInt().coerceIn(0, 11) else 0
    Column(Modifier.fillMaxSize().background(Color.White)) {
        Row(Modifier.fillMaxWidth().background(Color(0xFFF6F9F7)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("D${rowStart + activeRow + 2}", color = accent, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
            Text("   │   ƒx  ", color = OfficeMuted, fontSize = 14.sp)
            val formula = "=SUM(B2:C2)"
            Text(if (moment.typing) formula.take(((moment.second - 5) * 1.5f).toInt().coerceIn(0, formula.length)) + "│" else "=SUM(B${rowStart + activeRow + 2}:C${rowStart + activeRow + 2})", color = OfficeInk, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
        }
        Row { SheetCell("", .45f, OfficeDesk); listOf("A", "B", "C", "D").forEach { SheetCell(it, 1f, OfficeDesk) } }
        Row { SheetCell("1", .45f, OfficeDesk); listOf("樣本", "數據 A", "數據 B", "合計").forEach { SheetCell(it, 1f, accent.copy(alpha = .08f)) } }
        Column(Modifier.weight(1f).verticalScroll(sheetScroll)) {
            repeat(20) { index ->
                val row = rowStart + index
                val values = result?.rows?.getOrNull(row)
                Row {
                    SheetCell("${row + 2}", .45f, OfficeDesk)
                    SheetCell("${row + 1}", 1f)
                    repeat(3) { column ->
                        val selected = column == 2 && index == activeRow
                        SheetCell(values?.get(column)?.let { "%.2f".format(Locale.US, it) } ?: "—", 1f,
                            if (selected) accent.copy(alpha = .10f) else Color.White, selected)
                    }
                }
                if (index == 8 && moment.second >= 29) {
                    Text("成效趨勢", color = accent, fontSize = 13.sp, modifier = Modifier.padding(12.dp))
                    ReportImage(result, Modifier.fillMaxWidth().padding(horizontal = 16.dp).aspectRatio(16f / 9f))
                }
            }
        }
        Text("＋   活動數據    │    1,500 列・SUM 公式", color = accent, fontSize = 11.sp, modifier = Modifier.padding(12.dp))
    }
}

@Composable
private fun RowScope.SheetCell(text: String, weight: Float, background: Color = Color.White, selected: Boolean = false) {
    Box(Modifier.weight(weight).height(43.dp).background(background).border(if (selected) 2.dp else .5.dp, if (selected) OfficeAccents[1] else OfficeLine), contentAlignment = Alignment.Center) {
        Text(text, color = OfficeInk, fontSize = 11.sp, fontFamily = FontFamily.Monospace, maxLines = 1)
        if (selected) Box(Modifier.align(Alignment.BottomEnd).size(5.dp).background(OfficeAccents[1]))
    }
}

@Composable
private fun SlideEditor(moment: OfficeMoment, result: OfficeDocumentGenerator.Result?) {
    val slide = if (moment.second >= 34 && moment.second < 52) ((moment.second - 34) / 3).toInt().coerceIn(0, 5) else 0
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp)) {
        Text("版面配置  /  標題與內容", color = OfficeMuted, fontSize = 11.sp, modifier = Modifier.padding(bottom = 18.dp))
        Column(Modifier.fillMaxWidth().heightIn(min = 260.dp).background(Color.White, RoundedCornerShape(3.dp)).padding(18.dp)) {
            Text("BRAND STUDIO   /   ${slide + 1}", color = OfficeAccents[2], fontSize = 9.sp, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.height(12.dp))
            val title = listOf(OFFICE_TITLE, "活動目標", "成效趨勢", "內容策略", "執行時程", "下一步行動")[slide]
            Text(if (moment.typing) title.take(((moment.second - 5) * .9f).toInt().coerceIn(0, title.length)) + "│" else title,
                color = OfficeInk, fontSize = 21.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth().border(if (moment.second in 18f..24f) 1.dp else 0.dp, OfficeAccents[2]).padding(3.dp))
            Spacer(Modifier.height(8.dp))
            if (moment.second >= 29) ReportImage(result, Modifier.fillMaxWidth().aspectRatio(16f / 9f))
            else Text("整合活動數據，讓下一步更清晰。", color = OfficeMuted, fontSize = 11.sp)
        }
        Text("${slide + 1} / 6   ·   輕觸投影片以編輯", color = OfficeMuted, fontSize = 10.sp, modifier = Modifier.padding(vertical = 14.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(6) { index ->
                Column(Modifier.width(112.dp).height(80.dp).background(Color.White).border(if (index == slide) 2.dp else 1.dp, if (index == slide) OfficeAccents[2] else OfficeLine).padding(8.dp)) {
                    Text("${index + 1}  品牌企劃", color = OfficeInk, fontSize = 7.sp)
                    Spacer(Modifier.height(7.dp))
                    Box(Modifier.fillMaxWidth(.7f).height(3.dp).background(OfficeAccents[2]))
                    Spacer(Modifier.height(4.dp))
                    Box(Modifier.fillMaxWidth().height(2.dp).background(OfficeLine))
                }
            }
        }
        Text("備忘稿", color = OfficeMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 24.dp, bottom = 8.dp))
        Text(OFFICE_SUMMARY, color = OfficeMuted, fontSize = 12.sp, lineHeight = 21.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun AnimatedOfficeChart(moment: OfficeMoment, result: OfficeDocumentGenerator.Result?, accent: Color) {
    val values = remember(result?.batch) { List(6) { index -> result?.rows?.getOrNull((index * 299).coerceAtMost(1499))?.get(2)?.toFloat() ?: 0f } }
    val mode = (moment.second.toInt() / 6) % 3
    val progress = ((moment.second % 6f) / 1.8f).coerceIn(0f, 1f)
    val ease = 1f - (1f - progress) * (1f - progress) * (1f - progress)
    val colors = listOf(accent, Color(0xFFDC9450), Color(0xFF627DD0), Color(0xFF59A99D), Color(0xFFBE719C), Color(0xFF809B55))
    Column(Modifier.fillMaxSize().background(Color.White).padding(16.dp)) {
        Text("活動成效分析", color = OfficeInk, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("柱狀圖", "折線圖", "環圈圖").forEachIndexed { index, name ->
                Text(name, color = if (mode == index) accent else OfficeMuted, fontSize = 11.sp,
                    modifier = Modifier.background(if (mode == index) accent.copy(alpha = .10f) else Color.Transparent, RoundedCornerShape(5.dp)).padding(6.dp))
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Canvas(Modifier.fillMaxSize()) {
                if (values.all { it == 0f }) return@Canvas
                val max = values.max().coerceAtLeast(1f)
                val bottom = size.height - 16.dp.toPx()
                val top = 12.dp.toPx()
                val height = (bottom - top).coerceAtLeast(1f)
                if (mode != 2) repeat(5) { line ->
                    val y = top + height * line / 4f
                    drawLine(OfficeLine, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                }
                when (mode) {
                    0 -> values.forEachIndexed { index, value ->
                        val width = size.width / 6
                        val barHeight = height * value / max * .92f * ease
                        drawRoundRect(colors[index], Offset(width * index + width * .16f, bottom - barHeight),
                            androidx.compose.ui.geometry.Size(width * .68f, barHeight.coerceAtLeast(.1f)), androidx.compose.ui.geometry.CornerRadius(5.dp.toPx()))
                    }
                    1 -> {
                        val points = values.mapIndexed { index, value -> Offset(size.width * index / 5, bottom - height * value / max * .9f) }
                        val path = androidx.compose.ui.graphics.Path().apply { points.forEachIndexed { index, point -> if (index == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y) } }
                        clipRect(right = size.width * ease) {
                            val area = androidx.compose.ui.graphics.Path().apply { addPath(path); lineTo(size.width, bottom); lineTo(0f, bottom); close() }
                            drawPath(area, accent.copy(alpha = .13f))
                            drawPath(path, accent, style = Stroke(4.dp.toPx()))
                            points.forEach { drawCircle(Color.White, 6.dp.toPx(), it); drawCircle(accent, 6.dp.toPx(), it, style = Stroke(2.dp.toPx())) }
                        }
                        val pointer = (moment.second % 3f) / 3f * 5
                        val segment = pointer.toInt().coerceIn(0, 4)
                        val position = points[segment] + (points[segment + 1] - points[segment]) * (pointer - segment)
                        drawCircle(accent.copy(alpha = .16f), 15.dp.toPx(), position)
                        drawCircle(accent, 5.dp.toPx(), position)
                    }
                    else -> {
                        val diameter = minOf(size.width, size.height) * .84f
                        val origin = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)
                        var angle = -90f
                        values.forEachIndexed { index, value ->
                            val sweep = value / values.sum().coerceAtLeast(1f) * 360f
                            drawArc(colors[index], angle, (sweep - 3).coerceAtLeast(0f) * ease, false, origin,
                                androidx.compose.ui.geometry.Size(diameter, diameter), style = Stroke(diameter * .16f))
                            angle += sweep
                        }
                    }
                }
            }
            if (mode == 2) Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("數據合計", color = OfficeMuted, fontSize = 10.sp)
                Text("%.1f".format(Locale.US, values.sum()), color = OfficeInk, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.SpaceAround) {
            repeat(6) { Text("${it + 1}", color = colors[it], fontSize = 10.sp) }
        }
        Text("來源：本批 XLSX · 6 組樣本", color = OfficeMuted, fontSize = 10.sp)
    }
}

@Composable
private fun ReportImage(result: OfficeDocumentGenerator.Result?, modifier: Modifier) {
    val bitmap = remember(result?.batch) { result?.image?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() } }
    if (bitmap != null) Image(bitmap, "實際插入 Office 檔案的成效圖表", modifier)
    else Box(modifier.background(OfficeDesk), contentAlignment = Alignment.Center) { Text("正在產生成效圖片…", color = OfficeMuted, fontSize = 11.sp) }
}

@Composable
private fun BoxScope.PicturePicker(result: OfficeDocumentGenerator.Result?, accent: Color) {
    Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.White, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)).border(1.dp, OfficeLine, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)).padding(20.dp)) {
        Text("插入圖片", color = OfficeInk, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Text("此裝置  /  活動素材", color = OfficeMuted, fontSize = 11.sp, modifier = Modifier.padding(vertical = 10.dp))
        ReportImage(result, Modifier.fillMaxWidth().aspectRatio(2.2f).border(2.dp, accent))
        Text("report.png    ✓ 已選取", color = accent, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
    }
}

@Composable
private fun EditorToolbar(moment: OfficeMoment, accent: Color) {
    Row(Modifier.fillMaxWidth().background(Color.White).padding(vertical = 11.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
        val tools = when (moment.section) { 0 -> listOf("Aa", "B", "I", "≡", "圖片", "復原"); 1 -> listOf("ƒx", "Σ", "填滿", "圖表", "格式"); else -> listOf("版面", "文字", "圖片", "排列", "▶") }
        tools.forEachIndexed { index, text ->
            Text(text, color = if (index == (moment.second / 8).toInt() % tools.size) accent else OfficeInk,
                fontSize = 13.sp, fontWeight = if (text == "B") FontWeight.Bold else FontWeight.Normal)
        }
    }
}

@Composable
private fun SimulatedKeyboard(moment: OfficeMoment, accent: Color) {
    Column(Modifier.fillMaxWidth().background(Color(0xFFE3E8F0)).padding(5.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(if (moment.section == 1) "SUM    平均值    合計" else "活動    品牌    成效    企劃", color = OfficeInk, fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterHorizontally).padding(3.dp))
        val rows = if (moment.section == 1) listOf("1234567890", "=+-*/():", "ABC 0.↵") else listOf("QWERTYUIOP", "ASDFGHJKL", "⇧ZXCVBNM⌫")
        rows.forEachIndexed { row, keys ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                keys.forEachIndexed { index, char ->
                    Box(Modifier.weight(1f).height(40.dp).background(if ((moment.second * 5).toInt() % 27 == row * 9 + index) accent.copy(alpha = .30f) else Color.White, RoundedCornerShape(4.dp)), contentAlignment = Alignment.Center) {
                        Text(char.toString(), color = OfficeInk, fontSize = 12.sp)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(5.dp), horizontalArrangement = Arrangement.SpaceAround) {
            Text("🌐", color = OfficeInk, fontSize = 12.sp)
            Text("空白", color = OfficeInk, fontSize = 12.sp)
            Text("↵", color = OfficeInk, fontSize = 12.sp)
        }
    }
}

@Composable
private fun TouchGesture(moment: OfficeMoment, accent: Color) {
    Canvas(Modifier.fillMaxSize()) {
        val swipe = moment.second in 34f..51f
        val phase = (moment.second % 2f) / 2f
        val visible = if (swipe) phase < .65f else moment.second % 3f < .48f
        if (visible && !moment.typing) {
            val point = Offset(size.width * if (moment.picker) .6f else .76f, size.height * if (swipe) (.78f - phase * .70f) else .46f)
            drawCircle(accent.copy(alpha = .12f), 15.dp.toPx(), point)
            drawCircle(accent.copy(alpha = .7f), 15.dp.toPx(), point, style = Stroke(1.5.dp.toPx()))
            drawCircle(Color.White.copy(alpha = .8f), 4.dp.toPx(), point)
        }
    }
}
