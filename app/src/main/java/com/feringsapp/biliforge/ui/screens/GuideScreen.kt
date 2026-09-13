package com.feringsapp.biliforge.ui.screens

import androidx.compose.runtime.DisposableEffect
import com.feringsapp.biliforge.core.log.ForgeLogger
import top.yukonga.miuix.kmp.icon.extended.Help
import com.feringsapp.biliforge.ui.components.HeroColors
import com.feringsapp.biliforge.ui.components.HeroCard
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.feringsapp.biliforge.ui.components.OneLineItem
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back

/** 二级页：使用指南（从主页移入设置） */
@Composable
fun GuideScreen(onBack: () -> Unit) {
    ForgeLogger.render("GuideScreen")
    DisposableEffect(Unit) {
        ForgeLogger.trace("Screen", "GuideScreen 进入组合")
        onDispose { ForgeLogger.trace("Screen", "GuideScreen 离开组合") }
    }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            SmallTopAppBar(
                title = "使用指南",
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
                    title = "使用指南",
                    subtitle = "首次使用三步上手",
                    icon = MiuixIcons.Help,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            item { SmallTitle("首次使用") }
            item {
                Card(Modifier.padding(horizontal = 16.dp)) {
                    OneLineItem(
                        title = "01 · 启动 Shizuku",
                        summary = "安装 Shizuku 并启动，在主页完成授权",
                    )
                    OneLineItem(
                        title = "02 · 安装 FFmpeg 引擎",
                        summary = "Shizuku 就绪后自动安装，进度见任务条",
                    )
                    OneLineItem(
                        title = "03 · 缓存视频",
                        summary = "用哔哩哔哩客户端缓存视频",
                    )
                }
            }

            item { SmallTitle("日常使用") }
            item {
                Card(Modifier.padding(horizontal = 16.dp)) {
                    OneLineItem(
                        title = "扫描缓存",
                        summary = "提取页 → 扫描缓存",
                    )
                    OneLineItem(
                        title = "发起处理",
                        summary = "点卡片展开：合成 / 转码 / 提取",
                    )
                    OneLineItem(
                        title = "查看进度",
                        summary = "提取页任务条 → 任务详情",
                    )
                    OneLineItem(
                        title = "格式转换",
                        summary = "设置 → 格式转换",
                    )
                }
            }

            item { SmallTitle("输出说明") }
            item {
                Card(Modifier.padding(horizontal = 16.dp)) {
                    OneLineItem(
                        title = "输出位置",
                        summary = "可在设置中修改输出目录",
                    )
                    OneLineItem(
                        title = "文件命名",
                        summary = "[BiliForge]标题_分P_ID.扩展名",
                    )
                    OneLineItem(
                        title = "清理提示",
                        summary = "转换的中间文件在 Download/BiliForge/inbox",
                    )
                }
            }
        }
    }
}
