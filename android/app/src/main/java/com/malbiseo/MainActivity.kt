package com.malbiseo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.malbiseo.ui.AppRoot
import com.malbiseo.ui.theme.MalbiseoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MalbiseoTheme {
                AppRoot()
            }
        }
    }
}
