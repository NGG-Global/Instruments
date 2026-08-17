package com.ngg.instruments

import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.ngg.instruments.ui.InstrumentsApp
import com.ngg.instruments.ui.theme.InstrumentsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Edge-to-edge with explicitly transparent bars and light icons, set
        // in code (not themes.xml). The activity handles configuration changes
        // itself, so this survives rotation.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        // Keep the screen awake while the live instrument panel is in use.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val container = (application as InstrumentsApplication).container
        setContent {
            InstrumentsTheme {
                InstrumentsApp(container)
            }
        }
    }
}
