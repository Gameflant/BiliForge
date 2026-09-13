package com.feringsapp.biliforge.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

/**
 * BiliForge 主题：MIUIX（HyperOS 设计语言）包装。
 * 深色/浅色跟随系统；主色使用 MIUIX 默认 HyperOS 蓝。
 */
@Composable
fun BiliForgeTheme(content: @Composable () -> Unit) {
    MiuixTheme(
        colors = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme(),
        content = content,
    )
}
