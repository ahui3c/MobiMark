package tw.ahuimark.battery.workload

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineVideoUrlTest {
    @Test
    fun recognizesSupportedYoutubeLinks() {
        assertEquals("1b-_FC_hIAQ", youtubeVideoId("https://youtu.be/1b-_FC_hIAQ"))
        assertEquals("1b-_FC_hIAQ", youtubeVideoId("https://www.youtube.com/watch?v=1b-_FC_hIAQ"))
        assertEquals("1b-_FC_hIAQ", youtubeVideoId("https://youtube.com/embed/1b-_FC_hIAQ"))
    }

    @Test
    fun leavesDirectMediaUrlsForExoPlayer() {
        assertNull(youtubeVideoId("https://cdn.example.com/video.mp4"))
    }

    @Test
    fun embedRequestCarriesClientIdentityAndReportsPlayerErrors() {
        val url = youtubeEmbedUrl("1b-_FC_hIAQ")
        val headers = youtubeRequestHeaders()

        assertTrue(url.startsWith("https://www.youtube.com/embed/1b-_FC_hIAQ"))
        assertTrue(url.contains("origin=https%3A%2F%2Fahui3c.com"))
        assertEquals("https://ahui3c.com/", headers["Referer"])
        assertTrue(youtubePlayerErrorMessage(153).contains("Referer"))
    }

    @Test
    fun layoutFixForcesYoutubeVideoToFillTheViewport() {
        assertTrue(YOUTUBE_LAYOUT_FIX.contains("height:100vh!important"))
        assertTrue(YOUTUBE_LAYOUT_FIX.contains("video.html5-main-video"))
        assertTrue(YOUTUBE_LAYOUT_FIX.contains("background:#000!important"))
        assertTrue(YOUTUBE_LAYOUT_FIX.contains("window.innerHeight"))
        assertTrue(YOUTUBE_LAYOUT_FIX.contains("setProperty('height', viewportHeight, 'important')"))
    }
}
