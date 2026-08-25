package com.przunk.kratkoza

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.przunk.kratkoza.domain.settings.GameSettings
import com.przunk.kratkoza.navigation.KratkozaNavHost
import com.przunk.kratkoza.ui.theme.KratkozaTheme

/**
 * The single activity of the app. technical_requirements.md section 4: one Activity, screens are
 * Compose destinations behind Navigation Compose.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val container = (application as KratkozaApplication).container

        setContent {
            // Read here rather than inside the theme so the whole tree recomposes on one value, and
            // the theme itself stays a pure function of its argument.
            val settings by container.settingsRepository.settings
                .collectAsStateWithLifecycle(initialValue = GameSettings.DEFAULT)

            KratkozaTheme(themeMode = settings.themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    KratkozaNavHost(container = container)
                }
            }
        }
    }
}
