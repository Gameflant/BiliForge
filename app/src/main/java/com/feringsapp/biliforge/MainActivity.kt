package com.feringsapp.biliforge

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.feringsapp.biliforge.ui.ForgeApp
import com.feringsapp.biliforge.ui.theme.BiliForgeTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Android 16：edge-to-edge 强制模式，主动适配
        enableEdgeToEdge()
        setContent {
            BiliForgeTheme {
                ForgeApp()
            }
        }
    }
}
