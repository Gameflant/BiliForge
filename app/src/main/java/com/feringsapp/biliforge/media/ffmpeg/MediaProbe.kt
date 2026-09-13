package com.feringsapp.biliforge.media.ffmpeg

import com.feringsapp.biliforge.core.log.ForgeLogger
import com.feringsapp.biliforge.core.shizuku.IRemoteService
import com.feringsapp.biliforge.data.model.MediaInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 用 ffmpeg 读取媒体文件头（不解码），解析时长与分辨率。
 * 单次调用约 50~200ms，用于扫描后异步填充缓存卡片信息。
 */
object MediaProbe {

    private val DURATION = Regex("""Duration:\s*(\d+):(\d{2}):(\d{2}(?:\.\d+)?)""")
    private val VIDEO_SIZE = Regex("""Video:[^\n]*?(\d{2,5})x(\d{2,5})""")

    suspend fun probe(remote: IRemoteService, path: String): MediaInfo? = withContext(Dispatchers.IO) {
        val log = "${FfPaths.LOG_DIR}/probe-${System.nanoTime()}.log"
        try {
            runCatching { remote.deleteRecursive(log) }
            val handle = remote.spawn(
                arrayOf(FfPaths.FFMPEG, "-hide_banner", "-i", path),
                FfPaths.BIN_DIR,
                log,
            )
            if (handle < 0) return@withContext null
            val deadline = System.currentTimeMillis() + 5000
            while (remote.processAlive(handle) && System.currentTimeMillis() < deadline) {
                Thread.sleep(60)
            }
            runCatching { remote.killProcess(handle) }
            val text = runCatching { remote.readTextFile(log, 256 shl 10) }.getOrNull()
                ?: return@withContext null
            runCatching { remote.deleteRecursive(log) }

            val durationMs = DURATION.find(text)?.let {
                val hh = it.groupValues[1].toLongOrNull() ?: 0L
                val mm = it.groupValues[2].toLongOrNull() ?: 0L
                val ss = it.groupValues[3].toDoubleOrNull() ?: 0.0
                (hh * 3600 + mm * 60) * 1000L + (ss * 1000).toLong()
            } ?: 0L

            val size = VIDEO_SIZE.find(text)
            val w = size?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
            val h = size?.groupValues?.getOrNull(2)?.toIntOrNull() ?: 0

            val info = if (durationMs <= 0 && w <= 0) null else MediaInfo(durationMs, w, h)
            info?.let { ForgeLogger.ui2("Probe", "$path → ${it.durationText} ${it.resolutionText}") }
            info
        } catch (t: Throwable) {
            null
        }
    }
}
