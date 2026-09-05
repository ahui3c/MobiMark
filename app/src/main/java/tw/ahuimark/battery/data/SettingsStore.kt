package tw.ahuimark.battery.data

import android.content.Context
import tw.ahuimark.battery.model.BenchmarkSettings
import tw.ahuimark.battery.model.DEFAULT_ONLINE_VIDEO_URL
import tw.ahuimark.battery.model.DEFAULT_WEB_URLS
import tw.ahuimark.battery.model.VideoSourceMode
import tw.ahuimark.battery.model.WebSourceMode

class SettingsStore(context: Context) {
    private val preferences = context.getSharedPreferences("benchmark_settings", Context.MODE_PRIVATE)

    fun load(): BenchmarkSettings = BenchmarkSettings(
        webUrls = DEFAULT_WEB_URLS.indices.map { index ->
            preferences.getString("web_url_$index", DEFAULT_WEB_URLS[index]) ?: DEFAULT_WEB_URLS[index]
        },
        webSourceMode = runCatching {
            WebSourceMode.valueOf(preferences.getString("web_source", null) ?: WebSourceMode.ONLINE.name)
        }.getOrDefault(WebSourceMode.ONLINE),
        videoSourceMode = runCatching {
            VideoSourceMode.valueOf(preferences.getString("video_source", null) ?: VideoSourceMode.ONLINE.name)
        }.getOrDefault(VideoSourceMode.ONLINE),
        onlineVideoUrl = preferences.getString("online_video_url", DEFAULT_ONLINE_VIDEO_URL)
            ?: DEFAULT_ONLINE_VIDEO_URL,
        automaticDnd = preferences.getBoolean("automatic_dnd", true)
    )

    fun save(settings: BenchmarkSettings) {
        preferences.edit().apply {
            repeat(3) { index -> putString("web_url_$index", settings.webUrls.getOrElse(index) { "" }) }
            remove("web_url_3")
            putString("web_source", settings.webSourceMode.name)
            putString("video_source", settings.videoSourceMode.name)
            putString("online_video_url", settings.onlineVideoUrl)
            // Retired window overrides must never override external ADB calibration.
            remove("calibrated_brightness")
            remove("target_200_nits_confirmed")
            putBoolean("automatic_dnd", settings.automaticDnd)
        }.apply()
    }
}
