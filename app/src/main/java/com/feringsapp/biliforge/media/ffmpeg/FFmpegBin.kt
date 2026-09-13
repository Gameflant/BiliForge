package com.feringsapp.biliforge.media.ffmpeg

import android.content.res.AssetManager
import com.feringsapp.biliforge.core.shizuku.IRemoteService
import com.feringsapp.biliforge.core.shizuku.RemoteFs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * ffmpeg 工具链在设备上的布局。
 * 全部位于 ADB shell(uid=2000) 可写的 /data/local/tmp，
 * 这样 ffmpeg 可以：直接读 B 站缓存目录（/sdcard/Android/data/tv.danmaku.bili）
 *             + 直接写媒体库目录（/sdcard/Movies/BiliForge）→ 全程零拷贝。
 */
object FfPaths {
    const val BASE = "/data/local/tmp/biliforge"
    const val BIN_DIR = "$BASE/bin"
    const val WORK_DIR = "$BASE/work"
    const val LOG_DIR = "$BASE/logs"
    const val PKG_DIR = "$BASE/pkg"
    const val LIB_DIR = "$BASE/lib"
    const val FFMPEG = "$BIN_DIR/ffmpeg"

    /** 投放目录：用户（或 Termux 脚本）把 ffmpeg / ffmpeg-android.tar.gz 放这里 */
    const val STAGING_DIR = "/sdcard/BiliForge/tools"
    const val STAGING_FFMPEG = "$STAGING_DIR/ffmpeg"
    const val STAGING_ARCHIVE = "$STAGING_DIR/ffmpeg-android.tar.gz"
    const val STAGING_ARCHIVE_ALT = "$STAGING_DIR/ffmpeg-termux.tar.gz"

    /** 默认输出目录（shell 可写，且会被媒体扫描识别） */
    const val DEFAULT_OUTPUT_DIR = "/sdcard/Movies/BiliForge"
}

/** ffmpeg 二进制管理：安装（从投放目录）/ 探测 / 版本 */
class FFmpegBin(
    private val remote: IRemoteService,
    private val fs: RemoteFs,
) {

    data class InstallResult(val ok: Boolean, val message: String)

    fun isInstalled(): Boolean = fs.exists(FfPaths.FFMPEG)

    suspend fun ensureLayout() = withContext(Dispatchers.IO) {
        remote.mkdirs(FfPaths.BIN_DIR)
        remote.mkdirs(FfPaths.WORK_DIR)
        remote.mkdirs(FfPaths.LOG_DIR)
        remote.mkdirs(FfPaths.DEFAULT_OUTPUT_DIR)
        remote.mkdirs(FfPaths.STAGING_DIR)
        Unit
    }

    /**
     * 安装流程（按优先级）：
     * 1) /sdcard/BiliForge/tools/ffmpeg                ← 单个可执行文件（推荐，脚本已帮你验过）
     * 2) /sdcard/BiliForge/tools/ffmpeg-android.tar.gz ← 压缩包（解包后自动找 ffmpeg）
     * 3) /sdcard/BiliForge/tools/ffmpeg-termux.tar.gz  ← 来自 Termux 的打包（同上）
     */
    suspend fun installFromStaging(): InstallResult = withContext(Dispatchers.IO) {
        ensureLayout()
        val single = FfPaths.STAGING_FFMPEG
        val archive = listOf(FfPaths.STAGING_ARCHIVE, FfPaths.STAGING_ARCHIVE_ALT)
            .firstOrNull { fs.exists(it) }

        when {
            fs.exists(single) -> {
                val ok = remote.installExecutable(single, FfPaths.BIN_DIR, "ffmpeg")
                if (ok) InstallResult(true, "已安装 ffmpeg → ${FfPaths.FFMPEG}")
                else InstallResult(false, "拷贝失败（源文件不可读？）")
            }

            archive != null -> {
                if (!remote.untar(archive, FfPaths.BASE)) {
                    return@withContext InstallResult(false, "解包失败：$archive")
                }
                if (!remote.pathExists(FfPaths.FFMPEG)) {
                    return@withContext InstallResult(false, "包内未找到 bin/ffmpeg")
                }
                if (!remote.chmod(FfPaths.FFMPEG, 493)) {
                    return@withContext InstallResult(false, "chmod 0755 失败")
                }
                InstallResult(true, "已解包安装 ffmpeg → ${FfPaths.FFMPEG}")
            }

            else -> InstallResult(
                false,
                "未发现投放文件。请把 ffmpeg 或 ffmpeg-android.tar.gz 放入 ${FfPaths.STAGING_DIR}"
            )
        }
    }

    /**
     * 从 APK 内置资产安装（开箱即用路径）：
     *   1. assets/ffmpeg-pack.tar.gz 以 512KB 分块流式写入 /data/local/tmp/biliforge/work/
     *   2. shell 侧 tar 解包到 pkg/
     *   3. ffmpeg → bin/ffmpeg(0755)；依赖库 → bin/（同目录作为 LD_LIBRARY_PATH）
     *   4. 清理中间产物
     */
    suspend fun installFromAssets(
        assets: AssetManager,
        assetName: String = ASSET_PACK,
    ): InstallResult = withContext(Dispatchers.IO) {
        ensureLayout()
        val remotePack = "${FfPaths.WORK_DIR}/incoming-pack.tar.gz"
        remote.deleteRecursive(remotePack)

        val stream = openAsset(assets)
            ?: return@withContext InstallResult(false, "内置工具包不存在（资产缺失）")

        var offset = 0L
        try {
            stream.use { input ->
                val buf = ByteArray(192 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    val chunk = if (n == buf.size) buf else buf.copyOf(n)
                    if (!remote.writeChunk(remotePack, offset, chunk)) {
                        return@withContext InstallResult(false, "写入失败（/data/local/tmp 空间不足？）")
                    }
                    offset += n
                }
            }
        } catch (t: Throwable) {
            return@withContext InstallResult(false, "读取内置包失败: ${t.message}")
        }
        if (offset <= 0L) return@withContext InstallResult(false, "内置工具包不存在或为空")

        // 直接解到 BASE：bin/ffmpeg 与 lib 依赖库（运行时 LD_LIBRARY_PATH 自动包含 BASE/lib）
        if (!remote.untar(remotePack, FfPaths.BASE)) {
            return@withContext InstallResult(false, "解包失败（包损坏？）")
        }
        if (!remote.pathExists(FfPaths.FFMPEG)) {
            return@withContext InstallResult(false, "包内未找到 bin/ffmpeg")
        }
        if (!remote.chmod(FfPaths.FFMPEG, 493)) {
            return@withContext InstallResult(false, "chmod 0755 失败")
        }
        remote.deleteRecursive(remotePack)
        InstallResult(true, "内置引擎安装完成（${offset / 1024 / 1024} MB）")
    }

    /** 运行 ffmpeg -version 并返回版本行 */
    suspend fun probeVersion(): String? = withContext(Dispatchers.IO) {
        if (!isInstalled()) return@withContext null
        val log = "${FfPaths.LOG_DIR}/version.log"
        runCatching { remote.deleteRecursive(log) }
        val handle = remote.spawn(arrayOf(FfPaths.FFMPEG, "-version"), FfPaths.BIN_DIR, log)
        if (handle < 0) return@withContext null
        val deadline = System.currentTimeMillis() + 8_000
        while (remote.processAlive(handle) && System.currentTimeMillis() < deadline) {
            Thread.sleep(120)
        }
        runCatching { remote.killProcess(handle) }
        val text = runCatching { remote.readTextFile(log, 256 shl 10) }.getOrNull()
        text?.lineSequence()?.firstOrNull { it.startsWith("ffmpeg version") }?.trim()
    }

    companion object {
        /**
         * APK 内置工具包资产名。
         * 注意：AGP/aapt2 可能把 assets 中 .tar.gz 解压重打包并去掉 .gz 后缀，
         * 因此这里给出候选列表，运行时自动探测实际名称。
         */
        const val ASSET_PACK = "ffmpeg-pack.tar.gz"
        val ASSET_PACK_CANDIDATES = listOf(
            "ffmpeg-pack.tar.gz",
            "ffmpeg-pack.tar",
            "ffmpeg-pack.bin",
        )

        /** 内置工具包是否存在（自动探测候选名） */
        fun hasAsset(assets: AssetManager): Boolean =
            ASSET_PACK_CANDIDATES.any { name ->
                runCatching { assets.open(name).close(); true }.getOrDefault(false)
            }

        /** 打开内置工具包（自动探测候选名） */
        private fun openAsset(assets: AssetManager): java.io.InputStream? {
            for (name in ASSET_PACK_CANDIDATES) {
                val s = runCatching { assets.open(name) }.getOrNull()
                if (s != null) return s
            }
            return null
        }
    }

    private fun findInPackage(): String? {
        val candidates = ArrayList<String>()
        candidates += "${FfPaths.PKG_DIR}/ffmpeg"
        candidates += "${FfPaths.PKG_DIR}/bin/ffmpeg"
        for (e in fs.listDir(FfPaths.PKG_DIR)) {
            if (e.isDir) {
                candidates += "${FfPaths.PKG_DIR}/${e.name}/ffmpeg"
                candidates += "${FfPaths.PKG_DIR}/${e.name}/bin/ffmpeg"
            }
        }
        return candidates.firstOrNull { fs.exists(it) }
    }
}
