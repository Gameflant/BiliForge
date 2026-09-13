package com.feringsapp.biliforge.core.shizuku

import android.content.Context
import android.os.Build
import android.os.ParcelFileDescriptor
import android.system.Os
import android.util.Log
import androidx.annotation.Keep
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

private const val TAG = "BiliForge/RemoteFs"

/**
 * 以 ADB(shell, uid=2000) 身份运行的文件 + 进程服务。
 *
 * 注意（Shizuku UserService 约束）：
 *  - 由 Shizuku server 反射实例化，构造中不要依赖普通 App 的 Context 行为；
 *  - 保持类名稳定，或通过 UserServiceArgs.tag 固定（见 ShizukuBridge）；
 *  - destroy() 是 Shizuku 保留方法，用于回收服务进程。
 */
class RemoteFsService(private val hostContext: Context?) : IRemoteService.Stub() {

    /**
     * 无参构造（Shizuku 旧版反射入口）。
     * 带 Context 的实例化由主构造直接承担（JVM 签名为 (Landroid/content/Context;)V），
     * 无需额外次构造（避免委托环）。
     */
    @Keep
    constructor() : this(null)

    private val processes = ConcurrentHashMap<Int, Process>()
    private val handleGen = AtomicInteger(0)

    init {
        Log.i(
            TAG,
            "service created uid=${Os.getuid()} pid=${Os.getpid()} " +
                "withContext=${hostContext != null} sdk=${Build.VERSION.SDK_INT}"
        )
    }

    override fun destroy() {
        Log.i(TAG, "destroy() -> System.exit")
        System.exit(0)
    }

    override fun exit() = destroy()

    override fun info(): String {
        val selinux = runCatching {
            RandomAccessFile("/proc/self/attr/current", "r").use { it.readLine() }
        }.getOrNull() ?: "?"
        return "uid=${Os.getuid()} pid=${Os.getpid()} sdk=${Build.VERSION.SDK_INT} " +
            "abi=${Build.SUPPORTED_ABIS.firstOrNull()} selinux=$selinux"
    }

    override fun pathExists(path: String): Boolean = File(path).exists()

    override fun isDirectory(path: String): Boolean = File(path).isDirectory

    override fun fileSize(path: String): Long {
        val f = File(path)
        return if (f.isFile) f.length() else -1L
    }

    override fun listDir(path: String): Array<String>? {
        val files = File(path).listFiles() ?: return null
        return Array(files.size) { i ->
            val f = files[i]
            val isDir = f.isDirectory
            buildString(64) {
                append(f.name)
                append('\u0001')
                append(if (isDir) -1L else f.length())
                append('\u0001')
                append(f.lastModified())
                append('\u0001')
                append(if (isDir) '1' else '0')
            }
        }
    }

    override fun readChunk(path: String, offset: Long, length: Int): ByteArray? {
        if (length <= 0 || length > 8 shl 20) return null
        return runCatching {
            RandomAccessFile(path, "r").use { raf ->
                raf.seek(offset)
                val buf = ByteArray(length)
                var total = 0
                while (total < length) {
                    val r = raf.read(buf, total, length - total)
                    if (r < 0) break
                    total += r
                }
                if (total == length) buf else buf.copyOf(total)
            }
        }.getOrNull()
    }

    override fun readTextFile(path: String, maxBytes: Int): String? = runCatching {
        val f = File(path)
        if (!f.isFile) return@runCatching null
        val len = minOf(f.length(), maxBytes.toLong()).toInt()
        val bytes = ByteArray(len)
        RandomAccessFile(f, "r").use { it.readFully(bytes) }
        String(bytes, Charsets.UTF_8)
    }.getOrNull()

    override fun readTextFileFrom(path: String, offset: Long, maxBytes: Int): String? = runCatching {
        val f = File(path)
        if (!f.isFile) return@runCatching null
        val len = f.length()
        if (offset >= len) return@runCatching "$len\u0001"
        val toRead = minOf(len - offset, maxBytes.toLong().coerceAtLeast(0)).toInt()
        if (toRead <= 0) return@runCatching "$len\u0001"
        val bytes = ByteArray(toRead)
        RandomAccessFile(f, "r").use {
            it.seek(offset)
            it.readFully(bytes)
        }
        val newOffset = offset + toRead
        "$newOffset\u0001" + String(bytes, Charsets.UTF_8)
    }.getOrNull()

    override fun deleteRecursive(path: String): Boolean =
        runCatching { File(path).deleteRecursively() }.getOrDefault(false)

    override fun mkdirs(path: String): Boolean =
        runCatching { File(path).mkdirs() || File(path).isDirectory }.getOrDefault(false)

    override fun readIntoFd(path: String, offset: Long, length: Int, fd: ParcelFileDescriptor?): Int {
        if (fd == null || length <= 0 || length > 8 shl 20) return -1
        return runCatching {
            val buf = ByteArray(length)
            val read = RandomAccessFile(path, "r").use { raf ->
                raf.seek(offset)
                raf.read(buf)
            }
            val out = java.io.FileOutputStream(fd.fileDescriptor)
            out.write(buf, 0, read)
            out.flush()
            read
        }.getOrDefault(-1)
    }

    // ---------------- 进程 / 工具链 ----------------

    override fun spawn(argv: Array<String>?, cwd: String?, logPath: String?): Int {
        if (argv == null || argv.isEmpty()) return -1
        return try {
            val dir = cwd?.takeIf { it.isNotBlank() }?.let { File(it) }
            dir?.mkdirs()
            val pb = ProcessBuilder(*argv)
            if (dir != null) {
                pb.directory(dir)
                // 让 .so 依赖可被找到：cwd、cwd/lib、cwd 父目录、父目录/lib
                val cands = listOfNotNull(
                    dir,
                    File(dir, "lib"),
                    dir.parentFile,
                    dir.parentFile?.let { File(it, "lib") },
                ).filter { it.isDirectory }
                pb.environment()["LD_LIBRARY_PATH"] = cands.joinToString(":") { it.absolutePath }
            }
            pb.redirectErrorStream(true)
            if (!logPath.isNullOrBlank()) {
                val log = File(logPath)
                log.parentFile?.mkdirs()
                pb.redirectOutput(ProcessBuilder.Redirect.appendTo(log))
            } else {
                pb.redirectOutput(ProcessBuilder.Redirect.to(java.io.File("/dev/null")))
            }
            gcProcesses()
            val p = pb.start()
            val h = handleGen.incrementAndGet()
            processes[h] = p
            Log.i(TAG, "spawn[$h]: ${argv.joinToString(" ")}")
            h
        } catch (t: Throwable) {
            Log.e(TAG, "spawn failed", t)
            -1
        }
    }

    override fun processAlive(handle: Int): Boolean = processes[handle]?.isAlive ?: false

    override fun processExitCode(handle: Int): Int {
        val p = processes[handle] ?: return -404
        return if (p.isAlive) -999 else runCatching { p.exitValue() }.getOrDefault(-500)
    }

    override fun killProcess(handle: Int): Boolean {
        val p = processes[handle] ?: return false
        return runCatching {
            p.destroy()
            for (i in 0 until 10) {
                if (!p.isAlive) break
                Thread.sleep(100)
            }
            if (p.isAlive) p.destroyForcibly()
            true
        }.getOrDefault(false)
    }

    override fun installExecutable(srcPath: String, dstDir: String, name: String): Boolean =
        runCatching {
            val dir = File(dstDir)
            dir.mkdirs()
            val dst = File(dir, name)
            File(srcPath).inputStream().use { input ->
                dst.outputStream().use { out -> input.copyTo(out, 1 shl 20) }
            }
            Os.chmod(dst.absolutePath, 493) // 0755
            dst.isFile && dst.length() > 0
        }.getOrDefault(false)

    override fun untar(archivePath: String, dstDir: String): Boolean = runCatching {
        File(dstDir).mkdirs()
        val cmd = "cd ${shq(dstDir)} && " +
            "(toybox tar xzf ${shq(archivePath)} 2>/dev/null || " +
            "toybox tar xf ${shq(archivePath)} 2>/dev/null || " +
            "tar xzf ${shq(archivePath)} 2>/dev/null)"
        val p = ProcessBuilder("sh", "-c", cmd).redirectErrorStream(true).start()
        p.waitFor() == 0
    }.getOrDefault(false)

    override fun writeTextFile(path: String, content: String, append: Boolean): Boolean = runCatching {
        val f = File(path)
        f.parentFile?.mkdirs()
        if (append) f.appendText(content, Charsets.UTF_8)
        else f.writeText(content, Charsets.UTF_8)
        true
    }.getOrDefault(false)

    override fun writeChunk(path: String, offset: Long, data: ByteArray?): Boolean {
        if (data == null) return false
        return runCatching {
            val f = File(path)
            f.parentFile?.mkdirs()
            RandomAccessFile(f, "rw").use {
                it.seek(offset)
                it.write(data)
            }
            true
        }.getOrDefault(false)
    }

    override fun chmod(path: String, mode: Int): Boolean =
        runCatching { Os.chmod(path, mode); true }.getOrDefault(false)

    private fun shq(s: String): String = "'" + s.replace("'", "'\\''") + "'"

    private fun gcProcesses() {
        if (processes.size <= 32) return
        processes.entries.removeIf { !it.value.isAlive }
    }
}
