"""Renders the candidate styles for the "this piece is selected" marker side by side.

The samples use the board's own colours, the same cell proportions and the same glyph sheet as the
game, and the only thing that changes from one sample to the next is the shape of the marker - so the
choice comes down to shape alone. Reply with the letter of the one you want.

    python tools/preview_selection.py
"""

from __future__ import annotations

import os
import sys

from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from preview_gui import (  # noqa: E402  (path has to be set first)
    BOARD,
    GRID,
    RED,
    RESOURCES,
    SELECTED,
    TILE,
    load_font,
)

OUT = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "docs")
CELL = 56
SCALE = 2
SAMPLE = CELL * 3
SELECTED_RGB = SELECTED[:3]
SELECTED_ALPHA = SELECTED[3]
PIECE = int(CELL * 0.96)
GLYPH = RED["R"]


def glyph() -> Image.Image:
    atlas = Image.open(os.path.join(RESOURCES, "textures", "entity", "board_glyphs.png")).convert("RGBA")
    col, row = GLYPH % 6, GLYPH // 6
    tile = atlas.crop((col * TILE, row * TILE, (col + 1) * TILE, (row + 1) * TILE))
    return tile.resize((PIECE, PIECE), Image.LANCZOS)


def board_patch() -> Image.Image:
    image = Image.new("RGBA", (SAMPLE, SAMPLE), BOARD + (255,))
    draw = ImageDraw.Draw(image)
    centre = SAMPLE // 2
    for k in (-1, 0, 1):
        y = centre + k * CELL
        x = centre + k * CELL
        draw.rectangle((0, y, SAMPLE, y), fill=GRID + (255,))
        draw.rectangle((x, 0, x, SAMPLE), fill=GRID + (255,))
    return image


def marker(image: Image.Image, letter: str) -> None:
    draw = ImageDraw.Draw(image, "RGBA")
    centre = SAMPLE / 2
    half = CELL * 0.5
    thick = max(2, CELL // 10)
    piece = glyph()
    paste = (int(centre - PIECE / 2), int(centre - PIECE / 2))

    def square_ring(inset: float, width: int) -> None:
        h = half - inset
        draw.rectangle((centre - h, centre - h, centre + h, centre + h), outline=SELECTED, width=width)

    if letter == "A":       # the current style: a square ring around the cell
        image.alpha_composite(piece, paste)
        draw.rectangle((centre - half, centre - half, centre + half, centre + half),
                       outline=SELECTED, width=thick)
    elif letter == "B":     # a circle
        image.alpha_composite(piece, paste)
        draw.ellipse((centre - half, centre - half, centre + half, centre + half),
                     outline=SELECTED, width=thick)
    elif letter == "C":     # four corner brackets
        image.alpha_composite(piece, paste)
        arm = half * 0.55
        for sx in (-1, 1):
            for sy in (-1, 1):
                x, y = centre + sx * half, centre + sy * half
                draw.rectangle((min(x, x - sx * arm), min(y, y - sy * thick),
                                max(x, x - sx * arm), max(y, y - sy * thick)), fill=SELECTED)
                draw.rectangle((min(x, x - sx * thick), min(y, y - sy * arm),
                                max(x, x - sx * thick), max(y, y - sy * arm)), fill=SELECTED)
    elif letter == "D":     # a translucent cell fill under a solid rim
        draw.rectangle((centre - half, centre - half, centre + half, centre + half),
                       fill=SELECTED_RGB + (70,))
        image.alpha_composite(piece, paste)
        draw.rectangle((centre - half, centre - half, centre + half, centre + half),
                       outline=SELECTED, width=2)
    elif letter == "E":     # a lit pad on the board, under the piece
        draw.ellipse((centre - half, centre - half, centre + half, centre + half),
                     fill=SELECTED_RGB + (95,))
        draw.ellipse((centre - half, centre - half, centre + half, centre + half),
                     outline=SELECTED, width=3)
        image.alpha_composite(piece, paste)
    elif letter == "F":     # a halo hugging the piece itself
        image.alpha_composite(piece, paste)
        r = PIECE * 0.5 + thick
        draw.ellipse((centre - r, centre - r, centre + r, centre + r), outline=SELECTED, width=thick)
    elif letter == "G":     # double frame
        image.alpha_composite(piece, paste)
        square_ring(0.0, 2)
        square_ring(thick + 2, 2)
    elif letter == "H":     # diamond
        image.alpha_composite(piece, paste)
        points = [(centre, centre - half), (centre + half, centre), (centre, centre + half),
                  (centre - half, centre), (centre, centre - half)]
        draw.line(points, fill=SELECTED, width=thick, joint="curve")


CANDIDATES = [
    ("A", "方框环（现状）", "画在棋子之上"),
    ("B", "圆环", "画在棋子之上"),
    ("C", "四角括号", "画在棋子之上"),
    ("D", "实心底 + 细边框", "底色在棋子下，边框在上"),
    ("E", "地面光圈（圆盘 + 亮边）", "整体画在棋子之下"),
    ("F", "贴身光环", "画在棋子之上"),
    ("G", "双线方框", "画在棋子之上"),
    ("H", "菱形环", "画在棋子之上"),
]


def main() -> int:
    title_font = load_font(26)
    name_font = load_font(20)
    note_font = load_font(16)

    columns = 4
    rows = 2
    cell_w = SAMPLE * SCALE + 24
    cell_h = SAMPLE * SCALE + 78
    margin = 24
    width = margin * 2 + columns * cell_w
    height = margin * 3 + 60 + rows * cell_h

    sheet = Image.new("RGBA", (width, height), (24, 20, 14, 255))
    draw = ImageDraw.Draw(sheet)
    draw.text((margin, margin), "选中棋子的标记：候选样式（回复字母即可）", font=title_font,
              fill=(255, 233, 176))

    for index, (letter, name, note) in enumerate(CANDIDATES):
        col = index % columns
        row = index // columns
        x0 = margin + col * cell_w
        y0 = margin * 2 + 60 + row * cell_h

        sample = board_patch()
        marker(sample, letter)
        sample = sample.resize((SAMPLE * SCALE, SAMPLE * SCALE), Image.NEAREST)
        sheet.alpha_composite(sample.convert("RGBA"), (x0, y0 + 46))

        draw.rectangle((x0 - 1, y0 + 45, x0 + SAMPLE * SCALE, y0 + 46 + SAMPLE * SCALE),
                       outline=(74, 52, 24, 255))
        draw.text((x0, y0), f"{letter}. {name}", font=name_font, fill=(240, 230, 205))
        draw.text((x0, y0 + 24), note, font=note_font, fill=(176, 160, 130))

    os.makedirs(OUT, exist_ok=True)
    path = os.path.join(OUT, "selection-options.png")
    sheet.convert("RGB").save(path)
    print("wrote", path, sheet.size)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
