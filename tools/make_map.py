#!/usr/bin/env python3
"""Fond de carte de l'onglet Radar : pays Natural Earth (domaine public), villes GeoNames (CC BY 4.0).

Usage : python3 tools/make_map.py DOSSIER_GEOJSON
Le dossier doit contenir (https://github.com/nvkelso/natural-earth-vector, dossier geojson) :
  ne_10m_admin_0_countries.geojson, ne_50m_admin_0_countries.geojson, ne_10m_populated_places_simple.geojson
et, pour les villes, cities.pbf du paquet npm all-the-cities (sinon cities15000.txt / cities5000.txt de
GeoNames, sinon les villes Natural Earth).
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


def rank_of(pop):
    """Importance d'une ville selon sa population : 0 (≥ 5 M) à 9 (≥ 5 000)."""
    for rank, limit in enumerate((5000000, 2000000, 1000000, 500000, 200000, 100000, 50000, 30000, 15000)):
        if pop >= limit:
            return rank
    return 9


def varint(b, i):
    v = s = 0
    while True:
        c = b[i]
        i += 1
        v |= (c & 0x7F) << s
        s += 7
        if c < 0x80:
            return v, i


def all_the_cities(path):
    """cities.pbf du paquet npm « all-the-cities » (villes GeoNames de plus de 1 000 habitants) :
    renvoie (id, nom, code de type, population, lat, lon)."""
    b = open(path, "rb").read()
    i, lat, lon, out = 0, 0, 0, []
    while i < len(b):
        n, i = varint(b, i)
        end = i + n
        c = {"id": "", "name": "", "code": "", "pop": 0}
        while i < end:
            key, i = varint(b, i)
            tag, wire = key >> 3, key & 7
            if wire == 2:
                ln, i = varint(b, i)
                val = b[i:i + ln].decode("utf-8", "replace")
                i += ln
                if tag == 2:
                    c["name"] = val
                elif tag == 7:
                    c["code"] = val
            else:
                v, i = varint(b, i)
                sv = (v >> 1) ^ -(v & 1)
                if tag == 1:
                    c["id"] = str(sv)
                elif tag == 9:
                    c["pop"] = v
                elif tag == 10:
                    lon += sv
                elif tag == 11:
                    lat += sv
        out.append((c["id"], c["name"], c["code"], c["pop"], lat / 1e5, lon / 1e5))
    return out


def geonames(src):
    """Villes GeoNames (CC BY 4.0). Avec cities.pbf (paquet npm all-the-cities) : plus de 5 000 habitants
    en Europe, plus de 15 000 ailleurs. Sinon cities15000.txt pour le monde et cities5000.txt en Europe."""
    rows, seen = [], set()
    pbf = os.path.join(src, "cities.pbf")
    if os.path.exists(pbf):
        for cid, name, code, pop, lat, lon in all_the_cities(pbf):
            europe = EUROPE[0] <= lon <= EUROPE[2] and EUROPE[1] <= lat <= EUROPE[3]
            if pop < (5000 if europe else 15000) or code in ("PPLX", "PPLH", "PPLQ", "PPLW") or not name:
                continue
            rows.append((rank_of(pop), pop, name.replace("\t", " ").strip(), lat, lon))
        return rows
    for fname, europe_only in (("cities5000.txt", True), ("cities15000.txt", False)):
        path = os.path.join(src, fname)
        if not os.path.exists(path):
            continue
        for line in open(path, encoding="utf-8"):
            f = line.rstrip("\n").split("\t")
            if len(f) < 15 or f[0] in seen:
                continue
            lat, lon, pop = float(f[4]), float(f[5]), int(f[14] or 0)
            if europe_only and not (EUROPE[0] <= lon <= EUROPE[2] and EUROPE[1] <= lat <= EUROPE[3]):
                continue
            if f[7] in ("PPLX", "PPLH", "PPLQ", "PPLW"):  # quartiers, lieux disparus
                continue
            seen.add(f[0])
            rows.append((rank_of(pop), pop, f[1].replace("\t", " ").strip(), lat, lon))
    return rows


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

    rows = geonames(src)
    if not rows:
        places = json.load(open(os.path.join(src, "ne_10m_populated_places_simple.geojson"), encoding="utf-8"))["features"]
        for p in places:
            q = p["properties"]
            lon, lat = p["geometry"]["coordinates"][:2]
            name = (q.get("name") or q.get("nameascii") or "").replace("\t", " ").strip()
            if not name:
                continue
            rank = q.get("scalerank")
            rows.append((10 if rank is None else int(rank), int(q.get("pop_max") or 0), name, lat, lon))
    rows.sort(key=lambda r: (r[0], -r[1]))
    with open(os.path.join(root, "cities.tsv"), "w", encoding="utf-8") as f:
        for rank, pop, name, lat, lon in rows:
            f.write("%s\t%.4f\t%.4f\t%d\n" % (name, lat, lon, rank))
    print(len(chosen), "anneaux,", len(rows), "villes")


if __name__ == "__main__":
    main(sys.argv[1])
