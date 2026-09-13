package com.feringsapp.biliforge.ui.liquid

/**
 * 液态玻璃效果分级开关（性能优化）。
 *
 * 重特效默认关闭以保证界面流畅（滚动/滑动不掉帧）：
 *  - 透镜折射（RuntimeShader 每帧采样，最重）
 *  - 陀螺仪倾斜光斑（传感器 50Hz 刷新）
 *  - 内阴影（额外图形层）
 *
 * 保留的轻量效果：高斯模糊 + 饱和度增强 + 静态镜面高光 + 投影 + 拖拽跟手动画。
 */
object LiquidGlassConfig {
    var refractionEnabled: Boolean = false
    var tiltLightEnabled: Boolean = false
    var innerShadowEnabled: Boolean = false
}
