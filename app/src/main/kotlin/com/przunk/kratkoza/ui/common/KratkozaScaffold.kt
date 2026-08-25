package com.przunk.kratkoza.ui.common

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.przunk.kratkoza.R

/**
 * A titled screen with an optional back arrow.
 *
 * The arrow is a text glyph rather than a vector asset: material-icons-extended is a large
 * dependency for one chevron, and technical_requirements.md section 9 asks a dependency to earn its
 * place. It carries a content description so TalkBack announces "back", not "less-than sign".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KratkozaScaffold(
    title: String,
    modifier: Modifier = Modifier,
    onNavigateBack: (() -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    val backDescription = stringResource(R.string.common_back)

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    if (onNavigateBack != null) {
                        IconButton(
                            onClick = onNavigateBack,
                            modifier = Modifier.semantics { contentDescription = backDescription },
                        ) {
                            Text(text = "‹", style = MaterialTheme.typography.headlineMedium)
                        }
                    }
                },
            )
        },
        content = content,
    )
}
