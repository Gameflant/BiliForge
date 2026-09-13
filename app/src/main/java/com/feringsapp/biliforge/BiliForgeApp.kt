package com.feringsapp.biliforge

import android.app.Application
import com.feringsapp.biliforge.core.shizuku.ShizukuBridge
import com.feringsapp.biliforge.data.settings.AppSettings
import com.feringsapp.biliforge.ui.UiSettings
import com.feringsapp.biliforge.work.ForgeCore

class BiliForgeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ShizukuBridge.init(this)
        ForgeCore.attach(this)
        UiSettings.glassEnabled = AppSettings.glassEnabled(this)
    }
}
