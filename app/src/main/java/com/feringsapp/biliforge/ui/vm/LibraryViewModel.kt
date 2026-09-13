package com.feringsapp.biliforge.ui.vm

import com.feringsapp.biliforge.core.log.ForgeLogger
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.feringsapp.biliforge.core.shizuku.RemoteFs
import com.feringsapp.biliforge.core.shizuku.ShizukuBridge
import com.feringsapp.biliforge.data.model.CacheBundle
import com.feringsapp.biliforge.data.model.MediaInfo
import com.feringsapp.biliforge.media.ffmpeg.FFmpegBin
import com.feringsapp.biliforge.media.ffmpeg.MediaProbe
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import com.feringsapp.biliforge.data.scanner.BiliCacheScanner
import com.feringsapp.biliforge.work.DanmakuFormat
import com.feringsapp.biliforge.work.ForgeCore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LibraryViewModel : ViewModel() {

    data class UiState(
        val scanning: Boolean = false,
        val scannedParts: Int = 0,
        val root: String? = null,
        val bundles: List<CacheBundle> = emptyList(),
        val error: String? = null,
        val query: String = "",
        val lastScanAt: Long = 0L,
        val mediaInfo: Map<String, MediaInfo> = emptyMap(),
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun scan() {
        if (_state.value.scanning) return
        ForgeLogger.ui1("Library", "开始扫描缓存")
        viewModelScope.launch {
            val svc = ShizukuBridge.service.value
            if (svc == null) {
                _state.update { it.copy(error = "Shizuku 文件服务未就绪，请先在主页完成授权") }
                return@launch
            }
            _state.update { it.copy(scanning = true, error = null, scannedParts = 0) }
            try {
                val fs = RemoteFs(svc)
                val root = fs.findBiliRoot() ?: throw IllegalStateException(
                    "未找到 B 站缓存目录（确认已用哔哩哔哩客户端缓存过视频）"
                )
                val found = BiliCacheScanner(fs).scan(root) { p ->
                    _state.update { s -> s.copy(scannedParts = p.scannedParts) }
                }
                ForgeLogger.ui1("Library", "扫描完成：${found.size} 个缓存")
                _state.update {
                    it.copy(
                        scanning = false,
                        bundles = found,
                        root = root,
                        lastScanAt = System.currentTimeMillis(),
                        mediaInfo = emptyMap(),
                    )
                }
                probeAll(fs, found)
            } catch (t: Throwable) {
                ForgeLogger.ui1("Library", "扫描失败：${t.message}")
                _state.update {
                    it.copy(scanning = false, error = t.message ?: t.javaClass.simpleName)
                }
            }
        }
    }

    /** 扫描完成后异步探测每个缓存的时长 / 分辨率（并发 3，需 FFmpeg 就绪） */
    private fun probeAll(fs: RemoteFs, bundles: List<CacheBundle>) {
        viewModelScope.launch {
            val svc = ShizukuBridge.service.value ?: return@launch
            if (!FFmpegBin(svc, fs).isInstalled()) return@launch
            ForgeLogger.ui2("Probe", "开始探测 ${bundles.size} 项媒体信息")
            val gate = Semaphore(3)
            bundles.forEach { bundle ->
                launch {
                    gate.withPermit {
                        val path = bundle.videoTracks.firstOrNull()?.path
                            ?: bundle.legacySegments.firstOrNull()?.path
                            ?: return@withPermit
                        val info = MediaProbe.probe(svc, path) ?: return@withPermit
                        _state.update { st ->
                            st.copy(mediaInfo = st.mediaInfo + (bundle.item.dir to info))
                        }
                    }
                }
            }
        }
    }

    fun setQuery(q: String) = _state.update { it.copy(query = q) }

    fun merge(bundle: CacheBundle) = ForgeCore.enqueueMerge(bundle)

    fun extractAudio(bundle: CacheBundle, mp3: Boolean) =
        ForgeCore.enqueueExtractAudio(bundle, mp3)

    fun extractDanmaku(bundle: CacheBundle, format: DanmakuFormat) =
        ForgeCore.enqueueExtractDanmaku(bundle, format)

    fun transcode(bundle: CacheBundle, preset: com.feringsapp.biliforge.media.ffmpeg.TranscodePreset) =
        ForgeCore.enqueueTranscodeBundle(bundle, preset)
}
