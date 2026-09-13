package com.feringsapp.biliforge.core.shizuku

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile

data class RemoteEntry(
    val name: String,
    val size: Long,
    val mtime: Long,
    val isDir: Boolean,
)

/**
 * [IRemoteService] 的 Kotlin 封装 + 流式拷贝引擎。
 * 所有 IO 都发生在 shell 身份的服务进程，本类只做调度。
 */
class RemoteFs(val service: IRemoteService) {

    fun exists(path: String): Boolean =
        runCatching { service.pathExists(path) }.getOrDefault(false)

    fun isDirectory(path: String): Boolean =
        runCatching { service.isDirectory(path) }.getOrDefault(false)

    fun size(path: String): Long =
        runCatching { service.fileSize(path) }.getOrDefault(-1L)

    fun listDir(path: String): List<RemoteEntry> =
        runCatching { service.listDir(path) }.getOrNull()
            ?.mapNotNull(::decodeEntry)
            .orEmpty()

    fun readText(path: String, maxBytes: Int = 1 shl 20): String? =
        runCatching { service.readTextFile(path, maxBytes) }.getOrNull()

    fun writeText(path: String, content: String, append: Boolean = false): Boolean =
        runCatching { service.writeTextFile(path, content, append) }.getOrDefault(false)

    fun chmod(path: String, mode: Int): Boolean =
        runCatching { service.chmod(path, mode) }.getOrDefault(false)

    /**
     * 把远程文件流式拷贝到本地（通常是 App 私有工作目录）。
     * CHUNK = 256 KiB，兼顾 Binder 事务上限与吞吐；带进度回调。
     */
    suspend fun copyToFile(
        src: String,
        dst: File,
        onProgress: ((copied: Long, total: Long) -> Unit)? = null,
    ): File = withContext(Dispatchers.IO) {
        val total = size(src)
        dst.parentFile?.mkdirs()
        RandomAccessFile(dst, "rw").use { out ->
            out.setLength(0)
            var offset = 0L
            while (true) {
                currentCoroutineContext().ensureActive()
                val bytes = service.readChunk(src, offset, CHUNK)
                if (bytes == null || bytes.isEmpty()) break
                out.write(bytes)
                offset += bytes.size
                onProgress?.invoke(offset, total)
                if (bytes.size < CHUNK) break
            }
        }
        dst
    }

    /** 探测本机 B 站缓存根目录（多用户 / 不同挂载点都试一遍） */
    fun findBiliRoot(): String? = CANDIDATE_ROOTS.firstOrNull { exists(it) && isDirectory(it) }

    private fun decodeEntry(raw: String): RemoteEntry? {
        val p = raw.split('\u0001')
        if (p.size < 4) return null
        return RemoteEntry(
            name = p[0],
            size = p[1].toLongOrNull() ?: -1L,
            mtime = p[2].toLongOrNull() ?: 0L,
            isDir = p[3] == "1",
        )
    }

    companion object {
        const val CHUNK = 256 * 1024

        const val DEFAULT_BILI_ROOT =
            "/storage/emulated/0/Android/data/tv.danmaku.bili/download"

        private val CANDIDATE_ROOTS: List<String> = buildList {
            add(DEFAULT_BILI_ROOT)
            for (user in 0..9) add("/storage/emulated/$user/Android/data/tv.danmaku.bili/download")
            add("/sdcard/Android/data/tv.danmaku.bili/download")
        }.distinct()
    }
}
