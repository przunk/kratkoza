package com.przunk.kratkoza.data.ranking

import com.przunk.kratkoza.data.FakeDailyPuzzleDao
import com.przunk.kratkoza.data.FakeGameHistoryDao
import com.przunk.kratkoza.data.FakePuzzleDao
import com.przunk.kratkoza.data.FakeSavedGameDao
import com.przunk.kratkoza.data.FakeSolvedBoardDao
import com.przunk.kratkoza.data.repository.DailyRepository
import com.przunk.kratkoza.data.repository.GameRepository
import com.przunk.kratkoza.domain.game.FinishedGame
import com.przunk.kratkoza.domain.model.Difficulty
import com.przunk.kratkoza.domain.model.GameType
import com.przunk.kratkoza.domain.ranking.Leaderboard
import com.przunk.kratkoza.domain.ranking.LeaderboardOutcome
import com.przunk.kratkoza.domain.ranking.Ranking
import com.przunk.kratkoza.domain.ranking.RankingLog
import com.przunk.kratkoza.domain.ranking.SubmissionOutcome
import com.przunk.kratkoza.domain.sudoku.SudokuGenerator
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RankingSubmitterTest {

    private val puzzleDao = FakePuzzleDao()
    private val savedGameDao = FakeSavedGameDao(puzzleDao)
    private val historyDao = FakeGameHistoryDao(savedGameDao)
    private val dailyPuzzleDao = FakeDailyPuzzleDao()
    private val repository = GameRepository(
        puzzleDao = puzzleDao,
        dailyPuzzleDao = dailyPuzzleDao,
        savedGameDao = savedGameDao,
        historyDao = historyDao,
        solvedBoardDao = FakeSolvedBoardDao(puzzleDao, savedGameDao),
        generator = SudokuGenerator(),
        computationDispatcher = Dispatchers.Unconfined,
    )
    private val dailyRepository = DailyRepository(
        games = repository,
        puzzleDao = puzzleDao,
        dailyPuzzleDao = dailyPuzzleDao,
        savedGameDao = savedGameDao,
        historyDao = historyDao,
        generator = SudokuGenerator(),
        computationDispatcher = Dispatchers.Unconfined,
    )
    private val log = RankingLog()

    private val day: LocalDate = LocalDate.parse("2026-08-26")

    @Test
    fun `a finished daily reaches the level board and both totals`() = runTest {
        val (submitter, ranking) = submitterWith()
        val finished = finishDaily(Difficulty.EASY)

        submitter.submit(finished.game, finished.puzzleId)

        val score = requireNotNull(finished.game.score).toLong()
        // Three boards. With one daily solved all three carry the same number, which is why the
        // accumulating test below matters as well.
        assertEquals(
            listOf(
                Leaderboard.DAILY_EASY to score,
                Leaderboard.DAILY_TOTAL to score,
                Leaderboard.ALL_TIME_TOTAL to score,
            ),
            ranking.submissions,
        )
        assertTrue(historyDao.all.single().publiclyRanked)
    }

    @Test
    fun `the totals add up across the day rather than repeating one score`() = runTest {
        val (submitter, ranking) = submitterWith()

        val easy = finishDaily(Difficulty.EASY)
        submitter.submit(easy.game, easy.puzzleId)
        val hard = finishDaily(Difficulty.HARD)
        submitter.submit(hard.game, hard.puzzleId)

        val scores = historyDao.all.mapNotNull { it.score }.map(Int::toLong)
        assertEquals("both dailies must be scored", 2, scores.size)

        val totals = ranking.submissions.filter { it.first == Leaderboard.ALL_TIME_TOTAL }
        assertEquals(listOf(scores[0], scores.sum()), totals.map { it.second })
    }

    @Test
    fun `a daily that lost its ranking eligibility is never submitted`() = runTest {
        val (submitter, ranking) = submitterWith()
        val finished = finishDaily(Difficulty.EASY, rankedEligible = false)

        submitter.submit(finished.game, finished.puzzleId)

        assertTrue(ranking.submissions.isEmpty())
        assertEquals(listOf("ranking: skipped — this daily left the ranking"), log.entries.value)
    }

    @Test
    fun `a solo game is not offered to the ranking`() = runTest {
        // project.md section 29: only a Daily is scored, so only a Daily has anywhere to go.
        val (submitter, ranking) = submitterWith()
        val solo = FinishedGame(
            historyId = 1,
            gameType = GameType.SOLO,
            difficulty = Difficulty.EASY,
            score = 500,
            rankedEligible = true,
        )

        submitter.submit(solo, "solo")

        assertTrue(ranking.submissions.isEmpty())
    }

    @Test
    fun `a queued score is not claimed as confirmed`() = runTest {
        // Play Services took the score but cannot say what became of it. The board is on its way,
        // and publiclyRanked still means "confirmed" rather than "probably".
        val (submitter, _) = submitterWith(SubmissionOutcome.Queued("client reconnect required"))
        val finished = finishDaily(Difficulty.EASY)

        submitter.submit(finished.game, finished.puzzleId)

        val row = historyDao.all.single()
        assertTrue("the game is still finished and scored", row.completed)
        assertFalse("but nothing confirmed it reached the board", row.publiclyRanked)
    }

    @Test
    fun `a score the board already beats is accepted and said to have changed nothing`() = runTest {
        // The question this answers: "I submitted 995, the board still shows 996, is it broken".
        // It is not — a leaderboard keeps the best score — but "sent" alone next to an unchanged
        // board reads as the app lying about what it did.
        val (submitter, _) = submitterWith(SubmissionOutcome.Submitted(newBest = false))
        val finished = finishDaily(Difficulty.EASY)

        submitter.submit(finished.game, finished.puzzleId)

        assertTrue(
            "the log must distinguish accepted-and-ignored from accepted-and-applied",
            log.entries.value.any { it.contains("previous score was better") },
        )
    }

    @Test
    fun `a daily whose totals cannot be read still reaches its level board`() = runTest {
        // dailyTotals returns null when the board is not in daily_puzzles for the current ruleset
        // version — after a ruleset change, for a game begun under the old one. The level score is
        // still a real result and still belongs on its board.
        val (submitter, ranking) = submitterWith()
        val finished = finishDaily(Difficulty.EASY)

        submitter.submit(finished.game, puzzleId = "not-a-daily-board")

        assertEquals(
            listOf(Leaderboard.DAILY_EASY),
            ranking.submissions.map { it.first },
        )
        assertTrue(historyDao.all.single().publiclyRanked)
    }

    private fun submitterWith(
        outcome: SubmissionOutcome = SubmissionOutcome.Submitted(newBest = true),
    ): Pair<RankingSubmitter, RecordingRanking> {
        val ranking = RecordingRanking(outcome = outcome)
        // Unconfined, so the submission runs inline and the test does not have to chase it. In the
        // app this scope belongs to the application, which is the whole point of the class.
        val scope = CoroutineScope(UnconfinedTestDispatcher())
        return RankingSubmitter(repository, dailyRepository, ranking, log, scope) to ranking
    }

    /** Plays one daily to the end, the way the repository would have it. */
    private suspend fun finishDaily(
        difficulty: Difficulty,
        rankedEligible: Boolean = true,
    ): FinishedDaily {
        dailyRepository.dailySet(day)
        val game = requireNotNull(dailyRepository.startDaily(day, difficulty))
        val played = game.copy(elapsedMillis = 60_000, rankedEligible = rankedEligible)
        return FinishedDaily(repository.finishGame(played, completed = true), game.state.puzzle.id)
    }

}

private data class FinishedDaily(val game: FinishedGame, val puzzleId: String)

/** A ranking that remembers what it was handed, and answers however the test set it up to. */
private class RecordingRanking(
    override val isAvailable: Boolean = true,
    private val outcome: SubmissionOutcome = SubmissionOutcome.Submitted(newBest = true),
) : Ranking {

    val submissions = mutableListOf<Pair<Leaderboard, Long>>()

    override suspend fun isSignedIn(): Boolean = true

    override suspend fun signIn(): Boolean = true

    override suspend fun show(board: Leaderboard): LeaderboardOutcome =
        LeaderboardOutcome.Failed("the submitter never opens one")

    override suspend fun submit(board: Leaderboard, value: Long): SubmissionOutcome {
        submissions += board to value
        return outcome
    }
}
