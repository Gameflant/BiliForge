package com.feringsapp.biliforge.ui.screens

import androidx.compose.runtime.DisposableEffect
import com.feringsapp.biliforge.core.log.ForgeLogger
import top.yukonga.miuix.kmp.icon.extended.Play
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.feringsapp.biliforge.data.model.CacheBundle
import com.feringsapp.biliforge.media.ffmpeg.TranscodePresets
import com.feringsapp.biliforge.ui.components.OneLineItem
import com.feringsapp.biliforge.ui.vm.LibraryViewModel
import com.feringsapp.biliforge.work.DanmakuFormat

/** 二级页：缓存处理（合成 / 音频 / 弹幕 / 转码 全部操作入口） */
@Composable
fun CacheActionScreen(bundle: CacheBundle, onBack: () -> Unit) {
    ForgeLogger.render("CacheActionScreen")
    DisposableEffect(Unit) {
        ForgeLogger.trace("Screen", "CacheActionScreen 进入组合")
        onDispose { ForgeLogger.trace("Screen", "CacheActionScreen 离开组合") }
    }
    val vm: LibraryViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val info = state.mediaInfo[bundle.item.dir]

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            SmallTopAppBar(
                title = bundle.item.title,
                subtitle = "选择处理方式",
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
            // 媒体信息 Hero
            item {
                HeroCard(
                    title = bundle.item.title,
                    subtitle = "${info?.durationText ?: "--:--"} · ${info?.resolutionText ?: "读取中"} · ${fmtBytes(bundle.mediaBytes)}",
                    icon = MiuixIcons.Play,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            item { SmallTitle("合成") }
            item {
                Card(Modifier.padding(horizontal = 16.dp)) {
                    OneLineItem(
                        title = "合成完整视频（MP4）",
                        summary = "画面 + 声音，快速封装",
                        onClick = {
                            ForgeLogger.ui2("CacheAction", "选择: 合成完整视频")
                            vm.merge(bundle)
                        },
                    )
                }
            }

            item { SmallTitle("音频（最高质量）") }
            item {
                Card(Modifier.padding(horizontal = 16.dp)) {
                    OneLineItem(
                        title = "提取音频 · M4A 无损",
                        summary = "直接复制音轨 · 采样率保持源最高",
                        onClick = { vm.extractAudio(bundle, mp3 = false) },
                    )
                    OneLineItem(
                        title = "提取音频 · MP3 320k",
                        summary = "最高码率 · 48kHz",
                        onClick = { vm.extractAudio(bundle, mp3 = true) },
                    )
                }
            }

            item { SmallTitle("弹幕") }
            item {
                Card(Modifier.padding(horizontal = 16.dp)) {
                    OneLineItem(
                        title = "弹幕字幕 · ASS",
                        summary = "带样式，可挂载播放",
                        onClick = { vm.extractDanmaku(bundle, DanmakuFormat.ASS) },
                    )
                    OneLineItem(
                        title = "弹幕字幕 · SRT",
                        summary = "通用字幕格式",
                        onClick = { vm.extractDanmaku(bundle, DanmakuFormat.SRT) },
                    )
                }
            }

            item { SmallTitle("转码") }
            item {
                Card(Modifier.padding(horizontal = 16.dp)) {
                    TranscodePresets.ALL.forEach { preset ->
                        OneLineItem(
                            title = preset.label,
                            summary = preset.desc,
                            onClick = {
                                ForgeLogger.ui2("CacheAction", "选择转码: ${preset.label}")
                                vm.transcode(bundle, preset)
                            },
                        )
                    }
                }
            }

            item {
                Text(
                    text = "以上操作会加入任务队列，可在「提取」页任务条中查看进度",
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                )
            }
        }
    }
}
