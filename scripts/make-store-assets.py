#!/usr/bin/env python3
"""Draws the Google Play store assets.

Generated rather than drawn by hand so they can be regenerated after a palette change, and so the
exact geometry is reviewable in the same way the rest of the project is. No image library is used —
the PNG writer below is a few lines, and adding a dependency for it would be out of keeping with a
project that draws its own icons.

Everything is rendered at SUPERSAMPLE times the final size and averaged down, which is what gives
the edges their smoothness without any anti-aliasing machinery.
"""

import struct
import zlib
from pathlib import Path

SUPERSAMPLE = 4

# The app's own palette, so the store page and the running app agree.
SLATE_DEEP = (0x14, 0x28, 0x44)
SLATE = (0x1F, 0x3A, 0x5F)
SLATE_LIGHT = (0x2E, 0x57, 0x86)
ACCENT = (0x7F, 0xB2, 0xE5)
PAPER = (0xF7, 0xFA, 0xFC)
INK = (0x19, 0x1C, 0x1E)


class Canvas:
    """A plain RGB pixel buffer with the handful of primitives these two images need."""

    def __init__(self, width, height, colour):
        self.w, self.h = width, height
        self.px = bytearray(bytes(colour) * (width * height))

    def _set(self, x, y, colour):
        if 0 <= x < self.w and 0 <= y < self.h:
            i = (y * self.w + x) * 3
            self.px[i:i + 3] = bytes(colour)

    def fill_rect(self, x0, y0, x1, y1, colour):
        for y in range(max(0, int(y0)), min(self.h, int(y1))):
            row = y * self.w * 3
            for x in range(max(0, int(x0)), min(self.w, int(x1))):
                i = row + x * 3
                self.px[i:i + 3] = bytes(colour)

    def fill_rounded_rect(self, x0, y0, x1, y1, radius, colour):
        r = int(radius)
        self.fill_rect(x0 + r, y0, x1 - r, y1, colour)
        self.fill_rect(x0, y0 + r, x0 + r, y1 - r, colour)
        self.fill_rect(x1 - r, y0 + r, x1, y1 - r, colour)
        for cx, cy in ((x0 + r, y0 + r), (x1 - r, y0 + r), (x0 + r, y1 - r), (x1 - r, y1 - r)):
            for y in range(int(cy - r), int(cy + r) + 1):
                for x in range(int(cx - r), int(cx + r) + 1):
                    if (x - cx) ** 2 + (y - cy) ** 2 <= r * r:
                        self._set(x, y, colour)

    def vertical_gradient(self, top, bottom):
        for y in range(self.h):
            t = y / max(1, self.h - 1)
            colour = tuple(round(a + (b - a) * t) for a, b in zip(top, bottom))
            self.fill_rect(0, y, self.w, y + 1, colour)

    def downsample(self, factor):
        out = Canvas(self.w // factor, self.h // factor, (0, 0, 0))
        n = factor * factor
        for y in range(out.h):
            for x in range(out.w):
                r = g = b = 0
                for dy in range(factor):
                    row = ((y * factor + dy) * self.w + x * factor) * 3
                    for dx in range(factor):
                        i = row + dx * 3
                        r += self.px[i]
                        g += self.px[i + 1]
                        b += self.px[i + 2]
                out._set(x, y, (r // n, g // n, b // n))
        return out

    def write_png(self, path):
        """A minimal opaque-RGB PNG. Play rejects transparency on both of these assets anyway."""
        raw = bytearray()
        stride = self.w * 3
        for y in range(self.h):
            raw.append(0)  # filter: none
            raw += self.px[y * stride:(y + 1) * stride]

        def chunk(kind, data):
            body = kind + data
            return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body))

        header = struct.pack(">IIBBBBB", self.w, self.h, 8, 2, 0, 0, 0)
        png = (b"\x89PNG\r\n\x1a\n"
               + chunk(b"IHDR", header)
               + chunk(b"IDAT", zlib.compress(bytes(raw), 9))
               + chunk(b"IEND", b""))
        Path(path).write_bytes(png)
        return len(png)


# A 5x7 block alphabet. Letters built from cells rather than from curves, because the whole app is
# a grid of cells and a wordmark made of the same squares belongs to it.
GLYPHS = {
    "K": ["#...#", "#..#.", "#.#..", "##...", "#.#..", "#..#.", "#...#"],
    "R": ["####.", "#...#", "#...#", "####.", "#.#..", "#..#.", "#...#"],
    "A": [".###.", "#...#", "#...#", "#####", "#...#", "#...#", "#...#"],
    "T": ["#####", "..#..", "..#..", "..#..", "..#..", "..#..", "..#.."],
    "O": [".###.", "#...#", "#...#", "#...#", "#...#", "#...#", ".###."],
    "Z": ["#####", "....#", "...#.", "..#..", ".#...", "#....", "#####"],
    "S": [".####", "#....", "#....", ".###.", "....#", "....#", "####."],
    "U": ["#...#", "#...#", "#...#", "#...#", "#...#", "#...#", ".###."],
    "D": ["####.", "#...#", "#...#", "#...#", "#...#", "#...#", "####."],
    " ": ["....."] * 7,
}


def draw_word(canvas, word, x, y, cell, colour, letter_gap):
    """Letterforms built from the same square cell the board uses, set solid so the strokes read
    as strokes. Returns the pen position, which is also the measured width."""
    cursor = x
    for character in word:
        for row_index, row in enumerate(GLYPHS[character]):
            for column_index, mark in enumerate(row):
                if mark != "#":
                    continue
                cx = cursor + column_index * cell
                cy = y + row_index * cell
                canvas.fill_rect(cx, cy, cx + cell, cy + cell, colour)
        cursor += 5 * cell + letter_gap
    return cursor - letter_gap - x


def draw_board(canvas, x, y, size, line, filled):
    """A 3x3 plate: paper, slate rules, and whichever cells `filled` names in the accent."""
    canvas.fill_rounded_rect(x, y, x + size, y + size, size * 0.06, PAPER)

    step = size / 3
    for (row, column), colour in filled.items():
        canvas.fill_rounded_rect(
            x + column * step + line, y + row * step + line,
            x + (column + 1) * step - line, y + (row + 1) * step - line,
            size * 0.045, colour,
        )
    for index in (1, 2):
        canvas.fill_rect(x + index * step - line / 2, y, x + index * step + line / 2, y + size, SLATE)
        canvas.fill_rect(x, y + index * step - line / 2, x + size, y + index * step + line / 2, SLATE)


def make_icon(path):
    """512x512, opaque. Google applies its own mask and shadow, so this stays a full square."""
    s = 512 * SUPERSAMPLE
    canvas = Canvas(s, s, SLATE)
    canvas.vertical_gradient(SLATE_LIGHT, SLATE_DEEP)

    # Large enough to read at 48px, inset enough to survive Google's rounding.
    board = s * 0.62
    origin = (s - board) / 2
    draw_board(
        canvas, origin, origin, board, line=s * 0.012,
        filled={(0, 0): ACCENT, (1, 1): ACCENT, (2, 2): ACCENT},
    )
    size = canvas.downsample(SUPERSAMPLE).write_png(path)
    print(f"  {path}  512x512  {size / 1024:.0f} kB")


def make_feature_graphic(path):
    """1024x500, opaque. Play crops and overlays the edges, so nothing important goes near them."""
    w, h = 1024 * SUPERSAMPLE, 500 * SUPERSAMPLE
    canvas = Canvas(w, h, SLATE)
    canvas.vertical_gradient(SLATE_LIGHT, SLATE_DEEP)

    margin = 90 * SUPERSAMPLE
    board = 240 * SUPERSAMPLE
    board_x = w - board - margin
    draw_board(
        canvas, board_x, (h - board) / 2, board, line=3 * SUPERSAMPLE,
        filled={(0, 2): ACCENT, (1, 1): ACCENT, (2, 0): ACCENT},
    )

    title_cell, title_gap = 11 * SUPERSAMPLE, 12 * SUPERSAMPLE
    sub_cell, sub_gap = 6 * SUPERSAMPLE, 7 * SUPERSAMPLE
    title_height, sub_height = 7 * title_cell, 7 * sub_cell
    lead = 34 * SUPERSAMPLE
    top = (h - (title_height + lead + sub_height)) / 2

    title_width = draw_word(canvas, "KRATKOZA", margin, top, title_cell, PAPER, title_gap)
    draw_word(canvas, "SUDOKU", margin, top + title_height + lead, sub_cell, ACCENT, sub_gap)

    clearance = (board_x - (margin + title_width)) / SUPERSAMPLE
    assert clearance > 24, f"wordmark runs into the board ({clearance:.0f} px clearance)"
    size = canvas.downsample(SUPERSAMPLE).write_png(path)
    print(f"  {path}  1024x500  {size / 1024:.0f} kB   (odstep napis-plansza: {clearance:.0f} px)")


if __name__ == "__main__":
    out = Path(__file__).resolve().parent.parent / "store"
    out.mkdir(exist_ok=True)
    print("Grafiki do Google Play:")
    make_icon(out / "icon-512.png")
    make_feature_graphic(out / "feature-graphic-1024x500.png")
