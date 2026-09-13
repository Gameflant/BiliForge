package com.feringsapp.biliforge.ui.screens

import androidx.compose.runtime.DisposableEffect
import com.feringsapp.biliforge.core.log.ForgeLogger
import top.yukonga.miuix.kmp.icon.extended.Tasks
import com.feringsapp.biliforge.ui.components.HeroColors
import com.feringsapp.biliforge.ui.components.HeroCard
import androidx.compose.foundation.isSystemInDarkTheme
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.feringsapp.biliforge.ui.components.OneLineItem
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.feringsapp.biliforge.core.shizuku.ShizukuBridge
import com.feringsapp.biliforge.work.ForgeCore
import com.feringsapp.biliforge.work.ForgeTask

/** 二级页：任务详情 */
@Composable
fun TasksScreen(onBack: () -> Unit) {
    ForgeLogger.render("TasksScreen")
    DisposableEffect(Unit) {
        ForgeLogger.trace("Screen", "TasksScreen 进入组合")
        onDispose { ForgeLogger.trace("Screen", "TasksScreen 离开组合") }
    }
    val tasks by ForgeCore.tasks.collectAsStateWithLifecycle()
    val active = tasks.count { it.isActive }
    val done = tasks.count { it.status == ForgeTask.Status.DONE }
    val hasFinished = tasks.any { !it.isActive }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            SmallTopAppBar(
                title = "任务详情",
                subtitle = "活跃 $active · 完成 $done",
                navigationIcon = {
                    Box(
                        Modifier
                            .padding(start = 8.dp)
                            .clip(CircleShape)
                            .clickable { onBack() }
                            .padding(8.dp),
                    ) {
                        Icon(imageVector = MiuixIcons.Back, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 12.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
        ) {
            item {
                HeroCard(
                    title = "任务队列",
                    subtitle = if (active > 0) "进行中 $active 个 · 已完成 $done 个" else "暂无进行中的任务 · 已完成 $done 个",
                    icon = MiuixIcons.Tasks,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            if (hasFinished) {
                item {
                    Card(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        OneLineItem(
                            title = "清理已结束任务",
                            onClick = { ForgeCore.clearFinished() },
                        )
                    }
                }
            }

            items(tasks.asReversed(), key = { it.id }) { task ->
                TaskCard(task)
            }

            if (tasks.isEmpty()) {
                item {
                    Card(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        OneLineItem(
                            title = "暂无任务",
                            summary = "去「提取」页发起 合成 / 转码 / 提取",
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskCard(task: ForgeTask) {
    ForgeLogger.render("TaskCard")
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var exportState by remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri: Uri? ->
        val src = task.outputPath
        if (uri != null && src != null) {
            scope.launch {
                exportState = "导出中…"
                val ok = exportToUri(ctx, src, uri) { done, total ->
                    exportState = if (total > 0) "导出中 ${done * 100 / total}%" else "导出中…"
                }
                exportState = if (ok) "已导出 ✅" else "导出失败"
            }
        }
    }

    val accent: Color = when (task.status) {
        ForgeTask.Status.QUEUED -> MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        ForgeTask.Status.RUNNING -> MiuixTheme.colorScheme.primary
        ForgeTask.Status.DONE -> Color(0xFF2FA84F)
        ForgeTask.Status.FAILED -> MiuixTheme.colorScheme.error
        ForgeTask.Status.CANCELLED -> Color(0xFFE8A33D)
    }
    val statusText = when (task.status) {
        ForgeTask.Status.QUEUED -> "排队中"
        ForgeTask.Status.RUNNING -> "${(task.progress * 100).toInt()}%"
        ForgeTask.Status.DONE -> "完成"
        ForgeTask.Status.FAILED -> "失败"
        ForgeTask.Status.CANCELLED -> "已取消"
    }

    Card(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        OneLineItem(
            title = task.type.label,
            summary = task.title,
            endActions = {
                Text(text = statusText, fontSize = 13.sp, color = accent)
            },
        )
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp),
        ) {
            LinearProgressIndicator(
                progress = if (task.status == ForgeTask.Status.DONE) 1f else task.progress,
                modifier = Modifier.fillMaxWidth(),
            )
            if (task.note.isNotBlank()) {
                Text(
                    text = task.note,
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            task.error?.let { err ->
                Text(
                    text = err,
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            task.outputPath?.let { path ->
                Text(
                    text = "→ $path",
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
        if (task.isActive) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TextButton("取消任务", onClick = { ForgeCore.cancel(task.id) })
            }
        }

        if (task.status == ForgeTask.Status.DONE && task.outputPath != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TextButton(
                    text = "导出到…",
                    onClick = {
                        val name = task.outputPath.substringAfterLast('/')
                        exportLauncher.launch(name)
                    },
                )
                exportState?.let {
                    Text(
                        text = it,
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }
        }
    }
}

/** 通过 Shizuku(shell) 读取产物并写入系统文件选择器返回的目标 Uri */
private suspend fun exportToUri(
    ctx: android.content.Context,
    srcPath: String,
    dst: Uri,
    onProgress: (done: Long, total: Long) -> Unit,
): Boolean = withContext(Dispatchers.IO) {
    val svc = ShizukuBridge.service.value ?: return@withContext false
    try {
        val total = runCatching { svc.fileSize(srcPath) }.getOrDefault(-1L)
        ctx.contentResolver.openOutputStream(dst, "w")?.use { out ->
            var offset = 0L
            val chunk = 512 * 1024
            while (true) {
                val bytes = svc.readChunk(srcPath, offset, chunk) ?: break
                if (bytes.isEmpty()) break
                out.write(bytes)
                offset += bytes.size
                onProgress(offset, total)
                if (bytes.size < chunk) break
            }
            out.flush()
        } ?: return@withContext false
        true
    } catch (t: Throwable) {
        false
    }
}
