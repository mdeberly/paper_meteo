#!/usr/bin/env python3
"""Fond de carte de l'onglet Radar, à partir de Natural Earth (domaine public).

Usage : python3 tools/make_map.py DOSSIER_GEOJSON
Le dossier doit contenir (https://github.com/nvkelso/natural-earth-vector, dossier geojson) :
  ne_10m_admin_0_countries.geojson, ne_50m_admin_0_countries.geojson, ne_10m_populated_places_simple.geojson
Produit app/src/main/assets/map/borders.bin et cities.tsv.

borders.bin (gros-boutiste) : int nombre d'anneaux, puis pour chaque anneau : int n, int lat0, int lon0
(en 1e-4 degré), puis n-1 couples de short (écart de lat, écart de lon, en 1e-4 degré).
Europe au 1:10 M, reste du monde au 1:50 M, simplifiés (Douglas-Peucker).
"""
import json, math, os, struct, sys

EUROPE = (-32.0, 27.0, 45.0, 72.0)  # lon min, lat min, lon max, lat max


def rings(path):
    out = []
    for f in json.load(open(path, encoding="utf-8"))["features"]:
        g = f["geometry"]
        polys = g["coordinates"] if g["type"] == "MultiPolygon" else [g["coordinates"]]
        for p in polys:
            out.extend(p)
    return out


def simplify(pts, tol):
    if len(pts) < 3:
        return pts
    keep = [False] * len(pts)
    keep[0] = keep[-1] = True
    stack = [(0, len(pts) - 1)]
    while stack:
        a, b = stack.pop()
        ax, ay = pts[a]
        bx, by = pts[b]
        dx, dy = bx - ax, by - ay
        length = math.hypot(dx, dy)
        best, dist = -1, 0.0
        for i in range(a + 1, b):
            px, py = pts[i]
            d = abs(dy * (px - ax) - dx * (py - ay)) / length if length > 0 else math.hypot(px - ax, py - ay)
            if d > dist:
                best, dist = i, d
        if dist > tol:
            keep[best] = True
            stack += [(a, best), (best, b)]
    return [p for p, k in zip(pts, keep) if k]


def in_europe(r):
    xs = [p[0] for p in r]
    ys = [p[1] for p in r]
    return max(xs) >= EUROPE[0] and min(xs) <= EUROPE[2] and max(ys) >= EUROPE[1] and min(ys) <= EUROPE[3]


def encode(ring):
    pts = [(round(lat * 1e4), round(lon * 1e4)) for lon, lat in ring]
    # Écarts trop grands pour un short : points intermédiaires.
    out = [pts[0]]
    for la, lo in pts[1:]:
        pla, plo = out[-1]
        steps = max(1, math.ceil(max(abs(la - pla), abs(lo - plo)) / 32000))
        for s in range(1, steps + 1):
            out.append((pla + round((la - pla) * s / steps), plo + round((lo - plo) * s / steps)))
    data = struct.pack(">iii", len(out), out[0][0], out[0][1])
    for (a, b), (c, d) in zip(out, out[1:]):
        data += struct.pack(">hh", c - a, d - b)
    return data


def main(src):
    root = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app", "src", "main", "assets", "map")
    os.makedirs(root, exist_ok=True)
    chosen = [simplify(r, 0.01) for r in rings(os.path.join(src, "ne_10m_admin_0_countries.geojson")) if in_europe(r)]
    chosen += [simplify(r, 0.02) for r in rings(os.path.join(src, "ne_50m_admin_0_countries.geojson")) if not in_europe(r)]
    chosen = [r for r in chosen if len(r) >= 3]
    with open(os.path.join(root, "borders.bin"), "wb") as f:
        f.write(struct.pack(">i", len(chosen)))
        for r in chosen:
            f.write(encode(r))

    places = json.load(open(os.path.join(src, "ne_10m_populated_places_simple.geojson"), encoding="utf-8"))["features"]
    rows = []
    for p in places:
        q = p["properties"]
        lon, lat = p["geometry"]["coordinates"][:2]
        name = (q.get("name") or q.get("nameascii") or "").replace("\t", " ").strip()
        if not name:
            continue
        rows.append((int(q.get("scalerank") or 10), int(q.get("pop_max") or 0), name, lat, lon))
    rows.sort(key=lambda r: (r[0], -r[1]))
    with open(os.path.join(root, "cities.tsv"), "w", encoding="utf-8") as f:
        for rank, pop, name, lat, lon in rows:
            f.write("%s\t%.4f\t%.4f\t%d\n" % (name, lat, lon, rank))
    print(len(chosen), "anneaux,", len(rows), "villes")


if __name__ == "__main__":
    main(sys.argv[1])
