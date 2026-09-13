package com.feringsapp.biliforge.ui.screens

import androidx.compose.runtime.DisposableEffect
import com.feringsapp.biliforge.core.log.ForgeLogger
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.feringsapp.biliforge.R
import com.feringsapp.biliforge.core.shizuku.ShizukuBridge
import com.feringsapp.biliforge.data.settings.AppSettings
import com.feringsapp.biliforge.ui.components.OneLineItem
import com.feringsapp.biliforge.work.EngineStatus
import com.feringsapp.biliforge.work.ForgeCore

/** 状态卡配色（照搬 InstallerX-Revived） */
private val StatusGreen = Color(0xFF36D167)
private val StatusRed = Color(0xFFD13636)
private val CardGreenDark = Color(0xFF1A3825)
private val CardGreenLight = Color(0xFFDFFAE4)
private val CardRedDark = Color(0xFF381A1A)
private val CardRedLight = Color(0xFFFAEEEE)

@Composable
fun HomeScreen(topPadding: Dp, onOpenServices: () -> Unit) {
    ForgeLogger.render("HomeScreen")
    DisposableEffect(Unit) {
        ForgeLogger.trace("Screen", "HomeScreen 进入组合")
        onDispose { ForgeLogger.trace("Screen", "HomeScreen 离开组合") }
    }
    val phase by ShizukuBridge.phase.collectAsStateWithLifecycle()
    val detail by ShizukuBridge.detail.collectAsStateWithLifecycle()
    val ctx = LocalContext.current

    LaunchedEffect(phase) {
        if (phase == ShizukuBridge.Phase.READY) {
            ForgeCore.maybeAutoInstall()
            EngineStatus.refresh()
        }
    }

    val ffVersionRaw = EngineStatus.version
    val ffVersion = ffVersionRaw
        ?.let { raw ->
            raw.substringAfter("version ", raw).substringBefore(" ").takeIf { it.isNotBlank() } ?: raw
        }
    val shizukuReady = phase == ShizukuBridge.Phase.READY
    val allReady = shizukuReady && EngineStatus.installed
    val isDark = isSystemInDarkTheme()

    val bottomSpace = 64.dp + 36.dp + WindowInsets.navigationBars
        .only(WindowInsetsSides.Bottom)
        .asPaddingValues()
        .calculateBottomPadding() + 16.dp

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = topPadding + 12.dp, bottom = bottomSpace),
    ) {
        // ===== 大状态卡（参数与 InstallerX 完全一致）=====
        item {
            Box(Modifier.padding(horizontal = 16.dp)) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.defaultColors(
                        color = if (allReady) {
                            if (isDark) CardGreenDark else CardGreenLight
                        } else {
                            if (isDark) CardRedDark else CardRedLight
                        }
                    ),
                    onClick = onOpenServices,
                ) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        val textColor = if (isDark) Color.White else Color(0xFF1A1A1A)
                        // 水印图标：BottomEnd + offset(50, 38) + 170dp（照搬 InstallerX）
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .offset(50.dp, 38.dp),
                            contentAlignment = Alignment.BottomEnd,
                        ) {
                            Icon(
                                modifier = Modifier.size(170.dp),
                                imageVector = if (allReady) {
                                    ImageVector.vectorResource(R.drawable.ic_status_active)
                                } else {
                                    ImageVector.vectorResource(R.drawable.ic_status_inactive)
                                },
                                tint = (if (allReady) StatusGreen else StatusRed).copy(alpha = 0.8f),
                                contentDescription = null,
                            )
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                        ) {
                            // Slot 1: 主状态（20sp SemiBold）
                            Text(
                                text = if (allReady) "服务运行正常" else "服务未就绪",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = textColor,
                            )
                            Spacer(Modifier.height(2.dp))
                            // Slot 2: 状态描述（14sp Medium · alpha 0.8）
                            Text(
                                text = if (allReady) "Shizuku 已连接 · FFmpeg 就绪" else "点击配置服务",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = textColor.copy(alpha = 0.8f),
                            )
                            Spacer(Modifier.height(36.dp))
                            // Slot 3: 运行模式（14sp Medium · alpha 0.8）
                            Text(
                                text = if (allReady) "Shizuku · ADB shell" else "未连接",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = textColor.copy(alpha = 0.8f),
                            )
                        }
                    }
                }
            }
        }

        item { SmallTitle("运行状态") }
        item {
            Card(Modifier.padding(horizontal = 16.dp)) {
                OneLineItem(title = "Shizuku 通道", summary = detail)
                OneLineItem(
                    title = "FFmpeg 引擎",
                    summary = when {
                        !ffVersion.isNullOrBlank() -> ffVersion
                        shizukuReady -> "未安装"
                        else -> "等待 Shizuku"
                    },
                )
                OneLineItem(title = "输出目录", summary = AppSettings.outputDir(ctx))
            }
        }

        item {
            Text(
                text = "BiliForge · 缓存熔炉 · 仅供个人学习与缓存备份使用",
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            )
        }
    }
}
