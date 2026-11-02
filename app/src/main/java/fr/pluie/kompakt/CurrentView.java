package fr.pluie.kompakt;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.View;

/**
 * Météo actuelle, en tête de l'onglet Aujourd'hui.
 * En haut : la température en très grand (unité en petit) et l'icône du temps à droite.
 * En dessous : ressenti et humidité à gauche ; à droite le vent (flèche, direction, vitesse) et les rafales.
 */
public class CurrentView extends View {
    private Forecast.Current current;

    private final Paint big = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ink = new Paint(Paint.ANTI_ALIAS_FLAG);

    public CurrentView(Context c) {
        super(c);
        big.setColor(Ui.INK);
        big.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        text.setColor(Ui.INK);
        text.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        ink.setColor(Ui.INK);
    }

    public void setCurrent(Forecast.Current current) {
        this.current = current;
        invalidate();
    }

    private float sp(float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, getResources().getDisplayMetrics());
    }

    private float dp(float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    @Override
    protected void onDraw(Canvas c) {
        c.drawColor(Ui.BG);
        if (current == null) return;
        float w = getWidth(), h = getHeight();
        float top = h * 0.6f; // partie haute : température et icône

        // Température en très grand, l'unité (C ou F) en petit à côté.
        big.setTextSize(sp(76));
        big.setTextAlign(Paint.Align.LEFT);
        String t = Double.isNaN(current.temp) ? "–" : Fmt.deg(current.temp);
        float base = top * 0.86f;
        c.drawText(t, 0, base, big);
        text.setTextSize(sp(26));
        text.setTextAlign(Paint.Align.LEFT);
        c.drawText(Fmt.unit().replace("°", ""), big.measureText(t) + dp(6), base - sp(76) * 0.18f, text);

        // Icône du temps à droite.
        float icon = Math.min(top * 0.92f, w * 0.3f);
        WeatherIcon.draw(c, Weather.currentKind(current), !current.day, w - icon / 2f, top / 2f, icon, Ui.INK, Ui.BG);

        // Ressenti et humidité, à gauche.
        float y1 = top + (h - top) * 0.38f, y2 = top + (h - top) * 0.82f;
        text.setTextSize(sp(19));
        if (!Double.isNaN(current.feels)) c.drawText(L.f("feels", Fmt.deg(current.feels)), 0, y1, text);
        if (!Double.isNaN(current.rh)) c.drawText(L.f("humidity", Math.round(current.rh)), 0, y2, text);

        // Vent à droite : flèche, direction et vitesse en grand, km/h en petit ; rafales en dessous.
        if (!Double.isNaN(current.wind)) {
            text.setTextSize(sp(14));
            text.setTextAlign(Paint.Align.RIGHT);
            float x = w;
            c.drawText("km/h", x, y1, text);
            x -= text.measureText("km/h") + dp(4);
            big.setTextSize(sp(26));
            big.setTextAlign(Paint.Align.RIGHT);
            String v = (Double.isNaN(current.windDir) ? "" : Fmt.compass(current.windDir) + " ") + Math.round(current.wind);
            c.drawText(v, x, y1, big);
            x -= big.measureText(v) + dp(8);
            if (!Double.isNaN(current.windDir)) {
                float r = sp(11);
                arrow(c, x - r, y1 - sp(26) * 0.35f, r, current.windDir);
            }
            if (!Double.isNaN(current.gust) && current.gust >= current.wind + 5) {
                text.setTextSize(sp(15));
                c.drawText(L.f("gusts", Math.round(current.gust)), w, y2, text);
            }
            text.setTextAlign(Paint.Align.LEFT);
        }
    }

    /** Flèche indiquant où va le vent (il vient de {@code from} degrés). */
    private void arrow(Canvas c, float cx, float cy, float r, double from) {
        c.save();
        c.rotate((float) from + 180, cx, cy);
        Path p = new Path();
        p.moveTo(cx, cy - r);
        p.lineTo(cx + r * 0.62f, cy + r * 0.8f);
        p.lineTo(cx, cy + r * 0.42f);
        p.lineTo(cx - r * 0.62f, cy + r * 0.8f);
        p.close();
        c.drawPath(p, ink);
        c.restore();
    }
}
