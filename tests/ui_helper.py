#!/usr/bin/env python3
"""Segédprogram a fekete dobozos UI-tesztekhez.

A uiautomator dump XML-jét (stdin) elemzi, és parancssori alparancsokkal
szolgálja ki a bash tesztfuttatót:

  sq <mező>      – a mező koppintási koordinátái: "x y" (pl. sq e4)
  allsq          – mind a 64 mező: "e4 x y" soronként
  piece <mező>   – a mezőn álló bábu Unicode-karaktere, vagy "." ha üres
  board          – minden foglalt mező "e4=♙" formában, soronként
  find <regex> [ymin ymax] – az első illeszkedő szöveg középpontja: "x y"
                 (kilépés 1, ha nincs; opcionálisan y-tartományra szűkítve)
  texts          – minden szöveges csomópont: "szöveg<TAB>cx<TAB>cy"
  orient         – "white" ha az 1. sor van alul, "black" ha fent

A tábla geometriáját a koordinátafeliratokból (a–h, 1–8) számolja ki,
így a tábla forgatása után is helyes.
"""
import re
import sys


def parse_nodes(xml):
    nodes = []
    for m in re.finditer(r"<node[^>]*", xml):
        n = m.group(0)
        tm = re.search(r'text="([^"]*)"', n)
        bm = re.search(r'bounds="\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]"', n)
        if not tm or not bm:
            continue
        t = tm.group(1)
        if not t:
            continue
        x1, y1, x2, y2 = map(int, bm.groups())
        nodes.append((t, (x1 + x2) // 2, (y1 + y2) // 2))
    return nodes


def parse_bounds(xml):
    """Minden csomópont határai (szöveg nélküliek is)."""
    out = []
    for m in re.finditer(r"<node[^>]*", xml):
        bm = re.search(r'bounds="\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]"', m.group(0))
        if bm:
            out.append(tuple(map(int, bm.groups())))
    return out


PIECES = set("♔♕♖♗♘♙♚♛♜♝♞♟")


def board_geometry(nodes):
    """A koordinátafeliratokból megadja az oszlopok/sorok középpontjait."""
    files = {}
    ranks = {}
    for t, cx, cy in nodes:
        if t in "abcdefgh" and len(t) == 1:
            files[t] = cx
        elif t in "12345678" and len(t) == 1:
            # A sorfeliratok a tábla bal szélén állnak (kis x); a lépéslista
            # számai nem egykarakteresek, így nem zavarnak.
            ranks[t] = cy
    if len(files) != 8 or len(ranks) != 8:
        raise SystemExit("HIBA: nem talalhato mind a 8 fajl/sor felirat "
                         f"(files={len(files)}, ranks={len(ranks)})")
    return files, ranks


def square_center(files, ranks, sq):
    f, r = sq[0], sq[1]
    return files[f], ranks[r]


def nearest(d, v):
    return min(d.items(), key=lambda kv: abs(kv[1] - v))[0]


def main():
    xml = sys.stdin.read()
    nodes = parse_nodes(xml)
    cmd = sys.argv[1]

    if cmd == "texts":
        for t, cx, cy in nodes:
            print(f"{t}\t{cx}\t{cy}")
        return

    if cmd == "find":
        pat = re.compile(sys.argv[2])
        ymin = int(sys.argv[3]) if len(sys.argv) > 3 else -1
        ymax = int(sys.argv[4]) if len(sys.argv) > 4 else 10 ** 9
        for t, cx, cy in nodes:
            if ymin <= cy <= ymax and pat.search(t):
                print(cx, cy)
                return
        sys.exit(1)

    files, ranks = board_geometry(nodes)

    if cmd == "sq":
        x, y = square_center(files, ranks, sys.argv[2])
        print(x, y)
        return

    if cmd == "allsq":
        for f in "abcdefgh":
            for r in "12345678":
                print(f + r, files[f], ranks[r])
        return

    if cmd == "sqrect":
        # A mezőt lefedő négyzet alakú View határai: "x1 y1 x2 y2"
        fx, fy = square_center(files, ranks, sys.argv[2])
        for x1, y1, x2, y2 in parse_bounds(xml):
            if (x1 <= fx <= x2 and y1 <= fy <= y2
                    and 100 <= x2 - x1 <= 250 and 100 <= y2 - y1 <= 250):
                print(x1, y1, x2, y2)
                return
        sys.exit(1)

    if cmd == "orient":
        print("white" if ranks["1"] > ranks["8"] else "black")
        return

    # Bábuk mezőhöz rendelése a középpontjuk alapján.
    board = {}
    half = abs(ranks["1"] - ranks["2"]) // 2 + 8
    for t, cx, cy in nodes:
        if t in PIECES:
            f = nearest(files, cx)
            r = nearest(ranks, cy)
            if abs(files[f] - cx) <= half and abs(ranks[r] - cy) <= half:
                board[f + r] = t

    if cmd == "piece":
        print(board.get(sys.argv[2], "."))
        return

    if cmd == "board":
        for sq in sorted(board):
            print(f"{sq}={board[sq]}")
        return

    raise SystemExit(f"ismeretlen parancs: {cmd}")


if __name__ == "__main__":
    main()
