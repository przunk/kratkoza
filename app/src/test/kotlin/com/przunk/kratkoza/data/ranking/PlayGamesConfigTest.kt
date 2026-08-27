package com.przunk.kratkoza.data.ranking

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.przunk.kratkoza.domain.daily.DailyPuzzles
import com.przunk.kratkoza.domain.model.Difficulty
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlayGamesConfigTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `identifiers are absent until Play Console supplies them`() {
        // Asserts the shipped state, so it fails the moment the identifiers are filled in. That
        // failure is the point: it is the reminder to come back and cover the configured case with
        // real values, rather than leaving behind a test that quietly asserts nothing.
        val config = PlayGamesConfig(context)

        assertFalse(config.isConfigured)
        assertNull(config.leaderboardFor(Difficulty.EASY))
    }

    @Test
    fun `every Daily level has a leaderboard slot mapped to it`() {
        // Construction is the assertion: the map is built from the Daily level list and raises on
        // any level it cannot name a resource for. A fourth Daily level fails this test on the day
        // it is added, which is the only day the fix is cheap.
        PlayGamesConfig(context)

        assertTrue(DailyPuzzles.DIFFICULTIES.isNotEmpty())
    }
}
