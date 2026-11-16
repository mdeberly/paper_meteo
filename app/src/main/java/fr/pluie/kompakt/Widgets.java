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
 *   <li>{@link NowHours} : météo actuelle et prochaines heures.</li>
 * </ul>
 * Chaque widget est une image dessinée par l'appli (mêmes icônes et couleurs que dans l'appli),
 * à partir des dernières données téléchargées. Mise à jour toutes les heures (avec un nouveau
 * téléchargement si les données ont plus de 45 min) et après chaque actualisation dans l'appli.
 * Toucher le widget ouvre l'appli.
 */
public final class Widgets {
    private Widgets() {}

    static final int DAYS = 0, HOURS = 1, NOW_DAYS = 2, NOW_HOURS = 3;
    private static final long STALE_MS = 45 * 60 * 1000L;

    /** Point commun des quatre modèles. */
    public abstract static class Base extends AppWidgetProvider {
        abstract int kind();

        @Override
        public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
            for (int id : ids) draw(c, m, id, kind());
            SharedPreferences p = prefs(c);
            if (System.currentTimeMillis() - p.getLong("fetched", 0) > STALE_MS) {
                final PendingResult pending = goAsync();
                new Thread(() -> {
                    try {
                        fetch(c);
                        updateAll(c);
                    } finally {
                        pending.finish();
                    }
                }).start();
            }
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

    private static final Class<?>[] PROVIDERS = {Days.class, Hours.class, NowDays.class, NowHours.class};

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

    /** Téléchargement des prévisions depuis le widget (hors du fil principal). */
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

    private static void draw(Context c, AppWidgetManager m, int id, int kind) {
        float density = c.getResources().getDisplayMetrics().density;
        Bundle o = m.getAppWidgetOptions(id);
        int wDp = o == null ? 0 : o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH);
        int hDp = o == null ? 0 : o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT);
        if (wDp < 150) wDp = 280;
        if (hDp < 70) hDp = 120;
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
        Forecast f = null;
        String json = p.getString("json", null);
        if (json != null) {
            try { f = Forecast.parse(json); } catch (Exception ignored) { }
        }
        double lat = Double.parseDouble(p.getString("lat", Double.toString(MainActivity.DEF_LAT)));
        double lon = Double.parseDouble(p.getString("lon", Double.toString(MainActivity.DEF_LON)));
        String place = p.getString("name", MainActivity.DEF_NAME);
        return render(c, kind, w, h, f, lat, lon, place);
    }

    static Bitmap render(Context c, int kind, int w, int h, Forecast f, double lat, double lon, String place) {
        float density = c.getResources().getDisplayMetrics().density;
        // Échelle : 1 pour un widget de 280 × 120 dp.
        float u = Math.min(w / (280f * density), h / (120f * density)) * density;
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
