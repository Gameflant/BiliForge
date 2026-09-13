package com.feringsapp.biliforge.core.log

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

/**
 * 运行日志系统（四档）：
 *
 *  - [Cat.UI]   UI 日志：level 1 = 基础事件；level 2 = 详细事件；level 3 = 组件级调用追踪
 *  - [Cat.TASK] 任务日志：只记录"做了什么"（入队 / 开始 / 完成 / 失败 / 取消）
 *  - [Cat.SYS]  系统日志：Shizuku / 进程 / ffmpeg / 文件操作，level 越高越完整
 *
 *  超级日志 = 以上全部（含 level 3 的组件调用轨迹）。
 *
 * 性能设计：环形缓冲（固定上限）+ 250ms 节流通知；日志页未打开时零重组成本。
 */
object ForgeLogger {

    enum class Cat(val label: String) {
        UI("UI"), TASK("TASK"), SYS("SYS")
    }

    data class Entry(
        val time: Long,
        val cat: Cat,
        val level: Int,
        val tag: String,
        val msg: String,
    )

    /** 日志页筛选视图 */
    enum class Filter(val label: String) {
        UI_1("UI · 基础"),
        UI_2("UI · 详细"),
        TASK("任务"),
        SUPER("超级 · 全量"),
        RENDER("渲染 · 代码级"),
    }

    private const val MAX_ENTRIES = 3000
    private const val SNAPSHOT_LIMIT = 1500
    private const val MAX_RENDER = 8000
    private const val RENDER_SNAPSHOT_LIMIT = 3000
    private const val EMIT_INTERVAL_MS = 250L

    private val buffer = ArrayDeque<Entry>(MAX_ENTRIES + 1)
    private val renderBuffer = ArrayDeque<Entry>(MAX_RENDER + 1)
    private val lock = Any()
    private var lastEmit = 0L

    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version

    // ---------------- 写入 API ----------------

    /** UI 基础事件（页面切换、主要操作） */
    fun ui1(tag: String, msg: String) = write(Cat.UI, 1, tag, msg)

    /** UI 详细事件（点击、状态变化） */
    fun ui2(tag: String, msg: String) = write(Cat.UI, 2, tag, msg)

    /** 组件级调用追踪（超级日志） */
    fun trace(tag: String, msg: String) = write(Cat.UI, 3, tag, msg)

    /**
     * UI 渲染追踪（代码级）：每个 Composable 的**每次组合/重组**调用。
     * 独立大缓冲（8000 条），不污染业务日志。
     */
    fun render(tag: String, detail: String = "") {
        val e = Entry(System.currentTimeMillis(), Cat.UI, 4, tag, detail)
        var needEmit = false
        synchronized(lock) {
            renderBuffer.addLast(e)
            while (renderBuffer.size > MAX_RENDER) renderBuffer.removeFirst()
            val now = SystemClock.elapsedRealtime()
            if (now - lastEmit >= EMIT_INTERVAL_MS) {
                lastEmit = now
                needEmit = true
            }
        }
        if (needEmit) _version.value = _version.value + 1
    }

    /** 任务日志（只记录干了啥） */
    fun task(tag: String, msg: String) = write(Cat.TASK, 1, tag, msg)

    /** 系统日志（level 1 基础 / 2 详细） */
    fun sys(tag: String, msg: String, level: Int = 1) = write(Cat.SYS, level, tag, msg)

    private fun write(cat: Cat, level: Int, tag: String, msg: String) {
        val e = Entry(System.currentTimeMillis(), cat, level, tag, msg)
        var needEmit = false
        synchronized(lock) {
            buffer.addLast(e)
            while (buffer.size > MAX_ENTRIES) buffer.removeFirst()
            val now = SystemClock.elapsedRealtime()
            if (now - lastEmit >= EMIT_INTERVAL_MS) {
                lastEmit = now
                needEmit = true
            }
        }
        if (needEmit) _version.value = _version.value + 1
    }

    // ---------------- 读取 API ----------------

    fun snapshot(filter: Filter): List<Entry> {
        val (src, limit) = synchronized(lock) {
            if (filter == Filter.RENDER) {
                renderBuffer.toList() to RENDER_SNAPSHOT_LIMIT
            } else {
                buffer.toList() to SNAPSHOT_LIMIT
            }
        }
        val view = when (filter) {
            Filter.UI_1 -> src.filter { it.cat == Cat.UI && it.level == 1 }
            Filter.UI_2 -> src.filter { it.cat == Cat.UI && it.level <= 2 }
            Filter.TASK -> src.filter { it.cat == Cat.TASK }
            Filter.SUPER -> src
            Filter.RENDER -> src
        }
        return if (view.size > limit) view.subList(view.size - limit, view.size) else view
    }

    fun clear() {
        synchronized(lock) {
            buffer.clear()
            renderBuffer.clear()
        }
        _version.value = _version.value + 1
    }

    fun size(): Int = synchronized(lock) { buffer.size }

    /** 导出全部日志为文本 */
    fun exportText(): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
        val copy = synchronized(lock) { buffer.toList() }
        return buildString(copy.size * 96) {
            append("BiliForge 运行日志 · ").append(fmt.format(Date())).append('\n')
            append("共 ").append(copy.size).append(" 条\n")
            append("----------------------------------------\n")
            for (e in copy) {
                append(fmt.format(Date(e.time)))
                append(" [").append(e.cat.label).append("][L").append(e.level).append("] ")
                append(e.tag).append(": ").append(e.msg)
                append('\n')
            }
        }
    }
}
