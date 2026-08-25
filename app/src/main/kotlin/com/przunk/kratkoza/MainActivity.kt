package com.przunk.kratkoza

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.przunk.kratkoza.ui.theme.KratkozaTheme

/**
 * The single activity of the app. technical_requirements.md section 4: one Activity, screens are
 * Compose destinations behind Navigation Compose.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            KratkozaTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                }
            }
        }
    }
}
