package com.feringsapp.biliforge.data.model

/** 一个「分P 缓存」的元数据（来自 entry.json + 扫描结果） */
data class BiliCacheItem(
    val dir: String,
    val title: String,
    val partTitle: String,
    val pageIndex: Int = 1,
    val pageCount: Int = 1,
    val quality: Int = 0,
    val mediaType: Int = 0,
    val coverUrl: String? = null,
    val bvid: String? = null,
    val avid: Long? = null,
    val cid: Long? = null,
    val upName: String? = null,
    val isCompleted: Boolean = true,
    val totalBytes: Long = 0L,
    val rawJson: String? = null,
) {
    val displayName: String
        get() = if (partTitle.isNotBlank() && partTitle != title) "$title · $partTitle" else title

    val qualityLabel: String get() = qualityLabelOf(quality)

    val pageLabel: String get() = if (pageCount > 1) "P$pageIndex/$pageCount" else "单集"

    companion object {
        fun qualityLabelOf(q: Int): String = when (q) {
            6 -> "240P"
            16 -> "360P"
            32 -> "480P"
            64 -> "720P"
            74 -> "720P60"
            80 -> "1080P"
            112 -> "1080P⁺"
            116 -> "1080P60"
            120 -> "4K"
            125 -> "HDR"
            126 -> "杜比视界"
            127 -> "8K"
            else -> if (q > 0) "Q$q" else "未知画质"
        }
    }
}

enum class TrackKind {
    /** DASH 视频轨（video.m4s） */
    VIDEO,

    /** DASH 音频轨（audio.m4s） */
    AUDIO,

    /** 旧版 .blv 分片（FLV，音视频混合） */
    LEGACY_AV,

    UNKNOWN,
}

data class Track(
    val path: String,
    val size: Long,
    val kind: TrackKind,
)

/** 一个分P 的完整缓存集合 */
data class CacheBundle(
    val item: BiliCacheItem,
    val videoTracks: List<Track> = emptyList(),
    val audioTracks: List<Track> = emptyList(),
    val legacySegments: List<Track> = emptyList(),
    val danmakuPath: String? = null,
    val localCoverPath: String? = null,
) {
    val mediaBytes: Long
        get() = (videoTracks + audioTracks + legacySegments).sumOf { it.size }

    val hasDash: Boolean get() = videoTracks.isNotEmpty() || audioTracks.isNotEmpty()

    val hasLegacy: Boolean get() = legacySegments.isNotEmpty()

    /** 供 UI 显示的简要描述 */
    val summary: String
        get() = buildString {
            append(item.qualityLabel)
            when {
                hasDash -> {
                    append(" · DASH")
                    if (audioTracks.isEmpty()) append(" · 无音轨")
                }
                hasLegacy -> append(" · BLV×").append(legacySegments.size)
                else -> append(" · 无媒体流")
            }
        }
}
