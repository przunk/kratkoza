package com.przunk.kratkoza.ui.common

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.przunk.kratkoza.R

/**
 * A titled screen with an optional back arrow.
 *
 * The arrow is a local vector drawable; see `res/drawable/ic_arrow_back.xml` for why the icons are
 * drawn rather than pulled from a package. It is auto-mirrored for right-to-left layouts and
 * described, so TalkBack announces "back" rather than a shape.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KratkozaScaffold(
    title: String,
    modifier: Modifier = Modifier,
    onNavigateBack: (() -> Unit)? = null,
    /** Where transient messages appear; screens that have none leave it out. */
    snackbarHostState: SnackbarHostState? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    val backDescription = stringResource(R.string.common_back)

    Scaffold(
        modifier = modifier,
        snackbarHost = { snackbarHostState?.let { SnackbarHost(it) } },
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    if (onNavigateBack != null) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                painter = painterResource(R.drawable.ic_arrow_back),
                                contentDescription = backDescription,
                            )
                        }
                    }
                },
            )
        },
        content = content,
    )
}
