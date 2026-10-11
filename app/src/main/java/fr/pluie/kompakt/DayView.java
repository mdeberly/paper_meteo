package fr.pluie.kompakt;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.View;

import java.util.List;

/**
 * Les créneaux d'une journée sur un seul écran, sans défilement, en 2 colonnes
 * (heure par heure, la nuit regroupée en 0–3h et 3–6h).
 * Chaque case : heure, température, icône météo (facultative), barre et valeur.
 * Noir et blanc pur pour l'E Ink ; le créneau en cours est inversé.
 */
public class DayView extends View {
    /** Mise en forme d'une valeur ("–" quand elle est nulle). */
    public interface Format { String value(double v); }

    /** Une case de la grille. */
    public static final class Cell {
        String label = "";
        String temp = "";
        String value = "";
        /** Longueur de la barre, 0..1, ou négative pour ne pas en dessiner. */
        double bar = -1;
        int icon = Weather.NONE;
        boolean iconNight, highlight;
    }

    private List<Cell> cells;
    private boolean icons;

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

    /** @param icons réserver la place d'une icône météo dans chaque case */
    public void setCells(List<Cell> cells, boolean icons) {
        this.cells = cells;
        this.icons = icons;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        c.drawColor(Color.WHITE);
        if (cells == null || cells.isEmpty()) return;

        int rows = (cells.size() + 1) / 2;
        float cw = w / 2f, ch = (float) h / rows;
        float pad = Math.max(4, cw * 0.035f);
        float ts = Math.min(ch * 0.42f, cw * 0.085f);
        text.setTextSize(ts);
        bold.setTextSize(ts * 1.05f);
        float line = Math.max(1, ch * 0.03f);
        float labelW = text.measureText("0–3h") + pad * 0.8f;
        float tempW = bold.measureText("-10°") + pad * 0.8f;
        float valueW = bold.measureText("0,0") + pad;
        float iconSize = icons ? Math.min(ch * 0.82f, cw * 0.16f) : 0;

        for (int i = 0; i < cells.size(); i++) {
            Cell cell = cells.get(i);
            int col = i / rows, row = i % rows;
            float x0 = col * cw, y0 = row * ch;
            boolean inv = cell.highlight;

            if (inv) {
                ink.setColor(Color.BLACK);
                c.drawRect(x0, y0, x0 + cw, y0 + ch, ink);
            }
            int fg = inv ? Color.WHITE : Color.BLACK;
            text.setColor(fg);
            bold.setColor(fg);
            ink.setColor(fg);

            float baseline = y0 + ch / 2f + ts * 0.36f;
            float x = x0 + pad;
            c.drawText(cell.label, x, baseline, text);
            x += labelW;
            bold.setTextAlign(Paint.Align.RIGHT);
            c.drawText(cell.temp, x + tempW - pad * 0.8f, baseline, bold);
            x += tempW;
            if (icons) {
                WeatherIcon.draw(c, cell.icon, cell.iconNight, x + iconSize / 2f, y0 + ch / 2f, iconSize,
                        fg, inv ? Color.BLACK : Color.WHITE);
                x += iconSize + pad * 0.6f;
            }
            c.drawText(cell.value, x0 + cw - pad, baseline, bold);

            // Barre proportionnelle, entre la température (ou l'icône) et la valeur.
            float barR = x0 + cw - pad - valueW;
            if (cell.bar >= 0 && barR > x) {
                float bw = Math.max(line * 3, (float) ((barR - x) * Math.min(1, cell.bar)));
                float bh = ch * 0.34f;
                c.drawRect(x, y0 + (ch - bh) / 2f, x + bw, y0 + (ch + bh) / 2f, ink);
            }

            // Séparateur de ligne.
            ink.setColor(Color.BLACK);
            if (row < rows - 1 && !inv) c.drawRect(x0, y0 + ch - line, x0 + cw, y0 + ch, ink);
        }
        // Séparateur des deux colonnes.
        ink.setColor(Color.BLACK);
        c.drawRect(cw - line, 0, cw + line, h, ink);
    }
}
