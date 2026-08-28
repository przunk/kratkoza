package com.przunk.kratkoza.navigation

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The blank-screen defect, reduced to its two destinations.
 *
 * Pressing a back control twice quickly used to pop twice, which from a second screen meant leaving
 * the first one as well and rendering an empty NavHost that the player could not escape.
 */
@RunWith(AndroidJUnit4::class)
class NavigateBackTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var navController: NavHostController

    private fun start(onBack: (NavHostController) -> Unit) {
        compose.setContent {
            navController = rememberNavController()
            NavHost(navController = navController, startDestination = "first") {
                composable("first") {
                    Button(onClick = { navController.navigate("second") }) { Text("go") }
                }
                composable("second") {
                    Button(onClick = { onBack(navController) }) { Text("back") }
                }
            }
        }
        compose.onNodeWithText("go").performClick()
        compose.waitForIdle()
    }

    @Test
    fun `two presses in one frame go back once`() {
        start { it.navigateBackOnce(); it.navigateBackOnce() }

        compose.onNodeWithText("back").performClick()
        compose.waitForIdle()

        assertNotNull("the stack must not be empty", navController.currentBackStackEntry)
        assertEquals("first", navController.currentDestination?.route)
    }

    @Test
    fun `one press still goes back`() {
        // The guard must not turn into a refusal: a single press is the whole point of the control.
        start { it.navigateBackOnce() }

        compose.onNodeWithText("back").performClick()
        compose.waitForIdle()

        assertEquals("first", navController.currentDestination?.route)
    }
}
