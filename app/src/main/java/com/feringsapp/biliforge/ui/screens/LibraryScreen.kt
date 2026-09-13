package com.feringsapp.biliforge.ui.screens

import com.feringsapp.biliforge.core.log.ForgeLogger
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.util.Locale
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TextFieldDefaults
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.icon.extended.Folder
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.icon.extended.Scan
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.feringsapp.biliforge.data.model.CacheBundle
import com.feringsapp.biliforge.data.model.MediaInfo
import com.feringsapp.biliforge.ui.components.OneLineItem
import com.feringsapp.biliforge.ui.vm.LibraryViewModel
import com.feringsapp.biliforge.work.ForgeCore
import com.feringsapp.biliforge.work.ForgeTask

/** 一级页：提取（扫描主卡 + 任务条 + 搜索 + 视频卡片列表） */
@Composable
fun LibraryScreen(
    topPadding: Dp,
    onOpenTasks: () -> Unit,
    onOpenActions: (CacheBundle) -> Unit,
) {
    ForgeLogger.render("LibraryScreen")
    val vm: LibraryViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()

    val searchState = remember { TextFieldState() }
    val queryText = searchState.text.toString()
    LaunchedEffect(queryText) { vm.setQuery(queryText) }

    val filtered = remember(state.bundles, state.query) {
        val q = state.query.trim()
        if (q.isEmpty()) state.bundles
        else state.bundles.filter {
            it.item.title.contains(q, ignoreCase = true) ||
                it.item.partTitle.contains(q, ignoreCase = true)
        }
    }

    val bottomSpace = 64.dp + 36.dp + WindowInsets.navigationBars
        .only(WindowInsetsSides.Bottom)
        .asPaddingValues()
        .calculateBottomPadding() + 16.dp

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = topPadding + 12.dp, bottom = bottomSpace),
    ) {
        // ① 扫描主卡（蓝调大卡 + 水印图标）
        item {
            ScanHeroCard(
                state = state,
                onScan = {
                    if (!state.scanning) {
                        ForgeLogger.ui2("Library", "点击「立即扫描」")
                        vm.scan()
                    }
                },
            )
        }

        // ② 任务条（独立重组作用域：仅此组件随任务进度刷新，列表不受影响）
        item {
            TaskStatusSection(onOpenTasks = onOpenTasks)
        }

        // ③ 搜索
        if (state.bundles.isNotEmpty()) {
            item {
                Card(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Search,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            modifier = Modifier.size(20.dp),
                        )
                        TextField(
                            state = searchState,
                            label = "搜索标题",
                            useLabelAsPlaceholder = true,
                            colors = TextFieldDefaults.textFieldColors(
                                backgroundColor = Color.Transparent,
                                labelColor = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 8.dp),
                        )
                    }
                }
            }
            item { SmallTitle("缓存（${filtered.size} / ${state.bundles.size}）") }
        }

        // ④ 视频卡片
        items(filtered, key = { it.item.dir }) { bundle ->
            VideoCard(
                bundle = bundle,
                info = state.mediaInfo[bundle.item.dir],
                onClick = {
                    ForgeLogger.ui2("Library", "点击缓存: ${bundle.item.title}")
                    onOpenActions(bundle)
                },
            )
        }

        // ⑤ 空态
        if (state.bundles.isEmpty() && !state.scanning) {
            item { EmptyState() }
        } else if (filtered.isEmpty() && state.bundles.isNotEmpty()) {
            item {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "没有匹配的缓存",
                        fontSize = 14.sp,
                        color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    )
                }
            }
        }
    }
}

/** 扫描主卡：蓝调容器 + 大水印扫描图标（与主页状态卡同一设计语言） */
@Composable
private fun ScanHeroCard(state: LibraryViewModel.UiState, onScan: () -> Unit) {
    ForgeLogger.render("ScanHeroCard")
    val isDark = isSystemInDarkTheme()
    val container = if (isDark) Color(0xFF15294A) else Color(0xFFE3EDFF)
    val textColor = if (isDark) Color.White else Color(0xFF10203C)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.defaultColors(color = container),
        onClick = onScan,
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // 水印扫描图标
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .offset(46.dp, 28.dp),
                contentAlignment = Alignment.BottomEnd,
            ) {
                Icon(
                    modifier = Modifier.size(148.dp),
                    imageVector = MiuixIcons.Scan,
                    tint = MiuixTheme.colorScheme.primary.copy(alpha = 0.28f),
                    contentDescription = null,
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Text(
                    text = if (state.scanning) "正在扫描…" else "缓存扫描",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = textColor,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = when {
                        state.scanning -> "已扫描 ${state.scannedParts} 个分P"
                        state.bundles.isNotEmpty() -> "已找到 ${state.bundles.size} 个视频"
                        else -> "扫描 B 站客户端缓存"
                    },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = textColor.copy(alpha = 0.8f),
                )
                state.error?.let { err ->
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = err,
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.error,
                    )
                }
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = onScan,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.scanning,
                ) {
                    Text(if (state.scanning) "扫描中…" else "立即扫描")
                }
            }
        }
    }
}

/** 视频卡片：左侧伪缩略块（渐变 + 播放图标 + 时长角标） */
@Composable
private fun VideoCard(
    bundle: CacheBundle,
    info: MediaInfo?,
    onClick: () -> Unit,
) {
    ForgeLogger.render("VideoCard")
    val item = bundle.item
    Card(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 伪缩略图
            Box(
                Modifier
                    .size(width = 78.dp, height = 52.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF33507E), Color(0xFF18233A))
                        )
                    ),
            ) {
                Icon(
                    imageVector = MiuixIcons.Play,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.92f),
                    modifier = Modifier
                        .size(18.dp)
                        .align(Alignment.Center),
                )
                // 时长角标
                Text(
                    text = info?.durationText ?: "--:--",
                    fontSize = 9.sp,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "${info?.resolutionText ?: "读取中…"} · ${fmtBytes(bundle.mediaBytes)}" +
                        if (item.pageCount > 1) " · ${item.pageLabel}" else "",
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                imageVector = MiuixIcons.ChevronForward,
                contentDescription = "进入处理页",
                tint = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                modifier = Modifier
                    .padding(start = 6.dp)
                    .size(18.dp),
            )
        }
    }
}

/** 空态：居中大图标 + 引导文案 */
@Composable
private fun EmptyState() {
    ForgeLogger.render("EmptyState")
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 48.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = MiuixIcons.Folder,
            contentDescription = null,
            tint = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.16f),
            modifier = Modifier.size(88.dp),
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = "暂无缓存",
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.75f),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "先在哔哩哔哩客户端缓存视频\n再点击上方「立即扫描」",
            fontSize = 13.sp,
            lineHeight = 20.sp,
            textAlign = TextAlign.Center,
            color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        )
    }
}

/**
 * 任务状态条（独立 Composable = 独立重组作用域）。
 * 任务进度流只驱动这一小块刷新，不会波及整个缓存列表。
 */
@Composable
private fun TaskStatusSection(onOpenTasks: () -> Unit) {
    ForgeLogger.render("TaskStatusSection")
    val tasks by ForgeCore.tasks.collectAsStateWithLifecycle()
    val activeTask = tasks.firstOrNull { it.isActive }

    Card(Modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 4.dp)) {
        OneLineItem(
            title = "任务",
            summary = activeTask?.let {
                "${it.title} · ${(it.progress * 100).toInt()}%"
            } ?: "暂无进行中的任务",
            endActions = {
                Icon(
                    imageVector = MiuixIcons.ChevronForward,
                    contentDescription = "进入任务详情",
                    tint = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    modifier = Modifier.size(20.dp),
                )
            },
            onClick = onOpenTasks,
        )
        LinearProgressIndicator(
            progress = activeTask?.progress ?: 0f,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, top = 0.dp, end = 20.dp, bottom = 8.dp),
        )
    }
}

internal fun fmtBytes(b: Long): String = when {
    b >= 1L shl 30 -> String.format(Locale.US, "%.2f GB", b / 1073741824.0)
    b >= 1L shl 20 -> String.format(Locale.US, "%.1f MB", b / 1048576.0)
    b >= 1L shl 10 -> String.format(Locale.US, "%.0f KB", b / 1024.0)
    else -> "$b B"
}
