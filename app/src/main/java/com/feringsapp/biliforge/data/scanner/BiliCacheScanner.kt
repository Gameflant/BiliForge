package com.feringsapp.biliforge.data.scanner

import com.feringsapp.biliforge.core.shizuku.RemoteFs
import com.feringsapp.biliforge.data.model.BiliCacheItem
import com.feringsapp.biliforge.data.model.CacheBundle
import com.feringsapp.biliforge.data.model.Track
import com.feringsapp.biliforge.data.model.TrackKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * B 站缓存扫描器（运行在 App 进程，通过 Shizuku 服务远程访问文件系统）。
 *
 * 真实目录结构（参考实测）：
 *   download/{视频id}/{c1334568 之类}/
 *       ├── entry.json         ← 元数据
 *       ├── danmaku.xml        ← 弹幕（可能有）
 *       └── {数字目录}/         ← 媒体分片目录
 *             ├── video.m4s
 *             └── audio.m4s
 * 兼容旧版：分P 目录下直接扁平存在 0.blv / 1.blv ...
 */
class BiliCacheScanner(
    private val fs: RemoteFs,
    private val maxDepth: Int = 3,
) {

    data class Progress(
        val scannedParts: Int,
        val found: Int,
        val currentPath: String,
    )

    suspend fun scan(
        root: String,
        onProgress: (Progress) -> Unit = {},
    ): List<CacheBundle> = withContext(Dispatchers.IO) {
        val out = ArrayList<CacheBundle>()
        var scanned = 0
        for (l1 in fs.listDir(root).filter { it.isDir }) {
            currentCoroutineContext().ensureActive()
            val l1Path = join(root, l1.name)
            for (l2 in fs.listDir(l1Path).filter { it.isDir }) {
                currentCoroutineContext().ensureActive()
                scanned++
                val l2Path = join(l1Path, l2.name)
                onProgress(Progress(scanned, out.size, l2Path))
                readBundle(l2Path)?.let { out += it }
            }
        }
        out.sortedWith(compareBy({ it.item.title }, { it.item.pageIndex }))
    }

    private fun readBundle(dir: String): CacheBundle? {
        val entryPath = join(dir, ENTRY_JSON)
        val entryJson = if (fs.exists(entryPath)) fs.readText(entryPath, 2 shl 20) else null
        val item: BiliCacheItem = entryJson?.let { EntryJsonParser.parse(it, dir) } ?: return null

        val files = ArrayList<String>(32)
        walk(dir, 0, files)

        val video = ArrayList<Track>()
        val audio = ArrayList<Track>()
        val legacy = ArrayList<Track>()
        var danmaku: String? = null
        var cover: String? = null

        for (f in files) {
            val name = f.substringAfterLast('/').lowercase(Locale.ROOT)
            when {
                name == "danmaku.xml" -> danmaku = f
                name.endsWith(".m4s") -> {
                    val t = Track(f, fs.size(f), classifyM4s(name))
                    if (t.kind == TrackKind.AUDIO) audio += t else video += t
                }
                name.endsWith(".blv") -> legacy += Track(f, fs.size(f), TrackKind.LEGACY_AV)
                name.endsWith(".jpg") || name.endsWith(".png") || name.endsWith(".webp") ->
                    if (cover == null) cover = f
            }
        }
        legacy.sortBy { it.path.substringAfterLast('/').substringBefore('.').toIntOrNull() ?: Int.MAX_VALUE }
        video.sortBy { it.path }
        audio.sortBy { it.path }

        return CacheBundle(
            item = item,
            videoTracks = video,
            audioTracks = audio,
            legacySegments = legacy,
            danmakuPath = danmaku,
            localCoverPath = cover,
        )
    }

    private fun classifyM4s(name: String): TrackKind = when {
        name.contains("audio") -> TrackKind.AUDIO
        name.contains("video") -> TrackKind.VIDEO
        else -> TrackKind.UNKNOWN
    }

    private fun walk(dir: String, depth: Int, out: MutableList<String>) {
        if (depth > maxDepth) return
        for (e in fs.listDir(dir)) {
            val p = join(dir, e.name)
            if (e.isDir) walk(p, depth + 1, out) else out += p
        }
    }

    private fun join(a: String, b: String): String =
        if (a.endsWith('/')) a + b else "$a/$b"

    companion object {
        const val ENTRY_JSON = "entry.json"
        const val DANMAKU_XML = "danmaku.xml"
    }
}
