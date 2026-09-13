package com.feringsapp.biliforge.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.feringsapp.biliforge.core.log.ForgeLogger
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val BgDeep = Color(0xFF0B0F16)
private val BgPanel = Color(0xFF141B26)
private val TextHi = Color(0xFFE6EDF7)
private val TextDim = Color(0xFF7D8DA5)

private val timeFmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

/** 运行日志页（不依赖 miuix，基础组件实现，稳定优先） */
@Composable
fun LogScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var filter by remember { mutableStateOf(ForgeLogger.Filter.SUPER) }
    val version by ForgeLogger.version.collectAsStateWithLifecycle()
    val entries = remember(version, filter) { ForgeLogger.snapshot(filter) }
    val listState = rememberLazyListState()

    LaunchedEffect(entries.size) {
        if (entries.isNotEmpty()) listState.scrollToItem(entries.lastIndex)
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri ->
        if (uri != null) {
            runCatching {
                ctx.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(ForgeLogger.exportText().toByteArray(Charsets.UTF_8))
                    out.flush()
                }
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(BgDeep)
    ) {
        // 顶栏
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LogButton("← 返回") { onBack() }
            Spacer(Modifier.width(10.dp))
            Text(
                text = "运行日志",
                color = TextHi,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            LogButton("清空", accent = Color(0xFFE8A33D)) { ForgeLogger.clear() }
            Spacer(Modifier.width(8.dp))
            LogButton("导出", accent = Color(0xFF4CC2FF)) {
                val name = "biliforge-log-${SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())}.txt"
                exportLauncher.launch(name)
            }
        }

        // 筛选
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ForgeLogger.Filter.entries.forEach { f ->
                LogFilterChip(
                    label = f.label,
                    selected = f == filter,
                    onClick = { filter = f },
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // 日志列表
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            items(entries.size) { i ->
                LogRow(entries[i])
            }
            if (entries.isEmpty()) {
                item {
                    Text(
                        text = "（暂无日志 — 操作应用后将在此实时显示）",
                        color = TextDim,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }

        Text(
            text = "共 ${entries.size} 条 · 自动跟随底部 · 导出为完整日志",
            color = TextDim,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun LogButton(text: String, accent: Color = TextDim, onClick: () -> Unit) {
    Text(
        text = text,
        color = accent,
        fontSize = 13.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(BgPanel)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

@Composable
private fun LogFilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        color = if (selected) Color(0xFF0B0F16) else TextHi,
        fontSize = 12.sp,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(if (selected) Color(0xFF4CC2FF) else BgPanel)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

@Composable
private fun LogRow(e: ForgeLogger.Entry) {
    val color = when {
        e.level >= 4 -> Color(0xFF56B6C2)   // 渲染追踪：终端青
        e.level == 3 -> Color(0xFFB07CFF)
        e.cat == ForgeLogger.Cat.TASK -> Color(0xFF4CC2FF)
        e.cat == ForgeLogger.Cat.SYS -> Color(0xFFE8A33D)
        e.level == 1 -> Color(0xFF8FE388)
        else -> Color(0xFFC9D4E3)
    }
    Text(
        text = "${timeFmt.format(Date(e.time))} [${e.cat.label}] ${e.tag}: ${e.msg}",
        color = color,
        fontSize = 11.sp,
        fontFamily = FontFamily.Monospace,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp),
    )
}
