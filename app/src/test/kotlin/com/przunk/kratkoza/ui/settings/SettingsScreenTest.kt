package com.przunk.kratkoza.ui.settings

import android.content.Context
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.przunk.kratkoza.BuildConfig
import com.przunk.kratkoza.R
import com.przunk.kratkoza.data.repository.SettingsRepository
import com.przunk.kratkoza.domain.settings.BoardPalette
import com.przunk.kratkoza.domain.settings.GameSettings
import com.przunk.kratkoza.domain.settings.ThemeMode
import com.przunk.kratkoza.ui.theme.KratkozaTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "en-rUS")
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val repository = SettingsRepository(context)
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        viewModel = SettingsViewModel(repository)
        compose.setContent {
            KratkozaTheme(themeMode = ThemeMode.LIGHT) {
                SettingsScreen(viewModel = viewModel, onNavigateBack = {})
            }
        }
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `every settings section and the exact build version are shown`() {
        val sections = listOf(
            R.string.settings_language,
            R.string.settings_palette,
            R.string.settings_appearance,
            R.string.settings_gameplay,
            R.string.settings_about,
        )

        for (section in sections) scrollToText(context.getString(section))

        compose.onNodeWithText("${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            .assertIsDisplayed()
    }

    @Test
    fun `automatic candidate removal switch writes the new GameSettings value`() {
        val label = context.getString(R.string.settings_auto_remove_candidates)
        runBlocking { repository.setAutoRemoveCandidates(true) }
        dispatcher.scheduler.runCurrent()
        compose.waitForIdle()
        scrollToText(label)

        val toggle = compose.onNode(hasText(label) and hasClickAction())
        toggle.assertIsOn()
        toggle.performClick()
        dispatcher.scheduler.runCurrent()

        awaitSettings { !it.autoRemoveCandidates }
        dispatcher.scheduler.runCurrent()
        compose.waitForIdle()
        toggle.assertIsOff()
    }

    @Test
    fun `every palette has a preview and selecting it updates the radio state`() {
        val labels = mapOf(
            BoardPalette.SLATE to R.string.settings_palette_slate,
            BoardPalette.SEPIA to R.string.settings_palette_sepia,
            BoardPalette.FOREST to R.string.settings_palette_forest,
            BoardPalette.GRAPHITE to R.string.settings_palette_graphite,
        )

        for ((palette, labelRes) in labels) {
            val label = context.getString(labelRes)
            scrollToText(label)

            val option = compose.onNode(hasText(label) and hasClickAction())
            option.assertHasClickAction().performClick()
            dispatcher.scheduler.runCurrent()
            awaitSettings { it.palette == palette }
            compose.waitForIdle()
            option.assertIsSelected()

            compose.onNodeWithContentDescription(
                context.getString(R.string.settings_palette_preview, label),
            ).assertIsDisplayed()
        }
    }

    private fun scrollToText(text: String) {
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(text))
        compose.onNodeWithText(text).assertIsDisplayed()
    }

    private fun awaitSettings(predicate: (GameSettings) -> Boolean): GameSettings = runBlocking {
        withTimeout(5_000) { repository.settings.first(predicate) }
    }

}
