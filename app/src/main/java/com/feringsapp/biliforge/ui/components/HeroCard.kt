package com.feringsapp.biliforge.ui.components

import com.feringsapp.biliforge.core.log.ForgeLogger
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 页面头部 Hero 卡（与主页状态卡 / 提取页扫描卡同一设计语言）：
 * 大水印图标 + 20sp SemiBold 标题 + 14sp Medium 副文案（+ 可选附加内容，如主按钮）。
 */
@Composable
fun HeroCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    containerColor: Color? = null,
    contentColor: Color? = null,
    iconTint: Color? = null,
    onClick: (() -> Unit)? = null,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    ForgeLogger.render("HeroCard")
    DisposableEffect(title) {
        ForgeLogger.trace("HeroCard", "组合: $title")
        onDispose { }
    }

    val isDark = isSystemInDarkTheme()
    val container = containerColor ?: (if (isDark) Color(0xFF15294A) else Color(0xFFE3EDFF))
    val textColor = contentColor ?: (if (isDark) Color.White else Color(0xFF10203C))
    val tint = iconTint ?: MiuixTheme.colorScheme.primary

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.defaultColors(color = container),
        onClick = onClick,
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .offset(46.dp, 28.dp),
                contentAlignment = Alignment.BottomEnd,
            ) {
                Icon(
                    modifier = Modifier.size(148.dp),
                    imageVector = icon,
                    tint = tint.copy(alpha = 0.28f),
                    contentDescription = null,
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Text(
                    text = title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = textColor,
                    maxLines = 1,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = textColor.copy(alpha = 0.8f),
                    maxLines = 1,
                )
                if (content != null) {
                    Spacer(Modifier.height(12.dp))
                    content()
                }
            }
        }
    }
}

/** Hero 卡配色（绿/红状态变体，照搬 InstallerX） */
object HeroColors {
    val GreenDark = Color(0xFF1A3825)
    val GreenLight = Color(0xFFDFFAE4)
    val RedDark = Color(0xFF381A1A)
    val RedLight = Color(0xFFFAEEEE)
    val GreenAccent = Color(0xFF36D167)
    val RedAccent = Color(0xFFD13636)
}
