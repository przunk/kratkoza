package com.przunk.kratkoza.ui.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.przunk.kratkoza.BuildConfig
import com.przunk.kratkoza.R
import com.przunk.kratkoza.domain.settings.AppLanguage
import com.przunk.kratkoza.domain.settings.BoardPalette
import com.przunk.kratkoza.domain.settings.ThemeMode
import com.przunk.kratkoza.ui.common.KratkozaScaffold
import com.przunk.kratkoza.ui.theme.Palettes
import com.przunk.kratkoza.ui.theme.isDarkTheme
import com.przunk.kratkoza.util.AppLocales

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current

    KratkozaScaffold(
        title = stringResource(R.string.settings_title),
        modifier = modifier,
        onNavigateBack = onNavigateBack,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SettingsSection(stringResource(R.string.settings_appearance)) {
                    Text(
                        text = stringResource(R.string.settings_theme),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                    // A radio group rather than three switches: the three modes are one choice, and
                    // TalkBack should announce them as such.
                    Column(modifier = Modifier.selectableGroup()) {
                        ThemeOption(ThemeMode.SYSTEM, R.string.settings_theme_system, settings.themeMode, viewModel::setThemeMode)
                        ThemeOption(ThemeMode.LIGHT, R.string.settings_theme_light, settings.themeMode, viewModel::setThemeMode)
                        ThemeOption(ThemeMode.DARK, R.string.settings_theme_dark, settings.themeMode, viewModel::setThemeMode)
                    }
                }
            }

            item {
                SettingsSection(stringResource(R.string.settings_palette)) {
                    // Previews are drawn for the theme that is actually in force, so what is on
                    // screen is what the board will look like — not a light sample above a dark app.
                    val dark = isDarkTheme(settings.themeMode)

                    Column(modifier = Modifier.selectableGroup()) {
                        for (palette in BoardPalette.entries) {
                            PaletteOption(
                                palette = palette,
                                selected = settings.palette,
                                dark = dark,
                                highContrast = settings.highContrast,
                                onSelect = viewModel::setPalette,
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    SwitchRow(
                        title = stringResource(R.string.settings_high_contrast),
                        summary = stringResource(R.string.settings_high_contrast_summary),
                        checked = settings.highContrast,
                        onCheckedChange = viewModel::setHighContrast,
                    )
                }
            }

            item {
                SettingsSection(stringResource(R.string.settings_language)) {
                    // project.md section 4. Below API 33 Android offers no per-app language at all,
                    // and even above it the picker is buried in system settings, so the app carries
                    // its own.
                    // Below API 33 the locale is baked into the activity's base context, so the
                    // choice can only take effect by building a new one — and only once the write
                    // has landed, or the rebuilt activity would read the old value back.
                    val applyLanguage: (AppLanguage) -> Unit = { language ->
                        viewModel.setLanguage(language) {
                            if (AppLocales.handledByApp) context.findActivity()?.recreate()
                        }
                    }

                    Column(modifier = Modifier.selectableGroup()) {
                        LanguageOption(AppLanguage.SYSTEM, R.string.settings_language_system, settings.language, applyLanguage)
                        LanguageOption(AppLanguage.ENGLISH, R.string.settings_language_english, settings.language, applyLanguage)
                        LanguageOption(AppLanguage.POLISH, R.string.settings_language_polish, settings.language, applyLanguage)
                    }
                }
            }

            item {
                SettingsSection(stringResource(R.string.settings_gameplay)) {
                    SwitchRow(
                        title = stringResource(R.string.settings_auto_remove_candidates),
                        summary = stringResource(R.string.settings_auto_remove_candidates_summary),
                        checked = settings.autoRemoveCandidates,
                        onCheckedChange = viewModel::setAutoRemoveCandidates,
                    )
                    SwitchRow(
                        title = stringResource(R.string.settings_highlight_same_digits),
                        summary = stringResource(R.string.settings_highlight_same_digits_summary),
                        checked = settings.highlightSameDigits,
                        onCheckedChange = viewModel::setHighlightSameDigits,
                    )
                    SwitchRow(
                        title = stringResource(R.string.settings_show_remaining_counts),
                        summary = stringResource(R.string.settings_show_remaining_counts_summary),
                        checked = settings.showRemainingCounts,
                        onCheckedChange = viewModel::setShowRemainingCounts,
                    )
                    SwitchRow(
                        title = stringResource(R.string.settings_eight_of_nine_hint),
                        summary = stringResource(R.string.settings_eight_of_nine_hint_summary),
                        checked = settings.showEightOfNineHint,
                        onCheckedChange = viewModel::setShowEightOfNineHint,
                    )
                }
            }

            item {
                SettingsSection(stringResource(R.string.settings_about)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = stringResource(R.string.settings_version),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        // Shown so a screenshot or a phone identifies its own build. Without it
                        // there is no way to tell whether a report is about the code as it stands.
                        Text(
                            text = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Card {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            content()
        }
    }
}

@Composable
private fun ThemeOption(
    mode: ThemeMode,
    @StringRes labelRes: Int,
    selected: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
) {
    RadioRow(
        label = stringResource(labelRes),
        selected = mode == selected,
        onClick = { onSelect(mode) },
    )
}

@Composable
private fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The row carries the click and the semantics; the button itself must not announce a
        // second, competing control.
        RadioButton(selected = selected, onClick = null)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

/** One palette: its preview, its name, and the radio that picks it. */
@Composable
private fun PaletteOption(
    palette: BoardPalette,
    selected: BoardPalette,
    dark: Boolean,
    highContrast: Boolean,
    onSelect: (BoardPalette) -> Unit,
) {
    val label = stringResource(palette.labelRes())
    val isSelected = palette == selected

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = isSelected, onClick = { onSelect(palette) }, role = Role.RadioButton)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = isSelected, onClick = null)
        PalettePreview(
            colors = Palettes.boardColors(palette, dark, highContrast),
            description = stringResource(R.string.settings_palette_preview, label),
            modifier = Modifier.padding(start = 12.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 16.dp),
        )
    }
}

@StringRes
private fun BoardPalette.labelRes(): Int = when (this) {
    BoardPalette.SLATE -> R.string.settings_palette_slate
    BoardPalette.SEPIA -> R.string.settings_palette_sepia
    BoardPalette.FOREST -> R.string.settings_palette_forest
    BoardPalette.GRAPHITE -> R.string.settings_palette_graphite
}

@Composable
private fun LanguageOption(
    language: AppLanguage,
    @StringRes labelRes: Int,
    selected: AppLanguage,
    onSelect: (AppLanguage) -> Unit,
) {
    RadioRow(
        label = stringResource(labelRes),
        selected = language == selected,
        onClick = { onSelect(language) },
    )
}

@Composable
private fun SwitchRow(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, onValueChange = onCheckedChange, role = Role.Switch)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = null, modifier = Modifier.padding(start = 12.dp))
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
