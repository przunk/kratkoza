package com.przunk.kratkoza.data.ranking

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.przunk.kratkoza.domain.daily.DailyPuzzles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlayGamesConfigTest {

    private val config = PlayGamesConfig(ApplicationProvider.getApplicationContext<Context>())

    @Test
    fun `Play Console identifiers are present`() {
        assertTrue(config.isConfigured)
    }

    @Test
    fun `every Daily level has its own leaderboard`() {
        // The three identifiers differ only in their last character, which makes pasting the same
        // one twice both easy and invisible. Distinctness is the assertion that catches it: a
        // duplicate would send two levels to one board and leave the third permanently empty.
        val boards = DailyPuzzles.DIFFICULTIES.map { difficulty ->
            val board = config.leaderboardFor(difficulty)
            assertNotNull("no leaderboard configured for $difficulty", board)
            board
        }

        assertEquals(DailyPuzzles.DIFFICULTIES.size, boards.toSet().size)
    }
}
