"""Generates every texture the Chinese chess mod ships.

The script is intentionally kept in the repository: the art is procedurally derived from system
fonts, so regenerating it is preferable to committing opaque binaries with no provenance.

Run with the bundled interpreter:
    python tools/generate_textures.py
"""

from __future__ import annotations

import os
import sys

from PIL import Image, ImageDraw, ImageFont

# --------------------------------------------------------------------------- configuration

RESOURCES = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "src", "main", "resources", "assets", "chinese_chess", "textures",
)

FONT_CANDIDATES = [
    r"C:\Windows\Fonts\simkai.ttf",   # 楷体 - the traditional face for playing pieces
    r"C:\Windows\Fonts\simhei.ttf",   # 黑体 - fallback with full coverage
    r"C:\Windows\Fonts\simsun.ttc",   # 宋体 - last resort
    "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc",
    "/System/Library/Fonts/PingFang.ttc",
]

# 128 px per symbol: the GUI can draw a piece at roughly 110 device pixels on a 1440p window with
# GUI scale 4, and a tile that is never upscaled is the difference between a crisp character and a
# smeared one.
TILE = 96
SUPERSAMPLE = 2
COLUMNS = 6
# Three rows of symbols, plus one spare row that holds the plain tile described below.
ROWS = 4
# A solid white tile, used by geometry that supplies its own colour in the vertex colour - the side of
# a playing piece, for instance. White so the vertex colour is the final colour, and a tile rather than
# a second texture so the side can share the lit glyph layer instead of an unlit debug layer.
SOLID_TILE_INDEX = 18

# The character fills most of the counter and is stroked, which keeps the strokes readable once the
# piece is only a few dozen pixels wide.
CHARACTER_RATIO = 0.60
STROKE_RATIO = 0.045

RED_INK = (168, 26, 26)
BLACK_INK = (24, 24, 30)
IVORY_TOP = (252, 244, 222)
IVORY_BOTTOM = (228, 209, 170)
RIM = (58, 40, 20)
# The ring drawn around a general that is in check; matches XiangqiBoardScreen#CHECK_RING.
CHECK_RED = (255, 85, 68)

# Tile index -> (character, ink) ; row major, matching BoardGlyphAtlas.
TILES = [
    ("帅", RED_INK),    ("仕", RED_INK),    ("相", RED_INK),
    ("马", RED_INK),    ("车", RED_INK),    ("炮", RED_INK),
    ("将", BLACK_INK),    ("士", BLACK_INK),    ("象", BLACK_INK),
    ("马", BLACK_INK),    ("车", BLACK_INK),    ("炮", BLACK_INK),
    ("兵", RED_INK),    ("卒", BLACK_INK),
    ("楚", (94, 52, 22)),    ("河", (94, 52, 22)),
    ("汉", (94, 52, 22)),    ("界", (94, 52, 22)),
]

PIECE_TILES = set(range(14))


def load_font(size: int) -> ImageFont.FreeTypeFont:
    for path in FONT_CANDIDATES:
        if os.path.exists(path):
            try:
                return ImageFont.truetype(path, size)
            except OSError:
                continue
    raise RuntimeError("no CJK font found; install one of: " + ", ".join(FONT_CANDIDATES))


def centred_text(draw: ImageDraw.ImageDraw, text: str, font, ink, stroke: int, box) -> None:
    """Draws text centred in ``box`` using the glyph's own bounding box for optical centring."""
    left, top, right, bottom = box
    bbox = draw.textbbox((0, 0), text, font=font, stroke_width=stroke)
    width = bbox[2] - bbox[0]
    height = bbox[3] - bbox[1]
    x = left + (right - left - width) / 2 - bbox[0]
    y = top + (bottom - top - height) / 2 - bbox[1]
    draw.text((x, y), text, font=font, fill=ink, stroke_width=stroke, stroke_fill=ink)


def draw_piece_tile(character: str, ink) -> Image.Image:
    size = TILE * SUPERSAMPLE
    image = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)

    margin = int(size * 0.035)
    disc = (margin, margin, size - margin, size - margin)

    # Body: a vertical ivory gradient so the disc reads as a slightly domed counter.
    body = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    body_draw = ImageDraw.Draw(body)
    for y in range(margin, size - margin):
        ratio = (y - margin) / max(1, (size - 2 * margin))
        colour = tuple(
            int(IVORY_TOP[i] + (IVORY_BOTTOM[i] - IVORY_TOP[i]) * ratio) for i in range(3)
        )
        body_draw.line([(0, y), (size, y)], fill=colour + (255,))

    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).ellipse(disc, fill=255)
    image.paste(body, (0, 0), mask)

    # A dark outer rim first: on a light wooden board the ivory counter needs an edge to read
    # against, which is what makes the pieces legible at a glance.
    draw.ellipse(disc, outline=RIM + (255,), width=int(size * 0.045))
    ring_inset = int(size * 0.075)
    draw.ellipse(
        (margin + ring_inset, margin + ring_inset, size - margin - ring_inset, size - margin - ring_inset),
        outline=ink + (255,),
        width=int(size * 0.038),
    )

    font = load_font(int(size * CHARACTER_RATIO))
    stroke = max(1, int(size * CHARACTER_RATIO * STROKE_RATIO))
    centred_text(draw, character, font, ink + (255,), stroke, (margin, margin, size - margin, size - margin))

    return image.resize((TILE, TILE), Image.LANCZOS)


def draw_river_tile(character: str, ink) -> Image.Image:
    size = TILE * SUPERSAMPLE
    image = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    font = load_font(int(size * 0.80))
    stroke = max(1, int(size * 0.80 * 0.04))
    centred_text(draw, character, font, ink + (255,), stroke, (0, 0, size, size))
    return image.resize((TILE, TILE), Image.LANCZOS)


def generate_glyph_sheet() -> None:
    sheet = Image.new("RGBA", (COLUMNS * TILE, ROWS * TILE), (0, 0, 0, 0))
    for index, (character, ink) in enumerate(TILES):
        tile = (
            draw_piece_tile(character, ink)
            if index in PIECE_TILES
            else draw_river_tile(character, ink)
        )
        x = (index % COLUMNS) * TILE
        y = (index // COLUMNS) * TILE
        sheet.alpha_composite(tile, (x, y))
    solid = Image.new("RGBA", (TILE, TILE), (255, 255, 255, 255))
    sheet.alpha_composite(
        solid,
        ((SOLID_TILE_INDEX % COLUMNS) * TILE, (SOLID_TILE_INDEX // COLUMNS) * TILE),
    )
    write(sheet, "entity", "board_glyphs.png")


# --------------------------------------------------------------------------- wood


def plank_texture(base, seam, grain, lines_at, size=16, seed=7) -> Image.Image:
    """A deterministic fake-plank pattern; the same seed always produces the same texture."""
    scale = 8
    big = Image.new("RGB", (size * scale, size * scale), base)
    draw = ImageDraw.Draw(big)

    state = seed
    for y in range(size * scale):
        for x in range(size * scale):
            state = (state * 1103515245 + 12345) & 0x7FFFFFFF
            if state % 23 == 0:
                draw.point((x, y), fill=grain)

    for line in lines_at:
        draw.line([(0, line * scale), (size * scale, line * scale)], fill=seam, width=max(1, scale // 4))
    for line in lines_at:
        offset = ((line * 37) % size) * scale
        draw.line([(offset, line * scale), (offset, (line + 4) * scale)], fill=seam, width=max(1, scale // 5))

    return big.resize((size, size), Image.LANCZOS)


def generate_block_textures() -> None:
    top = plank_texture(
        base=(206, 172, 116), seam=(168, 134, 84), grain=(188, 154, 100), lines_at=[0, 5, 10, 15]
    )
    write(top, "block", "board_top.png")

    side = plank_texture(
        base=(163, 126, 74), seam=(128, 96, 54), grain=(146, 112, 64), lines_at=[0, 8], seed=13
    )
    write(side, "block", "board_side.png")


# --------------------------------------------------------------------------- item


def generate_item_icon() -> None:
    scale = 16
    size = 16 * scale
    image = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)

    wood = plank_texture(
        base=(214, 180, 122), seam=(176, 142, 92), grain=(196, 162, 108), lines_at=[0, 5, 10, 15]
    ).resize((size, size), Image.NEAREST)
    image.paste(wood, (0, 0))

    border = 1 * scale
    draw.rectangle((0, 0, size - 1, size - 1), outline=(120, 84, 44, 255), width=border)

    ink = (70, 44, 20, 255)
    inset = 3 * scale
    line_width = max(1, scale // 3)
    for index in range(4):
        position = inset + index * (size - 2 * inset) // 3
        draw.line([(inset, position), (size - inset, position)], fill=ink, width=line_width)
    for index in range(4):
        position = inset + index * (size - 2 * inset) // 3
        draw.line([(position, inset), (position, size - inset)], fill=ink, width=line_width)

    piece_radius = int(1.6 * scale)
    centre = size // 2
    spacing = (size - 2 * inset) // 3
    for (cx, cy), ink_colour in (
        ((centre, centre + spacing), RED_INK),
        ((centre, centre - spacing), BLACK_INK),
    ):
        draw.ellipse(
            (cx - piece_radius, cy - piece_radius, cx + piece_radius, cy + piece_radius),
            fill=(248, 238, 212, 255),
            outline=ink_colour + (255,),
            width=max(1, scale // 2),
        )

    write(image.resize((16, 16), Image.LANCZOS), "item", "xiangqi_board.png")


# --------------------------------------------------------------------------- plumbing


HALO_SIZE = 128
HALO_SUPERSAMPLE = 4
# The ring's radii as a fraction of the sprite's half width. The sprite is blitted at 1.2 cells, so
# 0.478..0.544 of that half width lands just outside the printed disc of a piece (whose painted edge
# reaches 0.472 of a cell) - a hair of a gap, and a line about a third as thick as the old block ring.
HALO_INNER = 51 / 64
HALO_OUTER = 58 / 64


def generate_halo() -> None:
    size = HALO_SIZE * HALO_SUPERSAMPLE
    image = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    centre = size / 2
    outer = centre * HALO_OUTER
    inner = centre * HALO_INNER
    draw.ellipse((centre - outer, centre - outer, centre + outer, centre + outer), fill=CHECK_RED + (255,))
    draw.ellipse((centre - inner, centre - inner, centre + inner, centre + inner), fill=(0, 0, 0, 0))
    write(image.resize((HALO_SIZE, HALO_SIZE), Image.LANCZOS), "gui", "halo.png")


def write(image: Image.Image, *parts: str) -> None:
    directory = os.path.join(RESOURCES, *parts[:-1])
    os.makedirs(directory, exist_ok=True)
    path = os.path.join(directory, parts[-1])
    image.save(path)
    print("wrote", path, image.size)


def main() -> int:
    generate_glyph_sheet()
    generate_halo()
    generate_block_textures()
    generate_item_icon()
    return 0


if __name__ == "__main__":
    sys.exit(main())
