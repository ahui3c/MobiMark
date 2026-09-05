package tw.ahuimark.battery.core

/** Persist the recovery snapshot BEFORE changing system state. No Android dependency. */
internal interface DndBackend {
    val hasAccess: Boolean
    fun capture(): String
    fun activate(snapshot: String)
    fun restore(snapshot: String)
}

internal interface DndJournal {
    fun read(): String?
    fun write(snapshot: String?)
}

internal class DndSessionGuard(private val backend: DndBackend, private val journal: DndJournal) {
    fun begin(enabled: Boolean): Result<Boolean> = runCatching {
        end().getOrThrow()
        if (!enabled) return@runCatching false
        check(backend.hasAccess) { "請先授予勿擾模式存取權" }
        val snapshot = backend.capture()
        journal.write(snapshot)
        try {
            backend.activate(snapshot)
        } catch (error: Exception) {
            val cleanup = end()
            if (cleanup.isFailure) throw IllegalStateException("勿擾啟用失敗，且復原尚未完成；請重新授權後重試", error)
            throw error
        }
        true
    }

    fun end(): Result<Boolean> = runCatching {
        val snapshot = journal.read() ?: return@runCatching false
        check(backend.hasAccess) { "勿擾復原尚未完成，請重新授權後重試" }
        backend.restore(snapshot)
        journal.write(null)
        true
    }
}
