#!/usr/bin/env python3
"""Generates the Versixty "V6" logo from one geometry definition.

Writes the launcher vector drawables (adaptive + monochrome), the legacy PNG launcher icons and
VersixtyArt.kt (the same paths for Compose, used by the splash screen and in-app marks).
Run from the repo root:  python3 tools/gen_logo.py [preview.png]
"""
import math, os, sys
from PIL import Image, ImageDraw

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
RES = os.path.join(ROOT, "app/src/main/res")
KT = os.path.join(ROOT, "app/src/main/kotlin/com/ky7ra/cpkeditor/ui/VersixtyArt.kt")

BG = "#141210"
ORANGE = "#FF5A36"
WHITE = "#F7F7F7"
SKEW = 0.22      # italic lean
SCALE = 0.66     # keeps every corner inside the 72dp circle that round launcher masks show

# --- raw glyph shapes (before lean / scale), 108-unit grid ------------------------------------
V_RAW = [(10, 26), (29, 26), (36, 52), (43, 26), (62, 26), (36, 82)]
STEM_RAW = [(63, 60), (80, 60), (99, 26), (82, 26)]
RING_OUT_RAW = [(62, 52), (98, 52), (98, 82), (62, 82)]
RING_IN_RAW = [(74, 61), (87, 61), (87, 73), (74, 73)]


def lean(pts):
    return [(x + (54 - y) * SKEW, y) for x, y in pts]


def area(pts):
    n = len(pts)
    return sum(pts[i][0] * pts[(i + 1) % n][1] - pts[(i + 1) % n][0] * pts[i][1] for i in range(n)) / 2


def orient(pts, positive=True):
    return pts if (area(pts) > 0) == positive else pts[::-1]


def corners(pts, r):
    """For every corner: (point before, control point, point after) of a rounded corner."""
    out = []
    n = len(pts)
    for i in range(n):
        p0, p1, p2 = pts[i - 1], pts[i], pts[(i + 1) % n]

        def unit(a, b):
            dx, dy = b[0] - a[0], b[1] - a[1]
            d = math.hypot(dx, dy)
            return dx / d, dy / d, d

        ux, uy, d1 = unit(p1, p0)
        vx, vy, d2 = unit(p1, p2)
        rr = min(r, d1 / 2, d2 / 2)
        out.append(((p1[0] + ux * rr, p1[1] + uy * rr), p1, (p1[0] + vx * rr, p1[1] + vy * rr)))
    return out


def sample(pts, r, n=10):
    poly = []
    for a, c, b in corners(pts, r):
        for k in range(n + 1):
            t = k / n
            poly.append(((1 - t) ** 2 * a[0] + 2 * (1 - t) * t * c[0] + t * t * b[0],
                         (1 - t) ** 2 * a[1] + 2 * (1 - t) * t * c[1] + t * t * b[1]))
    return poly


def f(p):
    return "%.2f,%.2f" % p


def pathdata(pts, r):
    parts = []
    for i, (a, c, b) in enumerate(corners(pts, r)):
        parts.append(("M%s" if i == 0 else "L%s") % f(a))
        parts.append("Q%s %s" % (f(c), f(b)))
    parts.append("Z")
    return " ".join(parts)


# --- build final, centred geometry -----------------------------------------------------------
shapes = {
    "V": (lean(V_RAW), 2.0),
    "STEM": (lean(STEM_RAW), 1.6),
    "RING_OUT": (lean(RING_OUT_RAW), 4.0),
    "RING_IN": (lean(RING_IN_RAW), 1.5),
}
allpts = [p for k, (pts, r) in shapes.items() if k != "RING_IN" for p in sample(pts, r)]
minx, maxx = min(p[0] for p in allpts), max(p[0] for p in allpts)
miny, maxy = min(p[1] for p in allpts), max(p[1] for p in allpts)
ocx, ocy = (minx + maxx) / 2, (miny + maxy) / 2


def place(pts):
    return [((x - ocx) * SCALE + 54, (y - ocy) * SCALE + 54) for x, y in pts]


G = {k: (orient(place(pts), k != "RING_IN"), r * SCALE) for k, (pts, r) in shapes.items()}
MARK_W = (maxx - minx) * SCALE
MARK_H = (maxy - miny) * SCALE

P_V = pathdata(*G["V"])
P_STEM = pathdata(*G["STEM"])
# the inner contour is wound the other way, so it is a real hole under the non-zero fill rule
P_RING = pathdata(*G["RING_OUT"]) + " " + pathdata(*G["RING_IN"])
P_ALL = P_V + " " + P_STEM + " " + P_RING


def vector(body):
    return ('<?xml version="1.0" encoding="utf-8"?>\n<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    android:width="108dp"\n    android:height="108dp"\n    android:viewportWidth="108"\n'
            '    android:viewportHeight="108">\n%s</vector>\n' % body)


def path(data, fill, extra=""):
    return '    <path\n        android:fillColor="%s"%s\n        android:pathData="%s" />\n' % (fill, extra, data)


def write(p, s):
    os.makedirs(os.path.dirname(p), exist_ok=True)
    open(p, "w").write(s)


def circle(r, cx=54.0, cy=54.0):
    return "M%.2f,%.2f a%.2f,%.2f 0 1,0 %.2f,0 a%.2f,%.2f 0 1,0 %.2f,0 z" % (cx - r, cy, r, r, 2 * r, r, r, -2 * r)


write(RES + "/drawable/ic_launcher_background.xml", vector(
    path("M0,0h108v108h-108z", BG) + path(circle(40), ORANGE, '\n        android:fillAlpha="0.12"')))
write(RES + "/drawable/ic_launcher_foreground.xml", vector(
    path(P_V, WHITE) + path(P_STEM, ORANGE) + path(P_RING, ORANGE)))
write(RES + "/drawable/ic_launcher_monochrome.xml", vector(path(P_ALL, "#000000")))
adaptive = ('<?xml version="1.0" encoding="utf-8"?>\n<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
            '    <background android:drawable="@drawable/ic_launcher_background" />\n'
            '    <foreground android:drawable="@drawable/ic_launcher_foreground" />\n'
            '    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />\n</adaptive-icon>\n')
write(RES + "/mipmap-anydpi-v26/ic_launcher.xml", adaptive)
write(RES + "/mipmap-anydpi-v26/ic_launcher_round.xml", adaptive)

write(KT, '''package com.ky7ra.cpkeditor.ui

/**
 * The Versixty "V6" logo. GENERATED by tools/gen_logo.py - edit the script, not this file.
 * All paths share one 108 x 108 viewport (the launcher icon's), centred on (CX, CY); the mark
 * itself is WIDTH x HEIGHT units. RING has a real hole (inner contour wound the other way).
 */
object VersixtyArt {
    const val VIEWPORT = 108f
    const val CX = 54f
    const val CY = 54f
    const val WIDTH = %.2ff
    const val HEIGHT = %.2ff
    const val V = "%s"
    const val STEM = "%s"
    const val RING = "%s"
}
''' % (MARK_W, MARK_H, P_V, P_STEM, P_RING))


# --- raster: legacy PNG icons + preview ------------------------------------------------------
def hexrgb(h):
    return tuple(int(h[i:i + 2], 16) for i in (1, 3, 5))


def render(size, shape):
    s = 4
    N = size * s
    k = N / 108
    mask = Image.new("L", (N, N), 0)
    md = ImageDraw.Draw(mask)
    if shape == "round":
        md.ellipse((0, 0, N - 1, N - 1), fill=255)
    else:
        md.rounded_rectangle((0, 0, N - 1, N - 1), radius=int(N * 0.22), fill=255)
    bg = Image.new("RGBA", (N, N), hexrgb(BG) + (255,))
    ov = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    ImageDraw.Draw(ov).ellipse(((54 - 40) * k, (54 - 40) * k, (54 + 40) * k, (54 + 40) * k),
                               fill=hexrgb(ORANGE) + (31,))
    bg = Image.alpha_composite(bg, ov)
    img = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    img.paste(bg, (0, 0), mask)
    d = ImageDraw.Draw(img)

    def sc(pts):
        return [(x * k, y * k) for x, y in pts]

    d.polygon(sc(sample(*G["V"])), fill=hexrgb(WHITE))
    d.polygon(sc(sample(*G["STEM"])), fill=hexrgb(ORANGE))
    d.polygon(sc(sample(*G["RING_OUT"])), fill=hexrgb(ORANGE))
    d.polygon(sc(sample(*G["RING_IN"])), fill=hexrgb(BG))
    return img.resize((size, size), Image.LANCZOS)


for folder, px in (("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)):
    os.makedirs(RES + "/mipmap-" + folder, exist_ok=True)
    render(px, "square").save(RES + "/mipmap-%s/ic_launcher.png" % folder)
    render(px, "round").save(RES + "/mipmap-%s/ic_launcher_round.png" % folder)
if len(sys.argv) > 1:
    render(512, "square").save(sys.argv[1])
print("ok  mark %.1f x %.1f" % (MARK_W, MARK_H))
