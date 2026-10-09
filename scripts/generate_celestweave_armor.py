#!/usr/bin/env python3
"""Author the worn Celestweave (苍穹织雷) armor, glow and phase-wing textures.

The palette is sampled from the Celestweave item icons: silver plating over a slate frame,
with the same pink energy ramp. Every glowing texel is mirrored into a matching *_glow.png,
which the client renders through RenderType.eyes so the energy lines stay lit at night.

CUBES must stay in sync with client/CelestweaveArmorModel.java: texture offsets,
sizes and the 128x64 texture size are shared. Base humanoid parts keep vanilla UVs.
Run from any directory: python3 scripts/generate_celestweave_armor.py
"""
from pathlib import Path
import struct
import zlib

ROOT = Path(__file__).resolve().parents[1]
ARMOR = ROOT / "src/main/resources/assets/ae2lt/textures/models/armor"
ENTITY = ROOT / "src/main/resources/assets/ae2lt/textures/entity"

# Glyph -> (hex color, glow strength). Glow strength 0 means the texel is plating only.
PALETTE = {
    "K": ("2E2D3D", 0), "O": ("413F54", 0), "o": ("53536A", 0),
    "s": ("696D88", 0), "S": ("878FA5", 0), "m": ("ADB0C4", 0),
    "n": ("CBCCD4", 0), "l": ("DEDFE3", 0), "w": ("F2F2F2", 0),
    # Undersuit weave, a darker step of the item outline slate.
    "U": ("2F2E42", 0), "u": ("3B3A52", 0), "v": ("4A4A64", 0),
    # Energy ramp from the item glyphs.
    "p": ("BD4E92", 0.55), "q": ("ED91BD", 0.8), "r": ("F4B4D3", 0.9),
    "e": ("FFE5FD", 1.0), "h": ("FFFEFF", 1.0),
}

TEX_W, TEX_H = 128, 64

# name -> (texture u, v, width, height, depth); all sizes are model units = texels.
CUBES = {
    # Outer layer (celestweave_layer_1): helmet, chestplate, boots.
    "head": (0, 0, 8, 8, 8),
    "body": (16, 16, 8, 12, 4),
    "arm": (40, 16, 4, 12, 4),
    "leg": (0, 16, 4, 12, 4),
    "crest": (64, 0, 1, 1, 6),
    "ear_fin": (80, 0, 1, 3, 5),
    "brow_gem": (92, 0, 2, 2, 1),
    "heart_core": (98, 0, 3, 3, 1),
    "back_pack": (64, 10, 6, 7, 2),
    "heel_fin": (80, 10, 1, 3, 4),
    "toe_cap": (92, 10, 5, 3, 1),
    "pauldron": (64, 20, 6, 4, 7),
    # Inner layer (celestweave_layer_2): leggings.
    "knee_plate": (64, 0, 4, 3, 1),
    "belt_buckle": (76, 0, 3, 2, 1),
    "tasset": (64, 8, 1, 5, 4),
}


def rgb(value):
    return tuple(int(value[i:i + 2], 16) for i in (0, 2, 4))


class Canvas:
    def __init__(self, width, height):
        self.width, self.height = width, height
        self.color = [[(0, 0, 0, 0)] * width for _ in range(height)]
        self.glow = [[(0, 0, 0, 0)] * width for _ in range(height)]

    def paint(self, x, y, rows):
        for dy, row in enumerate(rows):
            for dx, glyph in enumerate(row):
                if glyph == ".":
                    continue
                hex_color, strength = PALETTE[glyph]
                color = rgb(hex_color)
                self.color[y + dy][x + dx] = color + (255,)
                if strength:
                    self.glow[y + dy][x + dx] = tuple(round(c * strength) for c in color) + (255,)

    def fill(self, x, y, width, height, glyph):
        self.paint(x, y, [glyph * width] * height)


def faces(name):
    """Vanilla cube unwrap: top, bottom, then right | front | left | back."""
    u, v, w, h, d = CUBES[name]
    return {
        "top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h), "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h), "back": (u + d + w + d, v + d, w, h),
    }


def mirrored(rows):
    return [row[::-1] for row in rows]


def paint_cube(canvas, name, **rows_by_face):
    for face, (x, y, w, h) in faces(name).items():
        rows = rows_by_face.get(face)
        if rows is None:
            continue
        if isinstance(rows, str):
            canvas.fill(x, y, w, h, rows)
            continue
        assert len(rows) == h and all(len(r) == w for r in rows), (name, face)
        canvas.paint(x, y, rows)


# Helmet: 苍穹织雷·瞳. Full mask, wrap-around visor slit, ring sensors under swept fins.
HEAD_SIDE = [
    "snnlllll",
    "snlwwwwl",
    "snlmmnlw",
    "snSooSUU",
    "snoqrope",
    "snorqoUU",
    "snSooSnm",
    "ssmnnnms",
]


def paint_helmet(c):
    paint_cube(
        c, "head",
        top=["smnnnnms", "mnllllnm", "nlwwwwln", "nlwoowln",
             "nlwoowln", "nlwwwwln", "mnllllnm", "smnnnnms"],
        bottom="O",
        right=HEAD_SIDE,
        front=["nlwwwwln", "mnlwwlnm", "smmnnmms", "UUUUUUUU",
               "qrehherq", "UUUUUUUU", "mnloolnm", "smnoonms"],
        left=mirrored(HEAD_SIDE),
        back=["smnqqnms", "mnlpplnm", "nlwSSwln", "nlwnnwln",
              "mlnllnlm", "mnlSSlnm", "omnnnnmo", "OooooooO"],
    )
    paint_cube(c, "crest", top=["q", "r", "e", "e", "r", "q"], bottom="o",
               right=["mnllnm"], left=["mnllnm"], front=["n"], back=["m"])
    fin_side = ["lwwln", "erqpm", "mnmso"]
    paint_cube(c, "ear_fin", top=["l", "w", "w", "l", "n"], bottom="o",
               right=fin_side, left=mirrored(fin_side),
               front=["n", "p", "o"], back=["l", "e", "m"])
    paint_cube(c, "brow_gem", top=["nn"], bottom="o", right=["m", "s"], left=["m", "s"],
               front=["re", "er"], back="o")


# Chestplate: 苍穹织雷·心. Heart core socket feeding lines to the shoulders and down the abdomen.
BODY_SIDE = ["snlm", "snlm", "smnm", "oUUo", "oUqo", "oUUo",
             "smnm", "snlm", "oUUo", "oUqo", "oUUo", "smms"]
ARM_OUTER = ["uuuu", "uuuu", "uUUu", "vuuv", "vUUv", "smms",
             "mnln", "nlwn", "nlwn", "qrrq", "mnnm", "oooo"]
PAULDRON_OUTER = ["mlwwwlm", "nllllln", "pqrhrqp", "sooooos"]


def paint_chestplate(c):
    paint_cube(
        c, "body",
        top=["mnllllnm", "nlwwwwln", "nlwwwwln", "mnllllnm"],
        bottom="O",
        right=BODY_SIDE,
        front=["mnlwwlnm", "pnlwwlnp", "lqOooOql", "nlorhonl",
               "nlohroln", "mlOooOlm", "smlqqlms", "oUUppUUo",
               "smnqqnms", "oUUppUUo", "smnllnms", "OooppooO"],
        left=mirrored(BODY_SIDE),
        back=["mnllllnm", "nlwwwwln", "nloooolm", "nloooolm",
              "nloooolm", "nloooolm", "mnoooonm", "smoooOms",
              "smnqqnms", "oUUppUUo", "smnllnms", "OooppooO"],
    )
    paint_cube(c, "heart_core", top=["nln"], bottom="o", right=["m", "n", "s"],
               left=["m", "n", "s"], front=["qrq", "rhr", "qrq"], back="o")
    paint_cube(c, "back_pack", top=["mnllnm", "nlwwln"], bottom="o",
               right=["mn", "nl", "pq", "nl", "nl", "pq", "so"],
               left=["nm", "ln", "qp", "ln", "ln", "qp", "os"],
               front="o",
               back=["smnnms", "nollon", "nqllqn", "nrwwrn",
                     "nqllqn", "nollon", "soooos"])
    paint_cube(
        c, "arm",
        top="u", bottom="o",
        right=ARM_OUTER,
        front=["uvvu", "uuuu", "uuuu", "UuuU", "vUUv", "smms",
               "mnlm", "nlwn", "nlwn", "pmms", "mnnm", "oooo"],
        left=["uuuu", "uuuu", "uuuu", "uuuu", "vUUv", "smms",
              "mnnm", "nlln", "nlln", "smms", "mnnm", "oooo"],
        back=["uvvu", "uuuu", "uuuu", "UuuU", "vUUv", "smms",
              "mnnm", "nlln", "nlln", "smmp", "mnnm", "oooo"],
    )
    paint_cube(
        c, "pauldron",
        top=["smnnnm", "mqlwln", "mrlwln", "mhlwln", "mrlwln", "mqlwln", "smnnnm"],
        bottom="o",
        right=PAULDRON_OUTER,
        front=["mlwwlm", "nlllln", "pnmmns", "soooos"],
        left=["mnnnnnm", "snnnnns", "sooooos", "OOOOOOO"],
        back=["mnllnm", "snnnns", "snmmnp", "soooos"],
    )


# Boots: 苍穹织雷·阶. Only the lower five rows of the leg are plated.
BOOT_OUTER = ["....", "....", "....", "....", "....", "....", "....",
              "mnnm", "qrrq", "nlln", "mnlm", "OooO"]


def paint_boots(c):
    paint_cube(
        c, "leg",
        bottom="O",
        right=BOOT_OUTER,
        front=BOOT_OUTER[:7] + ["mnnm", "pmmm", "nlwn", "mnnm", "OooO"],
        left=BOOT_OUTER[:7] + ["mnnm", "smmm", "nlln", "mnnm", "OooO"],
        back=BOOT_OUTER[:7] + ["mnnm", "mmmp", "nlln", "smms", "OooO"],
    )
    paint_cube(c, "toe_cap", top=["mnlnm"], bottom="O", right=["m", "p", "O"],
               left=["m", "p", "O"], front=["nlwln", "pqrqp", "OoooO"], back="o")
    heel_side = ["wn..", "rlnm", "omms"]
    paint_cube(c, "heel_fin", top=["w", "n", ".", "."], bottom="o",
               right=heel_side, left=mirrored(heel_side),
               front=["n", "m", "s"], back=["w", "r", "o"])


# Leggings: 苍穹织雷·径. Undersuit with thigh plates and a conduit running down the outer seam.
LEG_OUTER = ["mnnm", "upuu", "uquv", "uquv", "urUv", "uquv",
             "uquv", "upuv", "uquv", "urUv", "uquv", "Upuu"]


def paint_leggings(c):
    belt = ["uvvvvvvu", "vuuuuuuv", "mnnoonnm", "nlloolln", "soooooos"]
    belt_side = ["uvvu", "vuuv", "mnnm", "nlln", "soos"]
    blank8, blank4 = ["." * 8] * 7, ["." * 4] * 7
    paint_cube(
        c, "body",
        bottom="U",
        right=blank4 + belt_side,
        front=blank8 + belt,
        left=blank4 + belt_side,
        back=blank8 + ["uvvvvvvu", "vuuuuuuv", "mnnnnnnm", "nllppllm", "soooooos"],
    )
    paint_cube(
        c, "leg",
        top="u", bottom="U",
        right=LEG_OUTER,
        front=["mnnm", "nlwn", "nlwn", "mnlm", "smms", "uUUu",
               "uUUu", "vuuv", "uvvu", "uuuu", "UuuU", "UUUU"],
        left=["mnnm", "uuuv", "uuuv", "uuuv", "uUuv", "uuuv",
              "uuuv", "uuuv", "uUuv", "uuuv", "uuuv", "UUuu"],
        back=["mnnm", "uvvu", "uvvu", "uuuu", "UuuU", "uUUu",
              "uuuu", "uvvu", "uuuu", "uuuu", "UuuU", "UUUU"],
    )
    paint_cube(c, "knee_plate", top=["mnnm"], bottom="o", right=["m", "n", "s"],
               left=["m", "n", "s"], front=["nwwn", "lqql", "smms"], back="o")
    paint_cube(c, "belt_buckle", top=["nln"], bottom="o", right=["m", "s"], left=["m", "s"],
               front=["qeq", "mnm"], back="o")
    tasset_side = ["mnnn", "nlwl", "nlwl", "pqrq", "somm"]
    paint_cube(c, "tasset", top=["m", "n", "n", "m"], bottom="o",
               right=tasset_side, left=mirrored(tasset_side),
               front=["n", "l", "l", "q", "o"], back=["m", "n", "n", "p", "o"])


# Phase wing: vanilla elytra unwrap (22, 0, 10x20x2). Rows run root -> tip; kept left-right
# symmetric so it reads the same on the mirrored wing.
WING_FACE = [
    "smnllllnms",
    "mnlwwwwlnm",
    "nlSoooooSl",
    "nlouqquuol",
    "mlouuruuol",
    "mlUqupuuqm",
    "nsuuruquul",
    "nsupuuuqul",
    "msuqurupum",
    "lsuuuqurul",
    "lUupuuuqUl",
    "msuruqupum",
    "nUuuuruuUn",
    "msuqupuqum",
    "lUuuuruuUl",
    "msUpuuqUsm",
    "nlsuruursl",
    "mns.qhq.sm",
    ".ms..e..s.",
    "..s.....s.",
]


def paint_wing(c):
    x, y = 22, 0
    c.fill(x + 2, y, 10, 2, "n")
    c.fill(x + 12, y, 10, 2, "o")
    edge = ["mn"] * 17 + ["s."] * 3
    c.paint(x, y + 2, edge)
    c.paint(x + 12, y + 2, edge)
    c.paint(x + 2, y + 2, WING_FACE)
    c.paint(x + 14, y + 2, WING_FACE)


def png(path, pixels):
    height, width = len(pixels), len(pixels[0])
    raw = b"".join(b"\x00" + bytes(v for px in row for v in px) for row in pixels)

    def chunk(kind, data):
        body = kind + data
        return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)

    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(
        b"\x89PNG\r\n\x1a\n"
        + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
        + chunk(b"IDAT", zlib.compress(raw, 9))
        + chunk(b"IEND", b""))


def build():
    outer, inner, wing = Canvas(TEX_W, TEX_H), Canvas(TEX_W, TEX_H), Canvas(64, 32)
    paint_helmet(outer)
    paint_chestplate(outer)
    paint_boots(outer)
    paint_leggings(inner)
    paint_wing(wing)
    return outer, inner, wing


def main():
    outer, inner, wing = build()
    png(ARMOR / "celestweave_layer_1.png", outer.color)
    png(ARMOR / "celestweave_layer_1_glow.png", outer.glow)
    png(ARMOR / "celestweave_layer_2.png", inner.color)
    png(ARMOR / "celestweave_layer_2_glow.png", inner.glow)
    png(ENTITY / "celestweave_phase_wing.png", wing.color)
    png(ENTITY / "celestweave_phase_wing_glow.png", wing.glow)


if __name__ == "__main__":
    main()
