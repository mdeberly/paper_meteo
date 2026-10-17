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
        return px(header ? 54 : 60);
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
            // Petite icône au-dessus de chaque colonne, puis le titre en gras et l'unité.
            float h = getHeight();
            float ic = h * 0.3f, iy = h * 0.2f, y1 = h * 0.6f, y2 = h * 0.88f;
            bold.setTextSize(sp(14));
            text.setTextSize(sp(12));
            text.setColor(Ui.MUTED);
            bold.setColor(Ui.INK);
            bold.setTextAlign(Paint.Align.RIGHT);
            text.setTextAlign(Paint.Align.RIGHT);
            title(c, SUN_ICON, L.t("col_sun"), "min", w * SUN, iy, ic, y1, y2);
            title(c, DROP_ICON, L.t("col_rain"), "mm", w * RAIN, iy, ic, y1, y2);
            title(c, THERMO_ICON, L.t("col_temp"), Fmt.unit(), w * TEMP, iy, ic, y1, y2);
            bold.setTextAlign(Paint.Align.CENTER);
            text.setTextAlign(Paint.Align.CENTER);
            title(c, WIND_ICON, L.t("col_wind"), "km/h", w * WIND, iy, ic, y1, y2);
            bold.setTextAlign(Paint.Align.LEFT);
            String hourName = L.t("col_hour");
            columnIcon(c, CLOCK_ICON, pad + bold.measureText(hourName) / 2f, iy, ic);
            c.drawText(hourName, pad, y1, bold);
            bold.setTextSize(sp(22));
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

    private static final int CLOCK_ICON = 0, SUN_ICON = 1, DROP_ICON = 2, THERMO_ICON = 3, WIND_ICON = 4;

    /** Titre de colonne : icône centrée au-dessus du nom (en gras), unité en dessous. Alignement selon les pinceaux. */
    private void title(Canvas c, int icon, String name, String unit, float x, float iy, float ic, float y1, float y2) {
        float nw = bold.measureText(name);
        float cx = bold.getTextAlign() == Paint.Align.RIGHT ? x - nw / 2f : x;
        columnIcon(c, icon, cx, iy, ic);
        c.drawText(name, x, y1, bold);
        c.drawText(unit, x, y2, text);
    }

    /** Icônes des titres : horloge, soleil, goutte, thermomètre, vent, dans un carré de côté s centré en (cx, cy). */
    private void columnIcon(Canvas c, int icon, float cx, float cy, float s) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        float r = s / 2f, sw = Math.max(1.5f, s * 0.1f);
        p.setStrokeWidth(sw);
        switch (icon) {
            case CLOCK_ICON:
                p.setColor(Ui.INK);
                p.setStyle(Paint.Style.STROKE);
                c.drawCircle(cx, cy, r * 0.88f, p);
                c.drawLine(cx, cy, cx, cy - r * 0.55f, p);
                c.drawLine(cx, cy, cx + r * 0.42f, cy + r * 0.12f, p);
                break;
            case SUN_ICON:
                p.setColor(Ui.COLOR ? Ui.SUN : Ui.INK);
                p.setStyle(Paint.Style.FILL);
                c.drawCircle(cx, cy, r * 0.42f, p);
                p.setStyle(Paint.Style.STROKE);
                for (int i = 0; i < 8; i++) {
                    double a = Math.PI / 4 * i;
                    float dx = (float) Math.cos(a), dy = (float) Math.sin(a);
                    c.drawLine(cx + dx * r * 0.66f, cy + dy * r * 0.66f, cx + dx * r, cy + dy * r, p);
                }
                break;
            case DROP_ICON: {
                p.setColor(Ui.COLOR ? Ui.RAIN : Ui.INK);
                p.setStyle(Paint.Style.FILL);
                float br = r * 0.58f, by = cy + r * 0.32f;
                Path d = new Path();
                d.moveTo(cx, cy - r);
                d.lineTo(cx + br * 0.92f, by - br * 0.4f);
                d.lineTo(cx - br * 0.92f, by - br * 0.4f);
                d.close();
                c.drawPath(d, p);
                c.drawCircle(cx, by, br, p);
                break;
            }
            case THERMO_ICON: {
                p.setColor(Ui.COLOR ? Ui.ACCENT : Ui.INK);
                float tw = r * 0.26f, br = r * 0.36f, by = cy + r * 0.62f;
                p.setStyle(Paint.Style.STROKE);
                c.drawRoundRect(cx - tw, cy - r, cx + tw, by, tw, tw, p);
                p.setStyle(Paint.Style.FILL);
                c.drawCircle(cx, by, br, p);
                c.drawRect(cx - tw * 0.4f, cy - r * 0.3f, cx + tw * 0.4f, by, p);
                break;
            }
            default: {
                // Vent : trois traits horizontaux, deux terminés par une boucle.
                p.setColor(Ui.COLOR ? Ui.CLOUD : Ui.INK);
                p.setStyle(Paint.Style.STROKE);
                Path wp = new Path();
                wp.moveTo(cx - r, cy - r * 0.4f);
                wp.lineTo(cx + r * 0.45f, cy - r * 0.4f);
                wp.arcTo(cx + r * 0.2f, cy - r * 0.9f, cx + r * 0.7f, cy - r * 0.4f, 90, -270, false);
                wp.moveTo(cx - r, cy + r * 0.05f);
                wp.lineTo(cx + r * 0.8f, cy + r * 0.05f);
                wp.moveTo(cx - r * 0.7f, cy + r * 0.5f);
                wp.lineTo(cx + r * 0.25f, cy + r * 0.5f);
                wp.arcTo(cx, cy + r * 0.5f, cx + r * 0.5f, cy + r, -90, 270, false);
                c.drawPath(wp, p);
                break;
            }
        }
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
