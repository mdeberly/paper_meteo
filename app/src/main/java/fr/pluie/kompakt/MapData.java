package fr.pluie.kompakt;

import android.content.Context;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Fond de carte de l'onglet Radar (Natural Earth, domaine public) : contours des pays et villes,
 * en coordonnées Mercator normalisées (0..1, comme les tuiles du radar). Chargé une seule fois.
 * Fichiers produits par tools/make_map.py.
 */
final class MapData {

    /** Un contour : x, y alternés, et sa boîte englobante. */
    static final class Ring {
        final float[] xy;
        final float minX, minY, maxX, maxY;

        Ring(float[] xy) {
            this.xy = xy;
            float a = Float.MAX_VALUE, b = Float.MAX_VALUE, c = -Float.MAX_VALUE, d = -Float.MAX_VALUE;
            for (int i = 0; i < xy.length; i += 2) {
                a = Math.min(a, xy[i]);
                c = Math.max(c, xy[i]);
                b = Math.min(b, xy[i + 1]);
                d = Math.max(d, xy[i + 1]);
            }
            minX = a; minY = b; maxX = c; maxY = d;
        }
    }

    static final class City {
        final String name;
        final double x, y;
        /** Importance Natural Earth : 0 (capitales, mégapoles) à 10 (petites villes). */
        final int rank;

        City(String name, double x, double y, int rank) {
            this.name = name; this.x = x; this.y = y; this.rank = rank;
        }
    }

    final List<Ring> rings;
    /** Triées de la plus importante à la moins importante. */
    final List<City> cities;

    private static MapData instance;

    private MapData(List<Ring> rings, List<City> cities) {
        this.rings = rings;
        this.cities = cities;
    }

    /** À appeler hors du fil principal. */
    static synchronized MapData get(Context c) throws Exception {
        if (instance == null) instance = load(c.getAssets().open("map/borders.bin"), c.getAssets().open("map/cities.tsv"));
        return instance;
    }

    static MapData load(InputStream borders, InputStream cities) throws Exception {
        return new MapData(loadRings(borders), loadCities(cities));
    }

    static double x(double lon) {
        return (lon + 180) / 360;
    }

    static double y(double lat) {
        double r = Math.toRadians(Math.max(-85, Math.min(85, lat)));
        return (1 - Math.log(Math.tan(r) + 1 / Math.cos(r)) / Math.PI) / 2;
    }

    static double lon(double x) {
        return x * 360 - 180;
    }

    static double lat(double y) {
        return Math.toDegrees(Math.atan(Math.sinh(Math.PI * (1 - 2 * y))));
    }

    private static List<Ring> loadRings(InputStream src) throws Exception {
        List<Ring> out = new ArrayList<>();
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(src, 1 << 16))) {
            int count = in.readInt();
            for (int r = 0; r < count; r++) {
                int n = in.readInt();
                int lat = in.readInt(), lon = in.readInt();
                float[] xy = new float[2 * n];
                for (int i = 0; i < n; i++) {
                    if (i > 0) {
                        lat += in.readShort();
                        lon += in.readShort();
                    }
                    xy[2 * i] = (float) x(lon / 1e4);
                    xy[2 * i + 1] = (float) y(lat / 1e4);
                }
                out.add(new Ring(xy));
            }
        }
        return out;
    }

    private static List<City> loadCities(InputStream src) throws Exception {
        List<City> out = new ArrayList<>();
        try (BufferedReader in = new BufferedReader(new InputStreamReader(src, StandardCharsets.UTF_8))) {
            String line;
            while ((line = in.readLine()) != null) {
                String[] f = line.split("\t");
                if (f.length < 4) continue;
                out.add(new City(f[0], x(Double.parseDouble(f[2])), y(Double.parseDouble(f[1])), Integer.parseInt(f[3])));
            }
        }
        return out;
    }
}
