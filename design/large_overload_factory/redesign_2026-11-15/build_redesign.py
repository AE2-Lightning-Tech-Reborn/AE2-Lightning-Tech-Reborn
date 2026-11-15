"""New 48-unit LT pulse-press factory, with proportion-preserving LT material extensions.

Run with --blockout to inspect the massing, or without arguments for the finished materials.
The earlier design generator and its textures are deliberately not dependencies.
"""
from __future__ import annotations

import argparse
import base64
from copy import deepcopy
import hashlib
import json
import math
from pathlib import Path
import random
import uuid

from PIL import Image, ImageChops, ImageColor, ImageDraw, ImageFont

OUT = Path(__file__).resolve().parent
ASSETS = OUT / 'export' / 'assets' / 'ae2lt'
TEXDIR = ASSETS / 'textures' / 'block' / 'large_overload_factory_redesign'
MODELDIR = ASSETS / 'models' / 'block' / 'large_overload_factory_redesign'
SIZE = 48
STATE_MATERIALS = ('power', 'crystal', 'console')
SCREEN_FRAMES = 16
SCREEN_FRAME_TICKS = 2
SRC = OUT.parents[2] / 'src/main/resources/assets/ae2lt/textures/block'
SOURCE_FILES = {
    'frame': 'overload_processing_factory/frame.png',
    'top': 'generics/top.png', 'bottom': 'generics/bottom.png', 'back': 'generics/back.png',
    'crystal': 'overload_crystal_block.png',
    'glass': 'overload_processing_factory/frame.png',
    'vent': 'matter_warping_matrix/matter_warping_matrix_constraint_frame.png',
    'thread': 'matter_warping_matrix/matter_warping_matrix_thread_unit_t2.png',
    'overload': 'matter_warping_matrix/matter_warping_matrix_overload_main_core.png',
    'output': 'overloaded_interface.png',
    'pattern': 'matter_warping_matrix/matter_warping_matrix_pattern_storage_t1.png',
}
SOURCE = {name: Image.open(SRC / path).convert('RGBA') for name, path in SOURCE_FILES.items()}
FACES = ('north', 'east', 'south', 'west', 'up', 'down')
P = {
    'ink': '#413f54', 'iron': '#4d4d67', 'steel': '#787e97',
    'mid': '#878fa5', 'edge': '#9a9fb4', 'rim': '#b3b6c6',
    'silver': '#bfc1cd', 'light': '#d5d6dc', 'white': '#f2f2f2',
    'plum': '#911d57', 'pink': '#ba5078', 'rose': '#f993c3',
    'pale': '#ffbcdd', 'hot': '#ffd8e9',
}
OFF = {'plum': '#611f37', 'pink': '#912b59', 'rose': '#b2436e',
       'pale': '#db789e', 'hot': '#e598b8'}
GLYPHS = {
    'L': ('100', '100', '100', '100', '111'),
    'T': ('111', '010', '010', '010', '010'),
    'I': ('111', '010', '010', '010', '111'),
    'O': ('111', '101', '101', '101', '111'),
    '1': ('010', '110', '010', '010', '111'),
    '2': ('110', '001', '010', '100', '111'),
    '3': ('110', '001', '010', '001', '110'),
}


def glyph(draw, x, y, text, color):
    for ch in text:
        for row, bits in enumerate(GLYPHS[ch]):
            for col, bit in enumerate(bits):
                if bit == '1':
                    draw.point((x + col, y + row), fill=color)
        x += 4


def bolt(draw, x, y, color, scale=1):
    for row, bits in enumerate(('00110', '01100', '11111', '00110', '01100')):
        for col, bit in enumerate(bits):
            if bit == '1':
                draw.rectangle((x + col * scale, y + row * scale,
                                x + (col + 1) * scale - 1, y + (row + 1) * scale - 1), fill=color)


def metal_patch(draw, x, y, w, color):
    draw.polygon([(x, y + 1), (x + 2, y + 1), (x + 2, y),
                  (x + w, y), (x + w - 1, y + 2), (x, y + 2)], fill=color)


def extend_band(source, length, axis=0):
    """Insert a neutral run; keep every original pixel and both end details at their original scale."""
    if axis == 1:
        return extend_band(source.transpose(Image.Transpose.TRANSPOSE), length).transpose(Image.Transpose.TRANSPOSE)
    if length < source.width:
        start = (source.width - length) // 2
        return source.crop((start, 0, start + length, source.height))
    middle = source.width // 2
    extra = length - source.width
    out = Image.new('RGBA', (length, source.height))
    out.paste(source.crop((0, 0, middle, source.height)), (0, 0))
    column = source.crop((middle, 0, middle + 1, source.height))
    for x in range(middle, middle + extra):
        out.paste(column, (x, 0))
    out.paste(source.crop((middle, 0, source.width, source.height)), (middle + extra, 0))
    return out


def metal_surface(w, h, color, seed=0):
    """Five close metal tones: stepped light falloff and coherent, native-pixel grain."""
    rgb = ImageColor.getrgb(color)
    rng = random.Random(0xAE21 + seed * 173 + w * 37 + h * 71 + sum(rgb))
    cell = 6
    cols, rows = w // cell + 2, h // cell + 2
    field = [[rng.uniform(-1, 1) for _ in range(cols)] for _ in range(rows)]
    palette = [tuple(max(0, min(255, v + shift)) for v in rgb) + (255,)
               for shift in (-10, -5, 0, 5, 10)]
    levels = []
    for y in range(h):
        row = []
        for x in range(w):
            ix, iy = x // cell, y // cell
            fx, fy = (x % cell) / cell, (y % cell) / cell
            fx, fy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)
            a = field[iy][ix] * (1 - fx) + field[iy][ix + 1] * fx
            b = field[iy + 1][ix] * (1 - fx) + field[iy + 1][ix + 1] * fx
            grain = a * (1 - fy) + b * fy
            light = 0.65 - 0.50 * x / max(1, w - 1) - 0.85 * y / max(1, h - 1)
            row.append(max(0, min(4, 2 + round(grain * 1.6 + light))))
        levels.append(row)
    # Sparse two/three-pixel scuffs stay within the same metal palette.
    for _ in range(w * h // 170):
        x, y = rng.randrange(w), rng.randrange(h)
        delta = rng.choice((-1, 1))
        for dx in range(rng.choice((2, 3))):
            if x + dx < w:
                levels[y][x + dx] = max(0, min(4, levels[y][x + dx] + delta))
    im = Image.new('RGBA', (w, h))
    im.putdata([palette[v] for row in levels for v in row])
    return im


def fastener(draw, x, y):
    draw.line((x, y, x + 1, y), fill=P['light'])
    draw.line((x, y + 1, x + 1, y + 1), fill=P['iron'])


def cable_connector():
    """An original recessed cable socket with a metal collar and energy contacts."""
    im = metal_surface(12, 12, P['silver'], 92)
    d = ImageDraw.Draw(im)
    outline = [(2, 0), (9, 0), (11, 2), (11, 9), (9, 11), (2, 11), (0, 9), (0, 2)]
    d.polygon(outline, fill=P['mid'], outline=P['iron'])
    d.line([(2, 1), (8, 1), (10, 3)], fill=P['light'])
    d.line([(1, 3), (1, 8), (3, 10)], fill=P['rim'])
    well = [(4, 2), (7, 2), (9, 4), (9, 7), (7, 9), (4, 9), (2, 7), (2, 4)]
    d.polygon(well, fill=P['ink'], outline=P['iron'])
    d.rectangle((4, 4, 7, 7), fill='#252634')
    d.line((4, 3, 7, 3), fill=P['pink'])
    d.line((3, 4, 3, 7), fill=P['pink'])
    d.line((4, 8, 7, 8), fill=P['plum'])
    d.line((8, 4, 8, 7), fill=P['plum'])
    d.point((4, 3), fill=P['pale'])
    d.line((5, 5, 6, 5), fill=P['steel'])
    d.line((5, 6, 6, 6), fill=P['iron'])
    return im


def screen_frame(w, h, active=True, phase=0):
    """Native-pixel telemetry: scrolling waveform, status lamp and moving progress segment."""
    im = Image.new('RGBA', (w, h), '#171923')
    d = ImageDraw.Draw(im)
    d.rectangle((1, 1, w - 2, h - 2), fill='#20232e' if active else '#1b1d26')
    d.polygon(((1, 1), (w - 3, 1), (w - 5, 3), (3, 3)),
              fill='#282b36' if active else '#22242d')
    if not active:
        glyph(d, w // 2 - 4, h // 2 - 2, 'LT', '#2c2f39')
        return im
    glyph(d, 3, 2, 'LT', P['edge'])
    d.line((14, 2, 14, h - 3), fill='#363947')
    d.line((3, h - 4, 9, h - 4), fill=P['steel'])
    d.line((11, h - 4, 12, h - 4), fill=P['pale'] if phase % 8 < 3 else P['pink'])
    signal = (4, 4, 2, 5, 5, 0, 3, 3, 1, 1, 4, 4, 5, 2, 3, 4)
    points = [(w - 17 + x, 2 + signal[(x + phase) % len(signal)]) for x in range(14)]
    d.line(points, fill=P['pink'])
    d.point(points[-1], fill=P['pale'])
    d.line((2, h - 2, w - 3, h - 2), fill='#393d4c')
    run = w - 4
    head = phase * run // SCREEN_FRAMES
    for offset in range(5):
        d.point((2 + (head + offset) % run, h - 2), fill=P['edge'] if offset < 4 else P['pale'])
    return im


def window(im, bounds, seed, contour=None):
    """A flush cutout frame, or the retained roof-window glass artwork."""
    x0, y0, x1, y1 = bounds
    if contour:
        # The complete frame and its four corners occupy one flat shell face.
        # Its transparent opening is separate from the actual glazing material.
        plate = metal_surface(x1 - x0 + 1, y1 - y0 + 1, P['silver'], seed)
        d = ImageDraw.Draw(plate)
        d.line((0, 0, plate.width - 1, 0), fill=P['light'])
        d.line((0, 1, 0, plate.height - 1), fill=P['rim'])
        d.line((plate.width - 1, 1, plate.width - 1, plate.height - 1), fill=P['steel'])
        d.line((1, plate.height - 1, plate.width - 2, plate.height - 1), fill=P['mid'])
        points = [(x - x0, y - y0) for x, y in contour]
        d.polygon(points, fill=(0, 0, 0, 0))
        for a, b in ((1, 2), (3, 4), (5, 6), (7, 0)):
            d.line((points[a], points[b]), fill=P['iron'], width=1)
        im.paste(plate, (x0, y0))
        return
    glass = Image.new('RGBA', (x1 - x0 + 1, y1 - y0 + 1),
                      (*ImageColor.getrgb(P['edge']), 20))
    d = ImageDraw.Draw(glass)
    d.line((2, 3, min(glass.width - 2, 4), 3), fill=(*ImageColor.getrgb(P['light']), 48))
    im.paste(glass, (x0, y0))


def panel(kind, w, h, active=True, blockout=False):
    c = dict(P)
    if not active:
        c.update(OFF)
    base = {'metal': 'silver', 'ceramic': 'light', 'structure': 'steel',
            'dark': 'iron', 'floor': 'steel', 'coil': 'ink', 'bus': 'iron',
            'crystal': 'pink', 'screen': 'ink', 'keys': 'iron',
            'magnet': 'silver', 'vent': 'iron', 'glass': 'edge',
            'inner_wall': 'mid', 'contact': 'pink', 'window_reveal': 'steel'}.get(kind, 'steel')
    im = Image.new('RGBA', (w, h), (0, 0, 0, 0) if kind == 'glass' else c[base]) if blockout or kind == 'glass' else metal_surface(w, h, c[base])
    d = ImageDraw.Draw(im)
    if blockout:
        if kind == 'glass':
            d.line((0, 0, 0, h - 1), fill=c['edge'])
        return im
    if kind == 'glass':
        im = Image.new('RGBA', (w, h), (0, 0, 0, 0))
        d = ImageDraw.Draw(im)
        d.rectangle((0, 0, w - 1, h - 1), outline=(*ImageColor.getrgb(c['edge']), 123))
        if min(w, h) >= 6:
            d.line((6, 5, 8, 5), fill=(*ImageColor.getrgb(c['light']), 96))
            d.point((5, 6), fill=(*ImageColor.getrgb(c['rim']), 72))
        return im
    if kind == 'window_reveal':
        if w < h:
            d.line((0, 0, 0, h - 1), fill=c['mid'])
            d.line((w - 1, 0, w - 1, h - 1), fill=c['iron'])
        else:
            d.line((0, 0, w - 1, 0), fill=c['mid'])
            d.line((0, h - 1, w - 1, h - 1), fill=c['iron'])
        return im
    if kind == 'crystal':
        # Preserve the original crystal clusters at one pixel per model unit.
        sx, sy = (16 - w) // 2, (16 - h) // 2
        im = SOURCE['crystal'].crop((sx, sy, sx + w, sy + h))
        if not active:
            im.putdata([(round(r * .70), round(g * .61), round(b * .70), a) for r, g, b, a in im.getdata()])
        return im
    if kind == 'screen':
        return screen_frame(w, h, active)
    if kind == 'keys':
        d.line((0, 0, w - 1, 0), fill=c['rim'])
        for x in range(2, w - 5, 4):
            d.rectangle((x, 2, x + 1, min(h - 2, 3)), fill=c['mid'])
        if w > 8 and h > 4:
            d.rectangle((w - 5, 2, w - 3, h - 3), fill=c['pink'])
        return im
    if kind == 'coil':
        # Wide conductive windings and a ceramic spine, rather than scattered glowing pixels.
        if h >= w:
            d.rectangle((0, 0, 1, h - 1), fill=c['steel'])
            d.line((0, 1, 0, h - 2), fill=c['rim'])
            for y in range(3, h - 1, 4):
                d.line((2, y, w - 1, y), fill=c['rose'])
                d.point((2, y), fill=c['pale'])
                d.line((2, y + 1, w - 1, y + 1), fill=c['plum'])
        else:
            d.rectangle((1, 1, w - 2, h - 2), fill=c['pink'])
            d.line((1, 1, w - 2, 1), fill=c['pale'])
        return im
    if kind == 'bus':
        if h > w:
            d.line((w // 2, 0, w // 2, h - 1), fill=c['pink'])
            d.line((0, 0, 0, h - 1), fill=c['mid'])
        else:
            d.line((0, h // 2, w - 1, h // 2), fill=c['pink'])
            d.line((0, 0, w - 1, 0), fill=c['mid'])
        return im
    if kind == 'vent':
        for y in range(2, h - 1, 3):
            d.line((2, y, w - 3, y), fill=c['steel'])
        if w > 6:
            d.line((w - 2, 1, w - 2, h - 2), fill=c['mid'])
        return im
    if kind == 'inner_wall':
        d.line((0, 0, w - 1, 0), fill=c['rim'])
        d.line((0, h - 1, w - 1, h - 1), fill=c['iron'])
        for x in (3, w - 5):
            d.line((x, 2, x, h - 3), fill=c['steel'], width=2)
            d.line((x, 3, x, h - 4), fill=c['light'])
        for x in (7, w - 9):
            for y in (4, h - 6):
                fastener(d, x, y)
        return im
    # Edge accents sit over the continuous, low-contrast metal grain.
    if w > 2:
        d.line((0, 0, w - 1, 0), fill=c['white' if kind in ('metal', 'ceramic', 'magnet') else 'rim'])
    if h > 3:
        d.line((0, 1, 0, h - 2), fill=c['rim' if kind in ('metal', 'ceramic', 'magnet') else 'mid'])
    if w >= 12 and h >= 8:
        # An existing metal glint is kept at its native 4x3 size, then joined to the new plate.
        sample = SOURCE['back' if base == 'silver' else 'bottom'].crop((1, 1, 5, 4))
        im.paste(sample, (2, 2), sample)
        d.line((7, 3, min(w - 3, 10), 3), fill=c['rim' if base == 'silver' else 'mid'])
    if kind == 'magnet':
        if h >= 10:
            d.rectangle((max(1, w - 3), 3, w - 1, h - 4), fill=c['iron'])
            d.line((w - 2, 4, w - 2, h - 5), fill=c['pink'])
        elif w >= 10:
            d.rectangle((3, max(1, h - 3), w - 4, h - 1), fill=c['iron'])
            d.line((4, h - 2, w - 5, h - 2), fill=c['pink'])
    return im


def global_skins(blockout):
    result = {}
    for index, direction in enumerate(('front', 'left', 'right')):
        im = Image.new('RGBA', (48, 48), P['silver']) if blockout else metal_surface(48, 48, P['silver'], index)
        d = ImageDraw.Draw(im)
        if not blockout:
            crown = extend_band(SOURCE['top'].crop((0, 0, 16, 2)), 48)
            im.paste(crown, (0, 0))
            d.line((0, 2, 47, 2), fill=P['silver'])
            d.line((0, 4, 47, 4), fill=P['steel'])
            for x in (0, 46):
                pillar = extend_band(SOURCE['frame'].crop((30, 1, 32, 15)), 37, axis=1)
                im.paste(pillar, (x, 5), pillar)
            for x in (3, 44):
                d.line((x, 5, x, 41), fill=P['rim'])
                for y in (12, 29):
                    d.point((x, y), fill=P['iron'])
                    d.point((x, y - 1), fill=P['light'])
            strip = extend_band(SOURCE['frame'].crop((2, 7, 14, 15)), 38)
            im.paste(strip, (5, 34), strip)
            d.line((0, 42, 47, 42), fill=P['mid'])
            d.line((0, 43, 47, 43), fill=P['iron'])
            d.rectangle((0, 44, 47, 47), fill=P['ink'])
            for x in (3, 40):
                patch = SOURCE['bottom'].crop((1, 1, 5, 3))
                im.paste(patch, (x, 44))
            if direction == 'front':
                emblem = SOURCE['top'].crop((6, 6, 10, 10))
                im.paste(emblem, (22, 0))
            else:
                for x in (7, 30):
                    metal_patch(d, x, 6, 6, P['light'])
        if not blockout and direction != 'front':
            rear_x = 32 if direction == 'left' else 0
            im.paste(metal_surface(16, 37, P['silver'], 70 + index), (rear_x, 5))
            d.line((rear_x + 1, 6, rear_x + 14, 6), fill=P['light'])
            d.line((rear_x + 1, 40, rear_x + 14, 40), fill=P['steel'])
            for x, y in ((rear_x + 3, 6), (rear_x + 11, 37)):
                fastener(d, x, y)
            # The side housing steps around the open fin bank, as real geometry does.
            cut_x = 40 if direction == 'left' else 0
            d.rectangle((cut_x, 9, cut_x + 7, 27), fill=(0, 0, 0, 0))
            web_x = 39 if direction == 'left' else 8
            d.line((web_x, 9, web_x, 27), fill=P['iron'])
        if not blockout and direction == 'front':
            im.paste(metal_surface(38, 16, P['silver'], 75), (5, 26))
            d.line((5, 26, 42, 26), fill=P['light'])
            d.line((5, 27, 5, 41), fill=P['rim'])
            d.line((42, 27, 42, 41), fill=P['steel'])
            d.line((6, 41, 41, 41), fill=P['iron'])
            d.rectangle((6, 27, 41, 40), outline=P['ink'])
            d.rectangle((7, 28, 40, 39), fill='#171923')
        # Only the opaque frame uses the shell atlas. Glazing has its own atlas.
        wy = 5 if direction == 'front' else 11
        window_bottom = 25 if direction == 'front' else 33
        wx0, wx1 = (5, 31) if direction == 'left' else (16, 42) if direction == 'right' else (5, 42)
        # The straight edges meet existing beam/post inner faces. Only the
        # corner caps need their own cutout faces and diagonal inner walls.
        contour = [(wx0 + 5, wy), (wx1 - 5, wy), (wx1, wy + 5),
                   (wx1, window_bottom - 5), (wx1 - 5, window_bottom),
                   (wx0 + 5, window_bottom), (wx0, window_bottom - 5), (wx0, wy + 5)]
        if blockout:
            d.polygon(contour, fill=(0, 0, 0, 0))
        else:
            window(im, (wx0, wy, wx1, window_bottom), 10 + index, contour)
        result['face_' + direction] = im

    roof = Image.new('RGBA', (48, 48), P['silver']) if blockout else Image.blend(
        metal_surface(48, 48, P['silver'], 20), Image.new('RGBA', (48, 48), P['silver']), 0.55)
    d = ImageDraw.Draw(roof)
    if not blockout:
        # One closed lid seam replaces the unrelated crossing highlight runs.
        lid = [(3, 1), (44, 1), (46, 3), (46, 44),
               (44, 46), (3, 46), (1, 44), (1, 3)]
        d.line(lid + lid[:1], fill=P['rim'], width=1)
        service = [(18, 2), (29, 2), (31, 4), (31, 15), (32, 16),
                   (32, 40), (16, 40), (16, 16), (17, 15), (17, 4)]
        d.polygon(service, fill=P['light'])
        d.line(service + service[:1], fill=P['mid'], width=1)
        # The logo sits on a shallow removable cap. The vent bed is one unit
        # below the surrounding rails; the slats themselves are solid geometry.
        d.rectangle((18, 31, 29, 38), fill=P['light'], outline=P['mid'])
        roof.paste(SOURCE['top'].crop((5, 5, 11, 11)), (21, 32))
        d.rectangle((18, 19, 29, 29), fill=P['steel'], outline=P['iron'])
        for z in (19, 22, 25, 28):
            v = 47 - z
            d.line((19, v, 28, v), fill=P['silver'])
        # The former painted wires are replaced by cable bodies, sockets and
        # bridge clamps. This is the continuous metal floor underneath them.
        cable_bed = [(17, 3), (30, 3), (30, 15), (29, 15),
                     (29, 17), (18, 17), (18, 15), (17, 15)]
        d.polygon(cable_bed, fill=P['steel'])
        d.line(cable_bed + cable_bed[:1], fill=P['iron'], width=1)
        for x, y in ((3, 3), (43, 3), (3, 43), (43, 43)):
            fastener(d, x, y)
    for x in (5, 32):
        if blockout:
            d.rectangle((x + 2, 18, x + 8, 38), fill=(0, 0, 0, 0))
        else:
            d.rectangle((x, 16, x + 10, 40), fill=P['mid'], outline=P['rim'])
            d.rectangle((x + 1, 17, x + 9, 39), outline=P['iron'])
            window(roof, (x + 2, 18, x + 8, 38), 80 + x)
    result['face_top'] = roof

    for name in ('face_bottom', 'deck'):
        floor = Image.new('RGBA', (48, 48), P['steel']) if blockout else metal_surface(48, 48, P['steel'], 40 if name == 'deck' else 41)
        d = ImageDraw.Draw(floor)
        if not blockout:
            d.rectangle((2, 2, 45, 45), outline=P['iron'])
            d.line((3, 3, 44, 3), fill=P['rim'])
            d.line((3, 4, 3, 44), fill=P['mid'])
            if name == 'deck':
                floor.paste(metal_surface(24, 24, P['mid'], 42), (12, 12))
                for x in (7, 39):
                    d.line((x, 7, x, 39), fill=P['iron'])
                    d.line((x + 1, 8, x + 1, 38), fill=P['rim'])
                for x in (9, 36):
                    d.line((x, 6, x, 12), fill=P['pink'])
                    d.line((x, 35, x, 40), fill=P['pink'])
                for y in (8, 38):
                    d.line((15, y, 32, y), fill=P['iron'])
                    d.line((16, y + 1, 31, y + 1), fill=P['mid'])
            else:
                floor.paste(metal_surface(30, 30, P['mid'], 43), (9, 9))
                d.line((9, 9, 38, 9), fill=P['rim'])
                d.line((9, 10, 9, 38), fill=P['edge'])
                d.line((38, 10, 38, 38), fill=P['iron'])
                d.line((10, 38, 37, 38), fill=P['iron'])
                for x in (16, 30):
                    d.line((x, 10, x, 37), fill=P['steel'], width=2)
                    d.line((x, 11, x, 36), fill=P['edge'])
                floor.paste(SOURCE['bottom'].crop((5, 5, 11, 11)), (21, 21))
                for y in (13, 33):
                    d.line((21, y, 26, y), fill=P['iron'])
                    d.line((21, y + 1, 26, y + 1), fill=P['edge'])
            for x, y in ((5, 5), (41, 5), (5, 41), (41, 41), (11, 11), (35, 35)):
                fastener(d, x, y)
        result[name] = floor

    back = Image.new('RGBA', (48, 48), P['silver']) if blockout else metal_surface(48, 48, P['silver'], 50)
    d = ImageDraw.Draw(back)
    if not blockout:
        back.paste(extend_band(SOURCE['top'].crop((0, 0, 16, 2)), 48), (0, 0))
        # The upper field supplies continuous casing color only. Its radiators,
        # pipes and mounting blocks are actual geometry in front of a backing wall.
        d.line((3, 5, 3, 29), fill=P['rim'])
        d.line((44, 5, 44, 29), fill=P['steel'])
        d.line((1, 32, 46, 32), fill=P['iron'])
        d.line((1, 33, 46, 33), fill=P['light'])
        # The left cable connector is original artwork; the other two motifs
        # retain their native pixels. No IO port image is used by this generator.
        for col, source_name in enumerate(('cable_connector', 'output', 'pattern')):
            x = 2 + col * 16
            tile = cable_connector() if col == 0 else SOURCE[source_name].crop((2, 2, 14, 14))
            back.paste(tile, (x, 34))
            fastener(d, x + 12, 35)
        d.line((0, 47, 47, 47), fill=P['iron'])
    result['face_back'] = back
    return result


ELEMENTS = []
GROUPS = {}


def add(name, a, b, material='mechanism', kind='metal', group='reactor', *,
        outer=False, rotation=None, face_kinds=None, only=None, shade=True):
    entry = {'name': name, 'from': list(a), 'to': list(b), 'material': material,
             'kind': kind, 'group': group, 'outer': outer, 'shade': shade,
             'face_kinds': face_kinds or {}, 'faces': list(only or FACES)}
    if rotation:
        entry['rotation'] = rotation
    ELEMENTS.append(entry)
    if group:
        GROUPS.setdefault(group, []).append(len(ELEMENTS) - 1)


def geometry():
    add('bottom', (0, 0, 0), (48, 6, 48), 'mechanism', 'structure', None, outer=True)
    # Keep the named root roof slab as the cable well floor, and close the
    # unaffected roof around it with three solid cover sections.
    add('top', (0, 43, 32), (48, 45, 48), group=None, outer=True)
    add('roof_rear_cover_left', (0, 45, 32), (17, 48, 48), group='top_drive', outer=True)
    add('roof_rear_cover_right', (31, 45, 32), (48, 48, 48), group='top_drive', outer=True)
    add('roof_rear_cover_end', (17, 45, 45), (31, 48, 48), group='top_drive', outer=True)
    add('inner_base', (12, 6, 13), (36, 11, 36), kind='dark', group=None)
    for suffix, x, z, depth in (('front_left', 0, 0, 5), ('front_right', 43, 0, 5),
                                ('rear_left', 0, 32, 8), ('rear_right', 43, 32, 8)):
        add('pillar_' + suffix, (x, 6, z), (x + 5, 43, z + depth),
            kind='ceramic', group='side_pillar', outer=True)
        if suffix.startswith('rear_'):
            add('rear_housing_lower_' + suffix, (x, 6, 40), (x + 5, 20, 48),
                kind='ceramic', group='side', outer=True)
            add('rear_housing_upper_' + suffix, (x, 39, 40), (x + 5, 43, 48),
                kind='ceramic', group='side', outer=True)
    add('front_crown', (0, 43, 0), (48, 48, 7), group='side', outer=True)
    for suffix, x in (('left', 0), ('right', 43)):
        add('roof_rail_' + suffix, (x, 43, 7), (x + 5, 48, 32), group='side', outer=True)
    add('drive_spine', (16, 43, 7), (32, 47, 30), group='top_drive', outer=True)
    for side, x in (('left', 16), ('right', 30)):
        add('roof_service_rail_' + side, (x, 47, 7), (x + 2, 48, 30),
            kind='ceramic', group='top_drive', outer=True)
        add('roof_cable_entry_cheek_' + side, (x, 45, 30), (x + 2, 48, 32),
            kind='structure', group='top_drive', outer=True)
    add('roof_service_front_cap', (18, 47, 7), (30, 48, 9),
        kind='ceramic', group='top_drive', outer=True)
    add('roof_emblem_plate', (18, 47, 9), (30, 48, 17),
        kind='ceramic', group='top_drive', outer=True)
    for end, z in (('front', 17), ('rear', 29)):
        add('roof_recess_' + end + '_rim', (18, 47, z), (30, 48, z + 1),
            kind='structure', group='top_drive', outer=True)
    for index, z in enumerate((19, 22, 25, 28)):
        add('roof_recess_slat_' + str(index + 1), (19, 47, z), (29, 48, z + 1),
            kind='structure', group='top_drive', outer=True)
    add('roof_cable_floor_front', (16, 43, 30), (32, 45, 32),
        kind='structure', group='top_drive', outer=True)
    for side, x in (('left', 19), ('right', 26)):
        add('roof_cable_' + side, (x, 45, 32), (x + 3, 47, 43),
            'power', 'bus', 'top_drive')
        for end, z in (('front', 30), ('rear', 42)):
            add('roof_cable_socket_' + side + '_' + end,
                (x - 1, 45, z), (x + 4, 48, z + 3),
                kind='ceramic', group='top_drive', face_kinds={'north': 'dark', 'south': 'dark'})
        add('roof_cable_clamp_' + side, (x - 1, 45, 37), (x + 4, 48, 39),
            kind='ceramic', group='top_drive')
    for suffix, x in (('left', 0), ('right', 44)):
        add('lower_shroud_' + suffix, (x, 6, 5), (x + 4, 14, 32), group='side', outer=True)
        add('upper_shroud_' + suffix, (x, 37, 5), (x + 4, 43, 32), group='side', outer=True)
    add('front_sill', (5, 6, 0), (43, 8, 6), group='side', outer=True)
    add('service_wall_4u_thick', (5, 16, 36), (43, 43, 40), kind='structure', group='side',
        face_kinds={'north': 'inner_wall', 'south': 'dark'})
    add('rear_port_plinth', (5, 6, 36), (43, 16, 48), kind='structure', group='side', outer=True)
    # Flush exterior caps, four-unit window wells, and two-unit inset panes.
    # Straight inner walls already belong to the existing beams and posts.
    # Each diagonal return adds only its visible inner face, not a full block.
    add('front_window_frame', (5, 22, 0), (43, 43, 4), kind='ceramic', group='side',
        outer=True, only=('north', 'south'))
    add('front_observation', (5, 22, 1), (43, 43, 3), 'glass', 'glass', 'glass', only=('north', 'south'))
    cut = 7 / math.sqrt(2)
    for level, y in (('lower', 22), ('upper', 43)):
        for side, x in (('left', 5), ('right', 43)):
            cx = x + (cut / 2 if side == 'left' else -cut / 2)
            cy = y + (cut / 2 if level == 'lower' else -cut / 2)
            angle = 45 if (level == 'upper') == (side == 'left') else -45
            add('window_reveal_front_' + level + '_' + side,
                (cx - 3.5, cy, 0), (cx + 3.5, cy, 4),
                kind='window_reveal', group='side', only=('up',) if level == 'lower' else ('down',),
                rotation={'axis': 'z', 'angle': angle, 'origin': [cx, cy, 2]})
    for side, frame_x, glass_x in (('left', 0, 1), ('right', 44, 45)):
        add('side_window_frame_' + side, (frame_x, 14, 5), (frame_x + 4, 37, 32),
            kind='ceramic', group='side', outer=True, only=('west', 'east'))
        add('side_observation_' + side, (glass_x, 14, 5), (glass_x + 2, 37, 32),
            'glass', 'glass', 'glass', only=('west', 'east'))
        for level, y in (('lower', 14), ('upper', 37)):
            for end, z in (('front', 5), ('rear', 32)):
                cy = y + (cut / 2 if level == 'lower' else -cut / 2)
                cz = z + (cut / 2 if end == 'front' else -cut / 2)
                angle = 45 if (level == 'lower') == (end == 'front') else -45
                add('window_reveal_' + side + '_' + level + '_' + end,
                    (frame_x, cy, cz - 3.5), (frame_x + 4, cy, cz + 3.5),
                    kind='window_reveal', group='side', only=('up',) if level == 'lower' else ('down',),
                    rotation={'axis': 'x', 'angle': angle, 'origin': [frame_x + 2, cy, cz]})
    for suffix, x in (('left', 5), ('right', 32)):
        add('roof_window_outer_rail_' + suffix, (x, 44, 7), (x + 2, 48, 32),
            kind='structure', group='side', outer=True)
        add('roof_window_inner_rail_' + suffix, (x + 9, 44, 7), (x + 11, 48, 32),
            kind='structure', group='side', outer=True)
        for end, z in (('front', 7), ('rear', 30)):
            add('roof_window_' + end + '_sill_' + suffix,
                (x + 2, 44, z), (x + 9, 48, z + 2), kind='structure', group='side', outer=True)
        add('roof_observation_' + suffix, (x + 2, 44, 9), (x + 9, 46, 30),
            'glass', 'glass', 'glass', only=('up', 'down'))
    add('console_top_frame', (5, 20, 0), (43, 22, 6), kind='structure', group='operator_console', outer=True)
    for suffix, x in (('left', 5), ('right', 41)):
        add('console_side_frame_' + suffix, (x, 8, 0), (x + 2, 20, 6),
            kind='structure', group='operator_console', outer=True)
    add('console_wide_display', (7, 8, 1), (41, 20, 6), 'console', 'dark', 'operator_console',
        face_kinds={'north': 'screen'})
    add('reactor_table', (13, 11, 14), (35, 14, 36), kind='ceramic')
    add('lower_focusing_head', (17, 14, 16), (31, 17, 28), kind='magnet')
    add('upper_focusing_head', (17, 35, 16), (31, 38, 28), kind='magnet')
    for suffix, x in (('left', 13), ('right', 32)):
        add('reaction_gantry_' + suffix, (x, 14, 33), (x + 3, 35, 37), kind='structure')
    add('reaction_gantry_bridge', (13, 35, 28), (35, 38, 37), kind='dark')
    for suffix, x in (('left', 8), ('right', 36)):
        add('opposed_magnetic_jaw_' + suffix, (x, 18, 16), (x + 4, 32, 26), kind='magnet')
        add('jaw_mount_' + suffix, (x - 2, 6, 17), (x + 6, 19, 28), kind='dark')
        for index, z in enumerate((18, 24)):
            name = suffix + '_' + str(index + 1)
            add('discharge_insulator_' + name, (x + 0.5, 32, z - 1.5),
                (x + 3.5, 34, z + 1.5), kind='ceramic')
            add('discharge_contact_' + name, (x + 1, 34, z - 1),
                (x + 3, 35, z + 1), 'power', 'contact', 'reactor')
            add('discharge_pin_' + name, (x + 1.5, 35, z - 0.5),
                (x + 2.5, 39, z + 0.5), kind='metal')
    add('overload_crystal', (18, 20, 18), (30, 32, 26), 'crystal', 'crystal', 'crystal',
        rotation={'axis': 'z', 'angle': 45, 'origin': [24, 26, 22]}, shade=False)
    for suffix, x in (('left', 6), ('right', 36)):
        add('capacitor_' + suffix, (x, 12, 28), (x + 6, 36, 36), 'power', 'coil', 'energy')
        add('capacitor_foot_' + suffix, (x - 1, 6, 27), (x + 7, 12, 37), kind='structure', group='energy')
        add('capacitor_cap_' + suffix, (x - 1, 36, 27), (x + 7, 39, 37), kind='ceramic', group='energy')
        add('bank_bus_riser_' + suffix, (x + 2, 39, 30), (x + 5, 43, 34), 'power', 'bus', 'energy')
        add('bank_bus_elbow_' + suffix, (x + 2, 39, 26), (x + 5, 42, 30), 'power', 'bus', 'energy')
    add('overhead_busbar', (11, 39, 30), (38, 42, 34), 'power', 'bus', 'energy')
    add('driver_feed', (22, 38, 26), (26, 41, 32), 'power', 'bus', 'energy')
    add('rear_data_trunk', (21, 10, 32), (27, 35, 36), 'power', 'bus', 'energy')
    add('top_actuator_housing', (17, 39, 17), (31, 43, 31), kind='vent', group='top_drive')
    add('top_actuator_ram', (21, 37, 18), (27, 39, 24), kind='ceramic', group='top_drive')
    # Two fin banks attach to the rear wall through solid headers. The pipes run
    # through every fin and into both headers, following the MoreMachine assembly.
    for suffix, x, end in (('left', 5, 18), ('right', 30, 43)):
        add('rear_radiator_lower_mount_' + suffix, (x, 16, 38), (end, 20, 48),
            kind='structure', group='rear_cooling')
        add('rear_radiator_upper_mount_' + suffix, (x, 39, 38), (end, 43, 48),
            kind='ceramic', group='rear_cooling')
        for index, px in enumerate((x + 2, x + 8)):
            add('rear_heat_pipe_' + suffix + '_' + str(index + 1),
                (px, 19, 42), (px + 3, 40, 45), 'power', 'bus', 'rear_cooling')
        for index, y in enumerate((21, 25, 29, 33, 37)):
            add('rear_radiator_fin_' + suffix + '_' + str(index + 1),
                (x - 2 if suffix == 'left' else x, y, 39),
                (end if suffix == 'left' else end + 2, y + 2, 48),
                kind='structure', group='rear_cooling',
                face_kinds={'up': 'metal'})
    add('rear_vertical_manifold', (22, 16, 42), (26, 43, 46), 'power', 'bus', 'rear_cooling')
    for suffix, y in (('upper', 40), ('lower', 17)):
        for side, a, b in (('left', 18, 22), ('right', 26, 30)):
            add('rear_manifold_link_' + suffix + '_' + side, (a, y, 42), (b, y + 2, 46),
                'power', 'bus', 'rear_cooling')
    for index, y in enumerate((22, 33)):
        add('rear_manifold_clamp_' + str(index + 1), (20, y, 39), (28, y + 2, 48),
            kind='ceramic', group='rear_cooling')


def dimensions(e, face):
    spans = [b - a for a, b in zip(e['from'], e['to'])]
    assert all(abs(v - round(v)) < 1e-8 for v in spans), e['name']
    s = [round(v) for v in spans]
    return {'north': (s[0], s[1]), 'south': (s[0], s[1]),
            'east': (s[2], s[1]), 'west': (s[2], s[1]),
            'up': (s[0], s[2]), 'down': (s[0], s[2])}[face]


def global_face(e, face):
    a, b = e['from'], e['to']
    if e['name'] in ('front_window_frame', 'side_window_frame_left', 'side_window_frame_right'):
        if e['name'] == 'front_window_frame':
            u = [48 - b[0], 48 - a[0]] if face == 'north' else [48 - a[0], 48 - b[0]]
            return 'face_front', [u[0], 48 - b[1], u[1], 48 - a[1]]
        if e['name'] == 'side_window_frame_left':
            u = [a[2], b[2]] if face == 'west' else [b[2], a[2]]
            return 'face_left', [u[0], 48 - b[1], u[1], 48 - a[1]]
        if e['name'] == 'side_window_frame_right':
            u = [48 - b[2], 48 - a[2]] if face == 'east' else [48 - a[2], 48 - b[2]]
            return 'face_right', [u[0], 48 - b[1], u[1], 48 - a[1]]
    if e['material'] == 'glass':
        if e['name'].startswith('roof_observation_'):
            v = [48 - a[2], 48 - b[2]] if face == 'up' else [48 - b[2], 48 - a[2]]
            return 'face_top', [a[0], v[0], b[0], v[1]]
    if not e['outer'] or 'rotation' in e:
        return None
    if face == 'north' and a[2] == 0:
        return 'face_front', [48 - b[0], 48 - b[1], 48 - a[0], 48 - a[1]]
    if face == 'south' and b[2] == 48:
        return 'face_back', [a[0], 48 - b[1], b[0], 48 - a[1]]
    if face == 'west' and a[0] == 0:
        return 'face_left', [a[2], 48 - b[1], b[2], 48 - a[1]]
    if face == 'east' and b[0] == 48:
        return 'face_right', [48 - b[2], 48 - b[1], 48 - a[2], 48 - a[1]]
    if face == 'up' and (b[1] == 48 or e['name'] in ('top', 'drive_spine', 'roof_cable_floor_front')):
        return 'face_top', [a[0], 48 - a[2], b[0], 48 - b[2]]
    if e['name'] == 'bottom' and face in ('up', 'down'):
        return ('deck', [a[0], 48 - a[2], b[0], 48 - b[2]]) if face == 'up' else (
            'face_bottom', [a[0], a[2], b[0], b[2]])
    return None


def pack(requests, size):
    result = {}
    free = [(0, 0, size, size)]
    for key in sorted(requests, key=lambda v: (-max(v[1:]), -v[1] * v[2], v[0], v[1], v[2])):
        _, w, h = key
        rw, rh = w + 2, h + 2
        candidates = [r for r in free if r[2] >= rw and r[3] >= rh]
        if not candidates:
            raise ValueError(f'Atlas full at {key}')
        x, y, _, _ = min(candidates, key=lambda r: (min(r[2] - rw, r[3] - rh), max(r[2] - rw, r[3] - rh)))
        result[key] = (x + 1, y + 1)
        next_free = []
        for fx, fy, fw, fh in free:
            if x >= fx + fw or x + rw <= fx or y >= fy + fh or y + rh <= fy:
                next_free.append((fx, fy, fw, fh))
                continue
            if x > fx:
                next_free.append((fx, fy, x - fx, fh))
            if x + rw < fx + fw:
                next_free.append((x + rw, fy, fx + fw - x - rw, fh))
            if y > fy:
                next_free.append((fx, fy, fw, y - fy))
            if y + rh < fy + fh:
                next_free.append((fx, y + rh, fw, fy + fh - y - rh))
        free = [r for i, r in enumerate(next_free) if not any(
            i != j and s[0] <= r[0] and s[1] <= r[1] and s[0] + s[2] >= r[0] + r[2]
            and s[1] + s[3] >= r[1] + r[3] and (s != r or j < i)
            for j, s in enumerate(next_free))]
    return result


def fit_atlas(requests):
    for size in (32, 64, 128, 256):
        try:
            return size, pack(requests, size)
        except ValueError:
            pass
    raise ValueError('Material atlas exceeds 256 pixels')


def uid(text):
    return str(uuid.uuid5(uuid.NAMESPACE_URL, 'ae2lt:factory-redesign-2026-11-15/' + text))


def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


def rotated_corners(e):
    from itertools import product
    points = list(product(*zip(e['from'], e['to'])))
    if 'rotation' not in e:
        return points
    r = e['rotation']
    axis = 'xyz'.index(r['axis'])
    p, q = ((1, 2), (2, 0), (0, 1))[axis]
    angle = math.radians(r['angle'])
    out = []
    for point in points:
        v = [point[i] - r['origin'][i] for i in range(3)]
        v[p], v[q] = v[p] * math.cos(angle) - v[q] * math.sin(angle), v[p] * math.sin(angle) + v[q] * math.cos(angle)
        out.append([v[i] + r['origin'][i] for i in range(3)])
    return out


def split_emissive(master_elements, variants, materials, sizes, layouts, blockout):
    """Split complementary pixel masks at identical surface coordinates.

    Only faces with luminous pixels get a second surface. Fully luminous
    components keep the original cube, avoiding a redundant opaque copy.
    Static light patches share one atlas; only the console atlas animates.
    """
    masks = {}
    for mat in materials:
        im = variants['on'][mat]
        mask = Image.new('L', im.size)
        if not blockout and mat != 'glass':
            mask.putdata([255 if a and (mat == 'crystal' or (r > g + 15 and b > g + 8)
                          or (mat == 'face_back' and ((b >= 170 and b > r + 12 and g > r + 8)
                              or (r >= 205 and g >= 115 and g > b + 35)))) else 0
                          for r, g, b, a in im.getdata()])
            if mat == 'console':
                for (kind, w, h), (x, y) in layouts[mat].items():
                    if kind == 'screen':
                        # The LCD interior has a stable mask in every frame.
                        # Its dark backing belongs to the display, not the bezel.
                        ImageDraw.Draw(mask).rectangle((x + 1, y + 1, x + w - 2, y + h - 2), fill=255)
        masks[mat] = mask
    light_sources = {state: {} for state in variants}
    for state, sheets in variants.items():
        for mat in materials:
            original = sheets[mat]
            mask = masks[mat]
            light = Image.new('RGBA', original.size)
            light.paste(original, (0, 0), mask)
            if state == 'off' and (mat.startswith('face_') or mat == 'deck'):
                light.putdata([(round(r * .48), round(g * .42), round(b * .48), a)
                               for r, g, b, a in light.getdata()])
            light_sources[state][mat] = light
            base = original.copy()
            base.paste((0, 0, 0, 0), (0, 0, *original.size), mask)
            assert not ImageChops.multiply(base.getchannel('A'), light.getchannel('A')).getbbox()
            assert ImageChops.lighter(base.getchannel('A'), light.getchannel('A')).tobytes() == original.getchannel('A').tobytes()
            sheets[mat] = base

    patches, face_patches, empty_base_faces = {}, {}, set()
    for index, cube in enumerate(master_elements):
        for face, data in cube['faces'].items():
            mat = data['texture'][1:]
            uv = [round(v * sizes[mat] / 16) for v in data['uv']]
            rect = (min(uv[0], uv[2]), min(uv[1], uv[3]), max(uv[0], uv[2]), max(uv[1], uv[3]))
            on = light_sources['on'][mat].crop(rect)
            if not on.getchannel('A').getbbox():
                continue
            off = light_sources['off'][mat].crop(rect)
            digest = hashlib.sha256(on.tobytes() + off.tobytes()).hexdigest()[:24]
            key = (digest, on.width, on.height)
            patches[key] = {'on': on, 'off': off, 'animated': mat == 'console'}
            face_patches[index, face] = (key, uv[0] > uv[2], uv[1] > uv[3])
            if not variants['on'][mat].crop(rect).getchannel('A').getbbox():
                empty_base_faces.add((index, face))
    if not patches:
        return {'enabled': False, 'surface_cubes': 0, 'faces': 0, 'fully_moved_cubes': []}
    light_layouts = {}
    for mat, animated in (('emissive', False), ('console_emissive', True)):
        requests = {key for key, patch in patches.items() if patch['animated'] == animated}
        size, layout = fit_atlas(requests)
        sizes[mat] = size
        materials.append(mat)
        light_layouts[mat] = layout
        for state, sheets in variants.items():
            frame_count = SCREEN_FRAMES if animated and state == 'on' else 1
            sheet = Image.new('RGBA', (size, size * frame_count))
            for key, pos in layout.items():
                patch = patches[key][state]
                for phase in range(frame_count):
                    if animated and state == 'on':
                        patch = screen_frame(key[1], key[2], True, phase)
                        alpha = patches[key]['on'].getchannel('A')
                        patch.putalpha(alpha)
                    sheet.paste(patch, (pos[0], pos[1] + size * phase))
            sheets[mat] = sheet
    source_count = len(ELEMENTS)
    original_groups = list(GROUPS)
    added, moved = [], []
    for index in range(source_count):
        original = master_elements[index]
        base = deepcopy(original)
        light = deepcopy(original)
        light['faces'] = {}
        light['shade'] = False
        for face in original['faces']:
            if (index, face) not in face_patches:
                continue
            key, flip_u, flip_v = face_patches[index, face]
            mat = 'console_emissive' if patches[key]['animated'] else 'emissive'
            x, y = light_layouts[mat][key]
            w, h = key[1:]
            uv = [x + w if flip_u else x, y + h if flip_v else y,
                  x if flip_u else x + w, y if flip_v else y + h]
            light['faces'][face] = {'uv': [v * 16 / sizes[mat] for v in uv], 'texture': '#' + mat}
            if (index, face) in empty_base_faces:
                del base['faces'][face]
        if not light['faces']:
            continue
        source = deepcopy(ELEMENTS[index])
        source.update({'render_layer': 'emissive', 'emissive_source': original['name'],
                       'shade': False, 'faces': list(light['faces'])})
        if not base['faces']:
            master_elements[index] = light
            if source['group'] != 'crystal':
                source['group'] = 'emissive'
            ELEMENTS[index] = source
            moved.append(source['name'])
        else:
            master_elements[index] = base
            ELEMENTS[index]['faces'] = list(base['faces'])
            light['name'] += '_emissive'
            source.update({'name': light['name'], 'group': 'emissive'})
            master_elements.append(light)
            ELEMENTS.append(source)
            added.append(source['name'])
    GROUPS.clear()
    GROUPS.update({name: [] for name in original_groups + ['emissive']})
    for index, source in enumerate(ELEMENTS):
        if source['group']:
            GROUPS[source['group']].append(index)
    used_materials = {face['texture'][1:] for cube in master_elements for face in cube['faces'].values()}
    materials[:] = [m for m in materials if m in used_materials]
    light_faces = sum(len(cube['faces']) for cube, source in zip(master_elements, ELEMENTS)
                      if source.get('render_layer') == 'emissive')
    return {'enabled': True, 'surface_cubes': len(added), 'faces': light_faces,
            'fully_moved_cubes': moved, 'base_emissive_alpha_overlap_pixels': 0,
            'surface_offset_units': 0, 'static_atlas': 'emissive',
            'animated_atlas': 'console_emissive', 'block_light': 15, 'sky_light': 15,
            'ambient_occlusion': False, 'metal_emits': False,
            'off_state': 'same light components, dim artwork, ordinary lighting and no animation',
            'new_surface_names': added}


def main():
    args = argparse.ArgumentParser()
    args.add_argument('--blockout', action='store_true')
    opts = args.parse_args()
    geometry()
    TEXDIR.mkdir(parents=True, exist_ok=True)
    requests = {}
    for e in ELEMENTS:
        for f in e['faces']:
            if global_face(e, f):
                continue
            key = (e['face_kinds'].get(f, e['kind']), *dimensions(e, f))
            requests.setdefault(e['material'], set()).add(key)
    skins = global_skins(opts.blockout)
    packed = {m: fit_atlas(r) for m, r in requests.items()}
    layouts = {m: layout for m, (_, layout) in packed.items()}
    materials = ['face_front', 'face_back', 'face_left', 'face_right', 'face_top',
                 'face_bottom', 'deck', *requests]
    sizes = {m: SIZE if m in skins else packed[m][0] for m in materials}
    variants = {}
    for active in (True, False):
        state = 'on' if active else 'off'
        sheets = {m: skins[m].copy() if m in skins else
                  Image.new('RGBA', (sizes[m], sizes[m]), (0, 0, 0, 0)) for m in materials}
        for mat, layout in layouts.items():
            for (kind, w, h), pos in layout.items():
                sheets[mat].paste(panel(kind, w, h, active, opts.blockout), pos)
        variants[state] = sheets

    master_elements = []
    density_errors = []
    for i, e in enumerate(ELEMENTS):
        cube = {'name': e['name'], 'from': [v - 16 for v in e['from']],
                'to': [v - 16 for v in e['to']], 'shade': e['shade'], 'faces': {}}
        if 'rotation' in e:
            cube['rotation'] = deepcopy(e['rotation'])
            cube['rotation']['origin'] = [v - 16 for v in cube['rotation']['origin']]
        for f in e['faces']:
            g = global_face(e, f)
            w, h = dimensions(e, f)
            if g:
                mat, uv = g
            else:
                mat = e['material']
                kind = e['face_kinds'].get(f, e['kind'])
                x, y = layouts[mat][(kind, w, h)]
                uv = [x, y, x + w, y + h]
            if abs(uv[2] - uv[0]) != w or abs(uv[3] - uv[1]) != h:
                density_errors.append((e['name'], f))
            assert min(uv) >= 0 and max(uv) <= sizes[mat], (e['name'], f, mat, uv)
            cube['faces'][f] = {'uv': [v * 16 / sizes[mat] for v in uv], 'texture': '#' + mat}
        master_elements.append(cube)
    source_cube_count = len(ELEMENTS)
    emission = split_emissive(master_elements, variants, materials, sizes, layouts, opts.blockout)
    state_materials = set(STATE_MATERIALS) | {'emissive', 'console_emissive'}
    output_textures, output_metadata = set(), []
    for state, sheets in variants.items():
        for mat in materials:
            suffix = '_' + state if mat in state_materials else ''
            path = TEXDIR / (mat + suffix + '.png')
            if mat not in state_materials:
                assert sheets[mat].tobytes() == variants['on'][mat].tobytes(), mat
            sheets[mat].save(path)
            output_textures.add(path)
            if mat == 'console_emissive' and state == 'on':
                metadata_path = path.with_suffix('.png.mcmeta')
                write_json(metadata_path, {'animation': {'frametime': SCREEN_FRAME_TICKS,
                           'width': sizes[mat], 'height': sizes[mat],
                           'interpolate': False, 'frames': list(range(SCREEN_FRAMES))}})
                output_metadata.append(metadata_path)
    bounds = list(zip(*[p for e in ELEMENTS for p in rotated_corners(e)]))
    for axis in bounds:
        assert min(axis) >= -1e-8 and max(axis) <= 48 + 1e-8
    assert not density_errors, density_errors
    for state in ('on', 'off'):
        texrefs = {m: 'ae2lt:block/large_overload_factory_redesign/' + m +
                   ('_' + state if m in state_materials else '') for m in materials}
        opaque_elements = [cube for cube, source in zip(master_elements, ELEMENTS)
                           if source['group'] != 'glass' and source.get('render_layer') != 'emissive']
        glass_elements = [cube for cube, source in zip(master_elements, ELEMENTS) if source['group'] == 'glass']
        light_elements = [deepcopy(cube) for cube, source in zip(master_elements, ELEMENTS)
                          if source.get('render_layer') == 'emissive']
        for cube in light_elements:
            cube['shade'] = state == 'off'
            for face in cube['faces'].values():
                if state == 'on':
                    face['neoforge_data'] = {'block_light': 15, 'sky_light': 15, 'ambient_occlusion': False}
        model = {'credit': 'AE2LT factory redesign, 2026-11-15. Native-scale AE2LT artwork; MoreMachine-inspired rear radiator assembly.',
                 'parent': 'minecraft:block/block', 'loader': 'neoforge:composite',
                 'texture_size': [SIZE, SIZE], 'ambientocclusion': True,
                 'textures': {**texrefs, 'particle': texrefs['face_bottom']},
                 'children': {
                     'body': {'render_type': 'cutout', 'textures': texrefs, 'elements': opaque_elements},
                     'windows': {'render_type': 'translucent', 'textures': texrefs, 'elements': glass_elements}},
                 'display': {'gui': {'rotation': [25, 225, 0], 'scale': [0.22, 0.22, 0.22]},
                             'ground': {'translation': [0, 3, 0], 'scale': [0.16, 0.16, 0.16]}}}
        if light_elements:
            model['children'] = {
                'body': model['children']['body'],
                'emissive': {'render_type': 'cutout', 'ambientocclusion': state == 'off',
                             'textures': texrefs, 'elements': light_elements},
                'windows': model['children']['windows']}
            model['item_render_order'] = ['body', 'emissive', 'windows']
        write_json(MODELDIR / ('factory_' + state + '.json'), model)
        bb = {'meta': {'format_version': '5.0', 'model_format': 'java_block', 'box_uv': False},
              'name': 'large_overload_factory_redesign' + ('_off' if state == 'off' else ''), 'java_block_version': '1.9.0',
              'credit': model['credit'], 'ambientocclusion': True,
              'resolution': {'width': SIZE, 'height': SIZE}, 'elements': [], 'groups': [],
              'outliner': [], 'textures': [], 'display': model['display']}
        if emission['enabled']:
            bb['texture_groups'] = [{'name': 'emissive', 'uuid': uid('texture_group/emissive'), 'is_material': False}]
        for m, ref in texrefs.items():
            path = TEXDIR / (ref.rsplit('/', 1)[-1] + '.png')
            encoded = base64.b64encode(path.read_bytes()).decode()
            bb['textures'].append({'name': path.name, 'id': m, 'uuid': uid('texture/' + m),
                                   'namespace': 'ae2lt', 'folder': 'block/large_overload_factory_redesign',
                                   'width': variants[state][m].width, 'height': variants[state][m].height,
                                   'uv_width': SIZE, 'uv_height': SIZE,
                                   'mode': 'bitmap', 'internal': True, 'saved': True,
                                   'render_mode': 'emissive' if state == 'on' and m in ('emissive', 'console_emissive') else 'default',
                                   'render_sides': 'auto', 'visible': True,
                                   'source': 'data:image/png;base64,' + encoded})
            if m in ('emissive', 'console_emissive'):
                bb['textures'][-1]['group'] = uid('texture_group/emissive')
            if m == 'console_emissive' and state == 'on':
                bb['textures'][-1].update({'frame_time': SCREEN_FRAME_TICKS, 'frame_order_type': 'custom',
                    'frame_order': ' '.join(map(str, range(SCREEN_FRAMES))), 'frame_interpolate': False})
        for i, cube in enumerate(master_elements):
            item = deepcopy(cube)
            if ELEMENTS[i].get('render_layer') == 'emissive':
                item['shade'] = state == 'off'
            item.update({'type': 'cube', 'uuid': uid('cube/' + item['name']),
                         'box_uv': False, 'autouv': 0, 'rescale': False, 'visibility': True,
                         'origin': [8, 8, 8], 'color': i % 8})
            rotation = item.pop('rotation', None)
            if rotation:
                item['origin'] = rotation['origin']
                item['rotation'] = [rotation['angle'] if a == rotation['axis'] else 0 for a in 'xyz']
            else:
                item['rotation'] = [0, 0, 0]
            if not rotation and ELEMENTS[i]['group'] == 'glass':
                # Blockbench sorts transparent objects by pivot; each sheet must
                # keep its pivot at the sheet, rather than inside the reactor.
                item['origin'] = [(a + b) / 2 for a, b in zip(item['from'], item['to'])]
            for face in FACES:
                if face in item['faces']:
                    faceinfo = item['faces'][face]
                    faceinfo['uv'] = [v * SIZE / 16 for v in faceinfo['uv']]
                    faceinfo['texture'] = materials.index(faceinfo['texture'][1:])
                else:
                    item['faces'][face] = {'uv': [0, 0, 0, 0], 'texture': None}
            bb['elements'].append(item)
            if not ELEMENTS[i]['group']:
                bb['outliner'].append(item['uuid'])
        for idx, (name, ids) in enumerate(GROUPS.items()):
            group_id = uid('group/' + name)
            bb['groups'].append({'name': name, 'uuid': group_id, 'origin': [8, 8, 8],
                                 'rotation': [0, 0, 0], 'color': idx % 8, 'visibility': True,
                                 'export': True, 'isOpen': False})
            bb['outliner'].append({'uuid': group_id, 'children': [bb['elements'][n]['uuid'] for n in ids]})
        if emission['enabled']:
            crystal_group = next(g for g in bb['outliner'] if isinstance(g, dict) and g['uuid'] == uid('group/crystal'))
            if all(ELEMENTS[n].get('render_layer') == 'emissive' for n in GROUPS['crystal']):
                bb['outliner'].remove(crystal_group)
                next(g for g in bb['outliner'] if isinstance(g, dict) and g['uuid'] == uid('group/emissive'))['children'].insert(0, crystal_group)
        name = 'large_overload_factory_redesign' + ('_off' if state == 'off' else '') + '.bbmodel'
        write_json(OUT / name, bb)
    output_textures = sorted(output_textures)
    # These older standalone crystal sheets are folded into the emissive atlas.
    # Restrict cleanup to generator-owned files, and retain them for blockout.
    for obsolete in ('crystal_on.png', 'crystal_off.png'):
        path = TEXDIR / obsolete
        if path not in output_textures and path.exists():
            path.unlink()
    report = {'target': 'Minecraft Java 1.21.1 / NeoForge', 'stage': 'blockout' if opts.blockout else 'textured',
              'footprint_blocks': [3, 3, 3], 'dimensions_units': [48, 48, 48],
              'world_bounds_units': [[min(v), max(v)] for v in bounds],
              'export_bounds_units': [-16, 32], 'origin_cell': [1, 1, 1], 'front': 'north',
              'controller_cell': [1, 1, 0], 'core_cell': [1, 1, 1], 'rear_function_cells': 3,
              'rear_function_layout': {'row': 'bottom', 'left_to_right': ['cable_connector', 'output', 'pattern'],
                                       'left_artwork': 'original 12x12 cable socket; no IO port texture',
                                       'other_native_source_crop': [2, 2, 14, 14],
                                       'texture_positions': [[2, 34], [18, 34], [34, 34]]},
              'cubes': len(ELEMENTS), 'groups': {k: len(v) for k, v in GROUPS.items()},
              'structural_cubes_before_emissive_split': source_cube_count,
              'textured_faces': sum(len(cube['faces']) for cube in master_elements),
              'emissive': emission,
              'textures_per_project': len(materials), 'logical_uv_resolution': [SIZE, SIZE],
              'exterior_resolution': {m: [sizes[m], sizes[m]] for m in materials if m.startswith('face_')},
              'material_frame_resolutions': {m: [sizes[m], sizes[m]] for m in materials},
              'material_resolutions': {m: list(variants['on'][m].size) for m in materials},
              'pixel_density': [1, 1], 'uv_density_errors': density_errors,
              'glass_alpha': sorted(set(variants['on']['glass'].getchannel('A').getdata())),
              'render_layers': {name: child['render_type'] for name, child in model['children'].items()},
              'window_braces': len([e for e in ELEMENTS if e['name'].startswith('window_brace_')]),
              'rear_cooling': {'banks': 2, 'fins_per_bank': 5, 'pipe_count': 4,
                               'connected_manifold': True, 'housing_depth_units': 16,
                               'fin_depth_units': 9, 'fin_width_units': 15,
                               'outward_extension_per_bank_units': 2, 'side_housing_wall_units': 5},
              'roof_windows': {'solid_frame_height_units': 4, 'solid_frame_width_units': 2,
                               'glass_thickness_units': 2, 'glass_recess_below_roof_units': 2},
              'roof_service': {'vent_recess_depth_units': 1, 'solid_vent_slats': 4,
                               'emblem_plate_height_units': 1,
                               'cable_well_depth_units': 3, 'cable_runs': 2,
                               'cable_cross_section_units': [3, 2],
                               'sockets_per_cable': 2, 'clamps_per_cable': 1,
                               'texture_layout': 'closed lid seam, service island, framed openings and four corner fasteners'},
              'magnetic_jaws': {'upright': True, 'discharge_pins_per_jaw': 2,
                                'insulated_contacts': True},
              'front_console': {'integrated': True, 'frame_width_units': 38,
                                'frame_height_units': 16, 'screen_pixels': [34, 12],
                                'screen_recess_units': 1, 'screen_off': 'dark idle glass',
                                'animation_frames': SCREEN_FRAMES if emission['enabled'] else 0,
                                'frame_ticks': SCREEN_FRAME_TICKS,
                                'loop_seconds': SCREEN_FRAMES * SCREEN_FRAME_TICKS / 20,
                                'interpolation': False, 'animation_geometry': 'unchanged'},
              'observation_windows': {'style': 'flush outer frame and recessed glass with visible window-well walls',
                                      'flush_frame_elements': 3, 'frame_depth_units': 4,
                                      'front_and_side_glass_inset_units': 1, 'glass_thickness_units': 2,
                                      'separate_glass_material': True, 'old_corner_castings': 0,
                                      'frame_textured_faces': 6, 'diagonal_inner_faces': 12,
                                      'straight_walls_reuse_existing_shell': True, 'independent_glass_panes': True},
              'metal_finish': 'Five close tones, stepped directional shading, coherent grain and sparse short scuffs.',
              'back_wall_thickness_units': 4, 'source_artwork_reused': True,
              'source_reuse_method': 'Cropped native pixels, extended neutral metal runs, newly connected full-face compositions. No bitmap scaling.',
              'source_materials': {k: {'path': str((SRC / v).relative_to(OUT.parents[2])).replace('\\', '/'),
                                       'resolution': list(SOURCE[k].size),
                                       'sha256': hashlib.sha256((SRC / v).read_bytes()).hexdigest()}
                                   for k, v in SOURCE_FILES.items()},
              'production_resources_changed': False, 'in_game_verified': False,
              'blockbench_preview_verified': False,
              'material_sha256': {p.name: hashlib.sha256(p.read_bytes()).hexdigest() for p in output_textures},
              'animation_metadata': [p.name for p in output_metadata]}
    write_json(OUT / 'validation.json', report)
    write_json(OUT / 'geometry.json', ELEMENTS)
    # Display-only nearest-neighbour enlargement never changes the exported source pixels.
    font = ImageFont.truetype('C:/Windows/Fonts/segoeui.ttf', 15)
    sheet = Image.new('RGB', (900, 296 * math.ceil(len(materials) / 3)), '#292934')
    d = ImageDraw.Draw(sheet)
    for index, m in enumerate(materials):
        x, y = (index % 3) * 300 + 8, (index // 3) * 296 + 8
        scale = 256 // sizes[m]
        im = variants['on'][m].crop((0, 0, sizes[m], sizes[m])).resize(
            (sizes[m] * scale, sizes[m] * scale), Image.Resampling.NEAREST)
        sheet.paste(im, (x + (280 - im.width) // 2, y), im)
        d.text((x + 2, y + 261), f'{m} | {sizes[m]} px | 1 px/unit', fill='#d5d6dc', font=font)
    sheet.save(OUT / 'material_sheet.png')
    face_sheet = Image.new('RGB', (1224, 840), '#292934')
    df = ImageDraw.Draw(face_sheet)
    for index, m in enumerate(materials[:6]):
        x, y = (index % 3) * 408 + 12, (index // 3) * 420 + 12
        enlarged = variants['on'][m].resize((384, 384), Image.Resampling.NEAREST)
        face_sheet.paste(enlarged, (x, y), enlarged)
        df.text((x, y + 389), f'{m} | 48 x 48 px', fill='#d5d6dc', font=font)
    face_sheet.save(OUT / 'exterior_48px_sheet.png')
    repeat = Image.new('RGB', (576, 320), '#292934')
    dr = ImageDraw.Draw(repeat)
    for index, kind in enumerate(('structure', 'metal', 'coil')):
        tile = panel(kind, 16, 16, True, opts.blockout)
        repeated = Image.new('RGBA', (48, 48))
        for y in range(3):
            for x in range(3):
                repeated.paste(tile, (16 * x, 16 * y))
        repeat.paste(repeated.resize((180, 180), Image.Resampling.NEAREST), (index * 192, 18))
        dr.text((index * 192 + 4, 210), kind + ' / repeat study', fill='#d5d6dc')
    dr.text((4, 252), 'Large shell faces use continuous 48px artwork, not repeated machine fronts.', fill='#d5d6dc')
    repeat.save(OUT / 'material_repeat_check.png')
    print(json.dumps({k: report[k] for k in ('stage', 'cubes', 'groups', 'dimensions_units', 'textures_per_project', 'uv_density_errors')}, ensure_ascii=False))


if __name__ == '__main__':
    main()
