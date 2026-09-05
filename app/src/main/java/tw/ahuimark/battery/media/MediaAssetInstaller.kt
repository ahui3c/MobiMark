package tw.ahuimark.battery.media

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.DigestInputStream
import java.security.MessageDigest

class MediaAssetInstaller(private val context: Context) {
    suspend fun download(onProgress: (Float) -> Unit): Result<File> = runCatching {
        val directory = File(context.filesDir, "media").apply { mkdirs() }
        val target = File(directory, VideoMaterial.FILE_NAME)
        val temporary = File(directory, "${VideoMaterial.FILE_NAME}.downloading")
        temporary.delete()

        val connection = (URL(VideoMaterial.DOWNLOAD_URL).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 30_000
            readTimeout = 30_000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "Ahuimark/0.1 Android")
        }

        try {
            connection.connect()
            require(connection.responseCode in 200..299) {
                "下載伺服器回應 ${connection.responseCode}"
            }
            val contentLength = connection.contentLengthLong.takeIf { it > 0 }
                ?: VideoMaterial.EXPECTED_SIZE_BYTES
            val digest = MessageDigest.getInstance("SHA-256")
            var copied = 0L
            var lastProgressNs = 0L
            DigestInputStream(connection.inputStream.buffered(), digest).use { input ->
                temporary.outputStream().buffered().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 8)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        copied += count
                        val now = System.nanoTime()
                        if (now - lastProgressNs >= 200_000_000L) {
                            onProgress((copied.toDouble() / contentLength).toFloat().coerceIn(0f, 1f))
                            lastProgressNs = now
                        }
                    }
                }
            }
            val hash = digest.digest().joinToString("") { "%02X".format(it) }
            require(temporary.length() == VideoMaterial.EXPECTED_SIZE_BYTES) {
                "影片大小不符，請重新下載"
            }
            require(hash == VideoMaterial.EXPECTED_SHA256) {
                "影片校驗失敗，請重新下載"
            }
            if (target.exists()) require(target.delete()) { "無法替換舊影片" }
            require(temporary.renameTo(target)) { "無法安裝測試影片" }
            onProgress(1f)
            target
        } catch (error: Throwable) {
            temporary.delete()
            throw error
        } finally {
            connection.disconnect()
        }
    }
}
