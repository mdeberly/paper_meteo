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

        Frame(long time, boolean forecast, Bitmap image, double cx, double cy, double half) {
            this.time = time; this.forecast = forecast; this.image = image;
            this.cx = cx; this.cy = cy; this.half = half;
        }
    }

    static final class Result {
        final List<Frame> frames = new ArrayList<>();
        boolean radarFailed, forecastFailed;
    }

    /** Zoom maximal des images RainViewer gratuites ; au-delà, on agrandit l'image du zoom 7. */
    static final int MAX_RADAR_ZOOM = 7;
    private static final int GRID = 9;

    /** Couleurs des niveaux 1 à 4 (faible, modérée, forte, très forte). */
    static final int[] LEVEL_COLORS = Ui.COLOR
            ? new int[]{0, 0x9970B8F0, 0xCC3D7CC9, 0xE0234E9A, 0xF0B8322A}
            : new int[]{0, 0x50000000, 0x90000000, 0xC8000000, 0xFF000000};

    static int levelOfAlpha(int a) {
        return a < 12 ? 0 : a < 60 ? 1 : a < 120 ? 2 : a < 180 ? 3 : 4;
    }

    static int levelOfRate(double mmPerHour) {
        return mmPerHour < 0.1 ? 0 : mmPerHour < 1 ? 1 : mmPerHour < 3 ? 2 : mmPerHour < 8 ? 3 : 4;
    }

    /** Charge tout (à appeler hors du fil principal). zoom : zoom d'affichage. */
    static Result load(double lat, double lon, int zoom) {
        Result r = new Result();
        double cx = MapData.x(lon), cy = MapData.y(lat);
        try {
            radar(r, lat, lon, cx, cy, Math.min(zoom, MAX_RADAR_ZOOM));
        } catch (Exception e) {
            r.radarFailed = true;
        }
        try {
            forecast(r, cx, cy, zoom);
        } catch (Exception e) {
            r.forecastFailed = true;
        }
        return r;
    }

    private static void radar(Result r, double lat, double lon, double cx, double cy, int z) throws Exception {
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
                boolean nowcast = o.getLong("time") > last;
                r.frames.add(new Frame(o.getLong("time"), nowcast, levels(src), cx, cy, half));
                src.recycle();
                ok++;
            } catch (Exception ignored) {
            }
        }
        if (ok == 0) throw new Exception("images illisibles");
    }

    /** Image RainViewer → mêmes niveaux que la prévision (l'intensité est dans l'alpha). */
    private static Bitmap levels(Bitmap src) {
        int w = src.getWidth(), h = src.getHeight();
        int[] px = new int[w * h];
        src.getPixels(px, 0, w, 0, 0, w, h);
        for (int i = 0; i < px.length; i++) px[i] = LEVEL_COLORS[levelOfAlpha(px[i] >>> 24)];
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888);
    }

    private static void forecast(Result r, double cx, double cy, int zoom) throws Exception {
        // Grille un peu plus large que la carte affichée (qui couvre ±1/2^zoom).
        double half = 1.25 / (1 << zoom);
        double[] lats = new double[GRID * GRID], lons = new double[GRID * GRID];
        StringBuilder la = new StringBuilder(), lo = new StringBuilder();
        for (int j = 0; j < GRID; j++) {
            for (int i = 0; i < GRID; i++) {
                int k = j * GRID + i;
                lons[k] = MapData.lon(cx - half + 2 * half * i / (GRID - 1));
                lats[k] = MapData.lat(cy - half + 2 * half * j / (GRID - 1));
                if (k > 0) { la.append(','); lo.append(','); }
                la.append(String.format(Locale.ROOT, "%.3f", lats[k]));
                lo.append(String.format(Locale.ROOT, "%.3f", lons[k]));
            }
        }
        String body = Http.get("https://api.open-meteo.com/v1/forecast?latitude=" + la + "&longitude=" + lo
                + "&minutely_15=precipitation&forecast_days=2&timezone=GMT&timeformat=unixtime");
        JSONArray all = body.trim().startsWith("[") ? new JSONArray(body) : new JSONArray().put(new JSONObject(body));
        if (all.length() != GRID * GRID) throw new Exception("grille incomplète");
        List<Map<Long, Double>> series = new ArrayList<>();
        for (int k = 0; k < all.length(); k++) {
            JSONObject m = all.getJSONObject(k).getJSONObject("minutely_15");
            JSONArray t = m.getJSONArray("time"), p = m.getJSONArray("precipitation");
            Map<Long, Double> s = new HashMap<>();
            for (int i = 0; i < t.length(); i++) if (!p.isNull(i)) s.put(t.getLong(i), p.getDouble(i));
            series.add(s);
        }
        long now = System.currentTimeMillis() / 1000;
        long base = now / 1800 * 1800;
        for (int step = 1; step <= 6; step++) {
            long t = base + 1800L * step;
            double[] rate = new double[GRID * GRID];
            boolean any = false;
            for (int k = 0; k < rate.length; k++) {
                // Cumul des deux quarts d'heure qui finissent à t, ramené à l'heure.
                Double a = series.get(k).get(t), b = series.get(k).get(t - 900);
                if (a == null && b == null) continue;
                rate[k] = 2 * ((a == null ? 0 : a) + (b == null ? 0 : b));
                any = true;
            }
            if (any) r.frames.add(new Frame(t, true, grid(rate), cx, cy, half));
        }
    }

    /** Grille de taux (mm/h) → image lissée par interpolation bilinéaire, en niveaux. */
    private static Bitmap grid(double[] rate) {
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
                px[y * n + x] = LEVEL_COLORS[levelOfRate(v)];
            }
        }
        return Bitmap.createBitmap(px, n, n, Bitmap.Config.ARGB_8888);
    }
}
