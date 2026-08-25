package com.przunk.kratkoza.domain.sudoku

import java.security.MessageDigest

/**
 * Derives a puzzle's permanent identifier from the board itself.
 *
 * A content hash rather than a random id, so the same board generated twice — on two devices, or
 * before and after a reinstall — resolves to the same identity. That is what lets history,
 * statistics and any future leaderboard agree on what "the same puzzle" means without a server
 * handing out ids.
 */
object PuzzleId {

    private const val HEX_CHARACTERS = 16

    /**
     * `<version>-<hash>`, e.g. `1-3f2a9c0b41d7e8a5`.
     *
     * The generator version is part of the id because two versions may legitimately grade the same
     * grid differently; keeping them apart avoids silently mixing incomparable results.
     */
    fun of(generatorVersion: Int, givens: SudokuBoard, solution: SudokuBoard): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$generatorVersion|${givens.toStorageString()}|${solution.toStorageString()}".toByteArray())
        val hash = buildString(HEX_CHARACTERS) {
            for (position in 0 until HEX_CHARACTERS / 2) {
                append("%02x".format(digest[position]))
            }
        }
        return "$generatorVersion-$hash"
    }
}
