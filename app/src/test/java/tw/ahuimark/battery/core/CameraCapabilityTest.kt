package tw.ahuimark.battery.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraCapabilityTest {
    @Test
    fun acceptsStandardUhdThirtyFpsProfiles() {
        assertTrue(isUhd30Profile(3840, 2160, 29))
        assertTrue(isUhd30Profile(3840, 2160, 30))
        assertTrue(isUhd30Profile(3840, 2160, 31))
        assertTrue(isUhd30Profile(3840, 2160, 60))
        assertTrue(isUhd30Profile(2160, 3840, 30))
        assertTrue(isUhd30Profile(4096, 2160, 30))
    }

    @Test
    fun rejectsNonUhdOrDifferentFrameRates() {
        assertFalse(isUhd30Profile(1920, 1080, 30))
        assertFalse(isUhd30Profile(3840, 2160, 24))
        assertFalse(isUhd30Profile(3840, 1080, 30))
    }
}
