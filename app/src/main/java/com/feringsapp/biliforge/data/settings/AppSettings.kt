package com.feringsapp.biliforge.data.settings

import android.content.Context

/** 应用设置（SharedPreferences 轻量存储） */
object AppSettings {

    private const val PREFS = "biliforge_settings"
    private const val KEY_OUTPUT_DIR = "output_dir"

    /** 预设输出目录（shell 可直接写入） */
    val PRESET_DIRS = listOf(
        "/sdcard/Movies/BiliForge",
        "/sdcard/Download/BiliForge",
        "/sdcard/DCIM/BiliForge",
    )

    fun outputDir(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_OUTPUT_DIR, null)
            ?.takeIf { it.isNotBlank() }
            ?: PRESET_DIRS[0]

    private const val KEY_GLASS = "liquid_glass_enabled"

    fun glassEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_GLASS, true)

    fun setGlassEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_GLASS, enabled)
            .apply()
    }

    fun setOutputDir(context: Context, dir: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_OUTPUT_DIR, dir.trim())
            .apply()
    }
}
