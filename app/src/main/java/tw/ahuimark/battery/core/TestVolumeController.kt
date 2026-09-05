package tw.ahuimark.battery.core

import android.content.Context
import android.media.AudioManager
import android.os.Build

internal interface VolumePort {
    var current: Int
    val minimum: Int
    var saved: Int?
}

internal class VolumeGuard(private val port: VolumePort) {
    fun mute(): Result<Unit> = runCatching {
        // Persist before changing the system, and never overwrite the original on retry.
        if (port.saved == null) port.saved = port.current
        port.current = port.minimum
        check(port.current == port.minimum) { "系統未將媒體音量調至最低" }
    }

    fun restore(): Result<Unit> = runCatching {
        val original = port.saved ?: return@runCatching
        // Respect a volume adjustment made by the user while the app was active.
        if (port.current == port.minimum) {
            port.current = original
            check(port.current == original) { "媒體音量尚未復原" }
        }
        port.saved = null
    }
}

class TestVolumeController(context: Context) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val prefs = context.getSharedPreferences("test_volume_recovery", Context.MODE_PRIVATE)
    private val guard = VolumeGuard(object : VolumePort {
        override var current: Int
            get() = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
            set(value) { audio.setStreamVolume(AudioManager.STREAM_MUSIC, value, 0) }
        override val minimum: Int
            get() = if (Build.VERSION.SDK_INT >= 28) audio.getStreamMinVolume(AudioManager.STREAM_MUSIC) else 0
        override var saved: Int?
            get() = if (prefs.contains("original")) prefs.getInt("original", 0) else null
            set(value) {
                check(prefs.edit().apply {
                    if (value == null) remove("original") else putInt("original", value)
                }.commit()) { "無法儲存音量復原紀錄" }
            }
    })

    fun mute() = guard.mute()
    fun restore() = guard.restore()
}
