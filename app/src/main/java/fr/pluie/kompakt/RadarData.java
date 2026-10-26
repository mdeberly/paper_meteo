package fr.pluie.kompakt;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Images de l'onglet Radar, toutes issues de RainViewer : une image de 512 px centrée sur le lieu
 * par instant. Passé : les 2 dernières heures, une image toutes les 10 min (le rythme de RainViewer).
 * Futur : les images de prévision immédiate (« nowcast ») si RainViewer en fournit.
 * Tout est en Mercator normalisé (0..1), comme {@link MapData}.
 */
final class RadarData {
    private RadarData() {}

    /**
     * Une image : intensité (0..255, lissée) sur size × size points, couvrant le carré de centre (cx, cy)
     * et de demi-côté half. La mise en couleurs se fait à l'affichage, à la résolution de l'écran.
     */
    static final class Frame {
        final long time;          // secondes Unix
        final boolean forecast;
        final byte[] intensity;
        final int size;
        final double cx, cy, half;

        Frame(long time, boolean forecast, byte[] intensity, int size, double cx, double cy, double half) {
            this.time = time; this.forecast = forecast; this.intensity = intensity; this.size = size;
            this.cx = cx; this.cy = cy; this.half = half;
        }

        /** Intensité interpolée (bilinéaire) au point (u, v) de l'image, en pixels ; 0 hors image. */
        float at(float u, float v) {
            if (u < 0 || v < 0 || u > size - 1 || v > size - 1) return 0;
            int i = Math.min(size - 2, (int) u), j = Math.min(size - 2, (int) v);
            float fu = u - i, fv = v - j;
            int k = j * size + i;
            float a = intensity[k] & 0xFF, b = intensity[k + 1] & 0xFF;
            float c = intensity[k + size] & 0xFF, d = intensity[k + size + 1] & 0xFF;
            return (1 - fv) * ((1 - fu) * a + fu * b) + fv * ((1 - fu) * c + fu * d);
        }
    }

    static final class Result {
        final List<Frame> frames = new ArrayList<>();
        boolean failed;
    }

    /** Zoom maximal des images RainViewer gratuites ; au-delà, on agrandit l'image du zoom 7. */
    static final int MAX_RADAR_ZOOM = 7;
    private static final long PAST_SECONDS = 120 * 60, STEP_SECONDS = 10 * 60;

    /**
     * Couleurs des niveaux 1 à 6, de la bruine à l'averse violente. Couleur : palette habituelle des radars
     * (vert, jaune, orange, rouge, magenta). Noir et blanc : gris de plus en plus foncés.
     */
    static final int[] RAIN_COLORS = Ui.COLOR
            ? new int[]{0, 0xB0A6E39A, 0xD04CBB4C, 0xE8F2D61E, 0xF0F28C1E, 0xF8DC2A22, 0xFFB42CB4}
            : new int[]{0, 0x38000000, 0x60000000, 0x88000000, 0xB0000000, 0xD8000000, 0xFF000000};

    /**
     * Couleur selon l'intensité (0..255), table de 256 entrées. Couleur : dégradé continu entre les teintes
     * habituelles, bords adoucis. Noir et blanc : les 6 gris, en paliers nets.
     */
    static final int[] COLOR_OF = new int[256];

    static {
        // Intensité → couleur (ARGB) aux points d'appui, interpolée entre eux.
        int[] at = {10, 22, 40, 80, 120, 165, 215, 255};
        int[] col = {0x00A6E39A, 0xA8A6E39A, 0xC07FD27A, 0xD84CBB4C, 0xE8F2D61E, 0xF0F28C1E, 0xF8DC2A22, 0xFFB42CB4};
        for (int a = 0; a < 256; a++) {
            if (!Ui.COLOR) {
                COLOR_OF[a] = RAIN_COLORS[levelOfAlpha(a)];
                continue;
            }
            if (a <= at[0]) continue;
            int k = 1;
            while (k < at.length - 1 && a > at[k]) k++;
            float t = Math.min(1f, (float) (a - at[k - 1]) / (at[k] - at[k - 1]));
            int c0 = col[k - 1], c1 = col[k], c = 0;
            for (int sh = 0; sh <= 24; sh += 8) {
                int x0 = (c0 >>> sh) & 0xFF, x1 = (c1 >>> sh) & 0xFF;
                c |= Math.round(x0 + (x1 - x0) * t) << sh;
            }
            COLOR_OF[a] = c;
        }
    }

    /** L'intensité est dans l'alpha des images RainViewer (faible ≈ 20, forte ≈ 200 et plus). */
    static int levelOfAlpha(int a) {
        return a < 12 ? 0 : a < 40 ? 1 : a < 80 ? 2 : a < 120 ? 3 : a < 165 ? 4 : a < 215 ? 5 : 6;
    }

    /** Charge tout (à appeler hors du fil principal). zoom : zoom d'affichage. */
    static Result load(double lat, double lon, int zoom) {
        Result r = new Result();
        try {
            radar(r, lat, lon, Math.min(zoom, MAX_RADAR_ZOOM));
        } catch (Exception e) {
            r.failed = true;
        }
        return r;
    }

    private static void radar(Result r, double lat, double lon, int z) throws Exception {
        JSONObject meta = new JSONObject(Http.get("https://api.rainviewer.com/public/weather-maps.json"));
        String host = meta.getString("host");
        JSONObject radar = meta.getJSONObject("radar");
        JSONArray past = radar.optJSONArray("past");
        if (past == null || past.length() == 0) throw new Exception("pas d'images");
        // Passé : une image toutes les 10 min, de la plus récente jusqu'à 2 h avant (la plus proche de
        // chaque créneau, au cas où une image manquerait).
        List<JSONObject> picks = new ArrayList<>();
        long last = past.getJSONObject(past.length() - 1).getLong("time");
        for (long target = last; target >= last - PAST_SECONDS; target -= STEP_SECONDS) {
            JSONObject best = null;
            long gap = Long.MAX_VALUE;
            for (int i = 0; i < past.length(); i++) {
                JSONObject o = past.getJSONObject(i);
                long d = Math.abs(o.getLong("time") - target);
                if (d < gap) { gap = d; best = o; }
            }
            if (best == null || gap > 6 * 60 || picks.contains(best)) continue;
            picks.add(0, best);
        }
        // Futur : toutes les images de prévision fournies.
        JSONArray now = radar.optJSONArray("nowcast");
        if (now != null) for (int i = 0; i < now.length(); i++) picks.add(now.getJSONObject(i));

        double cx = MapData.x(lon), cy = MapData.y(lat);
        double half = 1.0 / (1 << z); // 512 px sur un monde de 256·2^z px
        for (JSONObject o : picks) {
            String url = String.format(Locale.ROOT, "%s%s/512/%d/%.4f/%.4f/2/1_1.png",
                    host, o.getString("path"), z, lat, lon);
            try {
                byte[] png = Http.bytes(url);
                Bitmap src = BitmapFactory.decodeByteArray(png, 0, png.length);
                if (src == null) continue;
                long t = o.getLong("time");
                r.frames.add(new Frame(t, t > last, intensity(src), src.getWidth(), cx, cy, half));
                src.recycle();
            } catch (Exception ignored) {
            }
        }
        if (r.frames.isEmpty()) throw new Exception("images illisibles");
    }

    /** Intensité de l'image RainViewer (canal alpha), légèrement lissée (3 × 3) pour effacer le grain des pixels. */
    private static byte[] intensity(Bitmap src) {
        int w = src.getWidth(), h = src.getHeight();
        int[] px = new int[w * h];
        src.getPixels(px, 0, w, 0, 0, w, h);
        int[] a = new int[w * h], tmp = new int[w * h];
        for (int i = 0; i < a.length; i++) a[i] = px[i] >>> 24;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int l = a[y * w + Math.max(0, x - 1)], r = a[y * w + Math.min(w - 1, x + 1)];
                tmp[y * w + x] = (l + 2 * a[y * w + x] + r) / 4;
            }
        }
        byte[] out = new byte[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int u = tmp[Math.max(0, y - 1) * w + x], d = tmp[Math.min(h - 1, y + 1) * w + x];
                out[y * w + x] = (byte) ((u + 2 * tmp[y * w + x] + d) / 4);
            }
        }
        return out;
    }
}
