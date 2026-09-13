package com.feringsapp.biliforge.data.scanner

import com.feringsapp.biliforge.data.model.BiliCacheItem
import org.json.JSONObject

/**
 * entry.json 宽容解析器。
 * B 站不同版本/不同内容类型的字段差异很大（正片 / 番剧 / 课程），
 * 所有字段都做了回退链，任何一项缺失都不影响整体解析。
 */
object EntryJsonParser {

    fun parse(json: String, dir: String): BiliCacheItem? = runCatching {
        val o = JSONObject(json)
        val page = o.optJSONObject("page_data")
        val ep = o.optJSONObject("ep")
        val upperObj = o.optJSONObject("upper") ?: o.optJSONObject("owner")

        val title = firstNonBlank(
            o.optString("title"),
            page?.optString("download_title"),
            ep?.optString("title"),
        ) ?: "未命名缓存"

        val part = firstNonBlank(
            page?.optString("download_subtitle"),
            ep?.optString("index_title"),
            page?.optString("part"),
        ) ?: title

        BiliCacheItem(
            dir = dir,
            title = title,
            partTitle = part,
            pageIndex = page?.optInt("page", 1)?.takeIf { it > 0 } ?: 1,
            pageCount = o.optInt("page_count", 1).takeIf { it > 0 } ?: 1,
            quality = o.optInt("prefered_video_quality", o.optInt("quality", 0)),
            mediaType = o.optInt("media_type", 0),
            coverUrl = firstNonBlank(o.optString("cover"), ep?.optString("cover")),
            bvid = o.optString("bvid").takeIf { it.isNotBlank() },
            avid = o.optLong("av_id", o.optLong("avid", -1L)).takeIf { it > 0 },
            cid = page?.optLong("cid")?.takeIf { it > 0 },
            upName = firstNonBlank(
                upperObj?.optString("name"),
                o.optString("up_name"),
                o.optString("author"),
            ),
            isCompleted = o.optBoolean("is_completed", true),
            totalBytes = o.optLong("total_bytes", 0L),
            rawJson = json,
        )
    }.getOrNull()

    private fun firstNonBlank(vararg values: String?): String? =
        values.firstOrNull { !it.isNullOrBlank() }?.trim()
}
