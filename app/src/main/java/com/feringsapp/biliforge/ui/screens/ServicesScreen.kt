package com.feringsapp.biliforge.ui.screens

import androidx.compose.runtime.DisposableEffect
import com.feringsapp.biliforge.core.log.ForgeLogger
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.extended.Info
import com.feringsapp.biliforge.ui.components.HeroColors
import com.feringsapp.biliforge.ui.components.HeroCard
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import com.feringsapp.biliforge.ui.components.OneLineItem
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import com.feringsapp.biliforge.core.shizuku.RemoteFs
import com.feringsapp.biliforge.core.shizuku.ShizukuBridge
import com.feringsapp.biliforge.media.ffmpeg.FFmpegBin
import com.feringsapp.biliforge.work.ForgeCore

/** 二级页：服务设置（Shizuku 授权 / FFmpeg 引擎） */
@Composable
fun ServicesScreen(onBack: () -> Unit) {
    ForgeLogger.render("ServicesScreen")
    DisposableEffect(Unit) {
        ForgeLogger.trace("Screen", "ServicesScreen 进入组合")
        onDispose { ForgeLogger.trace("Screen", "ServicesScreen 离开组合") }
    }
    val phase by ShizukuBridge.phase.collectAsStateWithLifecycle()
    val detail by ShizukuBridge.detail.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var ffVersion by remember { mutableStateOf<String?>(null) }
    var ffStatus by remember { mutableStateOf<String?>(null) }
    var ffBusy by remember { mutableStateOf(false) }

    LaunchedEffect(phase) {
        if (phase == ShizukuBridge.Phase.READY && ffVersion == null) {
            val svc = ShizukuBridge.service.value
            if (svc != null) {
                val bin = FFmpegBin(svc, RemoteFs(svc))
                if (bin.isInstalled()) ffVersion = bin.probeVersion()
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            SmallTopAppBar(
                title = "服务设置",
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
                val ready = phase == ShizukuBridge.Phase.READY
                val isDark = isSystemInDarkTheme()
                HeroCard(
                    title = if (ready) "服务运行正常" else "服务未就绪",
                    subtitle = detail,
                    icon = if (ready) MiuixIcons.Ok else MiuixIcons.Info,
                    containerColor = if (ready) {
                        if (isDark) HeroColors.GreenDark else HeroColors.GreenLight
                    } else {
                        if (isDark) HeroColors.RedDark else HeroColors.RedLight
                    },
                    contentColor = if (isDark) Color.White else Color(0xFF1A1A1A),
                    iconTint = if (ready) HeroColors.GreenAccent else HeroColors.RedAccent,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            item { SmallTitle("Shizuku") }
            item {
                Card(Modifier.padding(horizontal = 16.dp)) {
                    OneLineItem(title = "连接状态", summary = detail)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        when (phase) {
                            ShizukuBridge.Phase.NEED_PERMISSION ->
                                TextButton("申请权限", onClick = { ShizukuBridge.requestPermission() })
                            ShizukuBridge.Phase.DENIED, ShizukuBridge.Phase.NO_BINDER ->
                                TextButton("重新检测", onClick = { ShizukuBridge.refresh() })
                            ShizukuBridge.Phase.BINDING ->
                                TextButton("取消绑定", onClick = { ShizukuBridge.unbind() })
                            ShizukuBridge.Phase.READY ->
                                TextButton("断开连接", onClick = { ShizukuBridge.unbind() })
                        }
                        TextButton("刷新状态", onClick = { ShizukuBridge.refresh() })
                    }
                }
            }

            item { SmallTitle("FFmpeg 引擎") }
            item {
                Card(Modifier.padding(horizontal = 16.dp)) {
                    OneLineItem(
                        title = "版本",
                        summary = ffVersion ?: ffStatus ?: "未安装",
                    )
                    OneLineItem(
                        title = "安装位置",
                        summary = "/data/local/tmp/biliforge",
                    )
                    OneLineItem(
                        title = "引擎来源",
                        summary = "内置 · Termux 官方 8.1.2",
                    )
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        TextButton(
                            text = if (ffBusy) "处理中…" else "安装 / 探测",
                            onClick = {
                                if (ffBusy) return@TextButton
                                scope.launch {
                                    ffBusy = true
                                    ffStatus = "检查中…"
                                    val svc = ShizukuBridge.service.value
                                    if (svc == null) {
                                        ffStatus = "Shizuku 未就绪"
                                    } else {
                                        val bin = FFmpegBin(svc, RemoteFs(svc))
                                        if (bin.isInstalled()) {
                                            ffVersion = bin.probeVersion() ?: "已安装（版本探测超时）"
                                            ffStatus = null
                                        } else {
                                            ForgeCore.enqueueInstallFfmpeg()
                                            ffStatus = "已加入安装任务"
                                        }
                                    }
                                    ffBusy = false
                                }
                            },
                        )
                        if (ffVersion != null) {
                            TextButton("重新探测", onClick = {
                                scope.launch {
                                    val svc = ShizukuBridge.service.value ?: return@launch
                                    ffVersion = FFmpegBin(svc, RemoteFs(svc)).probeVersion() ?: ffVersion
                                }
                            })
                        }
                    }
                }
            }

            item {
                Text(
                    text = "FFmpeg 随 APK 内置，首次使用自动安装（约 10~30 秒）。",
                    fontSize = 12.sp,
                    color = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                )
            }
        }
    }
}
