package fr.pluie.kompakt;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.View;

/** Météo actuelle : grande icône, température réelle, température ressentie, vent (direction et vitesse). */
public class CurrentView extends View {
    private Forecast.Current current;

    private final Paint big = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ink = new Paint(Paint.ANTI_ALIAS_FLAG);

    public CurrentView(Context c) {
        super(c);
        big.setColor(Ui.INK);
        big.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
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

    @Override
    protected void onDraw(Canvas c) {
        c.drawColor(Ui.BG);
        if (current == null) return;
        float w = getWidth(), h = getHeight();

        // Icône à gauche.
        float icon = h * 0.92f;
        WeatherIcon.draw(c, Weather.currentKind(current), !current.day, icon / 2f, h / 2f, icon, Ui.INK, Ui.BG);

        // Température réelle, en grand.
        float x = icon + h * 0.12f;
        big.setTextSize(sp(44));
        String t = Double.isNaN(current.temp) ? "–" : Fmt.deg(current.temp);
        c.drawText(t, x, h / 2f + sp(44) * 0.36f, big);
        x += big.measureText(t) + h * 0.18f;

        // À droite : ressenti, puis le vent.
        text.setTextSize(sp(17));
        if (!Double.isNaN(current.feels)) c.drawText(L.f("feels", Fmt.deg(current.feels)), x, h * 0.4f, text);
        if (!Double.isNaN(current.wind)) {
            float y = h * 0.74f;
            float r = sp(8);
            float tx = x;
            if (!Double.isNaN(current.windDir)) {
                arrow(c, x + r, y - sp(17) * 0.32f, r, current.windDir);
                tx = x + 2 * r + r * 0.6f;
                String dir = Fmt.compass(current.windDir) + " ";
                c.drawText(dir, tx, y, text);
                tx += text.measureText(dir);
            }
            c.drawText(Math.round(current.wind) + " km/h", tx, y, text);
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
