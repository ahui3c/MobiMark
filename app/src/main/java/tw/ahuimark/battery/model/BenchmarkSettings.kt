package tw.ahuimark.battery.model

enum class VideoSourceMode(val title: String) {
    LOCAL("本地影片"),
    ONLINE("線上影片")
}

enum class WebSourceMode(val title: String) {
    ONLINE("線上網頁"),
    OFFLINE("離線模擬網頁")
}

data class BenchmarkSettings(
    val webUrls: List<String> = DEFAULT_WEB_URLS,
    val webSourceMode: WebSourceMode = WebSourceMode.ONLINE,
    val videoSourceMode: VideoSourceMode = VideoSourceMode.ONLINE,
    val onlineVideoUrl: String = DEFAULT_ONLINE_VIDEO_URL,
    val automaticDnd: Boolean = true
) {
    val normalizedWebUrls: List<String>
        get() = webUrls.take(3).map(::normalizeHttpUrl).filter(String::isNotBlank)

    val normalizedOnlineVideoUrl: String
        get() = normalizeHttpUrl(onlineVideoUrl)

    val webUrlsValid: Boolean
        get() = normalizedWebUrls.isNotEmpty() && normalizedWebUrls.all(::isHttpUrl)

    val onlineVideoUrlValid: Boolean
        get() = isHttpUrl(normalizedOnlineVideoUrl)

    val isValid: Boolean
        get() = (webSourceMode == WebSourceMode.OFFLINE || webUrlsValid) &&
            (videoSourceMode == VideoSourceMode.LOCAL || onlineVideoUrlValid)
}

fun normalizeHttpUrl(value: String): String {
    val trimmed = value.trim()
    if (trimmed.isEmpty() || trimmed.contains(Regex("\\s"))) return trimmed
    return if (trimmed.startsWith("http://", true) || trimmed.startsWith("https://", true)) {
        trimmed
    } else {
        "https://$trimmed"
    }
}

internal fun isHttpUrl(value: String): Boolean = runCatching {
    val uri = java.net.URI(value)
    (uri.scheme == "http" || uri.scheme == "https") && !uri.host.isNullOrBlank()
}.getOrDefault(false)

val DEFAULT_WEB_URLS = listOf(
    "https://ahui3c.com",
    "https://www.toy-people.com/",
    "https://lpcomment.com/"
)

const val DEFAULT_ONLINE_VIDEO_URL = "https://youtu.be/1b-_FC_hIAQ"
