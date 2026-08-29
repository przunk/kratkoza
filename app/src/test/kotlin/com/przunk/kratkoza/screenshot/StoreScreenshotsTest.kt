package com.przunk.kratkoza.screenshot

import android.graphics.Bitmap
import androidx.compose.ui.Modifier
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.przunk.kratkoza.data.FakeDailyPuzzleDao
import com.przunk.kratkoza.data.FakeGameHistoryDao
import com.przunk.kratkoza.data.FakePuzzleDao
import com.przunk.kratkoza.data.FakeSavedGameDao
import com.przunk.kratkoza.data.repository.DailyRepository
import com.przunk.kratkoza.data.repository.GameRepository
import com.przunk.kratkoza.data.repository.StatisticsRepository
import com.przunk.kratkoza.domain.model.Difficulty
import com.przunk.kratkoza.domain.settings.GameSettings
import com.przunk.kratkoza.domain.settings.ThemeMode
import com.przunk.kratkoza.domain.sudoku.Sudoku
import com.przunk.kratkoza.domain.sudoku.SudokuGenerator
import com.przunk.kratkoza.ui.daily.DailyScreen
import com.przunk.kratkoza.ui.daily.DailyViewModel
import com.przunk.kratkoza.ui.game.GameScreen
import com.przunk.kratkoza.ui.game.GameViewModel
import com.przunk.kratkoza.ui.statistics.StatisticsScreen
import com.przunk.kratkoza.ui.statistics.StatisticsViewModel
import com.przunk.kratkoza.ui.theme.KratkozaTheme
import java.io.File
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Writes the store screenshots.
 *
 * A generator rather than a check, which is why it lives in the test tree: rendering a Compose screen
 * needs an Android runtime, and this is the only place the project has one without a device. Running
 * the suite rewrites the files in `store/screenshots/`; the output is deterministic, so an unchanged
 * UI leaves them byte for byte identical and git sees nothing.
 *
 * The home screen is absent on purpose. Its artwork posts the next animation frame for ever, so
 * advancing the clock to any moment never returns — see `store/README.md`.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// The device the screenshots are of. Without it Compose lays out for Robolectric's default handset
// and the screen sits in a corner of the bitmap.
@Config(qualifiers = "w360dp-h800dp-xhdpi")
@OptIn(ExperimentalCoroutinesApi::class)
class StoreScreenshotsTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val puzzleDao = FakePuzzleDao()
    private val savedGameDao = FakeSavedGameDao(puzzleDao)
    private val historyDao = FakeGameHistoryDao(savedGameDao)
    private val dailyPuzzleDao = FakeDailyPuzzleDao()

    private val repository = GameRepository(
        puzzleDao = puzzleDao,
        dailyPuzzleDao = dailyPuzzleDao,
        savedGameDao = savedGameDao,
        historyDao = historyDao,
        generator = SudokuGenerator(),
        computationDispatcher = dispatcher,
    )
    private val dailyRepository = DailyRepository(
        games = repository,
        puzzleDao = puzzleDao,
        dailyPuzzleDao = dailyPuzzleDao,
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
    fun `every screenshot is written in both themes`() {
        val written = mutableListOf<File>()

        for (theme in listOf(ThemeMode.DARK, ThemeMode.LIGHT)) {
            val suffix = theme.name.lowercase()

            // Built here rather than inside the composable: a ViewModel constructed during
            // composition is rebuilt on every recomposition, and the screens are only being drawn.
            val game = playedGame()
            val daily = DailyViewModel(dailyRepository) { FIXED_DAY }
            val statistics = StatisticsViewModel(statisticsRepository)

            written += write("board-$suffix") {
                KratkozaTheme(themeMode = theme) {
                    GameScreen(
                        viewModel = game,
                        onNavigateBack = {},
                        onNewGame = {},
                        modifier = Modifier,
                    )
                }
            }

            written += write("daily-$suffix") {
                KratkozaTheme(themeMode = theme) {
                    DailyScreen(
                        viewModel = daily,
                        onOpenGame = {},
                        onNavigateBack = {},
                        modifier = Modifier,
                    )
                }
            }

            written += write("statistics-$suffix") {
                KratkozaTheme(themeMode = theme) {
                    StatisticsScreen(
                        viewModel = statistics,
                        onNavigateBack = {},
                        modifier = Modifier,
                    )
                }
            }
        }

        for (file in written) {
            assertTrue("${file.name} was not written", file.length() > 0)
        }
    }

    /** A board with a few digits in it: an empty grid says nothing about the game. */
    private fun playedGame(): GameViewModel {
        val game = runBlocking { repository.startNewGame(Difficulty.NORMAL) }
        val model = GameViewModel(
            puzzleId = game.state.puzzle.id,
            gameRepository = repository,
            settingsFlow = MutableStateFlow(GameSettings.DEFAULT),
            debugTools = false,
            clock = { FIXED_ELAPSED_START },
        )

        val empties = (0 until Sudoku.CELL_COUNT).filter { game.state.puzzle.givens.isEmpty(it) }
        for (cell in empties.take(FILLED_CELLS)) {
            model.selectCell(cell)
            model.enterDigit(game.state.puzzle.solution[cell])
        }
        // Left on a cell, so the screenshot shows the highlighting rather than a bare grid.
        model.selectCell(empties[FILLED_CELLS])
        return model
    }

    private fun write(name: String, content: @androidx.compose.runtime.Composable () -> Unit): File {
        val bitmap = render(WIDTH_PX, HEIGHT_PX, content = content)
        val file = File(OUTPUT_DIR, "$name.png")
        file.parentFile?.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return file
    }

    private companion object {
        /** 360x800dp at xhdpi — an ordinary handset, and what Play shows the screenshot as. */
        const val WIDTH_PX = 720
        const val HEIGHT_PX = 1600

        const val FILLED_CELLS = 14
        const val FIXED_ELAPSED_START = 0L
        val FIXED_DAY: Instant = LocalDate.parse("2026-08-26").atStartOfDay(java.time.ZoneOffset.UTC)
            .plusHours(12).toInstant()

        /** Beside the icon and the feature graphic, since they are all one set of store assets. */
        val OUTPUT_DIR = File("../store/screenshots")
    }
}
