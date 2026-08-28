package com.przunk.kratkoza.domain.progress

import com.przunk.kratkoza.domain.model.Difficulty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerProgressTest {

    @Test
    fun `a harder board is worth more`() {
        // project.md section 32: XP depends at least on difficulty and on finishing.
        val awards = Difficulty.entries.map(PlayerProgress::xpForCompleting)
        assertEquals(awards.sortedBy { it }, awards)
        assertTrue("every level must be worth something", awards.all { it > 0 })
    }

    @Test
    fun `the agreed thresholds`() {
        assertEquals(0L, PlayerProgress.xpToReach(1))
        assertEquals(100L, PlayerProgress.xpToReach(2))
        assertEquals(300L, PlayerProgress.xpToReach(3))
        assertEquals(600L, PlayerProgress.xpToReach(4))
        assertEquals(1_000L, PlayerProgress.xpToReach(5))
        assertEquals(4_500L, PlayerProgress.xpToReach(10))
        assertEquals(19_000L, PlayerProgress.xpToReach(20))
    }

    @Test
    fun `each level costs a hundred more than the one before`() {
        // The whole point of the curve: level 40 has to mean more than level 4.
        for (level in 1..50) {
            val step = PlayerProgress.xpToReach(level + 1) - PlayerProgress.xpToReach(level)
            assertEquals("step to level ${level + 1}", 100L * level, step)
        }
    }

    @Test
    fun `a new player is level one with nothing behind them`() {
        val progress = PlayerProgress.of(0)

        assertEquals(1, progress.level)
        assertEquals(0L, progress.xpIntoLevel)
        assertEquals(100L, progress.xpForNextLevel)
        assertEquals(0f, progress.fractionIntoLevel, 0f)
        assertEquals(Progress.NONE, progress)
    }

    @Test
    fun `a threshold counts for the level it buys, not one short of it`() {
        // Exactly on the line is the case a square-root solution gets wrong, so it is pinned here.
        assertEquals(1, PlayerProgress.of(99).level)
        assertEquals(2, PlayerProgress.of(100).level)
        assertEquals(2, PlayerProgress.of(299).level)
        assertEquals(3, PlayerProgress.of(300).level)
        assertEquals(10, PlayerProgress.of(4_500).level)
        assertEquals(9, PlayerProgress.of(4_499).level)
    }

    @Test
    fun `progress through a level is measured against that level's own price`() {
        val progress = PlayerProgress.of(450)

        assertEquals(3, progress.level)
        assertEquals(150L, progress.xpIntoLevel)
        assertEquals(300L, progress.xpForNextLevel)
        assertEquals(0.5f, progress.fractionIntoLevel, 0.0001f)
    }

    @Test
    fun `the level never falls as XP rises`() {
        var previous = 0
        for (xp in 0L..5_000L step 37) {
            val level = PlayerProgress.of(xp).level
            assertTrue("level fell at $xp", level >= previous)
            previous = level
        }
    }

    @Test
    fun `progress stays inside its own level at every step`() {
        for (xp in 0L..3_000L step 13) {
            val progress = PlayerProgress.of(xp)
            assertTrue(progress.xpIntoLevel >= 0)
            assertTrue("overflowed at $xp", progress.xpIntoLevel < progress.xpForNextLevel)
            assertEquals(
                xp,
                PlayerProgress.xpToReach(progress.level) + progress.xpIntoLevel,
            )
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `there is no level zero`() {
        PlayerProgress.xpToReach(0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `XP cannot be negative`() {
        PlayerProgress.of(-1)
    }
}
