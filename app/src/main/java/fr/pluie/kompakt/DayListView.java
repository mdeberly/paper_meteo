package fr.pluie.kompakt;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;

import java.util.List;

/**
 * Onglet Prévisions : un jour par ligne (de demain à J+9). Jour et date, icône du temps, température
 * maximale et vent, ensoleillement et pluie, chevron. Toucher une ligne ouvre le détail heure par heure.
 */
public class DayListView extends View {
    public interface OnPick { void pick(int index); }

    /** Une ligne de la liste. */
    public static final class Row {
        String day = "", date = "", tmax = "", wind = "", sun = "", rain = "";
        double windDir = Double.NaN;
        int icon = Weather.NONE;
        boolean weekend;
    }

    private List<Row> rows;
    private OnPick listener;
    private float downY;

    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);

    /** Fond d'une ligne sur deux (couleur) ; en E Ink, simple trait de séparation. */
    private static final int ALT_BG = Ui.COLOR ? 0xFFF1E0CB : 0xFFFFFFFF;
    private static final int SOFT = Ui.COLOR ? Ui.MUTED : Ui.INK;

    public DayListView(Context c) {
        super(c);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeCap(Paint.Cap.ROUND);
        line.setStrokeJoin(Paint.Join.ROUND);
    }

    void setRows(List<Row> rows) {
        this.rows = rows;
        requestLayout();
        invalidate();
    }

    void setOnPick(OnPick l) { listener = l; }

    private float dp(float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    /** Version couleur : textes et icônes un peu plus petits, pour un rendu plus fin. */
    private static final float SCALE = Ui.COLOR ? 0.9f : 1f;

    private float sp(float v) {
        return SCALE * TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, getResources().getDisplayMetrics());
    }

    private float rowH() { return dp(66); }

    @Override
    protected void onMeasure(int w, int h) {
        setMeasuredDimension(MeasureSpec.getSize(w), Math.round((rows == null ? 0 : rows.size()) * rowH()));
    }

    @Override
    protected void onDraw(Canvas c) {
        c.drawColor(Ui.BG);
        if (rows == null) return;
        float w = getWidth(), rh = rowH(), pad = dp(4);
        // Colonnes : jour, icône, température et vent, soleil et pluie, chevron.
        float xIcon = w * 0.26f, xTemp = w * 0.39f, xSun = w * 0.64f, xChev = w - dp(8);
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            float y0 = i * rh, y1 = y0 + rh * 0.42f, y2 = y0 + rh * 0.8f;
            if (Ui.COLOR && i % 2 == 1) {
                fill.setColor(ALT_BG);
                c.drawRect(0, y0, w, y0 + rh, fill);
            }

            // Jour (gras le week-end) et date.
            text.setColor(Ui.INK);
            text.setTextAlign(Paint.Align.LEFT);
            text.setTypeface(Typeface.create("sans-serif", r.weekend ? Typeface.BOLD : Typeface.NORMAL));
            text.setTextSize(sp(19));
            c.drawText(r.day, pad, y1, text);
            text.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
            text.setTextSize(sp(16));
            text.setColor(SOFT);
            c.drawText(r.date, pad, y2, text);

            float size = rh * 0.74f * (Ui.COLOR ? 0.86f : 1f);
            WeatherIcon.draw(c, r.icon, false, xIcon, y0 + rh / 2f, size, Ui.INK, Ui.COLOR && i % 2 == 1 ? ALT_BG : Ui.BG);

            // Température maximale (flèche vers le haut) et vent (flèche dans le sens du vent).
            float ic = dp(9) * SCALE;
            text.setColor(Ui.INK);
            arrowUp(c, xTemp + ic / 2, y1 - sp(19) * 0.35f, ic);
            text.setTextSize(sp(21));
            c.drawText(r.tmax, xTemp + ic + dp(8), y1, text);
            if (!Double.isNaN(r.windDir)) windArrow(c, xTemp + ic / 2, y2 - sp(16) * 0.35f, ic * 0.75f, r.windDir);
            unit(c, r.wind, "km/h", xTemp + ic + dp(8), y2);

            // Ensoleillement et pluie.
            sunIcon(c, xSun + ic / 2, y1 - sp(19) * 0.35f, ic);
            text.setTextSize(sp(18));
            text.setColor(Ui.INK);
            c.drawText(r.sun, xSun + ic + dp(8), y1, text);
            drop(c, xSun + ic / 2, y2 - sp(16) * 0.35f, ic);
            text.setTextSize(sp(17));
            c.drawText(r.rain, xSun + ic + dp(8), y2, text);

            // Chevron : la ligne s'ouvre.
            line.setColor(Ui.INK);
            line.setStrokeWidth(dp(2));
            float cy = y0 + rh / 2, cs = dp(6);
            c.drawLine(xChev - cs * 0.6f, cy - cs, xChev + cs * 0.4f, cy, line);
            c.drawLine(xChev + cs * 0.4f, cy, xChev - cs * 0.6f, cy + cs, line);

            if (!Ui.COLOR) {
                fill.setColor(Ui.LINE);
                c.drawRect(0, y0 + rh - Math.max(1, dp(1)), w, y0 + rh, fill);
            }
        }
    }

    /** Valeur suivie de son unité en plus petit. */
    private void unit(Canvas c, String value, String u, float x, float y) {
        if (value.isEmpty()) return;
        text.setColor(Ui.INK);
        text.setTextSize(sp(18));
        c.drawText(value, x, y, text);
        float vx = x + text.measureText(value) + dp(3);
        text.setTextSize(sp(13));
        c.drawText(u, vx, y, text);
    }

    private void arrowUp(Canvas c, float cx, float cy, float s) {
        line.setColor(SOFT);
        line.setStrokeWidth(dp(1.6f));
        c.drawLine(cx, cy + s * 0.6f, cx, cy - s * 0.6f, line);
        c.drawLine(cx - s * 0.4f, cy - s * 0.2f, cx, cy - s * 0.6f, line);
        c.drawLine(cx + s * 0.4f, cy - s * 0.2f, cx, cy - s * 0.6f, line);
    }

    /** Flèche indiquant où va le vent (il vient de {@code from} degrés). */
    private void windArrow(Canvas c, float cx, float cy, float r, double from) {
        c.save();
        c.rotate((float) from + 180, cx, cy);
        Path p = new Path();
        p.moveTo(cx, cy - r);
        p.lineTo(cx + r * 0.62f, cy + r * 0.8f);
        p.lineTo(cx, cy + r * 0.42f);
        p.lineTo(cx - r * 0.62f, cy + r * 0.8f);
        p.close();
        line.setColor(SOFT);
        line.setStrokeWidth(dp(1.4f));
        c.drawPath(p, line);
        c.restore();
    }

    private void sunIcon(Canvas c, float cx, float cy, float s) {
        line.setColor(Ui.COLOR ? Ui.SUN : Ui.INK);
        line.setStrokeWidth(dp(1.4f));
        c.drawCircle(cx, cy, s * 0.32f, line);
        for (int k = 0; k < 8; k++) {
            double a = Math.PI / 4 * k;
            float dx = (float) Math.cos(a), dy = (float) Math.sin(a);
            c.drawLine(cx + dx * s * 0.5f, cy + dy * s * 0.5f, cx + dx * s * 0.72f, cy + dy * s * 0.72f, line);
        }
    }

    private void drop(Canvas c, float cx, float cy, float s) {
        Path p = new Path();
        p.moveTo(cx, cy - s * 0.65f);
        p.quadTo(cx + s * 0.55f, cy + s * 0.05f, cx + s * 0.4f, cy + s * 0.35f);
        p.quadTo(cx, cy + s * 0.75f, cx - s * 0.4f, cy + s * 0.35f);
        p.quadTo(cx - s * 0.55f, cy + s * 0.05f, cx, cy - s * 0.65f);
        line.setColor(Ui.COLOR ? Ui.RAIN : Ui.INK);
        line.setStrokeWidth(dp(1.4f));
        c.drawPath(p, line);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_DOWN) {
            downY = e.getY();
            return true;
        }
        if (e.getAction() == MotionEvent.ACTION_UP && rows != null && listener != null
                && Math.abs(e.getY() - downY) < dp(12)) {
            int i = (int) (e.getY() / rowH());
            if (i >= 0 && i < rows.size()) listener.pick(i);
        }
        return true;
    }
}
