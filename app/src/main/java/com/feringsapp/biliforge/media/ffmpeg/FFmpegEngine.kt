package com.feringsapp.biliforge.media.ffmpeg

import com.feringsapp.biliforge.core.log.ForgeLogger
import com.feringsapp.biliforge.core.shizuku.IRemoteService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** 转码预设（需要 ffmpeg 带对应编码器：libx264 / libx265 / libmp3lame） */
data class TranscodePreset(
    val id: String,
    val label: String,
    val desc: String,
    val videoArgs: List<String>,
    val audioArgs: List<String>,
) {
    val audioOnly: Boolean get() = videoArgs.contains("-vn")
}

object TranscodePresets {
    val P720 = TranscodePreset(
        "720p", "720P · H.264", "均衡画质",
        listOf("-vf", "scale=-2:720", "-c:v", "libx264", "-preset", "veryfast", "-crf", "21"),
        listOf("-c:a", "aac", "-b:a", "320k", "-ar", "48000"),
    )
    val P1080 = TranscodePreset(
        "1080p", "1080P · H.264", "高画质",
        listOf("-vf", "scale=-2:1080", "-c:v", "libx264", "-preset", "veryfast", "-crf", "20"),
        listOf("-c:a", "aac", "-b:a", "320k", "-ar", "48000"),
    )
    val HEVC = TranscodePreset(
        "hevc", "H.265 高压缩", "同画质体积更小",
        listOf("-c:v", "libx265", "-preset", "veryfast", "-crf", "24", "-tag:v", "hvc1"),
        listOf("-c:a", "aac", "-b:a", "320k", "-ar", "48000"),
    )
    val AUDIO_M4A = TranscodePreset(
        "m4a", "音频 · M4A（无损提取）", "直接复制音轨，采样率保持源最高",
        listOf("-vn"), listOf("-c:a", "copy"),
    )
    val AUDIO_MP3 = TranscodePreset(
        "mp3", "音频 · MP3 320k", "最高码率 · 48kHz",
        listOf("-vn"), listOf("-c:a", "libmp3lame", "-b:a", "320k", "-ar", "48000"),
    )

    val ALL = listOf(P720, P1080, HEVC, AUDIO_M4A, AUDIO_MP3)
}

/**
 * ffmpeg 执行引擎（运行在 Shizuku shell 侧）：
 *  - mergeJob       : video.m4s + audio.m4s（±多分片）→ output.mp4（-c copy 不重编码）
 *  - transcodeJob   : 任意转码预设
 *  - 进度来自 -progress 文件；日志来自 ffmpeg stderr，App 侧增量读取
 */
class FFmpegEngine(private val remote: IRemoteService) {

    class Job(
        val argv: List<String>,
        val logPath: String,
        val progressPath: String,
        val outputPath: String,
        val preCommands: List<List<String>> = emptyList(),
    )

    data class Snapshot(
        val processedUs: Long = 0L,
        val totalUs: Long = 0L,
        val speed: Double = 0.0,
        val finished: Boolean = false,
    ) {
        val fraction: Float
            get() = if (totalUs > 0) {
                (processedUs.toFloat() / totalUs.toFloat()).coerceIn(0f, 1f)
            } else if (finished) 1f else 0f
    }

    data class RunResult(val exitCode: Int, val logTail: String) {
        val ok: Boolean get() = exitCode == 0
    }

    // ---------------- 命令构建 ----------------

    /** 合成：多个输入 → -c copy 单文件 */
    fun mergeJob(
        id: String,
        videoPaths: List<String>,
        audioPaths: List<String>,
        output: String,
    ): Job {
        require(videoPaths.isNotEmpty() || audioPaths.isNotEmpty()) { "没有媒体流" }
        val argv = ArrayList<String>(32)
        val pre = ArrayList<List<String>>()
        argv += FfPaths.FFMPEG
        argv += listOf("-hide_banner", "-y")

        var inputIndex = 0
        var vMap: String? = null
        var aMap: String? = null

        when (videoPaths.size) {
            0 -> Unit
            1 -> {
                argv += listOf("-i", videoPaths[0])
                vMap = "${inputIndex++}:v:0"
            }
            else -> {
                val list = "${FfPaths.WORK_DIR}/$id.v.list"
                pre += concatListCommand(list, videoPaths)
                argv += listOf("-f", "concat", "-safe", "0", "-i", list)
                vMap = "${inputIndex++}:v:0"
            }
        }
        when (audioPaths.size) {
            0 -> Unit
            1 -> {
                argv += listOf("-i", audioPaths[0])
                aMap = "${inputIndex++}:a:0"
            }
            else -> {
                val list = "${FfPaths.WORK_DIR}/$id.a.list"
                pre += concatListCommand(list, audioPaths)
                argv += listOf("-f", "concat", "-safe", "0", "-i", list)
                aMap = "${inputIndex++}:a:0"
            }
        }
        vMap?.let { argv += listOf("-map", it) }
        aMap?.let { argv += listOf("-map", it) }
        argv += listOf("-c", "copy")
        if (!output.endsWith(".flv", true)) {
            argv += listOf("-movflags", "+faststart")
        }
        argv += listOf("-progress", progressPath(id))
        argv += output

        return Job(argv, logPath(id), progressPath(id), output, pre)
    }

    /** 自定义参数的任务（高级用法：legacy 拼接 remux 等） */
    fun jobWith(
        id: String,
        args: List<String>,
        output: String,
        preCommands: List<List<String>> = emptyList(),
    ): Job {
        val argv = ArrayList<String>(16)
        argv += FfPaths.FFMPEG
        argv += "-hide_banner"
        argv += "-y"
        argv += args
        argv += listOf("-progress", progressPath(id))
        argv += output
        return Job(argv, logPath(id), progressPath(id), output, preCommands)
    }

    /** 转码（或仅音频提取，传入 AUDIO_* 预设即可） */
    fun transcodeJob(
        id: String,
        input: String,
        output: String,
        preset: TranscodePreset,
    ): Job {
        val argv = ArrayList<String>(24)
        argv += FfPaths.FFMPEG
        argv += listOf("-hide_banner", "-y", "-i", input)
        argv += preset.videoArgs
        argv += preset.audioArgs
        argv += listOf("-movflags", "+faststart")
        argv += listOf("-progress", progressPath(id))
        argv += output
        return Job(argv, logPath(id), progressPath(id), output)
    }

    // ---------------- 执行 ----------------

    suspend fun ensureLayout() = withContext(Dispatchers.IO) {
        remote.mkdirs(FfPaths.BIN_DIR)
        remote.mkdirs(FfPaths.WORK_DIR)
        remote.mkdirs(FfPaths.LOG_DIR)
        remote.mkdirs(FfPaths.DEFAULT_OUTPUT_DIR)
        Unit
    }

    /**
     * 挂起执行直到进程结束；期间持续回调进度与日志。
     * 协程被取消时会自动 kill 掉 ffmpeg 进程。
     */
    suspend fun run(
        job: Job,
        pollIntervalMs: Long = 400,
        onSnapshot: (Snapshot) -> Unit = {},
        onLogLine: ((String) -> Unit)? = null,
    ): RunResult = withContext(Dispatchers.IO) {
        ensureLayout()
        runCatching { remote.deleteRecursive(job.logPath) }
        runCatching { remote.deleteRecursive(job.progressPath) }

        // 前置命令（如生成 concat 列表）
        for (cmd in job.preCommands) {
            runCatching { remote.spawn(cmd.toTypedArray(), FfPaths.WORK_DIR, null) }
        }

        ForgeLogger.sys("FFmpeg", "执行: " + job.argv.joinToString(" "), 2)
        val handle = remote.spawn(job.argv.toTypedArray(), FfPaths.BIN_DIR, job.logPath)
        if (handle < 0) {
            return@withContext RunResult(-1, "无法启动 ffmpeg：二进制缺失或不可执行")
        }

        var logOffset = 0L
        var progOffset = 0L
        var snap = Snapshot()

        try {
            while (true) {
                currentCoroutineContext().ensureActive()

                readNew(job.progressPath, progOffset)?.let { (off, text) ->
                    progOffset = off
                    snap = parseProgress(text, snap)
                }
                readNew(job.logPath, logOffset)?.let { (off, text) ->
                    logOffset = off
                    parseDuration(text)?.let { d -> if (d > snap.totalUs) snap = snap.copy(totalUs = d) }
                    onLogLine?.let { cb -> text.lineSequence().forEach(cb) }
                }
                onSnapshot(snap)

                if (!remote.processAlive(handle)) break
                delay(pollIntervalMs)
            }

            // 收尾：把最后增量读干净
            readNew(job.progressPath, progOffset)?.let { (_, text) -> snap = parseProgress(text, snap) }
            readNew(job.logPath, logOffset)?.let { (_, text) ->
                parseDuration(text)?.let { d -> if (d > snap.totalUs) snap = snap.copy(totalUs = d) }
                onLogLine?.let { cb -> text.lineSequence().forEach(cb) }
            }

            val code = remote.processExitCode(handle)
            ForgeLogger.sys("FFmpeg", "进程退出 exit=$code", 1)
            onSnapshot(snap.copy(finished = true))
            RunResult(code, readTail(job.logPath, 8 shl 10))
        } catch (t: Throwable) {
            runCatching { remote.killProcess(handle) }
            throw t
        }
    }

    // ---------------- 内部工具 ----------------

    private fun concatListCommand(listPath: String, paths: List<String>): List<String> {
        val sb = StringBuilder("rm -f '").append(listPath).append("'; ")
        for (p in paths) {
            require(!p.contains('\'') && !p.contains('\n')) { "路径包含非法字符: $p" }
            sb.append("echo \"file '").append(p).append("'\" >> '").append(listPath).append("'; ")
        }
        return listOf("sh", "-c", sb.toString())
    }

    private fun logPath(id: String) = "${FfPaths.LOG_DIR}/$id.log"

    private fun progressPath(id: String) = "${FfPaths.LOG_DIR}/$id.progress"

    /** 增量读取：返回 (新偏移, 内容)；文件不存在返回 null */
    private fun readNew(path: String, offset: Long): Pair<Long, String>? {
        val raw = runCatching { remote.readTextFileFrom(path, offset, 128 shl 10) }.getOrNull() ?: return null
        val sep = raw.indexOf('\u0001')
        if (sep < 0) return null
        val newOffset = raw.substring(0, sep).toLongOrNull() ?: return null
        return newOffset to raw.substring(sep + 1)
    }

    private fun readTail(path: String, maxBytes: Int): String {
        val size = runCatching { remote.fileSize(path) }.getOrDefault(-1L)
        if (size <= 0) return ""
        val offset = (size - maxBytes).coerceAtLeast(0L)
        val raw = runCatching { remote.readTextFileFrom(path, offset, maxBytes) }.getOrNull() ?: return ""
        val sep = raw.indexOf('\u0001')
        return if (sep >= 0) raw.substring(sep + 1) else raw
    }

    private fun parseProgress(text: String, state: Snapshot): Snapshot {
        var s = state
        for (line in text.lineSequence()) {
            val idx = line.indexOf('=')
            if (idx <= 0) continue
            val key = line.substring(0, idx).trim()
            val value = line.substring(idx + 1).trim()
            when (key) {
                // 注意：ffmpeg 的 out_time_ms 实际单位也是微秒（历史遗留）
                "out_time_us", "out_time_ms" ->
                    value.toLongOrNull()?.let { if (it > s.processedUs) s = s.copy(processedUs = it) }
                "speed" -> value.removeSuffix("x").trim().toDoubleOrNull()
                    ?.let { s = s.copy(speed = it) }
                "progress" -> if (value == "end") s = s.copy(finished = true)
            }
        }
        return s
    }

    private val durationRegex = Regex("""Duration:\s*(\d+):(\d{1,2}):(\d{1,2}(?:\.\d+)?)""")

    private fun parseDuration(text: String): Long? {
        val m = durationRegex.find(text) ?: return null
        val h = m.groupValues[1].toLongOrNull() ?: return null
        val min = m.groupValues[2].toLongOrNull() ?: return null
        val sec = m.groupValues[3].toDoubleOrNull() ?: return null
        return (h * 3600 + min * 60) * 1_000_000L + (sec * 1_000_000).toLong()
    }
}
