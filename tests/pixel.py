#!/usr/bin/env python3
"""Nyers screencap kimenetből (adb exec-out screencap) átlagszínt számol.

Használat: pixel.py <fajl> <x1> <y1> <x2> <y2> [további négyszögek...]
Kimenet négyszögenként: "R G B" — a négyszög középső 60%-án vett átlag.
A fejléc 12 vagy 16 bájt (w, h, formátum[, színtér]), RGBA_8888 képet vár.
"""
import struct
import sys


def main():
    data = open(sys.argv[1], "rb").read()
    w, h, fmt = struct.unpack_from("<III", data, 0)
    if len(data) == w * h * 4 + 16:
        off = 16
    elif len(data) == w * h * 4 + 12:
        off = 12
    else:
        raise SystemExit(f"varatlan meret: {len(data)} (w={w}, h={h}, fmt={fmt})")
    args = list(map(int, sys.argv[2:]))
    for i in range(0, len(args), 4):
        x1, y1, x2, y2 = args[i:i + 4]
        # középső 60%
        mx, my = (x2 - x1) // 5, (y2 - y1) // 5
        x1, y1, x2, y2 = x1 + mx, y1 + my, x2 - mx, y2 - my
        rs = gs = bs = n = 0
        for y in range(y1, y2, 6):
            for x in range(x1, x2, 6):
                p = off + (y * w + x) * 4
                rs += data[p]; gs += data[p + 1]; bs += data[p + 2]
                n += 1
        print(rs // n, gs // n, bs // n)


if __name__ == "__main__":
    main()
