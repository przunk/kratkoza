package com.przunk.kratkoza.data.ranking

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.przunk.kratkoza.domain.ranking.Leaderboard
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
    fun `every board has its own identifier`() {
        // The identifiers differ only in their last character, which makes pasting the same one
        // twice both easy and invisible. Distinctness is the assertion that catches it: a duplicate
        // would send two boards' scores to one and leave the other permanently empty.
        val ids = Leaderboard.entries.map { board ->
            val id = config.idFor(board)
            assertNotNull("no identifier configured for $board", id)
            id
        }

        assertEquals(Leaderboard.entries.size, ids.toSet().size)
    }
}
