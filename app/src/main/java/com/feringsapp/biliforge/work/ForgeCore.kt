package com.feringsapp.biliforge.work

import com.feringsapp.biliforge.core.log.ForgeLogger
import android.content.Context
import android.util.Log
import com.feringsapp.biliforge.core.shizuku.IRemoteService
import com.feringsapp.biliforge.core.shizuku.RemoteFs
import com.feringsapp.biliforge.core.shizuku.ShizukuBridge
import com.feringsapp.biliforge.data.settings.AppSettings
import com.feringsapp.biliforge.data.model.CacheBundle
import com.feringsapp.biliforge.media.danmaku.AssWriter
import com.feringsapp.biliforge.media.danmaku.DanmakuParser
import com.feringsapp.biliforge.media.danmaku.SrtWriter
import com.feringsapp.biliforge.media.ffmpeg.FFmpegBin
import com.feringsapp.biliforge.media.ffmpeg.FFmpegEngine
import com.feringsapp.biliforge.media.ffmpeg.FfPaths
import com.feringsapp.biliforge.media.ffmpeg.TranscodePreset
import com.feringsapp.biliforge.media.ffmpeg.TranscodePresets
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

enum class DanmakuFormat(val ext: String, val label: String) {
    ASS("ass", "ASS 字幕"),
    SRT("srt", "SRT 字幕"),
}

/**
 * 任务引擎（单例）：
 *  - 负责把「扫描结果」编排成 ffmpeg 命令并执行
 *  - 对外暴露任务流（UI 订阅）、取消、清理
 */
object ForgeCore {

    private const val TAG = "BiliForge/Core"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _tasks = MutableStateFlow<List<ForgeTask>>(emptyList())
    val tasks: StateFlow<List<ForgeTask>> = _tasks.asStateFlow()

    private val jobs = ConcurrentHashMap<String, Job>()
    private val gate = Semaphore(2)

    private var appContext: Context? = null

    fun attach(context: Context) {
        appContext = context.applicationContext
    }

    fun cancel(id: String) {
        jobs[id]?.cancel()
    }

    fun clearFinished() {
        _tasks.update { list -> list.filter { it.isActive } }
    }

    // ---------------- 任务入口 ----------------

    /** 合成：把 video.m4s + audio.m4s（或旧版 .blv 分片）导出为 MP4（输出到 Movies/BiliForge） */
    fun enqueueMerge(bundle: CacheBundle) {
        val item = bundle.item
        val id = newId()
        val out = "${outDir()}/[BiliForge]${OutputNaming.sanitize(item.title)}_" +
            "${OutputNaming.sanitize(item.partTitle)}_$id.mp4"

        submit(ForgeTask(id, TaskType.MERGE, item.displayName, bundle.summary)) { _, svc ->
            val engine = FFmpegEngine(svc)
            val job = if (bundle.hasDash) {
                engine.mergeJob(
                    id = id,
                    videoPaths = bundle.videoTracks.map { it.path },
                    audioPaths = bundle.audioTracks.map { it.path },
                    output = out,
                )
            } else {
                val concatFlv = "${FfPaths.WORK_DIR}/$id.legacy.flv"
                val pre = legacyConcatCommand(bundle.legacySegments.map { it.path }, concatFlv)
                engine.jobWith(
                    id = id,
                    args = listOf("-i", concatFlv, "-c", "copy", "-movflags", "+faststart"),
                    output = out,
                    preCommands = listOf(pre),
                )
            }
            val r = engine.run(job, onSnapshot = { snap ->
                update(id) { it.copy(progress = snap.fraction, speed = snap.speed, note = noteOf(snap)) }
            })
            if (!r.ok) throw IllegalStateException("ffmpeg(exit=${r.exitCode}) ${r.logTail.takeLast(240)}")
            out
        }
    }

    /** 转码（对已产出的 mp4，或任意可读路径） */
    fun enqueueTranscode(input: String, preset: TranscodePreset, title: String) {
        val id = newId()
        val ext = when (preset.id) {
            "m4a" -> "m4a"
            "mp3" -> "mp3"
            else -> "mp4"
        }
        val out = "${outDir()}/[BiliForge]${OutputNaming.sanitize(title)}_${preset.id}_$id.$ext"
        submit(ForgeTask(id, TaskType.TRANSCODE, title, preset.label)) { _, svc ->
            val engine = FFmpegEngine(svc)
            val job = engine.transcodeJob(id, input, out, preset)
            val r = engine.run(job, onSnapshot = { snap ->
                update(id) { it.copy(progress = snap.fraction, speed = snap.speed, note = noteOf(snap)) }
            })
            if (!r.ok) throw IllegalStateException("ffmpeg(exit=${r.exitCode}) ${r.logTail.takeLast(240)}")
            out
        }
    }

    /** 提取音频（直接读 audio.m4s，不经过合成） */
    fun enqueueExtractAudio(bundle: CacheBundle, mp3: Boolean) {
        val audio = bundle.audioTracks.firstOrNull()?.path
        if (audio == null) {
            failFast(TaskType.EXTRACT_AUDIO, bundle.item.displayName, "该缓存没有音频轨")
            return
        }
        val id = newId()
        val preset = if (mp3) TranscodePresets.AUDIO_MP3 else TranscodePresets.AUDIO_M4A
        val out = "${outDir()}/[BiliForge]${OutputNaming.sanitize(bundle.item.displayName)}_" +
            "$id.${if (mp3) "mp3" else "m4a"}"
        submit(ForgeTask(id, TaskType.EXTRACT_AUDIO, bundle.item.displayName, preset.label)) { _, svc ->
            val engine = FFmpegEngine(svc)
            val job = engine.transcodeJob(id, audio, out, preset)
            val r = engine.run(job, onSnapshot = { snap ->
                update(id) { it.copy(progress = snap.fraction, speed = snap.speed, note = noteOf(snap)) }
            })
            if (!r.ok) throw IllegalStateException("ffmpeg(exit=${r.exitCode}) ${r.logTail.takeLast(240)}")
            out
        }
    }

    /** 直接对缓存转码（跳过合成中间产物）：m4s → 目标规格 一条命令完成 */
    fun enqueueTranscodeBundle(bundle: CacheBundle, preset: TranscodePreset) {
        val item = bundle.item
        val id = newId()
        val ext = when (preset.id) {
            "m4a" -> "m4a"
            "mp3" -> "mp3"
            else -> "mp4"
        }
        val out = "${outDir()}/[BiliForge]${OutputNaming.sanitize(item.displayName)}_" +
            "${preset.id}_$id.$ext"
        submit(ForgeTask(id, TaskType.TRANSCODE, item.displayName, preset.label)) { _, svc ->
            val engine = FFmpegEngine(svc)
            val job = if (bundle.hasDash) {
                val args = ArrayList<String>(24)
                bundle.videoTracks.forEach { args += listOf("-i", it.path) }
                bundle.audioTracks.forEach { args += listOf("-i", it.path) }
                if (bundle.videoTracks.isNotEmpty() && !preset.audioOnly) {
                    args += listOf("-map", "0:v:0")
                }
                if (bundle.audioTracks.isNotEmpty()) {
                    args += listOf("-map", "${bundle.videoTracks.size}:a:0")
                }
                args += preset.videoArgs
                args += preset.audioArgs
                if (!preset.audioOnly) args += listOf("-movflags", "+faststart")
                engine.jobWith(id, args, out)
            } else {
                val concatFlv = "${FfPaths.WORK_DIR}/$id.legacy.flv"
                val pre = legacyConcatCommand(bundle.legacySegments.map { it.path }, concatFlv)
                val args = ArrayList<String>(16)
                args += listOf("-i", concatFlv)
                args += preset.videoArgs
                args += preset.audioArgs
                if (!preset.audioOnly) args += listOf("-movflags", "+faststart")
                engine.jobWith(id, args, out, preCommands = listOf(pre))
            }
            val r = engine.run(job, onSnapshot = { snap ->
                update(id) { it.copy(progress = snap.fraction, speed = snap.speed, note = noteOf(snap)) }
            })
            if (!r.ok) throw IllegalStateException("ffmpeg(exit=${r.exitCode}) ${r.logTail.takeLast(240)}")
            out
        }
    }

    /** 提取弹幕（danmaku.xml → ASS / SRT） */
    fun enqueueExtractDanmaku(bundle: CacheBundle, format: DanmakuFormat) {
        val xmlPath = bundle.danmakuPath
        if (xmlPath == null) {
            failFast(TaskType.EXTRACT_DANMAKU, bundle.item.displayName, "该缓存没有 danmaku.xml")
            return
        }
        val id = newId()
        val out = "${outDir()}/[BiliForge]${OutputNaming.sanitize(bundle.item.displayName)}_" +
            "$id.${format.ext}"
        submit(ForgeTask(id, TaskType.EXTRACT_DANMAKU, bundle.item.displayName, format.label)) { fs, _ ->
            val xml = fs.readText(xmlPath, 64 shl 20) ?: throw IllegalStateException("弹幕读取失败")
            update(id) { it.copy(progress = 0.35f, note = "解析中…") }
            val list = xml.byteInputStream(Charsets.UTF_8).use { DanmakuParser.parse(it) }
            update(id) { it.copy(progress = 0.7f, note = "${list.size} 条弹幕") }
            val content = when (format) {
                DanmakuFormat.ASS -> AssWriter.render(list, title = bundle.item.title)
                DanmakuFormat.SRT -> SrtWriter.render(list)
            }
            if (!fs.writeText(out, content)) throw IllegalStateException("写入失败: $out")
            out
        }
    }

    /** 安装 ffmpeg 工具链：优先 APK 内置引擎，其次 /sdcard/BiliForge/tools 投放区 */
    fun enqueueInstallFfmpeg() {
        val id = newId()
        submit(ForgeTask(id, TaskType.INSTALL_FFMPEG, "安装 FFmpeg 工具链", "内置引擎 / 投放包")) { fs, svc ->
            val bin = FFmpegBin(svc, fs)
            val ctx = appContext

            var installed = false
            if (ctx != null) {
                val hasAsset = FFmpegBin.hasAsset(ctx.assets)
                if (hasAsset) {
                    update(id) { it.copy(progress = 0.15f, note = "解压内置引擎…") }
                    val r = bin.installFromAssets(ctx.assets)
                    installed = r.ok
                    if (r.ok) {
                        update(id) { it.copy(progress = 0.8f, note = r.message) }
                    } else {
                        Log.w(TAG, "asset install failed: ${r.message}")
                    }
                }
            }

            if (!installed) {
                update(id) { it.copy(progress = 0.2f, note = "从 ${FfPaths.STAGING_DIR} 安装…") }
                val r = bin.installFromStaging()
                if (!r.ok) throw IllegalStateException(r.message)
                update(id) { it.copy(progress = 0.8f, note = r.message) }
            }

            bin.probeVersion() ?: "(已安装，版本探测超时)"
        }
    }

    /**
     * 自动安装（幂等）：Shizuku 就绪且引擎缺失时，只要存在内置包/投放包就自动装一次。
     * 由首页在 READY 状态下调用。
     */
    private val autoInstallTicket = java.util.concurrent.atomic.AtomicBoolean(false)

    fun maybeAutoInstall() {
        val ctx = appContext ?: return
        if (!autoInstallTicket.compareAndSet(false, true)) return
        scope.launch {
            val svc = ShizukuBridge.service.value ?: run {
                autoInstallTicket.set(false)
                return@launch
            }
            val fs = RemoteFs(svc)
            val bin = FFmpegBin(svc, fs)
            if (bin.isInstalled()) return@launch
            val hasAsset = FFmpegBin.hasAsset(ctx.assets)
            val hasStaging = fs.exists(FfPaths.STAGING_FFMPEG) ||
                fs.exists(FfPaths.STAGING_ARCHIVE) ||
                fs.exists(FfPaths.STAGING_ARCHIVE_ALT)
            if (hasAsset || hasStaging) {
                enqueueInstallFfmpeg()
            } else {
                autoInstallTicket.set(false)
            }
        }
    }

    /**
     * 手动导出：把已完成任务的产物流式拷贝到用户通过系统文件选择器（SAF）指定的位置。
     * 数据流：shell 侧 readChunk → App 侧写入 content URI。
     */
    fun enqueueExport(remotePath: String, targetUri: android.net.Uri, displayName: String) {
        val id = newId()
        submit(ForgeTask(id, TaskType.EXPORT, displayName, "导出到所选位置")) { fs, svc ->
            val ctx = appContext ?: throw IllegalStateException("应用上下文缺失")
            val total = fs.size(remotePath)
            if (total <= 0L) throw IllegalStateException("源文件不存在：$remotePath")
            val out = ctx.contentResolver.openOutputStream(targetUri, "wt")
                ?: throw IllegalStateException("无法写入所选位置")
            out.use { os ->
                var offset = 0L
                var lastTick = 0L
                while (offset < total) {
                    val bytes = svc.readChunk(remotePath, offset, 192 * 1024) ?: break
                    if (bytes.isEmpty()) break
                    os.write(bytes)
                    offset += bytes.size
                    val now = System.currentTimeMillis()
                    if (now - lastTick > 150 || offset >= total) {
                        lastTick = now
                        val mb = offset / 1048576.0
                        val totalMb = total / 1048576.0
                        update(id) {
                            it.copy(
                                progress = (offset.toFloat() / total).coerceIn(0f, 1f),
                                note = String.format(java.util.Locale.US, "%.1f / %.1f MB", mb, totalMb),
                            )
                        }
                    }
                }
                os.flush()
            }
            targetUri.toString()
        }
    }

    // ---------------- 内部 ----------------

    private fun submit(task: ForgeTask, block: suspend (RemoteFs, IRemoteService) -> String) {
        _tasks.update { it + task }
        ForgeLogger.task("Core", "任务入队 [${task.type.label}] ${task.title}")
        appContext?.let { ForgeTaskService.ensure(it) }

        val job = scope.launch {
            gate.withPermit {
                val svc = ShizukuBridge.service.value
                if (svc == null) {
                    ForgeLogger.task("Core", "任务失败 ${task.id}: Shizuku 服务未连接")
                    update(task.id) {
                        it.copy(status = ForgeTask.Status.FAILED, error = "Shizuku 文件服务未连接（先在主页授权）")
                    }
                    return@withPermit
                }
                update(task.id) { it.copy(status = ForgeTask.Status.RUNNING, note = "启动…") }
                ForgeLogger.task("Core", "开始执行 ${task.id} [${task.type.label}]")
                runCatching { svc.mkdirs(outDir()) }
                try {
                    val outPath = block(RemoteFs(svc), svc)
                    ForgeLogger.task("Core", "完成 ${task.id} → $outPath")
                    update(task.id) {
                        it.copy(status = ForgeTask.Status.DONE, progress = 1f, outputPath = outPath, note = "完成")
                    }
                } catch (c: CancellationException) {
                    ForgeLogger.task("Core", "取消 ${task.id}")
                    update(task.id) { it.copy(status = ForgeTask.Status.CANCELLED, note = "已取消") }
                    throw c
                } catch (t: Throwable) {
                    ForgeLogger.task("Core", "失败 ${task.id}: ${t.message ?: t.javaClass.simpleName}")
                    Log.w(TAG, "task failed: ${task.id}", t)
                    update(task.id) {
                        it.copy(status = ForgeTask.Status.FAILED, error = t.message ?: t.javaClass.simpleName)
                    }
                }
            }
        }
        jobs[task.id] = job
        job.invokeOnCompletion { jobs.remove(task.id) }
    }

    /** 当前输出目录（用户可在「设置」页修改） */
    private fun outDir(): String =
        appContext?.let { AppSettings.outputDir(it) } ?: FfPaths.DEFAULT_OUTPUT_DIR

    private fun update(id: String, block: (ForgeTask) -> ForgeTask) {
        _tasks.update { list -> list.map { if (it.id == id) block(it) else it } }
    }

    private fun failFast(type: TaskType, title: String, message: String) {
        _tasks.update {
            it + ForgeTask(newId(), type, title, status = ForgeTask.Status.FAILED, error = message)
        }
    }

    private fun newId(): String =
        "t" + System.currentTimeMillis().toString(36) + UUID.randomUUID().toString().substring(0, 4)

    private fun noteOf(snap: FFmpegEngine.Snapshot): String {
        val done = fmtUs(snap.processedUs)
        val speed = String.format(java.util.Locale.US, "%.2fx", snap.speed)
        return if (snap.totalUs > 0) "$done / ${fmtUs(snap.totalUs)} · $speed" else "$done · $speed"
    }

    private fun fmtUs(us: Long): String {
        val s = us / 1_000_000
        return String.format(java.util.Locale.US, "%02d:%02d", s / 60, s % 60)
    }

    /** shell 端：拼接旧版 .blv 分片（自动跳过后继分片的 FLV 头），输出单一 FLV */
    private fun legacyConcatCommand(segments: List<String>, out: String): List<String> {
        val sb = StringBuilder(": > ").append(shq(out)).append("; ")
        segments.forEachIndexed { i, seg ->
            if (i == 0) {
                sb.append("cat ").append(shq(seg)).append(" >> ").append(shq(out)).append("; ")
            } else {
                sb.append("if [ \"$(head -c 3 ").append(shq(seg)).append(")\" = \"FLV\" ]; then tail -c +14 ")
                    .append(shq(seg)).append(" >> ").append(shq(out))
                    .append("; else cat ").append(shq(seg)).append(" >> ").append(shq(out)).append("; fi; ")
            }
        }
        return listOf("sh", "-c", sb.toString())
    }

    private fun shq(s: String): String = "'" + s.replace("'", "'\\''") + "'"
}
