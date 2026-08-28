package com.przunk.kratkoza.ui.statistics

import com.przunk.kratkoza.data.FakeGameHistoryDao
import com.przunk.kratkoza.data.FakeDailyPuzzleDao
import com.przunk.kratkoza.data.FakePuzzleDao
import com.przunk.kratkoza.data.FakeSavedGameDao
import com.przunk.kratkoza.data.local.GameHistoryEntity
import com.przunk.kratkoza.data.repository.StatisticsRepository
import com.przunk.kratkoza.data.repository.GameRepository
import com.przunk.kratkoza.domain.model.Difficulty
import com.przunk.kratkoza.domain.model.GameType
import com.przunk.kratkoza.domain.sudoku.SudokuGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StatisticsViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val puzzleDao = FakePuzzleDao()
    private val savedGameDao = FakeSavedGameDao(puzzleDao)
    private val historyDao = FakeGameHistoryDao(savedGameDao)

    private val repository = GameRepository(
        puzzleDao = puzzleDao,
        dailyPuzzleDao = FakeDailyPuzzleDao(),
        savedGameDao = savedGameDao,
        historyDao = historyDao,
        generator = SudokuGenerator(),
        computationDispatcher = dispatcher,
    )
    private val statisticsRepository = StatisticsRepository(historyDao, savedGameDao)

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `resetting clears what the screen shows`() = runTest(dispatcher) {
        val finished = repository.startNewGame(Difficulty.EASY)
        repository.finishGame(finished.copy(elapsedMillis = 60_000), completed = true)
        repository.startNewGame(Difficulty.HARD)
        historyDao.insert(
            GameHistoryEntity(
                puzzleId = "daily-for-reset",
                gameType = GameType.DAILY.name,
                difficulty = Difficulty.MASTER.name,
                startedAt = 1_000,
                finishedAt = 61_000,
                elapsedMillis = 60_000,
                mistakeCount = 0,
                completed = true,
                score = 900,
                xpEarned = 0,
                publiclyRanked = false,
                rankedEligible = true,
                autoRemoveCandidates = true,
                rulesetVersion = SudokuGenerator.GENERATOR_VERSION,
            ),
        )

        val viewModel = StatisticsViewModel(statisticsRepository)
        assertEquals(2, viewModel.statistics.first().completedCount)
        assertNotNull(viewModel.statistics.first().daily)

        viewModel.resetStatistics()
        val cleared = statisticsRepository.statistics.first()
        assertEquals(0, cleared.startedCount)
        assertEquals(0, cleared.completedCount)
        assertNull(cleared.daily)
    }
}
