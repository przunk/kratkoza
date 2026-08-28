package com.przunk.kratkoza.navigation

import androidx.lifecycle.Lifecycle
import androidx.navigation.NavController

/**
 * Goes back once, however many times it is asked within the same frame.
 *
 * Two quick presses of a back control used to pop twice: the first left the screen, the second took
 * the one underneath it. From Settings that meant leaving Home as well, and a NavHost whose stack is
 * empty draws nothing while the Activity stays open — a blank screen with no way out, reproducible
 * every time.
 *
 * The guard is the lifecycle of the entry the press came from. A destination on its way off the
 * stack is no longer RESUMED, so the second call finds nothing to act on and returns. Compared with
 * a flag in each screen this needs no state to reset and cannot be forgotten by the next screen
 * somebody adds — which matters, because six screens shared the unguarded call and would have
 * shared the defect with any seventh.
 *
 * This is not the same problem as the double Start in Daily and does not have the same answer:
 * there the rule belonged in the repository, because the damage was a second row in the database.
 * Here nothing is written down; the only thing to protect is the stack itself.
 */
fun NavController.navigateBackOnce() {
    val entry = currentBackStackEntry ?: return
    if (entry.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
        popBackStack()
    }
}
