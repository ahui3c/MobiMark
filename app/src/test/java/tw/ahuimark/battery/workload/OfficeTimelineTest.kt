package tw.ahuimark.battery.workload

import org.junit.Assert.*
import org.junit.Test

class OfficeTimelineTest {
    @Test fun eachEditorGetsExactlyOneMinute() {
        assertEquals(0, officeMoment(59_999).section)
        assertEquals(1, officeMoment(60_000).section)
        assertEquals(2, officeMoment(120_000).section)
        assertEquals(0, officeMoment(180_000).section)
        assertEquals(0f, officeMoment(-1).second)
    }
    @Test fun typingPickingAndSavingAreSeparateActions() {
        assertTrue(officeMoment(10_000).typing)
        assertFalse(officeMoment(10_000).picker)
        assertTrue(officeMoment(27_000).picker)
        assertTrue(officeMoment(57_000).saving)
    }
    @Test fun chartsHaveFiftySecondsWithoutInterruptingTyping() {
        val moments = (0 until 180).map { officeMoment(it * 1000L + 500) }
        assertEquals(50, moments.count { it.chartFocus })
        assertTrue(moments.none { it.chartFocus && (it.typing || it.picker) })
    }
    private fun sheet() = (2..1501).joinToString("") { row ->
        "<row r=\"$row\"><c r=\"B$row\"><v>12.1234</v></c><c r=\"C$row\"><v>7.2000</v></c><c r=\"D$row\"><f>SUM(B$row:C$row)</f><v>19.3234</v></c></row>"
    }
    @Test fun verifiesEveryFormulaAndCache() { assertEquals(1500, verifyOfficeFormulaRows(sheet())) }
    @Test(expected = IllegalStateException::class) fun rejectsWrongCachedValue() {
        verifyOfficeFormulaRows(sheet().replaceFirst("19.3234", "20.0000"))
    }
    @Test(expected = IllegalStateException::class) fun rejectsWrongFormula() {
        verifyOfficeFormulaRows(sheet().replaceFirst("SUM(B2:C2)", "SUM(B2:C3)"))
    }
    @Test(expected = IllegalStateException::class) fun rejectsMissingRows() {
        verifyOfficeFormulaRows(sheet().replace(Regex("<row r=\"1501\">.*?</row>"), ""))
    }
}
