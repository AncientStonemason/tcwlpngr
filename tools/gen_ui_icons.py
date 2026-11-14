#!/usr/bin/env python3
"""Generates textures/gui/icons.png: 16px white pixel icons, tinted at draw time (gui/UiIcons).

Run: python3 tools/gen_ui_icons.py [preview.png]   (needs Pillow). Cell order must match UiIcons constants.
"""
import sys
from pathlib import Path

from PIL import Image, ImageDraw

S, COLS, ROWS = 16, 8, 4
W = (255, 255, 255, 255)
H = (255, 255, 255, 150)  # softer secondary stroke
CLEAR = (0, 0, 0, 0)


def L(d, *pts, c=W, w=1):
    d.line(pts, fill=c, width=w)


def search(d):
    d.ellipse((2, 2, 10, 10), outline=W, width=2)
    L(d, (10, 10), (13, 13), w=2)


def close(d):
    L(d, (4, 4), (11, 11), w=2)
    L(d, (11, 4), (4, 11), w=2)


def back(d):
    L(d, (3, 7), (13, 7), w=2)
    L(d, (3, 7), (7, 3), w=2)
    L(d, (3, 8), (7, 12), w=2)


def chev_right(d):
    L(d, (6, 3), (10, 7), w=2)
    L(d, (10, 8), (6, 12), w=2)


def chev_down(d):
    L(d, (3, 6), (7, 10), w=2)
    L(d, (8, 10), (12, 6), w=2)


def gear(d):
    for x, y in ((7, 1), (7, 13), (1, 7), (13, 7)):
        d.rectangle((x, y, x + 1, y + 1), fill=W)
    for x, y in ((3, 3), (11, 3), (3, 11), (11, 11)):
        d.rectangle((x, y, x + 1, y + 1), fill=W)
    d.ellipse((3, 3, 12, 12), fill=W)
    d.ellipse((6, 6, 9, 9), fill=CLEAR)


def hud(d):
    d.rectangle((1, 2, 14, 13), outline=W)
    d.rectangle((3, 4, 7, 6), fill=W)
    d.rectangle((3, 8, 5, 11), fill=H)
    d.rectangle((10, 9, 12, 11), fill=W)


def sliders(d):
    for y, k in ((3, 9), (7, 4), (11, 11)):
        L(d, (2, y + 1), (13, y + 1), c=H)
        d.rectangle((k, y - 1, k + 2, y + 3), fill=W)


def play(d):
    d.polygon(((5, 3), (5, 12), (12, 7.5)), fill=W)


def stop(d):
    d.rectangle((4, 4, 11, 11), fill=W)


def save(d):
    d.polygon(((2, 2), (11, 2), (13, 4), (13, 13), (2, 13)), outline=W)
    d.rectangle((5, 2, 9, 5), fill=W)
    d.rectangle((5, 9, 10, 13), fill=H)


def trash(d):
    L(d, (2, 4), (13, 4))
    d.rectangle((6, 2, 9, 3), fill=W)
    d.rectangle((4, 6, 11, 13), outline=W)
    L(d, (6, 8), (6, 11), c=H)
    L(d, (9, 8), (9, 11), c=H)


def pencil(d):
    L(d, (4, 11), (11, 4), w=3)
    d.rectangle((11, 2, 13, 4), fill=H)
    d.rectangle((2, 12, 3, 13), fill=W)


def grid(d):
    for x in (2, 9):
        for y in (2, 9):
            d.rectangle((x, y, x + 4, y + 4), fill=W)


def plus(d):
    d.rectangle((7, 3, 8, 12), fill=W)
    d.rectangle((3, 7, 12, 8), fill=W)


def folder(d):
    d.rectangle((2, 3, 6, 5), fill=W)
    d.rectangle((2, 5, 13, 12), outline=W)
    L(d, (3, 7), (12, 7), c=H)


def copy(d):
    d.rectangle((2, 2, 9, 10), outline=H)
    d.rectangle((6, 5, 13, 13), fill=CLEAR, outline=W)
    L(d, (8, 8), (11, 8), c=H)
    L(d, (8, 10), (11, 10), c=H)


def refresh(d):
    d.arc((3, 3, 12, 12), 40, 320, fill=W, width=2)
    d.polygon(((10, 2), (14, 2), (14, 6)), fill=W)


def pin(d):
    d.ellipse((4, 1, 11, 8), fill=W)
    d.polygon(((5, 7), (10, 7), (7.5, 14)), fill=W)
    d.ellipse((6, 3, 9, 6), fill=CLEAR)


def info(d):
    d.ellipse((1, 1, 14, 14), outline=W)
    d.rectangle((7, 4, 8, 5), fill=W)
    d.rectangle((7, 7, 8, 11), fill=W)


def warn(d):
    d.polygon(((7.5, 1), (14, 13), (1, 13)), outline=W)
    d.rectangle((7, 5, 8, 9), fill=W)
    d.rectangle((7, 11, 8, 11), fill=W)


def keyboard(d):
    d.rectangle((1, 4, 14, 12), outline=W)
    for x in (3, 6, 9, 12):
        d.point((x, 6), fill=W)
    for x in (4, 7, 10):
        d.point((x, 8), fill=W)
    L(d, (5, 10), (10, 10))


def eye(d):
    d.polygon(((1, 8), (4, 4), (11, 4), (14, 8), (11, 11), (4, 11)), outline=W)
    d.rectangle((6, 6, 9, 9), fill=W)


def check(d):
    L(d, (3, 8), (6, 11), w=2)
    L(d, (6, 11), (12, 4), w=2)


def pause(d):
    d.rectangle((4, 3, 6, 12), fill=W)
    d.rectangle((9, 3, 11, 12), fill=W)


def record(d):
    d.ellipse((2, 2, 13, 13), outline=H)
    d.ellipse((5, 5, 10, 10), fill=W)


def undo(d):
    d.arc((4, 4, 13, 13), 180, 90, fill=W, width=2)
    d.polygon(((1, 6), (6, 2), (6, 10)), fill=W)


def redo(d):
    d.arc((2, 4, 11, 13), 90, 0, fill=W, width=2)
    d.polygon(((14, 6), (9, 2), (9, 10)), fill=W)


def star(d):
    d.polygon(
        ((7.5, 1), (9.5, 5.5), (14, 6), (10.5, 9), (11.5, 14), (7.5, 11.5), (3.5, 14), (4.5, 9), (1, 6), (5.5, 5.5)),
        fill=W,
    )


def clock(d):
    d.ellipse((1, 1, 14, 14), outline=W)
    L(d, (7, 4), (7, 8))
    L(d, (7, 8), (10, 8))


def download(d):
    d.rectangle((7, 2, 8, 8), fill=W)
    d.polygon(((4, 7), (11, 7), (7.5, 11)), fill=W)
    L(d, (2, 13), (13, 13), w=2)


def upload(d):
    d.rectangle((7, 5, 8, 11), fill=W)
    d.polygon(((4, 6), (11, 6), (7.5, 2)), fill=W)
    L(d, (2, 13), (13, 13), w=2)


ICONS = [
    search, close, back, chev_right, chev_down, gear, hud, sliders,
    play, stop, save, trash, pencil, grid, plus, folder,
    copy, refresh, pin, info, warn, keyboard, eye, check,
    pause, record, undo, redo, star, clock, download, upload,
]  # fmt: skip

atlas = Image.new("RGBA", (S * COLS, S * ROWS), CLEAR)
for i, fn in enumerate(ICONS):
    cell = Image.new("RGBA", (S, S), CLEAR)
    draw = ImageDraw.Draw(cell)
    draw.fontmode = "1"  # no anti-aliasing: crisp pixels
    fn(draw)
    atlas.paste(cell, (i % COLS * S, i // COLS * S))
out = Path(__file__).resolve().parent.parent / "src/main/resources/assets/farmmacro/textures/gui/icons.png"
atlas.save(out, optimize=True)
print(out, atlas.size, len(ICONS))
if len(sys.argv) > 1:
    bg = Image.new("RGBA", atlas.size, (24, 28, 38, 255))
    bg.alpha_composite(atlas)
    bg.resize((atlas.width * 8, atlas.height * 8), Image.NEAREST).save(sys.argv[1])
