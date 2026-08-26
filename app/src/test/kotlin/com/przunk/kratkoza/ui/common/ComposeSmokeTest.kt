package com.przunk.kratkoza.ui.common

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Proves Compose UI tests run here at all, on the JVM, without a device.
 *
 * Kept as its own test because it answers a different question from every other test in the suite:
 * not "is this screen right" but "can a screen be examined at all in this project". If the toolchain
 * ever stops supporting it, this fails on its own rather than taking a screen test down with it and
 * looking like a bug in the screen.
 */
@RunWith(AndroidJUnit4::class)
class ComposeSmokeTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `a composable can be rendered and inspected`() {
        compose.setContent { Text("kratkoza") }
        compose.onNodeWithText("kratkoza").assertIsDisplayed()
    }
}
