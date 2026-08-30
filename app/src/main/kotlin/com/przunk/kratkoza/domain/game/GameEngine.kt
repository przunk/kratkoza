package com.przunk.kratkoza.domain.game

import com.przunk.kratkoza.domain.sudoku.Sudoku

/**
 * Every way a player can change the board, as pure transitions on [GameState].
 *
 * Kept out of the ViewModel on purpose (technical_requirements.md section 5): these rules — when a
 * mistake is counted, what undo restores, which pencil marks a placed digit clears — are the parts
 * of the app most worth testing, and they must not depend on a composable or on Android at all.
 *
 * Every transition returns the state unchanged when it would do nothing, so callers can fire events
 * freely and the automatic save can treat "same instance back" as "nothing to write".
 */
object GameEngine {

    /**
     * Reveals the solution digit in an empty, non-given cell (project.md section 18).
     *
     * A reveal is not a player move: it costs no mistake and is not appended to the undo history.
     * Existing history is sanitised so an older move cannot later erase the revealed digit,
     * restore a wrong one, or put pencil marks underneath it. The correct digit is then protected
     * by the same solution-derived lock as every other correct entry, including after a reload.
     */
    fun revealDigit(
        state: GameState,
        index: Int,
        autoRemoveCandidates: Boolean,
    ): GameState {
        if (state.isGiven(index)) return state
        if (!state.board.isEmpty(index)) return state

        val digit = state.puzzle.solution[index]
        var candidates = state.candidates.cleared(index)
        if (autoRemoveCandidates) {
            candidates = candidates.removeDigitFrom(Sudoku.peers[index], digit)
        }

        val safeUndoStack = state.undoStack.mapNotNull { move ->
            if (move.index == index) {
                null
            } else {
                move.copy(previousCandidates = move.previousCandidates - index)
            }
        }

        return state.copy(
            board = state.board.withValue(index, digit),
            candidates = candidates,
            undoStack = safeUndoStack,
        )
    }

    /**
     * Writes [digit] into [index].
     *
     * project.md section 16 counts a mistake for every digit committed that differs from the
     * solution. The count only ever goes up: correcting the cell later, or undoing this move, leaves
     * it where it is.
     *
     * project.md section 11: the cell's own pencil marks go when a digit lands in it.
     * A player-entered correct digit is locked by the accepted decision in codex.md. Keeping that
     * rule here prevents another input path from replacing it and creating a new mistake.
     *
     * [autoRemoveCandidates] then applies project.md section 15 — striking the digit from the marks
     * of every cell in the same row, column and block. It is done only for a *correct* digit, which
     * is what section 15 says ("po poprawnym wpisaniu cyfry"). Doing it for a wrong digit would
     * quietly destroy the player's correct notes across twenty cells; and it gives nothing away,
     * because section 16 requires a wrong digit to be marked as wrong the moment it is entered.
     */
    fun enterDigit(
        state: GameState,
        index: Int,
        digit: Int,
        autoRemoveCandidates: Boolean,
    ): GameState {
        require(Sudoku.isDigit(digit)) { "Not a sudoku digit: $digit" }
        if (state.isGiven(index)) return state
        if (state.hasCorrectDigit(index)) return state
        if (state.board[index] == digit) return state

        val isCorrect = digit == state.puzzle.solution[index]

        var candidates = state.candidates.cleared(index)
        if (autoRemoveCandidates && isCorrect) {
            candidates = candidates.removeDigitFrom(Sudoku.peers[index], digit)
        }

        return state.copy(
            board = state.board.withValue(index, digit),
            candidates = candidates,
            mistakeCount = state.mistakeCount + if (isCorrect) 0 else 1,
            undoStack = state.undoStack + Move(
                index = index,
                previousValue = state.board[index],
                newValue = digit,
                previousCandidates = changedCandidates(state.candidates, candidates),
            ),
        )
    }

    /**
     * Adds or removes one pencil mark (project.md section 14).
     *
     * Marks are never a mistake and never touch the mistake counter — section 14 calls them a normal
     * part of playing. A cell holding a digit takes no marks; erase it first.
     */
    fun toggleCandidate(state: GameState, index: Int, digit: Int): GameState {
        require(Sudoku.isDigit(digit)) { "Not a sudoku digit: $digit" }
        if (state.isGiven(index)) return state
        if (!state.board.isEmpty(index)) return state

        val candidates = state.candidates.toggle(index, digit)
        return state.copy(
            candidates = candidates,
            undoStack = state.undoStack + Move(
                index = index,
                previousValue = state.board[index],
                newValue = state.board[index],
                previousCandidates = changedCandidates(state.candidates, candidates),
            ),
        )
    }

    /**
     * Clears every pencil mark as one undoable action (wishlist item 5).
     *
     * [Move.previousCandidates] already holds snapshots for any number of cells, so no second undo
     * mechanism is needed. The first changed cell is only an anchor for the existing board-shaped
     * move; its value is recorded unchanged, while the map carries the whole operation.
     */
    fun clearAllCandidates(state: GameState): GameState {
        if (!state.candidates.hasAny) return state

        val candidates = CandidateGrid.EMPTY
        val previousCandidates = changedCandidates(state.candidates, candidates)
        val anchor = previousCandidates.keys.first()

        return state.copy(
            candidates = candidates,
            undoStack = state.undoStack + Move(
                index = anchor,
                previousValue = state.board[anchor],
                newValue = state.board[anchor],
                previousCandidates = previousCandidates,
            ),
        )
    }

    /**
     * Empties [index] — the Erase button of project.md section 11.
     *
     * Clears the digit and the pencil marks together. A cell never shows both at once, so "empty
     * this cell" has one unambiguous meaning. Correct digits are excluded because erasing one would
     * make the replacement lock trivial to bypass.
     */
    fun clearCell(state: GameState, index: Int): GameState {
        if (state.isGiven(index)) return state
        if (state.hasCorrectDigit(index)) return state
        if (state.board.isEmpty(index) && state.candidates.isEmpty(index)) return state

        val candidates = state.candidates.cleared(index)
        return state.copy(
            board = state.board.withValue(index, Sudoku.EMPTY),
            candidates = candidates,
            undoStack = state.undoStack + Move(
                index = index,
                previousValue = state.board[index],
                newValue = Sudoku.EMPTY,
                previousCandidates = changedCandidates(state.candidates, candidates),
            ),
        )
    }

    /**
     * Takes back the last move (project.md section 12).
     *
     * Restores the digit and every pencil mark the move changed — including the ones automatic
     * removal took away, since the move recorded those too.
     *
     * [GameState.mistakeCount] is untouched by design. Section 12 is explicit: undo must not be a
     * way to erase a mistake that has already been committed.
     */
    fun undo(state: GameState): GameState {
        val move = state.undoStack.lastOrNull() ?: return state

        var candidates = state.candidates
        for ((cell, digits) in move.previousCandidates) {
            candidates = candidates.with(cell, digits)
        }

        return state.copy(
            board = state.board.withValue(move.index, move.previousValue),
            candidates = candidates,
            undoStack = state.undoStack.dropLast(1),
        )
    }

    /** The pre-move marks of every cell that [after] changed, keyed by cell. */
    private fun changedCandidates(
        before: CandidateGrid,
        after: CandidateGrid,
    ): Map<Int, Set<Int>> {
        val changed = before.differingCells(after)
        if (changed.isEmpty()) return emptyMap()
        return changed.associateWith { before[it] }
    }

    /** Derived rather than persisted so the lock also applies immediately after restoring a save. */
    private fun GameState.hasCorrectDigit(index: Int): Boolean =
        board[index] == puzzle.solution[index]
}
