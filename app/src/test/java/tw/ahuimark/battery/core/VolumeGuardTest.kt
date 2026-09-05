package tw.ahuimark.battery.core

import org.junit.Assert.*
import org.junit.Test

class VolumeGuardTest {
    private class Port : VolumePort {
        var failSave = false
        var failSet = false
        override var current = 8
            set(value) {
                if (failSet) error("blocked")
                field = value
            }
        override val minimum = 0
        override var saved: Int? = null
            set(value) {
                if (failSave) error("disk failure")
                field = value
            }
    }

    @Test fun muteAndRestore() {
        val p = Port(); val g = VolumeGuard(p)
        assertTrue(g.mute().isSuccess); assertEquals(0, p.current)
        assertEquals(8, p.saved)
        assertTrue(g.restore().isSuccess); assertEquals(8, p.current); assertNull(p.saved)
    }
    @Test fun repeatedEntryDoesNotOverwriteOriginal() {
        val p = Port(); val g = VolumeGuard(p)
        g.mute(); g.mute(); g.restore(); g.restore()
        assertEquals(8, p.current)
    }
    @Test fun preservesUserAdjustment() {
        val p = Port(); val g = VolumeGuard(p)
        g.mute(); p.current = 3; g.restore()
        assertEquals(3, p.current); assertNull(p.saved)
    }
    @Test fun journalMustSucceedBeforeMutation() {
        val p = Port().apply { failSave = true }
        assertTrue(VolumeGuard(p).mute().isFailure); assertEquals(8, p.current)
    }
    @Test fun failedSystemChangeRetainsRecovery() {
        val p = Port().apply { failSet = true }
        assertTrue(VolumeGuard(p).mute().isFailure); assertEquals(8, p.saved)
    }
    @Test fun restoreCanRetryAfterFailureAndRecreation() {
        val p = Port(); VolumeGuard(p).mute(); p.failSet = true
        assertTrue(VolumeGuard(p).restore().isFailure); assertEquals(8, p.saved)
        p.failSet = false
        assertTrue(VolumeGuard(p).restore().isSuccess); assertEquals(8, p.current)
    }
    @Test fun alreadySilentStaysSilent() {
        val p = Port().apply { current = 0 }; val g = VolumeGuard(p)
        g.mute(); g.restore(); assertEquals(0, p.current); assertNull(p.saved)
    }
}
