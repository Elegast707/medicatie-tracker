"""Genereert de pixelicoontjes en pixelsterren van de app als vector drawables.

Gebruik: python3 tools/generate_pixel_icons.py
Schrijft XML's naar app/src/main/res/drawable/. Elke pixel wordt een vierkantje, dus de icoontjes
blijven op elke schermgrootte scherp. Geen extra pakketten nodig.

- ic_px_*: eenkleurige icoontjes op een raster van 12x12; Compose kleurt ze in met `tint`.
- ic_star_* en ic_check_gold: veelkleurige sterren en het gouden vinkje op 20x20.
"""
import math
import os

OUT = os.path.join(os.path.dirname(__file__), '..', 'app', 'src', 'main', 'res', 'drawable')

# Eenkleurige icoontjes, 12x12. '#' is gevuld.
MONO = {
    'check': """
............
..........##
.........###
........###.
.##....###..
.###..###...
..######....
...####.....
....##......
............
............
............
""",
    'close': """
............
.##......##.
.###....###.
..###..###..
...######...
....####....
....####....
...######...
..###..###..
.###....###.
.##......##.
............
""",
    'warning': """
.....##.....
....####....
....####....
...##..##...
..###..###..
..###..###..
.####..####.
.##########.
.####..####.
############
############
............
""",
    'home': """
.....##.....
....####....
...######...
..########..
.##########.
############
.##......##.
.##......##.
.##.####.##.
.##.#..#.##.
.##.#..#.##.
.##########.
""",
    'calendar': """
..##....##..
############
############
#..........#
#.##.##.##.#
#.##.##.##.#
#..........#
#.##.##.##.#
#.##.##.##.#
#..........#
############
............
""",
    'menu': """
............
............
############
############
............
............
############
############
............
............
############
############
""",
    'add': """
............
.....##.....
.....##.....
.....##.....
.....##.....
.##########.
.##########.
.....##.....
.....##.....
.....##.....
.....##.....
............
""",
    'export': """
.....##.....
....####....
...######...
..##.##.##..
.....##.....
.....##.....
.....##.....
##...##...##
##........##
##........##
############
############
""",
}

# Kleurladders: omlijning, schaduw, basis, licht, glans.
STAR_RAMPS = {
    'bronze': ('#3B1E0A', '#8A4A1C', '#C77A3A', '#EBA66A', '#FFE2C2'),
    'silver': ('#2A3038', '#6E7884', '#A9B4C0', '#D8E0E8', '#FFFFFF'),
    'gold':   ('#3D2A00', '#A8740A', '#F0B429', '#FFD86B', '#FFF6D0'),
    'blue':   ('#0E1E4A', '#2A56B8', '#3F86E8', '#8CC4FF', '#E6F4FF'),
}
GOLD = STAR_RAMPS['gold']


def mono_xml(rows):
    rows = [r for r in rows.strip('\n').split('\n')]
    return vector(len(rows[0]), len(rows), {'#FFFFFFFF': [(x, y) for y, r in enumerate(rows) for x, c in enumerate(r) if c == '#']})


def path_for(pixels):
    """Voegt pixels per rij samen tot strookjes, zodat er geen naadjes tussen de pixels zichtbaar zijn."""
    out = []
    by_row = {}
    for x, y in pixels:
        by_row.setdefault(y, []).append(x)
    for y in sorted(by_row):
        xs = sorted(by_row[y])
        start = prev = xs[0]
        for x in xs[1:] + [None]:
            if x is not None and x == prev + 1:
                prev = x
                continue
            out.append(f'M{start},{y}h{prev - start + 1}v1h-{prev - start + 1}z')
            if x is not None:
                start = prev = x
    return ''.join(out)


def vector(w, h, layers, size=24):
    paths = '\n'.join(
        f'    <path android:fillColor="{color}" android:pathData="{path_for(px)}" />'
        for color, px in layers.items() if px
    )
    return f'''<?xml version="1.0" encoding="utf-8"?>
<!-- Gegenereerd door tools/generate_pixel_icons.py; niet met de hand aanpassen. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="{size}dp"
    android:height="{size}dp"
    android:viewportWidth="{w}"
    android:viewportHeight="{h}">
{paths}
</vector>
'''


def shaded(inside, ramp, n):
    """Omlijning op de rand, licht linksboven en schaduw rechtsonder, zoals GBA-sprites."""
    outline, shade, base, light, shine = ramp
    layers = {c: [] for c in ramp}
    cx = cy = (n - 1) / 2
    for (x, y) in inside:
        edge = any((x + dx, y + dy) not in inside for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        if edge:
            layers[outline].append((x, y))
            continue
        # Pixel net binnen de rand rechtsonder wordt schaduw, linksboven licht.
        if (x + 1, y + 1) not in inside or (x + 1, y) not in inside and (x, y + 1) not in inside:
            layers[shade].append((x, y))
        elif (x - 1, y - 1) not in inside:
            layers[light].append((x, y))
        else:
            d = (x - cx) * 0.6 + (y - cy) * 0.8
            layers[shade if d > 3.2 else light if d < -2.6 else base].append((x, y))
    return layers


def star(ramp, n=20):
    cx, cy, ro, ri = (n - 1) / 2, n / 2, n / 2 - 0.2, n / 2 * 0.42
    pts = []
    for i in range(10):
        a = -math.pi / 2 + i * math.pi / 5
        r = ro if i % 2 == 0 else ri
        pts.append((cx + r * math.cos(a), cy + r * math.sin(a)))

    def inside_poly(px, py):
        hit = False
        for i in range(len(pts)):
            (x1, y1), (x2, y2) = pts[i], pts[(i + 1) % len(pts)]
            if (y1 > py) != (y2 > py) and px < (x2 - x1) * (py - y1) / (y2 - y1) + x1:
                hit = not hit
        return hit

    inside = {(x, y) for y in range(n) for x in range(n) if inside_poly(x + 0.5, y + 0.5)}
    # Losse puntjes aan de punten weghalen, anders worden het dunne naalden.
    for _ in range(2):
        inside = {(x, y) for (x, y) in inside
                  if sum((x + dx, y + dy) in inside for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))) >= 2}
    layers = shaded(inside, ramp, n)
    # Twee glanspixels linksboven in het midden.
    for p in ((int(cx) - 1, int(cy) - 2), (int(cx) - 2, int(cy) - 1)):
        if p in inside:
            for c in layers.values():
                if p in c:
                    c.remove(p)
            layers[ramp[4]].append(p)
    return vector(n, n, layers, size=48)


def check_coin(n=20):
    r = n / 2 - 0.3
    c = (n - 1) / 2
    inside = {(x, y) for y in range(n) for x in range(n) if (x - c) ** 2 + (y - c) ** 2 <= r * r}
    layers = shaded(inside, GOLD, n)
    stroke = [(4 + i, 9 + i) for i in range(5)] + [(9 + i, 12 - i) for i in range(7)]
    tick = [(x, y + dy) for x, y in stroke for dy in (0, -1, -2)]
    for p in tick:
        for col in layers.values():
            if p in col:
                col.remove(p)
    shadow = [(x + 1, y + 1) for x, y in tick if (x + 1, y + 1) not in tick]
    for p in shadow:
        for col in layers.values():
            if p in col:
                col.remove(p)
    layers['#FFFFFF'] = tick
    layers[GOLD[0]] = layers[GOLD[0]] + shadow
    return vector(n, n, layers, size=48)


def write(name, xml):
    with open(os.path.join(OUT, name + '.xml'), 'w') as f:
        f.write(xml)


if __name__ == '__main__':
    for name, rows in MONO.items():
        write('ic_px_' + name, mono_xml(rows))
    for name, ramp in STAR_RAMPS.items():
        write('ic_star_' + name, star(ramp))
    write('ic_check_gold', check_coin())
    print('Klaar:', len(MONO) + len(STAR_RAMPS) + 1, 'icoontjes')
