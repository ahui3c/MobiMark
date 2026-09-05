package tw.ahuimark.battery.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BenchmarkSettingsTest {
    @Test
    fun onlineAcceptsOneSiteInAnySlotAndIgnoresWhitespaceOnlySlots() {
        for (slot in 0..2) {
            val urls = MutableList(3) { "  " }.apply { this[slot] = " ahui3c.com " }
            val settings = BenchmarkSettings(webUrls = urls)
            assertTrue(settings.webUrlsValid)
            assertTrue(settings.isValid)
            assertEquals(listOf("https://ahui3c.com"), settings.normalizedWebUrls)
        }
    }

    @Test
    fun onlinePreservesOrderWhenMiddleSlotIsEmpty() {
        val settings = BenchmarkSettings(webUrls = listOf("ahui3c.com", "", "lpcomment.com"))
        assertTrue(settings.webUrlsValid)
        assertEquals(listOf("https://ahui3c.com", "https://lpcomment.com"), settings.normalizedWebUrls)
    }

    @Test
    fun allEmptyOrAnyNonemptyInvalidSiteBlocksOnline() {
        assertFalse(BenchmarkSettings(webUrls = listOf("", "  ", "")).webUrlsValid)
        assertFalse(BenchmarkSettings(webUrls = emptyList()).isValid)
        assertFalse(BenchmarkSettings(webUrls = listOf("ahui3c.com", "bad url", "")).isValid)
    }

    @Test
    fun oneSitePassesStartReadinessButZeroSitesDoesNot() {
        val state = BenchmarkUiState(dndAccessGranted = true,
            settings = BenchmarkSettings(webUrls = listOf("", "", "ahui3c.com")),
            readiness = DeviceReadiness(batteryAbove80 = true, unplugged = true,
                cameraPermission = true, rearUhd30Supported = true, storageReady = true))
        assertTrue(state.canStart)
        assertFalse(state.copy(settings = state.settings.copy(webUrls = listOf("", "", ""))).canStart)
    }

    @Test
    fun defaultsContainRequestedSitesAndOnlineVideo() {
        val settings = BenchmarkSettings()
        assertEquals(WebSourceMode.ONLINE, settings.webSourceMode)
        assertEquals(VideoSourceMode.ONLINE, settings.videoSourceMode)
        assertEquals(3, settings.webUrls.size)
        assertEquals("https://ahui3c.com", settings.webUrls.first())
        assertEquals("https://youtu.be/1b-_FC_hIAQ", settings.onlineVideoUrl)
        assertTrue(settings.isValid)
    }

    @Test
    fun normalizesHostNamesAndRejectsInvalidOnlineUrl() {
        assertEquals("https://ahui3c.com", normalizeHttpUrl("ahui3c.com"))
        val settings = BenchmarkSettings(videoSourceMode = VideoSourceMode.ONLINE, onlineVideoUrl = "not a url")
        assertFalse(settings.isValid)
    }

    @Test
    fun offlineWebModeDoesNotRequireOnlineSiteUrls() {
        val settings = BenchmarkSettings(
            webUrls = listOf("", "", ""),
            webSourceMode = WebSourceMode.OFFLINE
        )
        assertFalse(settings.webUrlsValid)
        assertTrue(settings.isValid)
    }

    @Test
    fun explicitOfflineAndLocalSelectionsRemainAvailable() {
        val settings = BenchmarkSettings(webSourceMode = WebSourceMode.OFFLINE,
            videoSourceMode = VideoSourceMode.LOCAL, webUrls = emptyList(), onlineVideoUrl = "")
        assertTrue(settings.isValid)
        assertEquals(WebSourceMode.OFFLINE, settings.webSourceMode)
        assertEquals(VideoSourceMode.LOCAL, settings.videoSourceMode)
    }

    @Test
    fun defaultOnlineModeDoesNotRequireDownloadedVideo() {
        val ready = BenchmarkUiState(
            settings = BenchmarkSettings(),
            dndAccessGranted = true,
            readiness = DeviceReadiness(batteryAbove80 = true, unplugged = true, cameraPermission = true,
                rearUhd30Supported = true, mediaAssetReady = false, storageReady = true))
        assertTrue(ready.canStart)
        assertFalse(ready.copy(settings = ready.settings.copy(videoSourceMode = VideoSourceMode.LOCAL)).canStart)
    }
}
