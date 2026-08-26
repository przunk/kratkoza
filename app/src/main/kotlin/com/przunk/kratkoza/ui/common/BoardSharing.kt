package com.przunk.kratkoza.ui.common

import android.content.Context
import android.content.Intent
import androidx.annotation.StringRes
import com.przunk.kratkoza.R

/**
 * Hands a board's code to whatever the player wants to send it with.
 *
 * Plain text rather than a link or a file: the code has to survive being pasted into a chat window,
 * quoted back, and read on a phone that may not have the app installed yet. Text is the only format
 * that manages all three.
 *
 * Lives here rather than in either screen because both offer sharing — from the game itself, where
 * the player first reaches for it, and from the saved-games list.
 */
fun shareBoard(context: Context, @StringRes difficultyLabel: Int, code: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.share_board_subject))
        putExtra(
            Intent.EXTRA_TEXT,
            context.getString(
                R.string.share_board_body,
                context.getString(difficultyLabel),
                code,
            ),
        )
    }
    context.startActivity(
        Intent.createChooser(intent, context.getString(R.string.share_board_chooser)),
    )
}
