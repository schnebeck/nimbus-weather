#!/usr/bin/env python3
#
# Nimbus - tools/dwd_reach_reference.py
# Writes the DWD radars' full reach the app starts from (DwdReach): read from a composite picture
# of a time all its radars measured, as DwdCoverage.compute reads its masks.
#
#   Copyright (C) 2026 Thorsten Schnebeck <thorsten.schnebeck@gmx.net>
#   Produced by Thorsten Schnebeck - the idea, the decisions, the testing.
#   Written by Anthropic Claude Opus 5.5 - AI generated content.
#
#   Free software under the GNU General Public License, version 3 or later.
#   There is no warranty, to the extent permitted by law. The full text is in
#   LICENSES/GPL-3.0-or-later.txt.
#
# SPDX-FileCopyrightText: (C) 2026 Thorsten Schnebeck <thorsten.schnebeck@gmx.net>
# SPDX-FileContributor: Anthropic Claude Opus 5.5 (AI generated content)
# SPDX-License-Identifier: GPL-3.0-or-later
#
# Usage: tools/dwd_reach_reference.py <time, e.g. 2026-11-12T12:00:00.000Z>   (needs Pillow)
# The picture: the DWD's GeoServer, layer Radar_wn-product_1x1km_ger over 1.4–18.8° E, 45.6–56.3° N,
# 696 × 428 pixels (0.025°). Covered: not the semi-transparent "no data" grey and not reachable
# from the picture's border without crossing it. One bit per cell, row by row from the north-west.

import io
import sys
import urllib.request

from PIL import Image

W, H = 696, 428
OUT = "app/src/main/resources/dev/nimbus/weather/ui/radar/dwd_reach_reference.bin"


def picture(time: str) -> Image.Image:
    url = ("https://maps.dwd.de/geoserver/dwd/wms?service=WMS&version=1.1.1&request=GetMap"
           "&layers=dwd:Radar_wn-product_1x1km_ger&styles=&format=image/png&transparent=true"
           f"&srs=EPSG:4326&bbox=1.4,45.6,18.8,56.3&width={W}&height={H}&time={time}")
    with urllib.request.urlopen(url, timeout=60) as r:
        return Image.open(io.BytesIO(r.read())).convert("RGBA")


def covered(im: Image.Image) -> list[bool]:
    px = list(im.getdata())
    grey = [1 <= p[3] <= 199 for p in px]
    outside = [False] * (W * H)
    stack = []

    def push(i: int) -> None:
        if not outside[i] and not grey[i]:
            outside[i] = True
            stack.append(i)

    for x in range(W):
        push(x)
        push((H - 1) * W + x)
    for y in range(H):
        push(y * W)
        push(y * W + W - 1)
    while stack:
        i = stack.pop()
        x, y = i % W, i // W
        if x > 0:
            push(i - 1)
        if x < W - 1:
            push(i + 1)
        if y > 0:
            push(i - W)
        if y < H - 1:
            push(i + W)
    return [not grey[i] and not outside[i] for i in range(W * H)]


def main() -> None:
    if len(sys.argv) != 2:
        sys.exit(__doc__ or "usage: tools/dwd_reach_reference.py <time>")
    cells = covered(picture(sys.argv[1]))
    bits = bytearray((len(cells) + 7) // 8)
    for i, c in enumerate(cells):
        if c:
            bits[i // 8] |= 1 << (i % 8)
    with open(OUT, "wb") as f:
        f.write(bits)
    print(f"{OUT}: {sum(cells)} cells covered")


if __name__ == "__main__":
    main()
