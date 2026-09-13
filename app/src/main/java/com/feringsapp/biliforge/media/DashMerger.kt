package com.feringsapp.biliforge.media

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer

/**
 * DASH 合成器（纯系统 API，copy 编码不重编码）：
 *
 *   video.m4s  ──┐
 *                ├──► MediaMuxer ──► output.mp4
 *   audio.m4s  ──┘
 *
 * 同时支持多分片顺序拼接（每段时间戳自动对齐到上一段末尾）。
 */
object DashMerger {

    private const val BUFFER_SIZE = 2 shl 20   // 2 MiB，足够容纳 4K 大帧
    private const val GAP_US = 1_000L          // 分片间隙 1ms

    class MergeException(message: String, cause: Throwable? = null) : Exception(message, cause)

    data class Stats(
        val videoSamples: Long,
        val audioSamples: Long,
        val writtenBytes: Long,
        val durationMs: Long,
    )

    fun merge(
        videoPaths: List<String>,
        audioPaths: List<String>,
        output: File,
        onProgress: ((doneBytes: Long, totalBytes: Long) -> Unit)? = null,
    ): Stats {
        if (videoPaths.isEmpty() && audioPaths.isEmpty()) throw MergeException("没有可合成的媒体流")
        output.parentFile?.mkdirs()
        if (output.exists()) output.delete()

        val muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var started = false
        var videoSamples = 0L
        var audioSamples = 0L
        var durationUs = 0L

        try {
            val videoFormat = videoPaths.firstOrNull()?.let { probe(it, video = true) }
            val audioFormat = audioPaths.firstOrNull()?.let { probe(it, video = false) }
            if (videoFormat == null && audioFormat == null) {
                throw MergeException("无法识别媒体轨道（缓存可能损坏、未下载完成或已加密）")
            }

            val videoTrack = videoFormat?.let { muxer.addTrack(it) } ?: -1
            val audioTrack = audioFormat?.let { muxer.addTrack(it) } ?: -1
            muxer.start()
            started = true

            val buf = ByteBuffer.allocate(BUFFER_SIZE)
            val info = MediaCodec.BufferInfo()
            val totalBytes = (videoPaths + audioPaths).sumOf { File(it).length() }.coerceAtLeast(1L)
            var doneBytes = 0L
            val reportProgress: (Long) -> Unit = { n ->
                doneBytes += n
                onProgress?.invoke(doneBytes, totalBytes)
            }

            var vBase = 0L
            for (p in videoPaths) {
                val r = drain(muxer, videoTrack, p, video = true, baseUs = vBase, buf, info, reportProgress)
                vBase = r.endUs + GAP_US
                videoSamples += r.samples
                durationUs = maxOf(durationUs, r.endUs)
            }
            var aBase = 0L
            for (p in audioPaths) {
                val r = drain(muxer, audioTrack, p, video = false, baseUs = aBase, buf, info, reportProgress)
                aBase = r.endUs + GAP_US
                audioSamples += r.samples
                durationUs = maxOf(durationUs, r.endUs)
            }

            muxer.stop()
            started = false
            return Stats(
                videoSamples = videoSamples,
                audioSamples = audioSamples,
                writtenBytes = output.length(),
                durationMs = durationUs / 1000,
            )
        } catch (t: MergeException) {
            throw t
        } catch (t: Throwable) {
            throw MergeException("合成失败: ${t.message}", t)
        } finally {
            if (started) runCatching { muxer.stop() }
            runCatching { muxer.release() }
        }
    }

    private class DrainResult(val samples: Long, val endUs: Long)

    private fun drain(
        muxer: MediaMuxer,
        muxerTrack: Int,
        path: String,
        video: Boolean,
        baseUs: Long,
        buf: ByteBuffer,
        info: MediaCodec.BufferInfo,
        onBytes: (Long) -> Unit,
    ): DrainResult {
        if (muxerTrack < 0) return DrainResult(0, baseUs)
        val extractor = MediaExtractor()
        var samples = 0L
        var endUs = baseUs
        try {
            extractor.setDataSource(path)
            val trackIndex = selectTrack(extractor, video)
            if (trackIndex < 0) return DrainResult(0, baseUs)
            extractor.selectTrack(trackIndex)

            var firstTimeUs = Long.MIN_VALUE
            while (true) {
                val size = extractor.readSampleData(buf, 0)
                if (size < 0) break
                val t = extractor.sampleTime
                if (firstTimeUs == Long.MIN_VALUE) firstTimeUs = t
                // 分片有可能从 0 重新计时：此类时间戳平移对齐；全局计时的分片直接透传
                info.presentationTimeUs = if (t >= baseUs) t else baseUs + (t - firstTimeUs)
                info.offset = 0
                info.size = size
                info.flags = extractor.sampleFlags and MediaCodec.BUFFER_FLAG_KEY_FRAME
                muxer.writeSampleData(muxerTrack, buf, info)
                endUs = info.presentationTimeUs
                samples++
                onBytes(size.toLong())
                extractor.advance()
            }
        } finally {
            runCatching { extractor.release() }
        }
        return DrainResult(samples, endUs)
    }

    private fun selectTrack(extractor: MediaExtractor, video: Boolean): Int {
        for (i in 0 until extractor.trackCount) {
            val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: continue
            val match = if (video) mime.startsWith("video/") else mime.startsWith("audio/")
            if (match) return i
        }
        return -1
    }

    private fun probe(path: String, video: Boolean): MediaFormat? {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(path)
            val i = selectTrack(extractor, video)
            if (i < 0) null else extractor.getTrackFormat(i)
        } catch (t: Throwable) {
            null
        } finally {
            runCatching { extractor.release() }
        }
    }
}
