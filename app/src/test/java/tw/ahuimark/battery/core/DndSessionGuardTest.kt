package tw.ahuimark.battery.core

import org.junit.Assert.*
import org.junit.Test

class DndSessionGuardTest {
    private class Fixture {
        val calls = mutableListOf<String>()
        var snapshot: String? = null
        var access = true
        var failSave = false
        var failActivate = false
        var failRestore = false
        val backend = object : DndBackend {
            override val hasAccess get() = access
            override fun capture() = "original".also { calls += "capture" }
            override fun activate(snapshot: String) {
                check(this@Fixture.snapshot == "original")
                calls += "activate"
                if (failActivate) error("activate failed")
            }
            override fun restore(snapshot: String) {
                calls += "restore"
                if (failRestore) error("restore failed")
            }
        }
        val journal = object : DndJournal {
            override fun read() = snapshot
            override fun write(snapshot: String?) {
                if (failSave) error("disk full")
                calls += if (snapshot == null) "clear" else "save"
                this@Fixture.snapshot = snapshot
            }
        }
        fun guard() = DndSessionGuard(backend, journal)
    }

    @Test fun savesBeforeActivationAndRestoresBeforeClearing() {
        val f = Fixture()
        assertEquals(true, f.guard().begin(true).getOrThrow())
        assertEquals(true, f.guard().end().getOrThrow())
        assertEquals(listOf("capture", "save", "activate", "restore", "clear"), f.calls)
    }
    @Test fun disabledSettingDoesNotChangeSystem() {
        val f = Fixture()
        assertEquals(false, f.guard().begin(false).getOrThrow())
        assertTrue(f.calls.isEmpty())
    }
    @Test fun permissionDeniedDoesNotWriteOrActivate() {
        val f = Fixture().apply { access = false }
        assertTrue(f.guard().begin(true).isFailure)
        assertTrue(f.calls.isEmpty())
    }
    @Test fun diskFailurePreventsSystemMutation() {
        val f = Fixture().apply { failSave = true }
        assertTrue(f.guard().begin(true).isFailure)
        assertEquals(listOf("capture"), f.calls)
    }
    @Test fun partialActivationFailureRestores() {
        val f = Fixture().apply { failActivate = true }
        assertTrue(f.guard().begin(true).isFailure)
        assertNull(f.snapshot)
        assertEquals(listOf("capture", "save", "activate", "restore", "clear"), f.calls)
    }
    @Test fun restoreFailureRetainsRecoveryForNextProcess() {
        val f = Fixture()
        f.guard().begin(true).getOrThrow()
        f.failRestore = true
        assertTrue(f.guard().end().isFailure)
        assertNotNull(f.snapshot)
        f.failRestore = false
        assertEquals(true, f.guard().end().getOrThrow())
        assertNull(f.snapshot)
    }
    @Test fun revokedPermissionKeepsSnapshotAndCanRetry() {
        val f = Fixture()
        f.guard().begin(true).getOrThrow()
        f.access = false
        assertTrue(f.guard().end().isFailure)
        assertEquals("original", f.snapshot)
        f.access = true
        f.guard().end().getOrThrow()
        assertNull(f.snapshot)
    }
    @Test fun nextStartRestoresStaleSnapshotEvenIfFeatureDisabled() {
        val f = Fixture().apply { snapshot = "original" }
        assertEquals(false, f.guard().begin(false).getOrThrow())
        assertEquals(listOf("restore", "clear"), f.calls)
    }
    @Test fun doubleStopIsIdempotent() {
        val f = Fixture()
        f.guard().begin(true).getOrThrow()
        f.guard().end().getOrThrow()
        assertEquals(false, f.guard().end().getOrThrow())
        assertEquals(1, f.calls.count { it == "restore" })
    }
    @Test fun failedCleanupBlocksAnotherStart() {
        val f = Fixture().apply { snapshot = "original"; failRestore = true }
        assertTrue(f.guard().begin(true).isFailure)
        assertEquals(listOf("restore"), f.calls)
    }
}
