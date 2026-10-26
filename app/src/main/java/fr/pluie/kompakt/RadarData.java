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
 * par instant. Passé : jusqu'à 2 h 30 (l'offre gratuite en garde environ 2 h), une image par quart d'heure.
 * Futur : les images de prévision immédiate (« nowcast ») si RainViewer en fournit.
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
        boolean failed;
    }

    /** Zoom maximal des images RainViewer gratuites ; au-delà, on agrandit l'image du zoom 7. */
    static final int MAX_RADAR_ZOOM = 7;
    private static final long PAST_SECONDS = 150 * 60, STEP_SECONDS = 15 * 60;

    /**
     * Couleurs des niveaux 1 à 6, de la bruine à l'averse violente. Couleur : palette habituelle des radars
     * (vert, jaune, orange, rouge, magenta). Noir et blanc : gris de plus en plus foncés.
     */
    static final int[] RAIN_COLORS = Ui.COLOR
            ? new int[]{0, 0xB0A6E39A, 0xD04CBB4C, 0xE8F2D61E, 0xF0F28C1E, 0xF8DC2A22, 0xFFB42CB4}
            : new int[]{0, 0x38000000, 0x60000000, 0x88000000, 0xB0000000, 0xD8000000, 0xFF000000};

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
        // Passé : une image par quart d'heure, de la plus récente jusqu'à 2 h 30 avant. RainViewer en
        // fournit une toutes les 10 min : on prend la plus proche de chaque quart d'heure.
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
                r.frames.add(new Frame(t, t > last, levels(src), cx, cy, half));
                src.recycle();
            } catch (Exception ignored) {
            }
        }
        if (r.frames.isEmpty()) throw new Exception("images illisibles");
    }

    /** Image RainViewer → niveaux d'intensité de la palette de l'appli. */
    private static Bitmap levels(Bitmap src) {
        int w = src.getWidth(), h = src.getHeight();
        int[] px = new int[w * h];
        src.getPixels(px, 0, w, 0, 0, w, h);
        for (int i = 0; i < px.length; i++) px[i] = RAIN_COLORS[levelOfAlpha(px[i] >>> 24)];
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888);
    }
}
