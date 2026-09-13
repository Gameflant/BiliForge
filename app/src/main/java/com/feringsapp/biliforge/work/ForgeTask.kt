package com.feringsapp.biliforge.work

enum class TaskType(val label: String, val code: String) {
    MERGE("合成 MP4", "MERGE"),
    TRANSCODE("转码", "TRANS"),
    EXTRACT_AUDIO("提取音频", "AUDIO"),
    EXTRACT_DANMAKU("提取弹幕", "DANM"),
    INSTALL_FFMPEG("安装工具链", "TOOL"),
    EXPORT("导出文件", "EXPORT"),
}

data class ForgeTask(
    val id: String,
    val type: TaskType,
    val title: String,
    val subtitle: String = "",
    val status: Status = Status.QUEUED,
    val progress: Float = 0f,
    val speed: Double = 0.0,
    val note: String = "",
    val outputPath: String? = null,
    val error: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
) {
    enum class Status { QUEUED, RUNNING, DONE, FAILED, CANCELLED }

    val isActive: Boolean
        get() = status == Status.QUEUED || status == Status.RUNNING
}

/** 输出文件命名 */
object OutputNaming {

    private val illegal = Regex("[\\\\/:*?\"<>|\\r\\n\\t]+")

    fun sanitize(raw: String, max: Int = 80): String {
        val s = illegal.replace(raw, "_").trim().trim('.').ifBlank { "未命名" }
        return if (s.length > max) s.substring(0, max) else s
    }
}
