package com.przunk.kratkoza

import android.content.Context
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
import com.przunk.kratkoza.domain.settings.AppLanguage
import com.przunk.kratkoza.domain.settings.GameSettings
import com.przunk.kratkoza.navigation.KratkozaNavHost
import com.przunk.kratkoza.ui.theme.KratkozaTheme
import com.przunk.kratkoza.util.AppLocales
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * The single activity of the app. technical_requirements.md section 4: one Activity, screens are
 * Compose destinations behind Navigation Compose.
 */
class MainActivity : ComponentActivity() {

    /**
     * Applies the stored language before any resource is read (project.md section 4).
     *
     * Only runs the wrapping on API 29–32; from 33 the platform has already resolved resources by
     * the time this is called. The read is blocking because there is no later point at which a
     * context's locale can still be changed — a few milliseconds of disk once per activity, and only
     * on the older path.
     */
    override fun attachBaseContext(newBase: Context) {
        val language = if (AppLocales.handledByApp) storedLanguage(newBase) else AppLanguage.SYSTEM
        super.attachBaseContext(AppLocales.wrap(newBase, language))
    }

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

    private fun storedLanguage(context: Context): AppLanguage = runBlocking {
        com.przunk.kratkoza.data.repository.SettingsRepository(context).settings.first().language
    }
}
