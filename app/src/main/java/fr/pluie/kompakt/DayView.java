package fr.pluie.kompakt;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.View;

import java.util.List;

/**
 * La journée en une seule colonne, une ligne par créneau (heure par heure, la nuit regroupée
 * en 0–3h et 3–6h) : heure, icône météo, soleil, pluie, température, vent et sa direction.
 * La vue prend la hauteur de toutes ses lignes et se place dans un ScrollView.
 * En mode en-tête, elle ne dessine que les titres des colonnes (à garder fixe au-dessus).
 * Noir et blanc pur pour l'E Ink ; le créneau en cours est inversé.
 */
public class DayView extends View {

    /** Une ligne de la liste. */
    public static final class Cell {
        String label = "", sun = "", rain = "", temp = "", wind = "";
        /** D'où vient le vent, en degrés, ou NaN. */
        double windDir = Double.NaN;
        int icon = Weather.NONE;
        boolean iconNight, highlight;
    }

    // Colonnes, en fraction de la largeur : centre de l'icône, bord droit des valeurs (alignées à droite),
    // centre de la colonne du vent (direction au-dessus, vitesse en dessous).
    private static final float ICON = 0.265f, SUN = 0.47f, RAIN = 0.63f, TEMP = 0.785f, WIND = 0.905f;

    private final boolean header;
    private List<Cell> cells;

    private final Paint ink = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bold = new Paint(Paint.ANTI_ALIAS_FLAG);

    public DayView(Context c, boolean header) {
        super(c);
        this.header = header;
        ink.setColor(Ui.INK);
        text.setColor(Ui.INK);
        text.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        bold.setColor(Ui.INK);
        bold.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
    }

    public void setCells(List<Cell> cells) {
        this.cells = cells;
        requestLayout();
        invalidate();
    }

    /** Hauteur d'une ligne, en pixels. */
    public int rowHeight() {
        return px(header ? 32 : 60);
    }

    private int px(float dp) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics()));
    }

    private float sp(float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, getResources().getDisplayMetrics());
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int rows = header ? 1 : (cells == null ? 0 : cells.size());
        setMeasuredDimension(MeasureSpec.getSize(widthSpec), rows * rowHeight());
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth();
        c.drawColor(Ui.BG);
        float line = Math.max(1, px(1));
        float pad = px(4);

        if (header) {
            text.setColor(Ui.MUTED);
            text.setTextSize(sp(12));
            text.setTextAlign(Paint.Align.RIGHT);
            float y1 = getHeight() * 0.45f, y2 = getHeight() * 0.85f;
            title(c, L.t("col_sun"), "min", w * SUN, y1, y2);
            title(c, L.t("col_rain"), "mm", w * RAIN, y1, y2);
            title(c, L.t("col_temp"), Fmt.unit(), w * TEMP, y1, y2);
            text.setTextAlign(Paint.Align.CENTER);
            title(c, L.t("col_wind"), "km/h", w * WIND, y1, y2);
            text.setTextAlign(Paint.Align.LEFT);
            c.drawText(L.t("col_hour"), pad, y2, text);
            ink.setColor(Ui.LINE);
            c.drawRect(0, getHeight() - px(Ui.LINE_DP), w, getHeight(), ink);
            return;
        }
        if (cells == null) return;

        float ch = rowHeight();
        float ts = sp(22);
        float small = sp(18);
        bold.setTextSize(ts);
        float iconSize = ch * 0.8f;

        for (int i = 0; i < cells.size(); i++) {
            Cell cell = cells.get(i);
            float y0 = i * ch;
            boolean inv = cell.highlight;
            int fg = inv ? Ui.HL_FG : Ui.INK, bg = inv ? Ui.HL_BG : Ui.BG;
            if (inv) {
                ink.setColor(Ui.HL_BG);
                c.drawRect(0, y0, w, y0 + ch, ink);
            }
            text.setColor(fg);
            bold.setColor(fg);
            float base = y0 + ch / 2f + ts * 0.36f;

            // Heure, réduite si besoin pour ne pas toucher l'icône (« 10 am », « 07:00 »…).
            text.setTextSize(ts);
            float room = w * ICON - iconSize / 2f - pad * 2;
            float lw = text.measureText(cell.label);
            if (lw > room) text.setTextSize(ts * room / lw);
            text.setTextAlign(Paint.Align.LEFT);
            c.drawText(cell.label, pad, base, text);
            text.setTextSize(ts);
            WeatherIcon.draw(c, cell.icon, cell.iconNight, w * ICON, y0 + ch / 2f, iconSize, fg, bg);

            text.setTextAlign(Paint.Align.RIGHT);
            bold.setTextAlign(Paint.Align.RIGHT);
            c.drawText(cell.sun, w * SUN, base, text);
            // Pluie en bleu dans la variante couleur, seulement quand il y a une quantité.
            boolean amount = !cell.rain.equals("–") && !cell.rain.equals(L.t("nd"));
            bold.setColor(amount && (Ui.COLOR || !inv) ? Ui.RAIN_TEXT : fg);
            // Valeur réduite si elle ne tient pas entre les colonnes Soleil et Pluie (« 0,3 cm »).
            float rainRoom = w * (RAIN - SUN) - pad * 2;
            float rw = bold.measureText(cell.rain);
            if (rw > rainRoom) bold.setTextSize(ts * rainRoom / rw);
            c.drawText(cell.rain, w * RAIN, base, bold);
            bold.setTextSize(ts);
            bold.setColor(fg);
            c.drawText(cell.temp, w * TEMP, base, bold);

            // Vent : flèche et direction sur la moitié haute, vitesse sur la moitié basse.
            text.setTextSize(small);
            float wx = w * WIND;
            if (!Double.isNaN(cell.windDir)) {
                String dir = Fmt.compass(cell.windDir);
                float r = ch * 0.17f;
                float tw = text.measureText(dir);
                float gap = r * 0.6f;
                float left = wx - (2 * r + gap + tw) / 2f;
                arrow(c, left + r, y0 + ch * 0.3f, r, cell.windDir, fg);
                text.setTextAlign(Paint.Align.LEFT);
                c.drawText(dir, left + 2 * r + gap, y0 + ch * 0.3f + small * 0.36f, text);
            }
            text.setTextAlign(Paint.Align.CENTER);
            c.drawText(cell.wind, wx, y0 + ch * 0.74f + small * 0.36f, text);

            ink.setColor(Ui.LINE);
            if (!inv) c.drawRect(0, y0 + ch - line, w, y0 + ch, ink);
        }
    }

    private void title(Canvas c, String name, String unit, float right, float y1, float y2) {
        c.drawText(name, right, y1, text);
        c.drawText(unit, right, y2, text);
    }

    /** Flèche indiquant où va le vent (il vient de {@code from} degrés). */
    private void arrow(Canvas c, float cx, float cy, float r, double from, int color) {
        c.save();
        c.rotate((float) from + 180, cx, cy); // 0° = flèche vers le haut (vent qui va vers le nord)
        Path p = new Path();
        p.moveTo(cx, cy - r);
        p.lineTo(cx + r * 0.62f, cy + r * 0.8f);
        p.lineTo(cx, cy + r * 0.42f);
        p.lineTo(cx - r * 0.62f, cy + r * 0.8f);
        p.close();
        ink.setColor(color);
        ink.setStyle(Paint.Style.FILL);
        c.drawPath(p, ink);
        c.restore();
    }
}
