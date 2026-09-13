package com.feringsapp.biliforge.work

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.feringsapp.biliforge.core.shizuku.RemoteFs
import com.feringsapp.biliforge.core.shizuku.ShizukuBridge
import com.feringsapp.biliforge.media.ffmpeg.FFmpegBin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 全局 FFmpeg 引擎状态（跨页面共享）。
 * 探测结果全局缓存，避免每次切回主页时先渲染"未就绪"再由红变绿。
 */
object EngineStatus {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** null = 尚未探测过；"" = 已探测且未安装；其他 = 版本号 */
    var version: String? by mutableStateOf(null)
        private set

    var probing: Boolean by mutableStateOf(false)
        private set

    val installed: Boolean
        get() = !version.isNullOrBlank()

    val known: Boolean
        get() = version != null

    /** 幂等探测：已探测过则直接返回（force = true 时强制刷新） */
    fun refresh(force: Boolean = false) {
        if (probing) return
        if (!force && version != null) return
        val svc = ShizukuBridge.service.value ?: return
        probing = true
        scope.launch {
            try {
                val bin = FFmpegBin(svc, RemoteFs(svc))
                version = if (bin.isInstalled()) {
                    bin.probeVersion() ?: "已安装"
                } else {
                    ""
                }
            } catch (t: Throwable) {
                if (version == null) version = ""
            } finally {
                probing = false
            }
        }
    }

    fun markInstalled(ver: String) {
        version = ver
    }
}
