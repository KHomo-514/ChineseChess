"""Renders a mock of the board GUI so the layout can be judged before touching Java.

The constants and the arithmetic below are the ones ``BoardGuiLayout`` and ``XiangqiBoardScreen``
use; only the font rasteriser differs (PIL here, Minecraft's font in game), so the preview is a
reliable check of geometry, contrast and legibility.

    python tools/preview_gui.py
"""

from __future__ import annotations

import math
import os
import sys

from PIL import Image, ImageDraw, ImageFilter, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
RESOURCES = os.path.join(os.path.dirname(HERE), "src", "main", "resources", "assets", "chinese_chess")
OUT = os.path.join(os.path.dirname(HERE), "docs")

# --- must match BoardGuiLayout -------------------------------------------------
TOPBAR = 44
FIRST_ROW = 4
SECOND_ROW = 24
MARGIN = 8
CELL_MARGIN = 0.55
GRID_W = 8
GRID_H = 9

# --- palette, must match XiangqiBoardScreen ------------------------------------
# The screen no longer paints an opaque backdrop: it asks for the vanilla blurred world first and
# only tints it, so the world stays recognisable behind the board. Alpha values matter here.
SCRIM = (20, 14, 8, 0x47)
SCRIM_EDGE = (0, 0, 0, 0x3C)
VANILLA_VEIL = (0, 0, 0, 0)         # the game takes only the blur, not the vanilla menu veil
BAR_FILL = (22, 15, 7, 0xB8)
BAR_LINE = (74, 52, 24, 0xCC)
PANEL_SHADOW = (0, 0, 0, 0x14)
FRAME = (58, 38, 16, 0xF2)
BOARD = (227, 200, 142)
BOARD_DARK = (206, 176, 118)
GRID = (58, 40, 20)
TITLE = (255, 233, 176)
STATUS = (235, 221, 190)
WARN = (255, 118, 108)
LABEL = (214, 198, 168)
LAST_MOVE = (255, 179, 0, 102)
SELECTED = (255, 213, 74, 150)
TARGET = (46, 204, 113, 204)
CHECK = (255, 68, 68, 150)
WARN_RING = (255, 68, 68, 235)
BUTTON = (48, 36, 20)
BUTTON_TEXT = (240, 230, 205)

TILES = 6
TILE = 96

# glyph tile index per (side, type) ??mirrors BoardGlyphAtlas
RED = {"G": 0, "A": 1, "E": 2, "H": 3, "R": 4, "C": 5, "S": 12}
BLACK = {"G": 6, "A": 7, "E": 8, "H": 9, "R": 10, "C": 11, "S": 13}

SETUP = [
    ("R", 0, 0, "red"), ("H", 1, 0, "red"), ("E", 2, 0, "red"), ("A", 3, 0, "red"),
    ("G", 4, 0, "red"), ("A", 5, 0, "red"), ("E", 6, 0, "red"), ("H", 7, 0, "red"),
    ("R", 8, 0, "red"),
    ("C", 1, 2, "red"), ("C", 7, 2, "red"),
    ("S", 0, 3, "red"), ("S", 2, 3, "red"), ("S", 4, 3, "red"), ("S", 6, 3, "red"), ("S", 8, 3, "red"),
    ("S", 0, 6, "black"), ("S", 2, 6, "black"), ("S", 4, 6, "black"), ("S", 6, 6, "black"),
    ("S", 8, 6, "black"),
    ("C", 1, 7, "black"), ("C", 7, 7, "black"),
    ("R", 0, 9, "black"), ("H", 1, 9, "black"), ("E", 2, 9, "black"), ("A", 3, 9, "black"),
    ("G", 4, 9, "black"), ("A", 5, 9, "black"), ("E", 6, 9, "black"), ("H", 7, 9, "black"),
    ("R", 8, 9, "black"),
]


def load_font(size: int):
    for path in (r"C:\Windows\Fonts\simhei.ttf", r"C:\Windows\Fonts\msyh.ttc",
                 "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc"):
        if os.path.exists(path):
            try:
                return ImageFont.truetype(path, size)
            except OSError:
                continue
    raise RuntimeError("no CJK font")


class Layout:
    def __init__(self, width, height, flipped=False):
        self.width, self.height, self.flipped = width, height, flipped

    # mirrors BoardGuiLayout.create
    def create(self):
        avail_w = self.width - 2 * MARGIN
        avail_h = self.height - TOPBAR - 2 * MARGIN
        self.cell = max(8, int(min(avail_w / (GRID_W + 2 * CELL_MARGIN), avail_h / (GRID_H + 2 * CELL_MARGIN))))
        self.pad = round(CELL_MARGIN * self.cell)
        self.panel_w = GRID_W * self.cell + 2 * self.pad
        self.panel_h = GRID_H * self.cell + 2 * self.pad
        self.panel_x = (self.width - self.panel_w) // 2
        self.panel_y = TOPBAR + MARGIN + (avail_h - self.panel_h) // 2
        self.grid_x = self.panel_x + self.pad
        self.grid_y = self.panel_y + self.pad
        return self

    def x(self, file):
        f = GRID_W - file if self.flipped else file
        return self.grid_x + f * self.cell

    def y(self, rank):
        r = rank if self.flipped else GRID_H - rank
        return self.grid_y + r * self.cell


def blurred_world(width, height):
    """A stand-in for the blurred game world, so contrast can be judged in the preview."""
    bg = Image.new("RGB", (width, height), (128, 172, 232))
    d = ImageDraw.Draw(bg, "RGBA")
    horizon = int(height * 0.52)
    d.rectangle((0, horizon, width, height), fill=(98, 140, 76))
    d.rectangle((0, horizon - 6, width, horizon), fill=(140, 178, 94))
    for i in range(16):
        x = (i * 149) % max(width, 1)
        y = horizon - 46 + (i * 53) % 96
        w = 24 + (i * 17) % 64
        h = 18 + (i * 29) % 72
        d.rectangle((x, y, x + w, y + h),
                    fill=(150 + (i * 31) % 80, 118 + (i * 23) % 70, 88 + (i * 19) % 60))
    return bg.filter(ImageFilter.GaussianBlur(max(3, width // 220)))


def render(width, height, scale=3, state="playing", flipped=False, out_name=None):
    L = Layout(width, height, flipped).create()
    # vanilla blurred world, then the vanilla veil, then our warm scrim - the same order the game
    # uses, all of it translucent so the world stays visible
    img = blurred_world(width, height)
    d = ImageDraw.Draw(img, "RGBA")
    d.rectangle((0, 0, width, height), fill=VANILLA_VEIL)
    d.rectangle((0, 0, width, height), fill=SCRIM)
    d.rectangle((0, TOPBAR, width, TOPBAR + 6), fill=SCRIM_EDGE)
    d.rectangle((0, height - 18, width, height), fill=SCRIM_EDGE)
    font = load_font(9)
    font_big = load_font(14)
    font_small = load_font(8)

    # board panel: soft drop shadow, then the opaque board so the pieces keep their contrast
    for ring in range(6, 0, -1):
        d.rectangle((L.panel_x - ring - 2, L.panel_y - ring - 2,
                     L.panel_x + L.panel_w + ring + 1, L.panel_y + L.panel_h + ring + 1), fill=PANEL_SHADOW)
    d.rectangle((L.panel_x - 2, L.panel_y - 2, L.panel_x + L.panel_w + 1, L.panel_y + L.panel_h + 1), fill=FRAME)
    d.rectangle((L.panel_x, L.panel_y, L.panel_x + L.panel_w - 1, L.panel_y + L.panel_h - 1), fill=BOARD)
    # subtle inner grain
    for i in range(L.panel_y + 2, L.panel_y + L.panel_h - 2, 4):
        d.line((L.panel_x + 1, i, L.panel_x + L.panel_w - 2, i), fill=(*BOARD_DARK, 70))

    # grid
    left, right = L.x(0), L.x(GRID_W)
    top, bottom = L.y(GRID_H), L.y(0)
    lo, hi = min(left, right), max(left, right)
    to, bo = min(top, bottom), max(top, bottom)
    for rank in range(GRID_H + 1):
        y = L.y(rank)
        d.rectangle((lo, y, hi, y), fill=GRID)
    for file in range(GRID_W + 1):
        x = L.x(file)
        if file in (0, GRID_W):
            d.rectangle((x, to, x, bo), fill=GRID)
        else:
            d.rectangle((x, L.y(5), x, bo), fill=GRID)
            d.rectangle((x, to, x, L.y(4)), fill=GRID)
    for (f0, r0, f1, r1) in ((3, 0, 5, 2), (5, 0, 3, 2), (3, 7, 5, 9), (5, 7, 3, 9)):
        steps = max(abs(L.x(f1) - L.x(f0)), abs(L.y(r1) - L.y(r0)))
        for s in range(steps + 1):
            x = L.x(f0) + (L.x(f1) - L.x(f0)) * s // steps
            y = L.y(r0) + (L.y(r1) - L.y(r0)) * s // steps
            d.point((x, y), fill=GRID)

    # river characters
    atlas = Image.open(os.path.join(RESOURCES, "textures", "entity", "board_glyphs.png")).convert("RGBA")
    halo_sprite = Image.open(os.path.join(RESOURCES, "textures", "gui", "halo.png")).convert("RGBA")

    def tile(index):
        col, row = index % TILES, index // TILES
        return atlas.crop((col * TILE, row * TILE, (col + 1) * TILE, (row + 1) * TILE))

    river_y = (L.y(4) + L.y(5)) // 2
    # Mirrors XiangqiBoardScreen's two position arrays: turning the board around trades the halves the
    # two phrases sit in, and each phrase still reads forwards. Mirroring every glyph on its own would
    # spell 河楚 and 界汉.
    river_positions = (5.5, 6.5, 1.5, 2.5) if flipped else (1.5, 2.5, 5.5, 6.5)
    for slot, file_pos in enumerate(river_positions):
        size = int(L.cell * 1.05)
        x = int(L.grid_x + file_pos * L.cell) - size // 2
        img.paste(tile(14 + slot).resize((size, size), Image.LANCZOS), (x, river_y - size // 2), tile(14 + slot).resize((size, size), Image.LANCZOS))

    # marks under the pieces: last move and the check warning
    def mark(square, colour, half_ratio=0.45):
        f, r = square
        cx, cy = L.x(f), L.y(r)
        h = int(L.cell * half_ratio)
        d.rectangle((cx - h, cy - h, cx + h, cy + h), fill=colour)

    def ring(square, colour, ratio=0.44, width=2):
        f, r = square
        cx, cy = L.x(f), L.y(r)
        h = int(L.cell * ratio)
        d.rectangle((cx - h, cy - h, cx + h, cy + h), outline=colour, width=width)

    def halo(square):
        """Mirrors XiangqiBoardScreen#halo: one blit of the anti-aliased ring sprite."""
        f, r = square
        cx, cy = L.x(f), L.y(r)
        size = max(6, int(round(L.cell * 1.2)))
        sprite = halo_sprite.resize((size, size), Image.LANCZOS)
        img.paste(sprite, (cx - size // 2, cy - size // 2), sprite)

    def brackets(square, colour, ratio=0.5):
        """Mirrors XiangqiBoardScreen#brackets: four corners, the selected piece's marker."""
        f, r = square
        cx, cy = L.x(f), L.y(r)
        h = max(4, int(L.cell * ratio))
        w = max(2, L.cell // 10)
        arm = max(w + 3, int(h * 0.55))
        left, right, top, bottom = cx - h, cx + h, cy - h, cy + h
        for (x0, y0, x1, y1) in (
            (left, top, left + arm, top + w), (left, top, left + w, top + arm),
            (right - arm, top, right, top + w), (right - w, top, right, top + arm),
            (left, bottom - w, left + arm, bottom), (left, bottom - arm, left + w, bottom),
            (right - arm, bottom - w, right, bottom), (right - w, bottom - arm, right, bottom),
        ):
            d.rectangle((x0, y0, x1, y1), fill=colour)

    if state != "waiting":
        for sq in ((0, 3), (0, 4)):
            mark(sq, LAST_MOVE, 0.42)

    # pieces
    piece = int(L.cell * 0.96)
    for (kind, f, r, side) in SETUP:
        img.paste(tile((BLACK if side == "black" else RED)[kind]).resize((piece, piece), Image.LANCZOS),
                  (L.x(f) - piece // 2, L.y(r) - piece // 2), tile((BLACK if side == "black" else RED)[kind]).resize((piece, piece), Image.LANCZOS))

    # marks over the pieces: rings stay visible even when a piece stands on the point
    if state == "playing":
        for sq in ((1, 4), (1, 5), (2, 2), (0, 2), (4, 2)):
            ring(sq, TARGET, 0.38, max(2, L.cell // 12))
        brackets((1, 2), SELECTED, 0.5)
    if state == "check":
        halo((4, 0))

    # top bar: two rows, everything at 2x (whole-number font scaling only)
    d.rectangle((0, 0, width, TOPBAR - 1), fill=BAR_FILL)
    d.rectangle((0, TOPBAR - 1, width, TOPBAR - 1), fill=BAR_LINE)
    font_2x = load_font(18)
    title = "中国象棋"
    d.text((10, FIRST_ROW), title, font=font_2x, fill=TITLE)
    cursor = 10 + int(d.textlength(title, font=font_2x)) + 18
    status = {"playing": "轮到你走棋", "waiting": "等待对手", "check": "你被将军，请应将", "done": "对局结束 · 黑方胜"}[state]
    colour = WARN if state == "check" else STATUS
    d.text((cursor, FIRST_ROW), status, font=font_2x, fill=colour)
    info = {"playing": "你执红方  ·  第 13 手  ·  自然限着 12 / 120",
            "waiting": "右击棋盘即可入座",
            "check": "你执红方  ·  第 13 手",
            "done": "你执红方  ·  第 13 手"}[state]
    # no leave button and no hint: escape is the only way out, silently
    can_resign = state in ("playing", "check")
    button_left = width - 8 - (58 + (6 + 58 if can_resign else 0))
    limit = button_left - 10 - 12
    if limit > 48:
        if d.textlength(info, font=font_2x) > limit:
            info = "第 13 手"
        if d.textlength(info, font=font_2x) <= limit:
            d.text((10, SECOND_ROW), info, font=font_2x, fill=LABEL)

    # buttons, laid out from the right inwards: resign needs a running game, reset only needs a seat
    def button(x, text):
        by = (TOPBAR - 20) // 2
        d.rectangle((x, by, x + 58, by + 20), fill=BUTTON, outline=BAR_LINE)
        w = d.textlength(text, font=load_font(9))
        d.text((x + (58 - w) / 2, by + 6), text, font=load_font(9), fill=BUTTON_TEXT)

    bx = width - 8
    if can_resign:
        bx -= 58
        button(bx, "认输")
        bx -= 6
    bx -= 58
    button(bx, "重置")

    if state == "done":
        text = "对局结束 · 黑方胜"
        w = d.textlength(text, font=font_big)
        d.rectangle(((width - w) // 2 - 12, height // 2 - 14, (width + w) // 2 + 12, height // 2 + 16), fill=(0, 0, 0, 210))
        d.text(((width - w) // 2, height // 2 - 8), text, font=font_big, fill=TITLE)

    big = img.resize((width * scale, height * scale), Image.NEAREST)
    name = out_name or f"ui-preview-{width}x{height}-{state}.png"
    path = os.path.join(OUT, name)
    os.makedirs(OUT, exist_ok=True)
    big.save(path)
    print("wrote", path, big.size, "cell =", L.cell, "panel =", L.panel_w, "x", L.panel_h)


def main() -> int:
    render(640, 360, 3, "playing", False)
    render(640, 360, 3, "check", False)
    render(426, 240, 3, "playing", True)
    render(640, 360, 3, "done", False)
    return 0


if __name__ == "__main__":
    sys.exit(main())
