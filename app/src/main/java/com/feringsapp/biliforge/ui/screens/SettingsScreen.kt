package com.feringsapp.biliforge.ui.screens

import androidx.compose.runtime.DisposableEffect
import com.feringsapp.biliforge.core.log.ForgeLogger
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.feringsapp.biliforge.ui.components.OneLineItem
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.TextFieldDefaults
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.preference.ArrowPreference
import com.feringsapp.biliforge.data.settings.AppSettings
import com.feringsapp.biliforge.ui.UiSettings

/** 一级页：设置（输出目录 / 格式转换 / 关于） */
@Composable
fun SettingsScreen(
    topPadding: Dp,
    onOpenConvert: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenGuide: () -> Unit,
    onOpenDebug: () -> Unit,
) {
    ForgeLogger.render("SettingsScreen")
    DisposableEffect(Unit) {
        ForgeLogger.trace("Screen", "SettingsScreen 进入组合")
        onDispose { ForgeLogger.trace("Screen", "SettingsScreen 离开组合") }
    }

    val ctx = LocalContext.current
    var outputDir by remember { mutableStateOf(AppSettings.outputDir(ctx)) }
    var dirExpanded by remember { mutableStateOf(false) }
    val customState = remember { TextFieldState() }

    val bottomSpace = 64.dp + 36.dp + WindowInsets.navigationBars
        .only(WindowInsetsSides.Bottom)
        .asPaddingValues()
        .calculateBottomPadding() + 16.dp

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = topPadding + 12.dp, bottom = bottomSpace),
    ) {
        item { SmallTitle("输出") }
        item {
            Card(Modifier.padding(horizontal = 16.dp)) {
                ArrowPreference(
                    title = "输出目录",
                    summary = outputDir,
                    onClick = { dirExpanded = !dirExpanded },
                )
                if (dirExpanded) {
                    AppSettings.PRESET_DIRS.forEach { dir ->
                        OneLineItem(
                            title = dir,
                            summary = if (dir == outputDir) "当前使用" else null,
                            onClick = {
                                ForgeLogger.ui2("Settings", "输出目录 → $dir")
                                AppSettings.setOutputDir(ctx, dir)
                                outputDir = dir
                                dirExpanded = false
                            },
                        )
                    }
                    OneLineItem(
                        title = "自定义路径",
                        summary = "在下方输入框填写后点「应用」",
                    )
                    TextField(
                        state = customState,
                        label = "如 /sdcard/Download/MyVideos",
                        useLabelAsPlaceholder = true,
                        colors = TextFieldDefaults.textFieldColors(
                            backgroundColor = Color.Transparent,
                            labelColor = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 2.dp),
                    ) {
                        TextButton(
                            text = "应用自定义路径",
                            onClick = {
                                val p = customState.text.toString().trim()
                                if (p.isNotBlank()) {
                                    AppSettings.setOutputDir(ctx, p)
                                    outputDir = p
                                    dirExpanded = false
                                }
                            },
                        )
                    }
                }
            }
        }

        item { SmallTitle("界面") }
        item {
            Card(Modifier.padding(horizontal = 16.dp)) {
                OneLineItem(
                    title = "液态玻璃底栏",
                    summary = if (UiSettings.glassEnabled) "已开启 · 毛玻璃 + 折射高光" else "已关闭 · 更省性能",
                    endActions = {
                        Switch(
                            checked = UiSettings.glassEnabled,
                            onCheckedChange = { v ->
                                ForgeLogger.ui2("Settings", "液态玻璃 → " + if (v) "开" else "关")
                                UiSettings.glassEnabled = v
                                AppSettings.setGlassEnabled(ctx, v)
                            },
                        )
                    },
                )
            }
        }

        item { SmallTitle("工具") }
        item {
            Card(Modifier.padding(horizontal = 16.dp)) {
                ArrowPreference(
                    title = "使用指南",
                    summary = "首次流程与操作说明",
                    onClick = onOpenGuide,
                )
                ArrowPreference(
                    title = "格式转换",
                    summary = "文件转 MP4 / 提取音频",
                    onClick = onOpenConvert,
                )
            }
        }

        item { SmallTitle("帮助") }
        item {
            Card(Modifier.padding(horizontal = 16.dp)) {
                ArrowPreference(
                    title = "使用说明",
                    summary = "运行流程 · 注意事项 · 免责声明",
                    onClick = onOpenGuide,
                )
            }
        }

        item { SmallTitle("调试") }
        item {
            Card(Modifier.padding(horizontal = 16.dp)) {
                ArrowPreference(
                    title = "运行日志",
                    summary = "UI / 任务 / 系统 全量记录",
                    onClick = onOpenDebug,
                )
            }
        }

        item { SmallTitle("关于") }
        item {
            Card(Modifier.padding(horizontal = 16.dp)) {
                ArrowPreference(
                    title = "关于 BiliForge",
                    summary = "版本与开源许可",
                    onClick = onOpenAbout,
                )
            }
        }
    }
}
