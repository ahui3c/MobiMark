package tw.ahuimark.battery.workload

import android.annotation.SuppressLint
import android.webkit.ConsoleMessage
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.video.Quality
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import tw.ahuimark.battery.model.WorkloadType
import tw.ahuimark.battery.model.BenchmarkSettings
import tw.ahuimark.battery.model.VideoSourceMode
import tw.ahuimark.battery.model.WEB_PAGE_DURATION_MS
import tw.ahuimark.battery.model.WebSourceMode
import tw.ahuimark.battery.media.VideoMaterial
import tw.ahuimark.battery.ui.theme.BatteryAmber
import tw.ahuimark.battery.ui.theme.LabPanel
import tw.ahuimark.battery.ui.theme.LabPanelRaised
import tw.ahuimark.battery.ui.theme.MutedSteel
import tw.ahuimark.battery.ui.theme.PaperMist
import tw.ahuimark.battery.ui.theme.SignalCyan
import java.io.File
import kotlin.math.sin

@Composable
fun WorkloadHost(type: WorkloadType, elapsedMs: Long, modifier: Modifier = Modifier, settings: BenchmarkSettings = BenchmarkSettings()) {
    Box(modifier.fillMaxSize().background(LabPanel)) {
        when (type) {
            WorkloadType.WEB -> WebBrowsingWorkload(elapsedMs, settings)
            WorkloadType.SHOOTER -> ShooterWorkload(elapsedMs)
            WorkloadType.VIDEO -> VideoPlaybackWorkload(settings)
            WorkloadType.CAMERA -> CameraWorkload(
                CameraSelector.LENS_FACING_BACK,
                Quality.UHD,
                elapsedMs
            )
            WorkloadType.VIDEO_CALL -> error("視訊工作負載已移除，僅供舊成績讀取")
            WorkloadType.OFFICE -> OfficeWorkload(elapsedMs)
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun WebBrowsingWorkload(elapsedMs: Long, settingsModel: BenchmarkSettings) {
    val urls = settingsModel.normalizedWebUrls
    if (settingsModel.webSourceMode == WebSourceMode.ONLINE && !settingsModel.webUrlsValid) {
        Text("請先設定至少一個有效的網頁網址", color = Color.White)
        return
    }
    val pageIndex = webPageIndex(elapsedMs, urls.size)
    val desiredUrl = if (settingsModel.webSourceMode == WebSourceMode.OFFLINE) {
        OFFLINE_WEB_URL
    } else {
        urls[pageIndex]
    }
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                setBackgroundColor(android.graphics.Color.rgb(238, 241, 240))
                settings.javaScriptEnabled = true
                settings.cacheMode = WebSettings.LOAD_NO_CACHE
                settings.domStorageEnabled = true
                settings.loadsImagesAutomatically = true
                settings.allowFileAccess = settingsModel.webSourceMode == WebSourceMode.OFFLINE
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = false
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String?) {
                        view.evaluateJavascript(WEB_AUTO_SCROLL_SCRIPT, null)
                    }
                }
                tag = desiredUrl
                loadUrl(desiredUrl)
            }
        },
        update = { webView ->
            if (webView.tag != desiredUrl) {
                webView.tag = desiredUrl
                webView.stopLoading()
                webView.scrollTo(0, 0)
                webView.loadUrl(desiredUrl)
            }
        },
        onRelease = { it.destroy() }
    )
}

internal fun webPageIndex(elapsedMs: Long, urlCount: Int): Int =
    ((elapsedMs.coerceAtLeast(0L) / WEB_PAGE_DURATION_MS) % urlCount.coerceAtLeast(1)).toInt()

@Composable
private fun ShooterWorkload(elapsedMs: Long) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context -> GodotShooterView(context) },
        update = { it.updateElapsed(elapsedMs) },
        onRelease = { it.release() }
    )
}

@OptIn(UnstableApi::class)
@Composable
private fun VideoPlaybackWorkload(settings: BenchmarkSettings) {
    val context = LocalContext.current
    val videoFile = remember { File(context.filesDir, "media/${VideoMaterial.FILE_NAME}") }
    if (settings.videoSourceMode == VideoSourceMode.ONLINE) {
        val url = settings.normalizedOnlineVideoUrl
        val youtubeId = remember(url) { youtubeVideoId(url) }
        if (youtubeId != null) {
            YouTubePlaybackWorkload(youtubeId)
        } else {
            NativeVideoPlayer(url)
        }
    } else {
        NativeVideoPlayer(videoFile.toURI().toString())
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun NativeVideoPlayer(uri: String) {
    val context = LocalContext.current
    val player = remember(uri) {
        ExoPlayer.Builder(context)
            .setVideoChangeFrameRateStrategy(C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_OFF)
            .build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { PlayerView(it).apply {
            useController = false
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            this.player = player
        } },
        update = { it.player = player }
    )
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun YouTubePlaybackWorkload(videoId: String) {
    val orientation = LocalConfiguration.current.orientation
    // The workload state can request landscape before Android has completed
    // the configuration change. Recreate the WebView after that transition so
    // Chromium allocates its video Surface at the final landscape dimensions.
    key(videoId, orientation) {
        YouTubeWebView(videoId)
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun YouTubeWebView(videoId: String) {
    var playerError by remember(videoId) { mutableStateOf<String?>(null) }
    var playerLoading by remember(videoId) { mutableStateOf(true) }
    var activeWebView by remember(videoId) { mutableStateOf<WebView?>(null) }
    LaunchedEffect(activeWebView, videoId) {
        val webView = activeWebView ?: return@LaunchedEffect
        repeat(12) { attempt ->
            kotlinx.coroutines.delay(2_500L)
            webView.evaluateJavascript(YOUTUBE_PLAYBACK_PROBE) { result ->
                when {
                    result.contains("PLAYING") -> {
                        playerLoading = false
                        playerError = null
                    }
                    result.contains("ERROR_153") -> {
                        playerLoading = false
                        playerError = youtubePlayerErrorMessage(153)
                    }
                    result.contains("ERROR_UNAVAILABLE") -> {
                        playerLoading = false
                        playerError = "YouTube 回報影片目前無法播放"
                    }
                    attempt == 5 && playerLoading -> {
                        // Recreate YouTube's internal video element once after
                        // the landscape Surface is stable. Use loadUrl again so
                        // the explicit Referer is preserved on the retry.
                        webView.loadUrl(youtubeEmbedUrl(videoId), youtubeRequestHeaders())
                    }
                    attempt == 11 && playerLoading -> {
                        playerLoading = false
                        playerError = "30 秒內未取得影片畫面，請檢查網路或更新 Android System WebView"
                    }
                }
            }
        }
    }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    setBackgroundColor(android.graphics.Color.BLACK)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    settings.cacheMode = WebSettings.LOAD_DEFAULT
                    webChromeClient = object : WebChromeClient() {
                        override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                            val message = consoleMessage.message()
                            if (message.contains("153") || message.contains("EMBEDDER_IDENTITY", true)) {
                                playerLoading = false
                                playerError = youtubePlayerErrorMessage(153)
                            }
                            return super.onConsoleMessage(consoleMessage)
                        }
                    }
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String?) {
                            view.evaluateJavascript(YOUTUBE_LAYOUT_FIX, null)
                        }

                        override fun onReceivedError(
                            view: WebView,
                            request: WebResourceRequest,
                            error: WebResourceError
                        ) {
                            if (request.isForMainFrame) {
                                playerLoading = false
                                playerError = "播放器網路錯誤：${error.description}"
                            }
                        }

                        override fun onReceivedHttpError(
                            view: WebView,
                            request: WebResourceRequest,
                            errorResponse: WebResourceResponse
                        ) {
                            if (request.isForMainFrame && errorResponse.statusCode >= 400) {
                                playerLoading = false
                                playerError = "播放器 HTTP ${errorResponse.statusCode}"
                            }
                        }

                        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                            playerLoading = false
                            playerError = "播放器處理程序已中止，請重新啟動測試"
                            view.destroy()
                            return true
                        }
                    }
                    activeWebView = this
                    // Loading the embed document as the WebView's main HTTPS
                    // resource lets us attach a real Referer to the very first
                    // YouTube request. loadDataWithBaseURL only supplied a
                    // virtual origin, which several Android WebView versions
                    // omit from the iframe request and YouTube rejects as 153.
                    loadUrl(youtubeEmbedUrl(videoId), youtubeRequestHeaders())
                }
            },
            onRelease = { webView ->
                if (activeWebView === webView) activeWebView = null
                webView.loadUrl("about:blank")
                webView.destroy()
            }
        )
        if (playerLoading && playerError == null) {
            Column(
                Modifier.align(Alignment.Center)
                    .background(Color.Black.copy(alpha = .72f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("正在連接線上影片", color = PaperMist, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(Modifier.height(5.dp))
                Text("YouTube · 1080p30", color = SignalCyan, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
            }
        }
        playerError?.let { message ->
            Column(
                Modifier.align(Alignment.Center)
                    .background(Color.Black.copy(alpha = .86f), RoundedCornerShape(8.dp))
                    .border(1.dp, Color(0xFFFF706A), RoundedCornerShape(8.dp))
                    .padding(horizontal = 22.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("線上影片無法播放", color = Color(0xFFFF8D87), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.height(6.dp))
                Text(message, color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            }
        }
    }
}

internal fun youtubeVideoId(url: String): String? = runCatching {
    val uri = java.net.URI(url)
    val host = uri.host?.lowercase()?.removePrefix("www.") ?: return null
    val candidate = when {
        host == "youtu.be" -> uri.path.trim('/').substringBefore('/')
        host.endsWith("youtube.com") -> when {
            uri.path == "/watch" -> uri.rawQuery.orEmpty().split('&')
                .firstOrNull { it.startsWith("v=") }?.substringAfter("v=")
            uri.path.startsWith("/embed/") -> uri.path.substringAfter("/embed/").substringBefore('/')
            uri.path.startsWith("/shorts/") -> uri.path.substringAfter("/shorts/").substringBefore('/')
            else -> null
        }
        else -> null
    }
    candidate?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{6,20}")) }
}.getOrNull()

internal fun youtubeEmbedUrl(videoId: String): String =
    "https://www.youtube.com/embed/$videoId" +
        "?enablejsapi=1&autoplay=1&mute=1&loop=1&playlist=$videoId" +
        "&controls=0&playsinline=1&rel=0&origin=https%3A%2F%2Fahui3c.com"

internal fun youtubeRequestHeaders(): Map<String, String> = mapOf(
    "Referer" to "$YOUTUBE_CLIENT_ORIGIN/"
)

internal fun youtubePlayerErrorMessage(code: Int): String = when (code) {
    2 -> "YouTube 錯誤 2：影片網址或參數無效"
    5 -> "YouTube 錯誤 5：HTML5 播放器無法播放"
    100 -> "YouTube 錯誤 100：影片不存在或設為私人"
    101, 150 -> "YouTube 錯誤 $code：影片擁有者禁止嵌入播放"
    153 -> "YouTube 錯誤 153：缺少 Referer 或客戶端識別"
    else -> "YouTube 播放器錯誤 $code"
}

private const val YOUTUBE_CLIENT_ORIGIN = "https://ahui3c.com"
internal const val YOUTUBE_LAYOUT_FIX = """
    (function() {
      var id = 'ahuimark-youtube-layout';
      var style = document.getElementById(id);
      if (!style) {
        style = document.createElement('style');
        style.id = id;
        style.textContent =
          'html,body{position:fixed!important;inset:0!important;width:100vw!important;height:100vh!important;' +
          'min-width:100vw!important;min-height:100vh!important;margin:0!important;padding:0!important;' +
          'overflow:hidden!important;background:#000!important;}' +
          '#player,.html5-video-player,.html5-video-container{position:fixed!important;inset:0!important;' +
          'width:100vw!important;height:100vh!important;min-width:100vw!important;min-height:100vh!important;' +
          'max-width:none!important;max-height:none!important;margin:0!important;background:#000!important;}' +
          'video.html5-main-video{position:fixed!important;inset:0!important;width:100vw!important;' +
          'height:100vh!important;min-width:100vw!important;min-height:100vh!important;' +
          'object-fit:cover!important;background:#000!important;}';
        (document.head || document.documentElement).appendChild(style);
      }
      document.documentElement.style.backgroundColor = '#000';
      if (document.body) document.body.style.backgroundColor = '#000';
      // WebView 151 on Android 17 can resolve 100vh to 0px inside a YouTube
      // embed even while window.innerHeight is valid. Apply measured CSS-pixel
      // dimensions inline to avoid that circular root-height calculation.
      var viewportWidth = Math.max(window.innerWidth || 0, document.documentElement.clientWidth || 0) + 'px';
      var viewportHeight = Math.max(window.innerHeight || 0, document.documentElement.clientHeight || 0) + 'px';
      [
        document.documentElement,
        document.body,
        document.querySelector('#player'),
        document.querySelector('.html5-video-player'),
        document.querySelector('.html5-video-container'),
        document.querySelector('video.html5-main-video')
      ].filter(Boolean).forEach(function(element) {
        element.style.setProperty('width', viewportWidth, 'important');
        element.style.setProperty('height', viewportHeight, 'important');
        element.style.setProperty('min-width', viewportWidth, 'important');
        element.style.setProperty('min-height', viewportHeight, 'important');
      });
      return 'LAYOUT_FIXED';
    })();
"""
private const val YOUTUBE_PLAYBACK_PROBE = YOUTUBE_LAYOUT_FIX + """
    (function() {
      var video = document.querySelector('video');
      var text = (document.body && document.body.innerText || '');
      if (video) {
        video.style.backgroundColor = '#000';
        video.style.visibility = 'visible';
        video.style.opacity = '1';
        video.style.transform = 'translateZ(0)';
        video.muted = true;
        if (video.paused) video.play().catch(function() {});
      }
      if (video && video.readyState >= 2 && video.videoWidth > 0) return 'PLAYING';
      if (text.indexOf('153') >= 0 || text.indexOf('Referer') >= 0) return 'ERROR_153';
      if (text.indexOf('無法播放') >= 0 || text.indexOf('unavailable') >= 0) return 'ERROR_UNAVAILABLE';
      return 'LOADING';
    })();
"""

@Composable
private fun CameraWorkload(lensFacing: Int, quality: Quality, elapsedMs: Long) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val controller = remember(lensFacing, quality) { CameraWorkloadController(context, lifecycleOwner, lensFacing, quality) }
    DisposableEffect(controller) {
        controller.start()
        onDispose { controller.stop() }
    }
    Box(Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { controller.previewView }
        )
        CameraRecordingHud(elapsedMs)
    }
}

@Composable
private fun BoxScope.CameraRecordingHud(elapsedMs: Long) {
    Canvas(Modifier.fillMaxSize()) {
        val top = size.height * .20f
        val bottom = size.height * .78f
        val gridColor = Color.White.copy(alpha = .22f)
        for (part in 1..2) {
            val x = size.width * part / 3f
            drawLine(gridColor, Offset(x, top), Offset(x, bottom), 1.2f)
            val y = top + (bottom - top) * part / 3f
            drawLine(gridColor, Offset(0f, y), Offset(size.width, y), 1.2f)
        }
        val cx = size.width / 2f
        val cy = size.height / 2f
        val half = size.minDimension * .075f
        val corner = half * .38f
        val focus = Color(0xFFE9FF68).copy(alpha = .92f)
        listOf(
            Offset(cx - half, cy - half) to Offset(cx - half + corner, cy - half),
            Offset(cx - half, cy - half) to Offset(cx - half, cy - half + corner),
            Offset(cx + half, cy - half) to Offset(cx + half - corner, cy - half),
            Offset(cx + half, cy - half) to Offset(cx + half, cy - half + corner),
            Offset(cx - half, cy + half) to Offset(cx - half + corner, cy + half),
            Offset(cx - half, cy + half) to Offset(cx - half, cy + half - corner),
            Offset(cx + half, cy + half) to Offset(cx + half - corner, cy + half),
            Offset(cx + half, cy + half) to Offset(cx + half, cy + half - corner)
        ).forEach { (start, end) -> drawLine(focus, start, end, 3f) }
        drawCircle(focus, radius = 3.5f, center = Offset(cx, cy))
        drawLine(Color.White.copy(alpha = .72f), Offset(cx - 42f, bottom + 13f), Offset(cx + 42f, bottom + 13f), 2f)
        drawCircle(focus, radius = 3f, center = Offset(cx, bottom + 13f))
    }

    Row(
        Modifier.align(Alignment.TopStart).padding(start = 14.dp, top = 82.dp)
            .background(Color.Black.copy(alpha = .34f), RoundedCornerShape(5.dp)).padding(horizontal = 9.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(9.dp).height(9.dp).background(Color(0xFFFF3B3B), CircleShape))
        Spacer(Modifier.width(7.dp))
        Text("REC", color = Color.White, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        Spacer(Modifier.width(10.dp))
        Text(formatHudTime(elapsedMs), color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
    }
    Column(
        Modifier.align(Alignment.CenterEnd).padding(end = 14.dp)
            .background(Color.Black.copy(alpha = .28f), RoundedCornerShape(5.dp)).padding(horizontal = 9.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.End
    ) {
        Text("4K  30P", color = Color.White, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        Text("H.264", color = Color.White.copy(alpha = .82f), fontFamily = FontFamily.Monospace, fontSize = 9.sp)
        Text("AF-C  AUTO", color = Color(0xFFE9FF68), fontFamily = FontFamily.Monospace, fontSize = 9.sp)
    }
    Row(
        Modifier.align(Alignment.BottomCenter).padding(bottom = 76.dp)
            .background(Color.Black.copy(alpha = .30f), RoundedCornerShape(5.dp)).padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Text("ISO AUTO", color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
        Text("1/60", color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
        Text("EV  0.0", color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
        Text("WB AUTO", color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
    }
}

internal fun formatHudTime(elapsedMs: Long): String {
    val totalSeconds = elapsedMs.coerceAtLeast(0L) / 1_000L
    return "%02d:%02d".format(totalSeconds / 60L, totalSeconds % 60L)
}


private const val WEB_AUTO_SCROLL_SCRIPT = """
(() => {
  if (window.__ahuimarkScrollRunning) return;
  window.__ahuimarkScrollRunning = true;
  const started = performance.now();
  function frame(now) {
    const total = Math.max(0, document.documentElement.scrollHeight - innerHeight);
    const progress = Math.min(1, (now - started) / 50000);
    scrollTo(0, total * progress);
    if (progress >= 1) { setTimeout(() => location.reload(), 700); return; }
    requestAnimationFrame(frame);
  }
  requestAnimationFrame(frame);
})();
"""

private const val OFFLINE_WEB_URL = "file:///android_asset/offline_web_workload.html"
