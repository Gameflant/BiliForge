package com.feringsapp.biliforge.data.model

import java.util.Locale

/** 媒体文件的探测信息（时长 / 分辨率） */
data class MediaInfo(
    val durationMs: Long = 0L,
    val width: Int = 0,
    val height: Int = 0,
) {
    val durationText: String
        get() {
            if (durationMs <= 0) return "--:--"
            val totalSec = durationMs / 1000
            val h = totalSec / 3600
            val m = totalSec % 3600 / 60
            val s = totalSec % 60
            return if (h > 0) {
                String.format(Locale.US, "%d:%02d:%02d", h, m, s)
            } else {
                String.format(Locale.US, "%02d:%02d", m, s)
            }
        }

    val resolutionText: String
        get() = when {
            width <= 0 || height <= 0 -> "读取中…"
            height >= 2160 -> "4K"
            height >= 1440 -> "2K"
            height >= 1080 -> "1080P"
            height >= 720 -> "720P"
            height >= 480 -> "480P"
            else -> "${width}×${height}"
        }
}
