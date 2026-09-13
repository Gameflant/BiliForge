package com.feringsapp.biliforge.media.danmaku

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream

/**
 * B 站弹幕（danmaku.xml）模型 + 解析。
 *
 * XML 结构：
 *   <i><d p="时间秒,模式,字号,颜色,时间戳,池,用户hash,id">文本</d>...</i>
 * 模式：1/2/3 滚动 · 4 底部 · 5 顶部 · 6 逆向 · 7 高级(JSON) · 8 代码
 */
data class Danmaku(
    val timeMs: Long,
    val mode: Int,
    val fontSize: Int,
    val color: Int,      // 0xRRGGBB（十进制来源）
    val text: String,
) {
    val isScroll: Boolean get() = mode == 1 || mode == 2 || mode == 3
    val isBottom: Boolean get() = mode == 4
    val isTop: Boolean get() = mode == 5
    val isReverse: Boolean get() = mode == 6
}

object DanmakuParser {

    fun parse(input: InputStream): List<Danmaku> {
        val parser = Xml.newPullParser()
        parser.setInput(input, "UTF-8")
        val out = ArrayList<Danmaku>(4096)
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "d") {
                val text = parser.nextText()
                parseOne(parser.getAttributeValue(null, "p"), text)?.let(out::add)
            }
            event = parser.next()
        }
        return out
    }

    private fun parseOne(p: String?, text: String): Danmaku? {
        if (p.isNullOrBlank() || text.isBlank()) return null
        val f = p.split(',')
        if (f.size < 4) return null
        val timeSec = f[0].toDoubleOrNull() ?: return null
        return Danmaku(
            timeMs = (timeSec * 1000).toLong(),
            mode = f[1].toIntOrNull() ?: 1,
            fontSize = f[2].toIntOrNull() ?: 25,
            color = f[3].toIntOrNull() ?: 0xFFFFFF,
            text = text.replace('\n', ' ').replace('\r', ' ').trim(),
        )
    }
}
