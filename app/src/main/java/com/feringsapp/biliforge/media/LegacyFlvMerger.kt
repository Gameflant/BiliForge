package com.feringsapp.biliforge.media

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream

/**
 * 旧版 .blv 分片拼接器。
 *
 * .blv 本质是 FLV：第一片带 FLV Header(9B) + PrevTagSize0(4B)=13B 头，
 * 后续分片可能带也可能不带。拼接时探测魔数，丢弃后续分片的 FLV 头。
 */
object LegacyFlvMerger {

    private const val FLV_HEADER_SIZE = 13
    private const val BUF = 1 shl 20

    fun concat(
        segments: List<File>,
        output: File,
        onProgress: ((written: Long, total: Long) -> Unit)? = null,
    ): File {
        require(segments.isNotEmpty()) { "没有可拼接的分片" }
        output.parentFile?.mkdirs()
        val total = segments.sumOf { it.length() }.coerceAtLeast(1L)
        var written = 0L

        FileOutputStream(output).buffered(BUF).use { out ->
            segments.forEachIndexed { index, seg ->
                FileInputStream(seg).buffered(BUF).use { input ->
                    val head = ByteArray(FLV_HEADER_SIZE)
                    val n = readAtMost(input, head)
                    val isFlv = n >= 3 &&
                        head[0] == 'F'.code.toByte() &&
                        head[1] == 'L'.code.toByte() &&
                        head[2] == 'V'.code.toByte()
                    if (index > 0 && isFlv && n == FLV_HEADER_SIZE) {
                        // 丢弃后续分片的 FLV 头
                    } else if (n > 0) {
                        out.write(head, 0, n)
                        written += n
                    }
                    val buf = ByteArray(BUF)
                    while (true) {
                        val r = input.read(buf)
                        if (r < 0) break
                        out.write(buf, 0, r)
                        written += r
                        onProgress?.invoke(written, total)
                    }
                }
            }
        }
        return output
    }

    private fun readAtMost(input: InputStream, buf: ByteArray): Int {
        var off = 0
        while (off < buf.size) {
            val r = input.read(buf, off, buf.size - off)
            if (r < 0) break
            off += r
        }
        return off
    }
}
