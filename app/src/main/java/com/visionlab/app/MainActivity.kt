package com.visionlab.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.visionlab.app.ui.VisionLabScreen
import com.visionlab.app.ui.theme.VisionLabTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VisionLabTheme {
                VisionLabScreen()
            }
        }
    }
}
