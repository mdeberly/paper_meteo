package fr.pluie.kompakt;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Images de l'onglet Radar.
 * Passé (2 h, une image toutes les 20 min) : radar RainViewer, une image de 512 px centrée sur le lieu.
 * Futur (3 h, toutes les 30 min) : précipitations prévues par Open-Meteo sur une grille de points
 * autour du lieu, interpolées. Les deux sont ramenés aux mêmes 4 niveaux d'intensité.
 * Tout est en Mercator normalisé (0..1), comme {@link MapData}.
 */
final class RadarData {
    private RadarData() {}

    /** Une image : couvre le carré de centre (cx, cy) et de demi-côté half. */
    static final class Frame {
        final long time;          // secondes Unix
        final boolean forecast;
        final Bitmap image;
        final double cx, cy, half;
        /** Points (Mercator normalisé) où il neige : x, y alternés. */
        double[] flakes = new double[0];

        Frame(long time, boolean forecast, Bitmap image, double cx, double cy, double half) {
            this.time = time; this.forecast = forecast; this.image = image;
            this.cx = cx; this.cy = cy; this.half = half;
        }
    }

    /** Grille du modèle : pluie et neige (mm d'eau par quart d'heure) à chaque point et chaque instant. */
    private static final class Grid {
        double cx, cy, half;
        final double[] lats = new double[GRID * GRID], lons = new double[GRID * GRID];
        final List<Map<Long, double[]>> series = new ArrayList<>(); // instant → {précipitations mm, neige cm}

        /** Taux en mm/h (deux quarts d'heure finissant à t) et part de neige, au point k. */
        double rate(int k, long t) {
            double[] a = series.get(k).get(t), b = series.get(k).get(t - 900);
            return 2 * ((a == null ? 0 : a[0]) + (b == null ? 0 : b[0]));
        }

        boolean snow(int k, long t) {
            double[] a = series.get(k).get(t), b = series.get(k).get(t - 900);
            double p = (a == null ? 0 : a[0]) + (b == null ? 0 : b[0]);
            double sn = (a == null ? 0 : a[1]) + (b == null ? 0 : b[1]);
            return p > 0 && sn * 7 >= 0.5 * p; // 1 cm de neige ≈ 0,7 mm d'eau
        }

        boolean has(long t) {
            return series.get(0).containsKey(t);
        }

        double x(int k) { return cx - half + 2 * half * (k % GRID) / (GRID - 1); }
        double y(int k) { return cy - half + 2 * half * (k / GRID) / (GRID - 1); }
    }

    static final class Result {
        final List<Frame> frames = new ArrayList<>();
        boolean radarFailed, forecastFailed;
    }

    /** Zoom maximal des images RainViewer gratuites ; au-delà, on agrandit l'image du zoom 7. */
    static final int MAX_RADAR_ZOOM = 7;
    private static final int GRID = 9;

    /**
     * Couleurs des niveaux 1 à 6, de la bruine à l'averse violente. Couleur : palette habituelle des radars
     * (vert, jaune, orange, rouge, magenta), neige en bleus. Noir et blanc : gris de plus en plus foncés.
     */
    static final int[] RAIN_COLORS = Ui.COLOR
            ? new int[]{0, 0xB0A6E39A, 0xD04CBB4C, 0xE8F2D61E, 0xF0F28C1E, 0xF8DC2A22, 0xFFB42CB4}
            : new int[]{0, 0x38000000, 0x60000000, 0x88000000, 0xB0000000, 0xD8000000, 0xFF000000};
    static final int[] SNOW_COLORS = Ui.COLOR
            ? new int[]{0, 0xB0C4E4FA, 0xD08EC3F2, 0xE05E9BE6, 0xE8406FD6, 0xF04A44B8, 0xFF6A2AA0}
            : RAIN_COLORS;

    static int levelOfAlpha(int a) {
        return a < 12 ? 0 : a < 40 ? 1 : a < 80 ? 2 : a < 120 ? 3 : a < 165 ? 4 : a < 215 ? 5 : 6;
    }

    static int levelOfRate(double mmPerHour) {
        return mmPerHour < 0.1 ? 0 : mmPerHour < 0.5 ? 1 : mmPerHour < 2 ? 2 : mmPerHour < 5 ? 3
                : mmPerHour < 10 ? 4 : mmPerHour < 30 ? 5 : 6;
    }

    /** Charge tout (à appeler hors du fil principal). zoom : zoom d'affichage. */
    static Result load(double lat, double lon, int zoom) {
        Result r = new Result();
        double cx = MapData.x(lon), cy = MapData.y(lat);
        Grid g = null;
        try {
            g = grid(cx, cy, zoom);
            forecast(r, g);
        } catch (Exception e) {
            r.forecastFailed = true;
        }
        try {
            radar(r, g, lat, lon, cx, cy, Math.min(zoom, MAX_RADAR_ZOOM));
        } catch (Exception e) {
            r.radarFailed = true;
        }
        return r;
    }

    private static void radar(Result r, Grid g, double lat, double lon, double cx, double cy, int z) throws Exception {
        JSONObject meta = new JSONObject(Http.get("https://api.rainviewer.com/public/weather-maps.json"));
        String host = meta.getString("host");
        JSONObject radar = meta.getJSONObject("radar");
        JSONArray past = radar.optJSONArray("past");
        if (past == null || past.length() == 0) throw new Exception("pas d'images");
        // Une image sur deux (20 min), en partant de la plus récente, sur 2 h.
        List<JSONObject> picks = new ArrayList<>();
        long last = past.getJSONObject(past.length() - 1).getLong("time");
        for (int i = past.length() - 1; i >= 0; i -= 2) {
            JSONObject o = past.getJSONObject(i);
            if (last - o.getLong("time") > 2 * 3600) break;
            picks.add(0, o);
        }
        JSONArray now = radar.optJSONArray("nowcast");
        if (now != null) for (int i = 0; i < now.length(); i++) picks.add(now.getJSONObject(i));
        double half = 1.0 / (1 << z); // 512 px sur un monde de 256·2^z px
        int ok = 0;
        for (JSONObject o : picks) {
            String url = String.format(Locale.ROOT, "%s%s/512/%d/%.4f/%.4f/2/1_1.png",
                    host, o.getString("path"), z, lat, lon);
            try {
                byte[] png = Http.bytes(url);
                Bitmap src = BitmapFactory.decodeByteArray(png, 0, png.length);
                if (src == null) continue;
                long t = o.getLong("time");
                // Neige d'après le modèle, au quart d'heure le plus proche.
                long q = Math.round(t / 900.0) * 900;
                boolean[] snow = new boolean[GRID * GRID];
                if (g != null && g.has(q)) for (int k = 0; k < snow.length; k++) snow[k] = g.snow(k, q);
                Frame f = new Frame(t, t > last, levels(src, g, snow, cx, cy, half), cx, cy, half);
                f.flakes = flakesOnImage(f, g, snow);
                r.frames.add(f);
                src.recycle();
                ok++;
            } catch (Exception ignored) {
            }
        }
        if (ok == 0) throw new Exception("images illisibles");
    }

    /** Image RainViewer → mêmes niveaux que la prévision (l'intensité est dans l'alpha), neige en bleus. */
    private static Bitmap levels(Bitmap src, Grid g, boolean[] snow, double cx, double cy, double half) {
        int w = src.getWidth(), h = src.getHeight();
        int[] px = new int[w * h];
        src.getPixels(px, 0, w, 0, 0, w, h);
        for (int y = 0; y < h; y++) {
            int row = -1;
            if (g != null) row = nearest(g, cy - half + 2 * half * (y + 0.5) / h, false);
            for (int x = 0; x < w; x++) {
                int i = y * w + x, lv = levelOfAlpha(px[i] >>> 24);
                boolean sn = false;
                if (lv > 0 && g != null && row >= 0) {
                    int col = nearest(g, cx - half + 2 * half * (x + 0.5) / w, true);
                    sn = col >= 0 && snow[row * GRID + col];
                }
                px[i] = (sn ? SNOW_COLORS : RAIN_COLORS)[lv];
            }
        }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888);
    }

    /** Colonne (horizontal) ou ligne de la grille la plus proche de la coordonnée v, ou -1 hors grille. */
    private static int nearest(Grid g, double v, boolean horizontal) {
        double c = horizontal ? g.cx : g.cy;
        double f = (v - (c - g.half)) / (2 * g.half) * (GRID - 1);
        int k = (int) Math.round(f);
        return k < 0 || k >= GRID ? -1 : k;
    }

    /** Flocons aux points de grille où il neige et où le radar voit des précipitations. */
    private static double[] flakesOnImage(Frame f, Grid g, boolean[] snow) {
        if (g == null) return new double[0];
        List<Double> out = new ArrayList<>();
        int w = f.image.getWidth(), h = f.image.getHeight();
        for (int k = 0; k < snow.length; k++) {
            if (!snow[k]) continue;
            int x = (int) ((g.x(k) - (f.cx - f.half)) / (2 * f.half) * w);
            int y = (int) ((g.y(k) - (f.cy - f.half)) / (2 * f.half) * h);
            if (x < 0 || y < 0 || x >= w || y >= h || f.image.getPixel(x, y) == 0) continue;
            out.add(g.x(k));
            out.add(g.y(k));
        }
        double[] a = new double[out.size()];
        for (int i = 0; i < a.length; i++) a[i] = out.get(i);
        return a;
    }

    /** Pluie et neige prévues par Open-Meteo sur une grille autour du lieu, de 2 h avant à 3 h après. */
    private static Grid grid(double cx, double cy, int zoom) throws Exception {
        Grid g = new Grid();
        g.cx = cx;
        g.cy = cy;
        g.half = 1.25 / (1 << zoom); // un peu plus large que la carte affichée (±1/2^zoom)
        StringBuilder la = new StringBuilder(), lo = new StringBuilder();
        for (int k = 0; k < GRID * GRID; k++) {
            g.lons[k] = MapData.lon(g.x(k));
            g.lats[k] = MapData.lat(g.y(k));
            if (k > 0) { la.append(','); lo.append(','); }
            la.append(String.format(Locale.ROOT, "%.3f", g.lats[k]));
            lo.append(String.format(Locale.ROOT, "%.3f", g.lons[k]));
        }
        String url = "https://api.open-meteo.com/v1/forecast?latitude=" + la + "&longitude=" + lo
                + "&minutely_15=precipitation,snowfall&timezone=GMT&timeformat=unixtime";
        String body;
        try {
            body = Http.get(url + "&past_minutely_15=12&forecast_minutely_15=16");
        } catch (Exception e) {
            body = Http.get(url + "&past_days=1&forecast_days=2");
        }
        JSONArray all = body.trim().startsWith("[") ? new JSONArray(body) : new JSONArray().put(new JSONObject(body));
        if (all.length() != GRID * GRID) throw new Exception("grille incomplète");
        for (int k = 0; k < all.length(); k++) {
            JSONObject m = all.getJSONObject(k).getJSONObject("minutely_15");
            JSONArray t = m.getJSONArray("time"), p = m.getJSONArray("precipitation");
            JSONArray sn = m.optJSONArray("snowfall");
            Map<Long, double[]> s = new HashMap<>();
            for (int i = 0; i < t.length(); i++) {
                if (p.isNull(i)) continue;
                double snow = sn == null || sn.isNull(i) ? 0 : sn.getDouble(i);
                s.put(t.getLong(i), new double[]{p.getDouble(i), snow});
            }
            g.series.add(s);
        }
        return g;
    }

    private static void forecast(Result r, Grid g) {
        long now = System.currentTimeMillis() / 1000;
        long base = now / 1800 * 1800;
        for (int step = 1; step <= 6; step++) {
            long t = base + 1800L * step;
            if (!g.has(t)) continue;
            double[] rate = new double[GRID * GRID];
            boolean[] snow = new boolean[GRID * GRID];
            List<Double> flakes = new ArrayList<>();
            for (int k = 0; k < rate.length; k++) {
                rate[k] = g.rate(k, t);
                snow[k] = g.snow(k, t);
                if (snow[k] && levelOfRate(rate[k]) > 0) { flakes.add(g.x(k)); flakes.add(g.y(k)); }
            }
            Frame f = new Frame(t, true, image(rate, snow), g.cx, g.cy, g.half);
            f.flakes = new double[flakes.size()];
            for (int i = 0; i < f.flakes.length; i++) f.flakes[i] = flakes.get(i);
            r.frames.add(f);
        }
    }

    /** Grille de taux (mm/h) → image lissée par interpolation bilinéaire, en niveaux. */
    private static Bitmap image(double[] rate, boolean[] snow) {
        int n = 256;
        int[] px = new int[n * n];
        for (int y = 0; y < n; y++) {
            double gy = (GRID - 1) * (y + 0.5) / n;
            int j = Math.min(GRID - 2, (int) gy);
            double fy = gy - j;
            for (int x = 0; x < n; x++) {
                double gx = (GRID - 1) * (x + 0.5) / n;
                int i = Math.min(GRID - 2, (int) gx);
                double fx = gx - i;
                double v = (1 - fy) * ((1 - fx) * rate[j * GRID + i] + fx * rate[j * GRID + i + 1])
                        + fy * ((1 - fx) * rate[(j + 1) * GRID + i] + fx * rate[(j + 1) * GRID + i + 1]);
                boolean sn = snow[(int) Math.round(gy) * GRID + (int) Math.round(gx)];
                px[y * n + x] = (sn ? SNOW_COLORS : RAIN_COLORS)[levelOfRate(v)];
            }
        }
        return Bitmap.createBitmap(px, n, n, Bitmap.Config.ARGB_8888);
    }
}
