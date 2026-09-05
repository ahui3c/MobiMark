package tw.ahuimark.battery.workload

import org.junit.Assert.assertEquals
import org.junit.Test

class WebRotationTest {
    @Test
    fun singleSiteKeepsScrollingForEntireThreeMinutes() {
        listOf(0L, 59_999L, 60_000L, 120_000L, 179_999L).forEach {
            assertEquals(0, webPageIndex(it, 1))
        }
    }

    @Test
    fun twoSitesRotateAThenBThenA() {
        val urls = tw.ahuimark.battery.model.BenchmarkSettings(
            webUrls = listOf("ahui3c.com", " ", "lpcomment.com")).normalizedWebUrls
        assertEquals(listOf("https://ahui3c.com", "https://lpcomment.com", "https://ahui3c.com"),
            listOf(0L, 60_000L, 120_000L).map { urls[webPageIndex(it, urls.size)] })
    }

    @Test
    fun rotatesAcrossThreeSitesAtOneMinuteBoundaries() {
        assertEquals(0, webPageIndex(0L, 3))
        assertEquals(0, webPageIndex(59_999L, 3))
        assertEquals(1, webPageIndex(60_000L, 3))
        assertEquals(2, webPageIndex(120_000L, 3))
        assertEquals(0, webPageIndex(180_000L, 3))
    }
}
