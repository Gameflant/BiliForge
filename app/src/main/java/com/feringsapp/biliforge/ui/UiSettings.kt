package com.feringsapp.biliforge.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** 全局 UI 开关（Compose 状态，设置页修改后主界面实时生效） */
object UiSettings {
    /** 液态玻璃底栏（关闭后底栏为纯色，且不再录制内容纹理 → 最省性能） */
    var glassEnabled by mutableStateOf(true)
}
