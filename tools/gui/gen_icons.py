#!/usr/bin/env python3
"""Генератор атласов иконок интерфейса Kaelix.

Оригинальная векторная графика Kaelix (CC0, как и остальной собственный арт): каждая иконка описана
примитивами на сетке 24×24, рисуется в большом разрешении и уменьшается усреднением — без внешних
шрифтов, SVG и сторонних наборов иконок. Нужен только Pillow.

Иконки одноцветные (белые) с альфа-маской: штрих — полная непрозрачность, «второй тон» (заливка
формы) — FILL_ALPHA. Цвет задаёт игра при отрисовке (см. gui/Icon.java), поэтому иконки следуют теме.

Запуск из корня проекта:
    python3 tools/gui/gen_icons.py            # перезаписать атласы в src/main/resources
    python3 tools/gui/gen_icons.py --out DIR  # в другую папку
    python3 tools/gui/gen_icons.py --preview FILE.png  # дополнительно лист предпросмотра

Порядок иконок = порядок констант в gui/Icon.java (проверяет IconAtlasTest по icons.txt).
"""
from __future__ import annotations

import argparse
import math
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw

GRID = 24  # сторона иконки в условных единицах
MASTER = 768  # мастер-рендер одной иконки, px (32 px на единицу)
U = MASTER / GRID
STROKE = 2.0  # толщина штриха в единицах сетки
FILL_ALPHA = 0.26  # непрозрачность «второго тона»
CELLS = (24, 48, 96, 192)  # размеры ячейки атласов; игра берёт ближайший не меньше нужного
COLS = 8

DEFAULT_OUT = Path("src/main/resources/assets/farmmacro/textures/gui/icons")


# ── геометрия ────────────────────────────────────────────────────────────────────────────────
def arc(cx, cy, r, a0, a1, ry=None):
    """Дуга: углы в градусах, 0 — вправо, 90 — вниз (ось Y экрана направлена вниз)."""
    ry = r if ry is None else ry
    n = max(6, int(abs(a1 - a0) / 3))
    return [
        (
            cx + r * math.cos(math.radians(a0 + (a1 - a0) * i / n)),
            cy + ry * math.sin(math.radians(a0 + (a1 - a0) * i / n)),
        )
        for i in range(n + 1)
    ]


def circle(cx, cy, r, ry=None):
    return arc(cx, cy, r, 0, 360, ry)[:-1]


def rrect(x, y, w, h, r):
    r = min(r, w / 2, h / 2)
    return (
        arc(x + w - r, y + r, r, -90, 0)
        + arc(x + w - r, y + h - r, r, 0, 90)
        + arc(x + r, y + h - r, r, 90, 180)
        + arc(x + r, y + r, r, 180, 270)
    )


def bez(p0, p1, p2, p3, n=28):
    out = []
    for i in range(n + 1):
        t = i / n
        a, b, c, d = (1 - t) ** 3, 3 * (1 - t) ** 2 * t, 3 * (1 - t) * t * t, t**3
        out.append(
            (a * p0[0] + b * p1[0] + c * p2[0] + d * p3[0], a * p0[1] + b * p1[1] + c * p2[1] + d * p3[1])
        )
    return out


def path(*parts):
    """Склейка кусков: точка (x, y) или список точек; повторы на стыках убираются."""
    out = []
    for part in parts:
        pts = [part] if isinstance(part, tuple) else list(part)
        for p in pts:
            if not out or abs(out[-1][0] - p[0]) > 1e-6 or abs(out[-1][1] - p[1]) > 1e-6:
                out.append(p)
    return out


def mirror(pts):
    return [(GRID - x, y) for x, y in pts]


class Canvas:
    def __init__(self):
        self.stroke_img = Image.new("L", (MASTER, MASTER), 0)
        self.tone_img = Image.new("L", (MASTER, MASTER), 0)
        self._s = ImageDraw.Draw(self.stroke_img)
        self._t = ImageDraw.Draw(self.tone_img)

    @staticmethod
    def _px(pts):
        return [(x * U, y * U) for x, y in pts]

    def line(self, *pts, w=STROKE, closed=False):
        """Ломаная со скруглёнными концами и стыками."""
        if len(pts) == 1 and isinstance(pts[0], list):
            pts = pts[0]
        p = self._px(pts)
        r = w * U / 2
        seq = p + [p[0]] if closed else p
        self._s.line(seq, fill=255, width=max(1, round(w * U)))
        for x, y in p:
            self._s.ellipse((x - r, y - r, x + r, y + r), fill=255)

    def shape(self, pts, tone=True, w=STROKE):
        """Замкнутый контур; tone — залить вторым тоном."""
        if tone:
            self._t.polygon(self._px(pts), fill=255)
        self.line(list(pts), w=w, closed=True)

    def tone(self, pts):
        self._t.polygon(self._px(pts), fill=255)

    def solid(self, pts):
        self._s.polygon(self._px(pts), fill=255)

    def dot(self, cx, cy, r=1.15):
        self._s.ellipse(((cx - r) * U, (cy - r) * U, (cx + r) * U, (cy + r) * U), fill=255)

    def ring(self, cx, cy, r, tone=True, w=STROKE):
        self.shape(circle(cx, cy, r), tone=tone, w=w)

    def mask(self):
        return ImageChops.lighter(self.stroke_img, self.tone_img.point(lambda v: int(v * FILL_ALPHA)))


# ── иконки ───────────────────────────────────────────────────────────────────────────────────
def grid(c):
    c.shape(rrect(3.5, 3.5, 7, 7, 1.8))
    c.shape(rrect(13.5, 3.5, 7, 7, 1.8), tone=False)
    c.shape(rrect(3.5, 13.5, 7, 7, 1.8), tone=False)
    c.shape(rrect(13.5, 13.5, 7, 7, 1.8))


def sprout(c):
    c.line((6.5, 21), (17.5, 21))
    c.line((12, 21), (12, 11))
    c.shape(path(bez((12, 14.5), (8.5, 15), (4.8, 13), (4.2, 8.6)), bez((4.2, 8.6), (8.6, 8.2), (11.6, 10.4), (12, 14.5))))
    c.shape(path(bez((12, 11), (12.2, 7), (15, 4), (19.8, 3.6)), bez((19.8, 3.6), (20.2, 8.2), (17, 11.2), (12, 11))))


def sword(c):
    c.shape([(20.5, 3.5), (20.5, 7.2), (11.6, 16.1), (7.9, 12.4), (16.8, 3.5)])
    c.line((6.2, 10.8), (13.2, 17.8))
    c.line((8.6, 15.4), (4.9, 19.1))
    c.dot(4.3, 19.7, 1.5)


def palette(c):
    body = path(
        bez((12, 3), (7, 3), (3, 7), (3, 12)),
        bez((3, 12), (3, 17), (7, 21), (12, 21)),
        bez((12, 21), (13.5, 21), (14.5, 20), (14.5, 18.7)),
        bez((14.5, 18.7), (14.5, 17.4), (13.2, 16.7), (13.2, 15.1)),
        bez((13.2, 15.1), (13.2, 13.9), (14.2, 13), (15.4, 13)),
        (17.5, 13),
        bez((17.5, 13), (19.4, 13), (21, 11.4), (21, 9.5)),
        bez((21, 9.5), (21, 5.9), (17, 3), (12, 3)),
    )
    c.shape(body)
    c.dot(7.4, 11.6, 1.25)
    c.dot(10.2, 7.4, 1.25)
    c.dot(15, 7.2, 1.25)


def sliders(c):
    for y, knob in ((6, 9.0), (12, 15.0), (18, 8.0)):
        c.line((3.5, y), (knob - 3.3, y))
        c.line((knob + 3.3, y), (20.5, y))
        c.ring(knob, y, 2.3)


def gear(c):
    pts = []
    r_in, r_out = 7.0, 9.6
    for i in range(8):
        a = i * 45
        pts += arc(12, 12, r_in, a - 22.5 + 9, a - 12)[:-1]
        pts += [
            (12 + r_out * math.cos(math.radians(a - 7.5)), 12 + r_out * math.sin(math.radians(a - 7.5))),
            (12 + r_out * math.cos(math.radians(a + 7.5)), 12 + r_out * math.sin(math.radians(a + 7.5))),
        ]
        pts += arc(12, 12, r_in, a + 12, a + 22.5 - 9)[:1]
    c.shape(pts, w=1.8)
    c.ring(12, 12, 3.0, tone=False, w=1.8)


def play(c):
    c.shape([(7.5, 4.8), (19.2, 12), (7.5, 19.2)])


def stop(c):
    c.shape(rrect(5.5, 5.5, 13, 13, 2.4))


def pause(c):
    c.shape(rrect(5.8, 4.8, 4.4, 14.4, 1.3))
    c.shape(rrect(13.8, 4.8, 4.4, 14.4, 1.3))


def record(c):
    c.ring(12, 12, 8.6)
    c.dot(12, 12, 3.6)


def pencil(c):
    c.shape([(15.6, 4.2), (19.8, 8.4), (8.6, 19.6), (3.8, 20.2), (4.4, 15.4)])
    c.line((13.2, 6.6), (17.4, 10.8))


def save(c):
    c.shape(path((5.5, 3.5), (15.8, 3.5), (20.5, 8.2), arc(18.5, 18.5, 2, 0, 90), arc(5.5, 18.5, 2, 90, 180), arc(5.5, 5.5, 2, 180, 270)))
    c.line((7.5, 20.2), (7.5, 14), (16.5, 14), (16.5, 20.2))
    c.line((8, 3.8), (8, 8), (14.2, 8), (14.2, 3.8))


def trash(c):
    c.line((3.8, 6.5), (20.2, 6.5))
    c.line((9, 6.3), (9, 3.8), (15, 3.8), (15, 6.3))
    c.shape([(5.8, 6.5), (6.9, 20.5), (17.1, 20.5), (18.2, 6.5)])
    c.line((10, 10.6), (10, 16.4))
    c.line((14, 10.6), (14, 16.4))


def arrow_left(c):
    c.line((20, 12), (4.2, 12))
    c.line((10.6, 5.6), (4.2, 12), (10.6, 18.4))


def chevron_right(c):
    c.line((9, 5), (16, 12), (9, 19))


def chevron_down(c):
    c.line((5, 9), (12, 16), (19, 9))


def chevron_up(c):
    c.line((5, 15), (12, 8), (19, 15))


def close(c):
    c.line((6, 6), (18, 18))
    c.line((18, 6), (6, 18))


def search(c):
    c.ring(10.5, 10.5, 6.6)
    c.line((15.4, 15.4), (20.6, 20.6))


def plus(c):
    c.line((12, 5), (12, 19))
    c.line((5, 12), (19, 12))


def minus(c):
    c.line((5, 12), (19, 12))


def check(c):
    c.line((4.5, 12.6), (9.6, 17.7), (19.5, 6.6))


def copy(c):
    c.shape(rrect(8.5, 8.5, 12, 12, 2.2))
    c.line(path((15.5, 8.2), arc(13.5, 5.5, 2, 0, -90), arc(5.5, 5.5, 2, 270, 180), arc(5.5, 13.5, 2, 180, 90), (8.2, 15.5)))


def _tray(c):
    c.line(path((4, 14.5), arc(6, 18, 2, 180, 90), arc(18, 18, 2, 90, 0), (20, 14.5)))


def import_(c):
    _tray(c)
    c.line((12, 3.5), (12, 14.2))
    c.line((7.4, 9.6), (12, 14.2), (16.6, 9.6))


def export(c):
    _tray(c)
    c.line((12, 15), (12, 4))
    c.line((7.4, 8.6), (12, 4), (16.6, 8.6))


def _undo_pts():
    return path((3.8, 9.5), (14.5, 9.5), arc(14.5, 14.5, 5, -90, 90), (9, 19.5))


def undo(c):
    c.line(_undo_pts())
    c.line((8.2, 5), (3.8, 9.5), (8.2, 14))


def redo(c):
    c.line(mirror(_undo_pts()))
    c.line(mirror([(8.2, 5), (3.8, 9.5), (8.2, 14)]))


def refresh(c):
    c.line(arc(12, 12, 8.2, -32, -350))
    c.line((19.8, 2.8), (19.8, 7.9), (14.7, 7.9))


def warning(c):
    c.shape([(12, 3.4), (21.6, 20), (2.4, 20)])
    c.line((12, 9.6), (12, 14.2))
    c.dot(12, 17.1)


def info(c):
    c.ring(12, 12, 9)
    c.line((12, 11.2), (12, 16.8))
    c.dot(12, 7.6)


def help_(c):
    c.ring(12, 12, 9)
    c.line(path(arc(12, 9.9, 2.9, 180, 400), (12, 13.6)), w=1.9)
    c.dot(12, 16.9)


def eye(c):
    c.shape(path(bez((2.5, 12), (5.5, 6.6), (9, 5), (12, 5)), bez((12, 5), (15, 5), (18.5, 6.6), (21.5, 12)), bez((21.5, 12), (18.5, 17.4), (15, 19), (12, 19)), bez((12, 19), (9, 19), (5.5, 17.4), (2.5, 12))))
    c.ring(12, 12, 3.1, tone=False)


def folder(c):
    c.shape(path(arc(5, 18, 1.5, 180, 90), arc(19, 18, 1.5, 90, 0), arc(19, 9.5, 1.5, 0, -90), (12.4, 8), (10.2, 5.2), arc(5, 6.7, 1.5, 270, 180)))


def file(c):
    c.shape(path((14, 3.5), (19, 8.5), arc(17.5, 19, 1.5, 0, 90), arc(6.5, 19, 1.5, 90, 180), arc(6.5, 5, 1.5, 180, 270)))
    c.line((14, 3.8), (14, 8.5), (18.7, 8.5))
    c.line((8.6, 13), (15.4, 13))
    c.line((8.6, 16.6), (13, 16.6))


def keyboard(c):
    c.shape(rrect(2.5, 6, 19, 12.5, 2.6))
    for x in (6.6, 10.2, 13.8, 17.4):
        c.dot(x, 10.2, 0.95)
    c.line((8.6, 14.4), (15.4, 14.4), w=1.9)


def mouse(c):
    c.shape(rrect(6.5, 3, 11, 18, 5.5))
    c.line((12, 7), (12, 10.4))


def clock(c):
    c.ring(12, 12, 9)
    c.line((12, 6.8), (12, 12), (15.6, 14.2))


def target(c):
    c.ring(12, 12, 6.6)
    c.line((12, 2.4), (12, 6.2))
    c.line((12, 17.8), (12, 21.6))
    c.line((2.4, 12), (6.2, 12))
    c.line((17.8, 12), (21.6, 12))
    c.dot(12, 12, 1.5)


def fish(c):
    c.shape(path(bez((2.8, 12), (6, 6.4), (13, 6), (17.2, 12)), bez((17.2, 12), (13, 18), (6, 17.6), (2.8, 12))))
    c.shape([(17.2, 12), (21.4, 7.6), (21.4, 16.4)], tone=False)
    c.dot(7.6, 11.2, 1.0)


def shield(c):
    c.shape(path((12, 3), (20, 6), (20, 11.5), bez((20, 11.5), (20, 16.5), (16.5, 20), (12, 21.4)), bez((12, 21.4), (7.5, 20), (4, 16.5), (4, 11.5)), (4, 6)))
    c.line((12, 8.4), (12, 13))
    c.dot(12, 16.1)


def siren(c):
    c.shape(path((6.5, 17), arc(12, 12.5, 5.5, 180, 360), (17.5, 17)))
    c.shape(rrect(4, 17, 16, 3.6, 1.2), tone=False)
    c.line((12, 2.4), (12, 4.2))
    c.line((4.3, 5.4), (5.6, 6.7))
    c.line((19.7, 5.4), (18.4, 6.7))
    c.line((12, 10.2), (12, 13.4), w=1.8)


def bell(c):
    c.shape(path((5.6, 17), (6.4, 15.6), (6.4, 11), bez((6.4, 11), (6.4, 7.4), (8.8, 4.8), (12, 4.8)), bez((12, 4.8), (15.2, 4.8), (17.6, 7.4), (17.6, 11)), (17.6, 15.6), (18.4, 17)))
    c.line(arc(12, 18.8, 2.1, 15, 165))
    c.line((12, 2.6), (12, 4.4))


def waypoint(c):
    c.shape(path(bez((12, 21.6), (7.4, 16.6), (5.4, 13.6), (5.4, 10)), bez((5.4, 10), (5.4, 6.2), (8.4, 3.2), (12, 3.2)), bez((12, 3.2), (15.6, 3.2), (18.6, 6.2), (18.6, 10)), bez((18.6, 10), (18.6, 13.6), (16.6, 16.6), (12, 21.6))))
    c.ring(12, 10, 2.5, tone=False)


def route(c):
    c.ring(5.6, 18.4, 2.5)
    c.ring(18.4, 5.6, 2.5)
    c.line(path(bez((8.2, 18.4), (15.4, 18.4), (15.8, 13.6), (12, 12)), bez((12, 12), (8.2, 10.4), (8.6, 5.6), (15.8, 5.6))))


def camera(c):
    c.shape(rrect(2.5, 7, 19, 13, 2.6))
    c.line((8, 6.8), (9.6, 4.2), (14.4, 4.2), (16, 6.8))
    c.ring(12, 13.5, 3.5, tone=False)


def video(c):
    c.shape(rrect(2.5, 6.5, 13, 11, 2.6))
    c.shape([(15.7, 10.6), (21.5, 7.4), (21.5, 16.6), (15.7, 13.4)], tone=False)


def layout(c):
    c.shape(rrect(3, 4, 18, 16, 2.6), tone=False)
    c.tone(path(arc(5.6, 6.6, 2.6, 180, 270), arc(18.4, 6.6, 2.6, 270, 360), (21, 9.2), (3, 9.2)))
    c.line((3.2, 9.2), (20.8, 9.2))
    c.line((9.6, 9.4), (9.6, 19.8))


def coins(c):
    c.line(arc(14.6, 14.6, 6, -68, 158))
    c.ring(9.4, 9.4, 6)
    c.line((9.4, 7), (9.4, 11.8))


def sparkles(c):
    cx, cy, r, k = 10, 13.2, 8, 1.5
    star = path(
        bez((cx, cy - r), (cx + k * 0.4, cy - k), (cx + k, cy - k * 0.4), (cx + r, cy)),
        bez((cx + r, cy), (cx + k, cy + k * 0.4), (cx + k * 0.4, cy + k), (cx, cy + r)),
        bez((cx, cy + r), (cx - k * 0.4, cy + k), (cx - k, cy + k * 0.4), (cx - r, cy)),
        bez((cx - r, cy), (cx - k, cy - k * 0.4), (cx - k * 0.4, cy - k), (cx, cy - r)),
    )
    c.shape(star)
    c.line((18.6, 2.8), (18.6, 8.2))
    c.line((15.9, 5.5), (21.3, 5.5))


def gauge(c):
    c.shape(path(arc(12, 14.6, 9, 180, 360), (21, 16.4), (3, 16.4)))
    c.line((12, 14.4), (16.2, 9.4))
    c.dot(12, 14.4, 1.6)


def motion(c):
    c.line(bez((3.6, 19.4), (3.6, 10.4), (9.6, 6.6), (19.4, 6.6)))
    c.line((15.4, 2.6), (19.8, 6.6), (15.4, 10.6))
    c.dot(3.6, 19.4, 1.5)


def launch(c):
    c.ring(12, 12, 9)
    c.solid([(9.8, 7.8), (16.6, 12), (9.8, 16.2)])
    c.line((9.8, 7.8), (16.6, 12), (9.8, 16.2), w=1.2, closed=True)


def macro(c):
    c.dot(5.6, 6.6, 2.3)
    c.line((11, 6.6), (20, 6.6))
    c.line((4, 12.4), (20, 12.4))
    c.line((4, 18.2), (14.6, 18.2))


def star(c):
    pts = []
    for i in range(10):
        r = 9.6 if i % 2 == 0 else 4.3
        a = math.radians(-90 + i * 36)
        pts.append((12 + r * math.cos(a), 12.7 + r * math.sin(a)))
    c.shape(pts)


def flip(c):
    c.line((12, 3), (12, 5.6))
    c.line((12, 10.2), (12, 13.8))
    c.line((12, 18.4), (12, 21))
    c.shape([(8.6, 6.8), (3.2, 17.4), (8.6, 17.4)], tone=False)
    c.shape([(15.4, 6.8), (20.8, 17.4), (15.4, 17.4)])


def layers(c):
    c.shape([(12, 3.4), (20.6, 8), (12, 12.6), (3.4, 8)])
    c.line((3.4, 12.2), (12, 16.8), (20.6, 12.2))
    c.line((3.4, 16.2), (12, 20.8), (20.6, 16.2))


def sound(c):
    c.shape([(3.8, 9.4), (7.8, 9.4), (12.4, 5.2), (12.4, 18.8), (7.8, 14.6), (3.8, 14.6)])
    c.line(arc(12.4, 12, 3.9, -48, 48))
    c.line(arc(12.4, 12, 7.6, -48, 48))


def send(c):
    c.shape([(21.2, 2.8), (2.6, 10.4), (10, 14), (13.6, 21.4)])
    c.line((10, 14), (21.2, 2.8))


def heart(c):
    c.shape(path(
        bez((12, 20.6), (5, 15.6), (3, 11.6), (3, 8.6)),
        bez((3, 8.6), (3, 5.7), (5.2, 3.8), (7.6, 3.8)),
        bez((7.6, 3.8), (9.5, 3.8), (11.1, 4.9), (12, 6.6)),
        bez((12, 6.6), (12.9, 4.9), (14.5, 3.8), (16.4, 3.8)),
        bez((16.4, 3.8), (18.8, 3.8), (21, 5.7), (21, 8.6)),
        bez((21, 8.6), (21, 11.6), (19, 15.6), (12, 20.6)),
    ))


def flag(c):
    c.line((6, 21), (6, 3.4))
    c.shape([(6, 4.4), (18.6, 4.4), (15.6, 8.7), (18.6, 13), (6, 13)])


def dice(c):
    c.shape(rrect(3.5, 3.5, 17, 17, 3.6))
    for x, y in ((8.4, 8.4), (15.6, 15.6), (12, 12), (15.6, 8.4), (8.4, 15.6)):
        c.dot(x, y, 1.2)


def lock(c):
    c.shape(rrect(4.5, 10.5, 15, 10.5, 2.4))
    c.line(path((7.8, 10.3), (7.8, 7.6), arc(12, 7.6, 4.2, 180, 360), (16.2, 10.3)))
    c.dot(12, 15.6, 1.4)


def window(c):
    c.shape(rrect(3, 4.5, 18, 15, 2.6), tone=False)
    c.tone(path(arc(5.6, 7.1, 2.6, 180, 270), arc(18.4, 7.1, 2.6, 270, 360), (21, 9), (3, 9)))
    c.line((3.2, 9), (20.8, 9))
    c.dot(6.2, 6.8, 0.85)
    c.dot(8.9, 6.8, 0.85)


def user(c):
    c.ring(12, 8, 4)
    c.shape(path(bez((4.4, 20.4), (4.4, 16.8), (7.6, 14.4), (12, 14.4)), bez((12, 14.4), (16.4, 14.4), (19.6, 16.8), (19.6, 20.4))))


def bolt(c):
    c.shape([(13.4, 2.8), (4.6, 13.6), (11, 13.6), (10.2, 21.2), (19.4, 10.2), (12.8, 10.2)])


ICONS = [
    ("grid", grid), ("sprout", sprout), ("sword", sword), ("palette", palette), ("sliders", sliders),
    ("gear", gear), ("play", play), ("stop", stop), ("pause", pause), ("record", record),
    ("pencil", pencil), ("save", save), ("trash", trash), ("arrow_left", arrow_left),
    ("chevron_right", chevron_right), ("chevron_down", chevron_down), ("chevron_up", chevron_up),
    ("close", close), ("search", search), ("plus", plus), ("minus", minus), ("check", check),
    ("copy", copy), ("import", import_), ("export", export), ("undo", undo), ("redo", redo),
    ("refresh", refresh), ("warning", warning), ("info", info), ("help", help_), ("eye", eye),
    ("folder", folder), ("file", file), ("keyboard", keyboard), ("mouse", mouse), ("clock", clock),
    ("target", target), ("fish", fish), ("shield", shield), ("siren", siren), ("bell", bell),
    ("waypoint", waypoint), ("route", route), ("camera", camera), ("video", video), ("layout", layout),
    ("coins", coins), ("sparkles", sparkles), ("gauge", gauge), ("motion", motion), ("launch", launch),
    ("macro", macro), ("star", star), ("flip", flip), ("layers", layers), ("sound", sound),
    ("send", send), ("heart", heart), ("flag", flag), ("dice", dice), ("lock", lock),
    ("window", window), ("user", user), ("bolt", bolt),
]


def render_masks():
    masks = []
    for name, fn in ICONS:
        canvas = Canvas()
        fn(canvas)
        masks.append((name, canvas.mask()))
    return masks


def build_atlas(masks, cell):
    rows = math.ceil(len(masks) / COLS)
    alpha = Image.new("L", (COLS * cell, rows * cell), 0)
    for i, (_, mask) in enumerate(masks):
        alpha.paste(mask.reduce(MASTER // cell), ((i % COLS) * cell, (i // COLS) * cell))
    # Цвет белый и под прозрачными пикселями: иначе линейная фильтрация даёт тёмную кайму.
    atlas = Image.new("RGBA", alpha.size, (255, 255, 255, 0))
    atlas.putalpha(alpha)
    return atlas


def build_preview(masks, target):
    from PIL import ImageFont

    font = ImageFont.load_default()
    sizes, pad, label_h = (16, 24, 48), 10, 12
    cols = 11
    cw = sum(sizes) + pad * (len(sizes) + 1)
    ch = max(sizes) + pad * 2 + label_h
    rows = math.ceil(len(masks) / cols)
    sheet = Image.new("RGBA", (cols * cw, rows * ch), (17, 21, 30, 255))
    draw = ImageDraw.Draw(sheet)
    tints = ((233, 238, 247), (110, 170, 255), (233, 238, 247))
    for i, (name, mask) in enumerate(masks):
        ox, oy = (i % cols) * cw, (i // cols) * ch
        x = ox + pad
        for size, tint in zip(sizes, tints):
            m = mask.reduce(MASTER // 192).resize((size, size), Image.LANCZOS) if size not in CELLS else mask.reduce(MASTER // size)
            layer = Image.new("RGBA", (size, size), tint + (0,))
            layer.putalpha(m)
            sheet.alpha_composite(layer, (x, oy + pad + (max(sizes) - size) // 2))
            x += size + pad
        draw.text((ox + pad, oy + ch - label_h - 2), name, fill=(140, 150, 170, 255), font=font)
    sheet.convert("RGB").save(target)


def main():
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--out", type=Path, default=DEFAULT_OUT)
    ap.add_argument("--preview", type=Path)
    args = ap.parse_args()
    for cell in CELLS:
        assert MASTER % cell == 0, cell
    masks = render_masks()
    args.out.mkdir(parents=True, exist_ok=True)
    for cell in CELLS:
        atlas = build_atlas(masks, cell)
        atlas.save(args.out / f"icons_{cell}.png", optimize=True)
        (args.out / f"icons_{cell}.png.mcmeta").write_text('{"texture":{"blur":true,"clamp":true}}\n', encoding="utf-8")
    (args.out / "icons.txt").write_text("\n".join(name for name, _ in masks) + "\n", encoding="utf-8")
    if args.preview:
        build_preview(masks, args.preview)
    print(f"{len(masks)} icons, atlases {', '.join(str(c) for c in CELLS)} px -> {args.out}")


if __name__ == "__main__":
    main()
