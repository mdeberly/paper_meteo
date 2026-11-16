package fr.pluie.kompakt;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.widget.RemoteViews;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Widgets 4 × 2 de l'écran d'accueil. Quatre modèles au choix, de même taille :
 * <ul>
 *   <li>{@link Days} : prévisions sur 5 jours ;</li>
 *   <li>{@link Hours} : prévisions des prochaines heures ;</li>
 *   <li>{@link NowDays} : météo actuelle et 3 jours ;</li>
 *   <li>{@link NowHours} : météo actuelle et prochaines heures ;</li>
 *   <li>{@link Big} (4 × 4) : tout à la fois, avec lever et coucher du soleil.</li>
 * </ul>
 * Lieu : celui de l'appli, ou la position GPS si l'option est choisie dans les réglages (suivie au fil
 * des déplacements, à chaque mise à jour).
 * Chaque widget est une image dessinée par l'appli (mêmes icônes et couleurs que dans l'appli),
 * à partir des dernières données téléchargées. Mise à jour toutes les heures (avec un nouveau
 * téléchargement si les données ont plus de 45 min) et après chaque actualisation dans l'appli.
 * Toucher le widget ouvre l'appli.
 */
public final class Widgets {
    private Widgets() {}

    static final int DAYS = 0, HOURS = 1, NOW_DAYS = 2, NOW_HOURS = 3, BIG = 4;
    /** Déplacement à partir duquel les widgets changent de lieu (mode GPS). */
    private static final double MOVE_KM = 2;
    private static final long STALE_MS = 45 * 60 * 1000L;

    /** Point commun des quatre modèles. */
    public abstract static class Base extends AppWidgetProvider {
        abstract int kind();

        @Override
        public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
            for (int id : ids) draw(c, m, id, kind());
            final PendingResult pending = goAsync();
            new Thread(() -> {
                try {
                    if (refresh(c)) updateAll(c);
                } finally {
                    pending.finish();
                }
            }).start();
        }

        @Override
        public void onAppWidgetOptionsChanged(Context c, AppWidgetManager m, int id, Bundle options) {
            draw(c, m, id, kind());
        }
    }

    public static final class Days extends Base { @Override int kind() { return DAYS; } }
    public static final class Hours extends Base { @Override int kind() { return HOURS; } }
    public static final class NowDays extends Base { @Override int kind() { return NOW_DAYS; } }
    public static final class NowHours extends Base { @Override int kind() { return NOW_HOURS; } }
    public static final class Big extends Base { @Override int kind() { return BIG; } }

    private static final Class<?>[] PROVIDERS = {Days.class, Hours.class, NowDays.class, NowHours.class, Big.class};

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("pluie", Context.MODE_PRIVATE);
    }

    /** Redessine tous les widgets posés (après une actualisation). */
    static void updateAll(Context c) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        if (m == null) return;
        for (int k = 0; k < PROVIDERS.length; k++) {
            for (int id : m.getAppWidgetIds(new ComponentName(c, PROVIDERS[k]))) draw(c, m, id, k);
        }
    }

    /** Mise à jour en arrière-plan (lieu GPS et données), puis nouveau dessin des widgets. */
    static void refreshAsync(Context c) {
        Context app = c.getApplicationContext();
        new Thread(() -> {
            refresh(app);
            updateAll(app);
        }).start();
    }

    /**
     * Données des widgets à jour (hors du fil principal). Mode GPS : dernière position connue ; nouveau
     * téléchargement si l'on s'est déplacé de plus de 2 km ou si les données ont plus de 45 min.
     * Sinon : prévisions du lieu de l'appli, retéléchargées si elles ont plus de 45 min.
     * Renvoie true si quelque chose a changé.
     */
    static boolean refresh(Context c) {
        SharedPreferences p = prefs(c);
        long now = System.currentTimeMillis();
        if (p.getBoolean("widget_gps", false)) {
            Location loc = lastLocation(c);
            if (loc != null) {
                double la = loc.getLatitude(), lo = loc.getLongitude();
                boolean known = p.contains("w_lat");
                double km = known ? distanceKm(la, lo, Double.parseDouble(p.getString("w_lat", "0")),
                        Double.parseDouble(p.getString("w_lon", "0"))) : Double.MAX_VALUE;
                if (km > MOVE_KM || now - p.getLong("w_fetched", 0) > STALE_MS) {
                    try {
                        String body = Http.get(MainActivity.forecastUrl(la, lo));
                        Forecast.parse(body);
                        String name = p.getString("w_name", null);
                        if (km > MOVE_KM || name == null) {
                            MainActivity.applySettings(p);
                            String found = MainActivity.placeName(c, la, lo);
                            name = found != null ? found : String.format(java.util.Locale.ROOT, "GPS %.2f, %.2f", la, lo);
                        }
                        p.edit().putString("w_json", body).putLong("w_fetched", now)
                                .putString("w_lat", Double.toString(la)).putString("w_lon", Double.toString(lo))
                                .putString("w_name", name).apply();
                        return true;
                    } catch (Exception ignored) {
                        return false; // pas de réseau : on garde les dernières données
                    }
                }
                return false;
            }
        }
        if (now - p.getLong("fetched", 0) > STALE_MS) {
            fetch(c);
            return true;
        }
        return false;
    }

    /** Téléchargement des prévisions du lieu de l'appli (hors du fil principal). */
    private static void fetch(Context c) {
        SharedPreferences p = prefs(c);
        try {
            double lat = Double.parseDouble(p.getString("lat", Double.toString(MainActivity.DEF_LAT)));
            double lon = Double.parseDouble(p.getString("lon", Double.toString(MainActivity.DEF_LON)));
            String body = Http.get(MainActivity.forecastUrl(lat, lon));
            Forecast.parse(body); // données valides seulement
            p.edit().putString("json", body).putLong("fetched", System.currentTimeMillis()).apply();
        } catch (Exception ignored) {
            // pas de réseau : on garde les dernières données
        }
    }

    /** Dernière position connue (réseau, GPS ou passive), ou null sans autorisation ni position. */
    @SuppressWarnings("MissingPermission")
    private static Location lastLocation(Context c) {
        LocationManager lm = (LocationManager) c.getSystemService(Context.LOCATION_SERVICE);
        if (lm == null) return null;
        Location best = null;
        for (String provider : new String[]{LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER,
                LocationManager.PASSIVE_PROVIDER}) {
            try {
                Location l = lm.getLastKnownLocation(provider);
                if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
            } catch (Exception ignored) {
                // fournisseur absent ou autorisation refusée
            }
        }
        return best;
    }

    private static double distanceKm(double la1, double lo1, double la2, double lo2) {
        double dLa = Math.toRadians(la2 - la1), dLo = Math.toRadians(lo2 - lo1);
        double h = Math.sin(dLa / 2) * Math.sin(dLa / 2)
                + Math.cos(Math.toRadians(la1)) * Math.cos(Math.toRadians(la2)) * Math.sin(dLo / 2) * Math.sin(dLo / 2);
        return 12742 * Math.asin(Math.sqrt(h));
    }

    private static void draw(Context c, AppWidgetManager m, int id, int kind) {
        float density = c.getResources().getDisplayMetrics().density;
        Bundle o = m.getAppWidgetOptions(id);
        int wDp = o == null ? 0 : o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH);
        int hDp = o == null ? 0 : o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT);
        if (wDp < 150) wDp = 280;
        if (hDp < 70) hDp = kind == BIG ? 300 : 120;
        Bitmap b = render(c, kind, Math.round(wDp * density), Math.round(hDp * density));
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget);
        v.setImageViewBitmap(R.id.widget_image, b);
        Intent open = new Intent(c, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        v.setOnClickPendingIntent(R.id.widget_image, PendingIntent.getActivity(c, kind, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
        m.updateAppWidget(id, v);
    }

    // ---------------------------------------------------------------- dessin

    /** Image du widget {@code kind}, de w × h pixels. */
    static Bitmap render(Context c, int kind, int w, int h) {
        SharedPreferences p = prefs(c);
        MainActivity.applySettings(p);
        // Mode GPS : données de la dernière position (préfixe « w_ »), sinon celles du lieu de l'appli.
        String pre = p.getBoolean("widget_gps", false) && p.contains("w_json") ? "w_" : "";
        Forecast f = null;
        String json = p.getString(pre + "json", null);
        if (json != null) {
            try { f = Forecast.parse(json); } catch (Exception ignored) { }
        }
        double lat = Double.parseDouble(p.getString(pre + "lat", Double.toString(MainActivity.DEF_LAT)));
        double lon = Double.parseDouble(p.getString(pre + "lon", Double.toString(MainActivity.DEF_LON)));
        String place = p.getString(pre + "name", MainActivity.DEF_NAME);
        return render(c, kind, w, h, f, lat, lon, place);
    }

    static Bitmap render(Context c, int kind, int w, int h, Forecast f, double lat, double lon, String place) {
        float density = c.getResources().getDisplayMetrics().density;
        // Échelle : 1 pour un widget de 280 × 120 dp (280 × 300 dp pour le grand).
        float u = Math.min(w / (280f * density), h / ((kind == BIG ? 300f : 120f) * density)) * density;
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);

        // Fond : carte arrondie, cernée en E Ink.
        RectF card = new RectF(u, u, w - u, h - u);
        float r = 14 * u;
        fill.setColor(Ui.BG);
        cv.drawRoundRect(card, r, r, fill);
        if (!Ui.COLOR) {
            Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
            border.setStyle(Paint.Style.STROKE);
            border.setStrokeWidth(Math.max(1.5f, 1.6f * u));
            border.setColor(Ui.INK);
            cv.drawRoundRect(card, r, r, border);
        }

        Paint t = new Paint(Paint.ANTI_ALIAS_FLAG);
        t.setColor(Ui.INK);
        float pad = 10 * u;
        RectF area = new RectF(card.left + pad, card.top + pad * 0.8f, card.right - pad, card.bottom - pad * 0.8f);
        if (f == null) {
            t.setTextSize(15 * u);
            t.setTextAlign(Paint.Align.CENTER);
            cv.drawText(L.t("no_data"), area.centerX(), area.centerY(), t);
            return b;
        }
        ZoneId zone = f.zone;
        LocalDateTime now = LocalDateTime.now(zone);
        switch (kind) {
            case DAYS:
                days(cv, area, u, f, zone, lat, lon, now.toLocalDate(), 5, place, true);
                break;
            case HOURS:
                hours(cv, area, u, f, zone, lat, lon, now, 6, place, true);
                break;
            case BIG:
                big(cv, area, u, f, zone, lat, lon, now, place);
                break;
            case NOW_DAYS: {
                RectF left = new RectF(area.left, area.top, area.left + area.width() * 0.4f, area.bottom);
                RectF right = new RectF(left.right + 6 * u, area.top, area.right, area.bottom);
                current(cv, left, u, f, place);
                separator(cv, left.right + 3 * u, area, u);
                days(cv, right, u, f, zone, lat, lon, now.toLocalDate(), 3, null, false);
                break;
            }
            default: {
                RectF left = new RectF(area.left, area.top, area.left + area.width() * 0.4f, area.bottom);
                RectF right = new RectF(left.right + 6 * u, area.top, area.right, area.bottom);
                current(cv, left, u, f, place);
                separator(cv, left.right + 3 * u, area, u);
                hours(cv, right, u, f, zone, lat, lon, now, 4, null, false);
            }
        }
        return b;
    }

    /**
     * Grand widget : lieu, météo actuelle, mini / maxi du jour, lever et coucher du soleil, pluie des
     * 2 prochaines heures, prochaines heures et 5 jours.
     */
    private static void big(Canvas cv, RectF a, float u, Forecast f, ZoneId zone, double lat, double lon,
                            LocalDateTime now, String place) {
        float h = a.height(), y = a.top;
        Paint t = new Paint(Paint.ANTI_ALIAS_FLAG);
        t.setColor(Ui.INK);

        // Météo actuelle (titre compris) : un peu plus d'un quart de la hauteur.
        RectF cur = new RectF(a.left, y, a.right, y + h * 0.27f);
        current(cv, new RectF(cur.left, cur.top, cur.left + cur.width() * 0.5f, cur.bottom), u, f, place);
        Forecast.Current c = f.current;
        if (c != null) {
            t.setTextSize(13 * u);
            t.setTextAlign(Paint.Align.RIGHT);
            float ry = cur.top + 30 * u;
            if (!Double.isNaN(c.rh)) { cv.drawText(L.f("humidity", Math.round(c.rh)), cur.right, ry, t); ry += 18 * u; }
            if (!Double.isNaN(c.gust) && !Double.isNaN(c.wind) && c.gust >= c.wind + 5) {
                cv.drawText(L.f("gusts", Math.round(c.gust)), cur.right, ry, t);
            }
            t.setTextAlign(Paint.Align.LEFT);
        }
        y = cur.bottom;
        line(cv, a, y, u);

        // Mini / maxi du jour, lever et coucher du soleil.
        float rowH = h * 0.1f, mid = y + rowH * 0.62f;
        Day today = f.day(now.toLocalDate());
        t.setTextSize(17 * u);
        if (today != null && today.temp.hasAny()) {
            cv.drawText("↓ " + Fmt.deg(today.temp.low()) + "   ↑ " + Fmt.deg(today.temp.high()), a.left, mid, t);
        }
        Sun sun = Sun.of(now.toLocalDate(), lat, lon, zone);
        if (sun.state == Sun.NORMAL) {
            t.setTextSize(15 * u);
            t.setTextAlign(Paint.Align.RIGHT);
            int is = Math.round(18 * u);
            String set = Fmt.clock(sun.set), rise = Fmt.clock(sun.rise);
            float x = a.right;
            cv.drawText(set, x, mid, t);
            x -= t.measureText(set) + 4 * u + is;
            SunEventIcon down = new SunEventIcon(false, is);
            down.setBounds(Math.round(x), Math.round(mid - is * 0.85f), Math.round(x) + is, Math.round(mid - is * 0.85f) + is);
            down.draw(cv);
            x -= 10 * u;
            cv.drawText(rise, x, mid, t);
            x -= t.measureText(rise) + 4 * u + is;
            SunEventIcon up = new SunEventIcon(true, is);
            up.setBounds(Math.round(x), Math.round(mid - is * 0.85f), Math.round(x) + is, Math.round(mid - is * 0.85f) + is);
            up.draw(cv);
            t.setTextAlign(Paint.Align.LEFT);
        }
        y += rowH;
        line(cv, a, y, u);

        // Pluie des 2 prochaines heures, en une ligne (réduite si besoin).
        String soon = Weather.nextTwoHours(f, zone, lat, lon, now);
        t.setTextSize(13 * u);
        fit(t, soon, a.width(), 13 * u);
        cv.drawText(soon, a.left, y + h * 0.065f, t);
        y += h * 0.09f;
        line(cv, a, y, u);

        // Prochaines heures puis 5 jours.
        float hh = (a.bottom - y) / 2;
        hours(cv, new RectF(a.left, y + 4 * u, a.right, y + hh - 2 * u), u, f, zone, lat, lon, now, 6, null, false);
        line(cv, a, y + hh, u);
        days(cv, new RectF(a.left, y + hh + 4 * u, a.right, a.bottom), u, f, zone, lat, lon, now.toLocalDate(), 5, null, false);
    }

    private static void line(Canvas cv, RectF a, float y, float u) {
        Paint p = new Paint();
        p.setColor(Ui.LINE);
        cv.drawRect(a.left, y - u * 0.6f, a.right, y + u * 0.6f, p);
    }

    private static void separator(Canvas cv, float x, RectF area, float u) {
        Paint p = new Paint();
        p.setColor(Ui.LINE);
        cv.drawRect(x - u * 0.6f, area.top + 4 * u, x + u * 0.6f, area.bottom - 4 * u, p);
    }

    /** Petit titre (lieu) en haut à gauche ; renvoie le haut de la zone restante. */
    private static float title(Canvas cv, RectF a, float u, String place) {
        Paint t = new Paint(Paint.ANTI_ALIAS_FLAG);
        t.setColor(Ui.COLOR ? Ui.MUTED : Ui.INK);
        t.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        t.setTextSize(12 * u);
        cv.drawText(place, a.left, a.top + 11 * u, t);
        return a.top + 16 * u;
    }

    /** Météo actuelle : lieu, icône, température en grand, ressenti. */
    private static void current(Canvas cv, RectF a, float u, Forecast f, String place) {
        float top = title(cv, a, u, place);
        Forecast.Current cur = f.current;
        if (cur == null) return;
        Paint t = new Paint(Paint.ANTI_ALIAS_FLAG);
        t.setColor(Ui.INK);
        float icon = Math.min(a.width() * 0.45f, (a.bottom - top) * 0.55f);
        WeatherIcon.draw(cv, Weather.currentKind(cur), !cur.day, a.left + icon / 2, top + icon / 2 + 2 * u, icon, Ui.INK, Ui.BG);
        t.setTextSize(34 * u);
        String temp = Double.isNaN(cur.temp) ? "–" : Fmt.deg(cur.temp);
        float tw = t.measureText(temp);
        if (tw > a.width() - icon - 4 * u) t.setTextSize(34 * u * (a.width() - icon - 4 * u) / tw);
        t.setTextAlign(Paint.Align.RIGHT);
        cv.drawText(temp, a.right, top + icon * 0.72f, t);
        t.setTextAlign(Paint.Align.LEFT);
        t.setTextSize(13 * u);
        float y = a.bottom - 4 * u;
        if (!Double.isNaN(cur.feels)) cv.drawText(L.f("feels", Fmt.deg(cur.feels)), a.left, y - 17 * u, t);
        if (!Double.isNaN(cur.wind)) {
            String wind = (Double.isNaN(cur.windDir) ? "" : Fmt.compass(cur.windDir) + " ") + Math.round(cur.wind) + " km/h";
            cv.drawText(wind, a.left, y, t);
        }
    }

    /** Colonnes de jours : jour, icône, mini / maxi, pluie. */
    private static void days(Canvas cv, RectF a, float u, Forecast f, ZoneId zone, double lat, double lon,
                             LocalDate today, int n, String place, boolean withTitle) {
        float top = withTitle ? title(cv, a, u, place) : a.top;
        float cw = a.width() / n, h = a.bottom - top;
        Paint t = new Paint(Paint.ANTI_ALIAS_FLAG);
        t.setColor(Ui.INK);
        t.setTextAlign(Paint.Align.CENTER);
        for (int i = 0; i < n; i++) {
            LocalDate d = today.plusDays(i);
            Day day = f.day(d);
            float cx = a.left + cw * i + cw / 2;
            t.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            t.setTextSize(13 * u);
            String label = i == 0 ? L.item("days_short", 1)
                    : Fmt.cap(d.format(DateTimeFormatter.ofPattern("EEE", L.locale())).replace(".", ""));
            cv.drawText(label, cx, top + h * 0.16f, t);
            float icon = Math.min(cw * 0.78f, h * 0.36f);
            WeatherIcon.draw(cv, Weather.dayKind(f, zone, lat, lon, d), false, cx, top + h * 0.42f, icon, Ui.INK, Ui.BG);
            t.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
            if (day != null && day.temp.hasAny()) {
                t.setTextSize(13 * u);
                String temps = Fmt.deg(day.temp.low()) + " / " + Fmt.deg(day.temp.high());
                fit(t, temps, cw - 2 * u, 13 * u);
                cv.drawText(temps, cx, top + h * 0.78f, t);
            }
            if (day != null && day.rain.hasAny()) {
                double snow = day.snow.total();
                String rain = snow >= 0.1 ? Fmt.total(snow) + " cm" : Fmt.total(day.rain.total()) + " mm";
                t.setTextSize(11 * u);
                fit(t, rain, cw - 2 * u, 11 * u);
                t.setColor(Ui.COLOR ? Ui.RAIN_TEXT : Ui.INK);
                cv.drawText(rain, cx, top + h * 0.97f, t);
                t.setColor(Ui.INK);
            }
        }
    }

    /** Colonnes d'heures : heure, icône, température, pluie. */
    private static void hours(Canvas cv, RectF a, float u, Forecast f, ZoneId zone, double lat, double lon,
                              LocalDateTime now, int n, String place, boolean withTitle) {
        float top = withTitle ? title(cv, a, u, place) : a.top;
        float cw = a.width() / n, h = a.bottom - top;
        List<Weather.Hour> hs = Weather.hours(f, zone, lat, lon, now.toLocalDate(), now.getHour(), n);
        Paint t = new Paint(Paint.ANTI_ALIAS_FLAG);
        t.setColor(Ui.INK);
        t.setTextAlign(Paint.Align.CENTER);
        for (int i = 0; i < hs.size(); i++) {
            Weather.Hour x = hs.get(i);
            float cx = a.left + cw * i + cw / 2;
            t.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            t.setTextSize(13 * u);
            cv.drawText(i == 0 ? L.t("now_short") : L.hour(x.h), cx, top + h * 0.16f, t);
            float icon = Math.min(cw * 0.78f, h * 0.36f);
            WeatherIcon.draw(cv, x.kind, x.night, cx, top + h * 0.42f, icon, Ui.INK, Ui.BG);
            t.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
            if (!Double.isNaN(x.temp)) {
                t.setTextSize(15 * u);
                cv.drawText(Fmt.deg(x.temp), cx, top + h * 0.78f, t);
            }
            if (x.has && x.mm >= 0.1) {
                t.setTextSize(11 * u);
                String rain = Fmt.total(x.mm) + " mm";
                fit(t, rain, cw - 2 * u, 11 * u);
                t.setColor(Ui.COLOR ? Ui.RAIN_TEXT : Ui.INK);
                cv.drawText(rain, cx, top + h * 0.97f, t);
                t.setColor(Ui.INK);
            }
        }
    }

    /** Réduit le texte s'il dépasse la largeur disponible. */
    private static void fit(Paint t, String s, float room, float size) {
        t.setTextSize(size);
        float w = t.measureText(s);
        if (w > room) t.setTextSize(size * room / w);
    }
}
