package com.feringsapp.biliforge.ui.screens

import com.feringsapp.biliforge.BuildConfig
import androidx.compose.runtime.DisposableEffect
import com.feringsapp.biliforge.core.log.ForgeLogger
import top.yukonga.miuix.kmp.icon.extended.Info
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
import androidx.compose.ui.unit.sp
import com.feringsapp.biliforge.ui.components.OneLineItem
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 二级页：关于（版本 + 开源库） */
@Composable
fun AboutScreen(onBack: () -> Unit) {
    ForgeLogger.render("AboutScreen")
    DisposableEffect(Unit) {
        ForgeLogger.trace("Screen", "AboutScreen 进入组合")
        onDispose { ForgeLogger.trace("Screen", "AboutScreen 离开组合") }
    }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            SmallTopAppBar(
                title = "关于",
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
                    title = "BiliForge",
                    subtitle = "缓存熔炉 · v" + BuildConfig.VERSION_NAME,
                    icon = MiuixIcons.Info,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            item { SmallTitle("BiliForge") }
            item {
                Card(Modifier.padding(horizontal = 16.dp)) {
                    OneLineItem(title = "版本", summary = BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")")
                    OneLineItem(title = "简介", summary = "Shizuku 读缓存 · FFmpeg 处理")
                    OneLineItem(title = "运行要求", summary = "Android 13+ · Shizuku v11+")
                }
            }

            item { SmallTitle("引用的开源库") }
            item {
                Card(Modifier.padding(horizontal = 16.dp)) {
                    OneLineItem(title = "MIUIX", summary = "HyperOS 组件库 · Apache-2.0")
                    OneLineItem(title = "AndroidLiquidGlass", summary = "液态玻璃效果 · Apache-2.0")
                    OneLineItem(title = "AndroidX / Compose", summary = "UI 框架 · Apache-2.0")
                    OneLineItem(title = "Kotlin & Coroutines", summary = "语言与异步 · Apache-2.0")
                    OneLineItem(title = "Shizuku API", summary = "ADB 权限桥接 · RikkaApps")
                    OneLineItem(title = "FFmpeg 8.1.2", summary = "音视频引擎 · LGPL/GPL")
                }
            }

            item { SmallTitle("声明") }
            item {
                Card(Modifier.padding(horizontal = 16.dp)) {
                    OneLineItem(
                        title = "免责声明",
                        summary = "仅供个人学习与缓存备份使用。",
                    )
                }
            }
            item {
                Text(
                    text = "BiliForge · 缓存熔炉\nMade with Compose + MIUIX",
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                )
            }
        }
    }
}
