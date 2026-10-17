"""Reads a board position out of a screenshot of the mod's board screen.

It finds the printed grid first (the darkest brown columns and rows inside the panel), then classifies
each intersection by the ink it contains, and finally prints the position both as an ASCII board and as
a Xiangqi FEN that tools/perft/Perft.java can consume.
"""

from __future__ import annotations

import sys

from PIL import Image

GRID = (58, 40, 20)
TAN = (227, 200, 142)


def is_grid(pixel) -> bool:
    r, g, b = pixel[0], pixel[1], pixel[2]
    return r < 110 and g < 90 and b < 70 and r >= b


def is_ink_red(pixel) -> bool:
    # Red ink is (168, 26, 26). The yellow last-move wash mixed with a grid line lands near
    # (137, 95, 12), which the looser "greenish < 100" test used to swallow - so green is capped.
    r, g, b = pixel[0], pixel[1], pixel[2]
    return r > 110 and g < 80 and b < 80


def is_ink_dark(pixel) -> bool:
    r, g, b = pixel[0], pixel[1], pixel[2]
    return r < 90 and g < 90 and b < 110 and b >= r - 10


def is_body(pixel) -> bool:
    r, g, b = pixel[0], pixel[1], pixel[2]
    return r > 170 and g > 150 and b > 110


def find_lines(img, axis: str) -> list[int]:
    """Columns (or rows) whose dark-brown count peaks: these are the printed grid lines."""
    width, height = img.size
    pixels = img.load()
    if axis == "x":
        lo, hi, span = int(width * 0.02), int(width * 0.98), height
    else:
        lo, hi, span = int(height * 0.08), int(height * 0.98), width
    scores = []
    for fixed in range(lo, hi):
        count = 0
        for other in range(0, span, 3):
            pixel = pixels[fixed, other] if axis == "x" else pixels[other, fixed]
            if is_grid(pixel):
                count += 1
        scores.append((count, fixed))
    scores.sort(reverse=True)
    peaks: list[int] = []
    for count, fixed in scores:
        if count < 6:
            break
        if all(abs(fixed - kept) > 12 for kept in peaks):
            peaks.append(fixed)
        if len(peaks) >= 12:
            break
    return sorted(peaks)


def is_tan(pixel) -> bool:
    r, g, b = pixel[0], pixel[1], pixel[2]
    return r > 185 and 155 < g < 235 and 95 < b < 200


def dark_score(img, y: int, lo: int, hi: int) -> int:
    """
    How many dark-brown pixels on this horizontal line have board panel above or below them.

    The tan neighbours matter: the information strip is one long dark band too, and without this test
    the rank fit happily parks the top rank inside it.
    """
    pixels = img.load()
    height = img.size[1]
    count = 0
    for x in range(lo, hi, 2):
        if is_grid(pixels[x, y]):
            above = pixels[x, max(0, y - 9)]
            below = pixels[x, min(height - 1, y + 9)]
            if is_tan(above) or is_tan(below):
                count += 1
    return count


def rank_lines(img, xs: list[int], ys_seed: list[int]) -> list[int]:
    """
    The grid is square, so the rank spacing equals the file spacing.

    Rather than trusting every horizontal peak - the panel border, the river characters and the
    information strip all produce one - the ten rank lines are fitted as an arithmetic progression with
    that spacing, choosing the offset that covers the most printed grid.
    """
    spacing = (xs[-1] - xs[0]) // 8
    lo, hi = xs[0], xs[-1]
    best = None
    for y0 in range(100, img.size[1] - spacing * 9, 2):
        ys = [y0 + k * spacing for k in range(10)]
        if ys[-1] >= img.size[1]:
            break
        # nudge each line onto its local maximum so a one-pixel offset does not lose the score
        score = 0
        for y in ys:
            score += max(dark_score(img, y - 1, lo, hi), dark_score(img, y, lo, hi),
                         dark_score(img, y + 1, lo, hi))
        if best is None or score > best[0]:
            best = (score, ys)
    return best[1] if best else ys_seed


def group_lines(peaks: list[int], expected: int) -> list[int]:
    """Merges nearby peaks and returns the best run of `expected` evenly spaced lines."""
    merged: list[int] = []
    for value in peaks:
        if merged and value - merged[-1] <= 14:
            merged[-1] = (merged[-1] + value) // 2
        else:
            merged.append(value)
    if len(merged) < expected:
        return merged
    best = None
    for start in range(0, len(merged) - expected + 1):
        run = merged[start:start + expected]
        gaps = [run[i + 1] - run[i] for i in range(len(run) - 1)]
        spread = max(gaps) - min(gaps)
        if best is None or spread < best[0]:
            best = (spread, run)
    return best[1] if best else merged


def main(path: str) -> int:
    img = Image.open(path).convert("RGB")
    width, height = img.size
    print("image", width, "x", height)

    xs = group_lines(find_lines(img, "x"), 9)
    ys = rank_lines(img, xs, group_lines(find_lines(img, "y"), 10))
    print("file lines", xs)
    print("rank lines", ys)
    if len(xs) != 9 or len(ys) != 10:
        print("could not locate the grid")
        return 1

    pixels = img.load()
    step = min((xs[-1] - xs[0]) // 8, (ys[-1] - ys[0]) // 9)
    reach = max(3, step // 4)
    board = []
    highlighted = []
    for row, y in enumerate(ys):
        line = []
        for col, x in enumerate(xs):
            red = dark = yellow = 0
            for dy in range(-reach, reach + 1, 2):
                for dx in range(-reach, reach + 1, 2):
                    px, py = x + dx, y + dy
                    if 0 <= px < width and 0 <= py < height:
                        pixel = pixels[px, py]
                        if is_ink_red(pixel):
                            red += 1
                        elif is_ink_dark(pixel):
                            dark += 1
                        r, g, b = pixel[0], pixel[1], pixel[2]
                        if r > 200 and 140 < g < 220 and b < 130:
                            yellow += 1
            # A printed character leaves ink; bare board leaves none. The body colour is too close to
            # the tan panel to be a reliable test, and the grid's brown fails both ink tests.
            if red >= 12 or dark >= 12:
                line.append("R" if red >= dark else "B")
            else:
                line.append(".")
            if yellow > reach:
                highlighted.append((col, row))
        board.append(line)

    print()
    for row, line in enumerate(board):
        print(f"rank {9 - row if False else row:>2}: " + " ".join(line))
    print()
    print("yellow (last move) hints at column,row:", highlighted)

    # engine convention: rank 0 = red's back rank; FEN rows run rank 9 first.
    # The screen is mirrored for black, so column 0 of the image is engine file 8.
    fen_rows = []
    for row in range(9, -1, -1):
        text = ""
        empty = 0
        for col in range(8, -1, -1):
            cell = board[row][col]
            if cell == ".":
                empty += 1
                continue
            if empty:
                text += str(empty)
                empty = 0
            text += cell
        if empty:
            text += str(empty)
        fen_rows.append(text)
    print("fen (mirrored, i.e. as seen by black):", "/".join(fen_rows), "b")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1] if len(sys.argv) > 1 else "image.png"))
