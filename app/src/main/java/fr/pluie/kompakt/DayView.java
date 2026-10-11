package fr.pluie.kompakt;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.View;

/**
 * Les 24 créneaux d'une journée sur un seul écran, sans défilement :
 * 2 colonnes de 12 lignes (0h–11h à gauche, 12h–23h à droite).
 * Sert pour la pluie (mm) comme pour l'ensoleillement (minutes).
 * Noir et blanc pur pour l'E Ink ; le créneau en cours est inversé.
 */
public class DayView extends View {
    /** Mise en forme d'une valeur horaire ("–" quand elle est nulle). */
    public interface Format { String value(double v); }

    private Series series;
    private double scale = 2, min;
    private Format format;
    private boolean[] night;
    private int highlightHour = -1;

    private final Paint ink = new Paint();
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bold = new Paint(Paint.ANTI_ALIAS_FLAG);

    public DayView(Context c) {
        super(c);
        ink.setColor(Color.BLACK);
        ink.setStyle(Paint.Style.FILL);
        text.setColor(Color.BLACK);
        text.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        bold.setColor(Color.BLACK);
        bold.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        bold.setTextAlign(Paint.Align.RIGHT);
    }

    /**
     * @param scale valeur horaire correspondant à une barre pleine
     * @param min   en dessous, pas de barre (valeur considérée comme nulle)
     * @param night créneaux de nuit, laissés vides (null : aucun)
     */
    public void setSeries(Series series, double scale, double min, Format format, boolean[] night, int highlightHour) {
        this.series = series;
        this.scale = scale;
        this.min = min;
        this.format = format;
        this.night = night;
        this.highlightHour = highlightHour;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        c.drawColor(Color.WHITE);
        if (series == null) return;

        float cw = w / 2f, ch = h / 12f;
        float pad = Math.max(4, cw * 0.04f);
        float ts = ch * 0.46f;
        text.setTextSize(ts);
        bold.setTextSize(ts * 1.05f);
        float line = Math.max(1, ch * 0.03f);

        for (int i = 0; i < 24; i++) {
            int col = i / 12, row = i % 12;
            float x0 = col * cw, y0 = row * ch;
            boolean inv = i == highlightHour;

            if (inv) {
                ink.setColor(Color.BLACK);
                c.drawRect(x0, y0, x0 + cw, y0 + ch, ink);
            }
            int fg = inv ? Color.WHITE : Color.BLACK;
            text.setColor(fg);
            bold.setColor(fg);
            ink.setColor(fg);

            float baseline = y0 + ch / 2f + ts * 0.36f;
            c.drawText(String.format(Fmt.FR, "%02dh", i), x0 + pad, baseline, text);

            boolean dark = night != null && night[i];
            String val = !series.has[i] ? "n/d" : dark ? "" : format.value(series.v[i]);
            c.drawText(val, x0 + cw - pad, baseline, bold);

            // Barre proportionnelle, entre l'heure et la valeur.
            float barL = x0 + pad + ts * 1.9f;
            float barR = x0 + cw - pad - ts * 1.7f;
            if (series.has[i] && !dark && series.v[i] >= min && barR > barL) {
                double f = Math.min(1.0, series.v[i] / scale);
                float bw = Math.max(line * 3, (float) ((barR - barL) * f));
                float bh = ch * 0.38f;
                c.drawRect(barL, y0 + (ch - bh) / 2f, barL + bw, y0 + (ch + bh) / 2f, ink);
            }

            // Séparateur de ligne.
            ink.setColor(Color.BLACK);
            if (row < 11 && !inv) c.drawRect(x0, y0 + ch - line, x0 + cw, y0 + ch, ink);
        }
        // Séparateur des deux colonnes.
        ink.setColor(Color.BLACK);
        c.drawRect(cw - line, 0, cw + line, h, ink);
    }
}
