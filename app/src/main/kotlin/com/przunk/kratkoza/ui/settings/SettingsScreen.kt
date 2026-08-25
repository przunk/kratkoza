package com.przunk.kratkoza.ui.settings

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.przunk.kratkoza.R
import com.przunk.kratkoza.domain.settings.ThemeMode
import com.przunk.kratkoza.ui.common.KratkozaScaffold

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()

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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = mode == selected,
                onClick = { onSelect(mode) },
                role = Role.RadioButton,
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The row carries the click and the semantics; the button itself must not announce a
        // second, competing control.
        RadioButton(selected = mode == selected, onClick = null)
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
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
