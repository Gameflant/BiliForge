package com.feringsapp.biliforge.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.feringsapp.biliforge.media.ffmpeg.TranscodePreset
import com.feringsapp.biliforge.media.ffmpeg.TranscodePresets
import com.feringsapp.biliforge.ui.components.OneLineItem
import com.feringsapp.biliforge.ui.vm.LibraryViewModel
import com.feringsapp.biliforge.work.DanmakuFormat

/**
 * 二级页：视频处理（合成 / 转码 / 提取全部集中在此）。
 * 从「提取」页的缓存卡片点击进入。
 */
@Composable
fun ProcessScreen(dir: String, onBack: () -> Unit) {
    val vm: LibraryViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val bundle = state.bundles.firstOrNull { it.item.dir == dir }
    val info = state.mediaInfo[dir]
    var notice by remember { mutableStateOf<String?>(null) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            SmallTopAppBar(
                title = "视频处理",
                subtitle = bundle?.item?.partTitle?.take(20) ?: "",
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
        if (bundle == null) {
            Text(
                text = "缓存数据已失效，请返回重新扫描",
                fontSize = 14.sp,
                color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(24.dp, padding.calculateTopPadding()),
            )
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 12.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
        ) {
            item {
                Card(Modifier.padding(horizontal = 16.dp)) {
                    OneLineItem(
                        title = bundle.item.title,
                        summary = buildString {
                            append(info?.durationText ?: "--:--")
                            append(" · ")
                            append(info?.resolutionText ?: "读取中…")
                        },
                    )
                }
            }

            item { SmallTitle("输出") }
            item {
                Card(Modifier.padding(horizontal = 16.dp)) {
                    OneLineItem(
                        title = "合成 MP4",
                        summary = "m4s → MP4 · 秒级完成",
                        onClick = {
                            vm.merge(bundle)
                            notice = "已加入合成任务"
                        },
                    )
                }
            }

            item { SmallTitle("转码") }
            item {
                Card(Modifier.padding(horizontal = 16.dp)) {
                    TranscodePresets.ALL.forEach { preset: TranscodePreset ->
                        OneLineItem(
                            title = preset.label,
                            summary = preset.desc,
                            onClick = {
                                vm.transcode(bundle, preset)
                                notice = "已加入转码任务"
                            },
                        )
                    }
                }
            }

            item { SmallTitle("提取") }
            item {
                Card(Modifier.padding(horizontal = 16.dp)) {
                    OneLineItem(
                        title = "音频 · M4A",
                        summary = "AAC 192k",
                        onClick = {
                            vm.extractAudio(bundle, mp3 = false)
                            notice = "已加入音频提取任务"
                        },
                    )
                    OneLineItem(
                        title = "音频 · MP3",
                        summary = "LAME VBR",
                        onClick = {
                            vm.extractAudio(bundle, mp3 = true)
                            notice = "已加入音频提取任务"
                        },
                    )
                    OneLineItem(
                        title = "弹幕 · ASS",
                        summary = "可挂载播放",
                        onClick = {
                            vm.extractDanmaku(bundle, DanmakuFormat.ASS)
                            notice = "已加入弹幕提取任务"
                        },
                    )
                    OneLineItem(
                        title = "弹幕 · SRT",
                        summary = "通用字幕",
                        onClick = {
                            vm.extractDanmaku(bundle, DanmakuFormat.SRT)
                            notice = "已加入弹幕提取任务"
                        },
                    )
                }
            }

            notice?.let { msg ->
                item {
                    Text(
                        text = "$msg → 可在「提取」页任务条查看进度",
                        fontSize = 13.sp,
                        color = MiuixTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    )
                }
            }
        }
    }
}
