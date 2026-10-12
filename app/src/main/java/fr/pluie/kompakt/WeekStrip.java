package fr.pluie.kompakt;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;

/**
 * Les 8 jours (hier → J+6) en 2 lignes de 4 cases. Chaque case montre le jour, une valeur
 * (cumul de pluie, durée d'ensoleillement) ou une petite lune, et les températures mini / maxi.
 * Toucher une case l'ouvre.
 */
public class WeekStrip extends View {
    public interface OnPick { void pick(int index); }

    private String[] labels, values, temps;
    private double[] moons;
    private boolean south;
    private int selected;
    private OnPick listener;

    private final Paint ink = new Paint();
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint value = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint small = new Paint(Paint.ANTI_ALIAS_FLAG);

    public WeekStrip(Context c) {
        super(c);
        ink.setColor(Ui.LINE);
        label.setTextAlign(Paint.Align.CENTER);
        label.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        value.setTextAlign(Paint.Align.CENTER);
        value.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        small.setTextAlign(Paint.Align.CENTER);
        small.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
    }

    public void setOnPick(OnPick l) { listener = l; }

    /**
     * @param moons élongations lunaires à dessiner à la place des valeurs (ou null)
     * @param temps "6° / 14°" pour chaque jour
     */
    public void setCells(String[] labels, String[] values, double[] moons, String[] temps, boolean south, int selected) {
        this.labels = labels;
        this.values = values;
        this.temps = temps;
        this.moons = moons;
        this.south = south;
        this.selected = selected;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        c.drawColor(Ui.BG);
        if (labels == null) return;
        float w = getWidth(), h = getHeight();
        float cw = w / 4f, ch = h / 2f;
        float line = Math.max(1, h * 0.012f);
        label.setTextSize(ch * 0.25f);
        value.setTextSize(ch * 0.28f);
        small.setTextSize(ch * 0.23f);

        for (int i = 0; i < labels.length && i < 8; i++) {
            float x0 = (i % 4) * cw, y0 = (i / 4) * ch;
            boolean sel = i == selected;
            if (Ui.COLOR) {
                // Couleur : pas de cadres, le jour choisi dans une pastille arrondie.
                if (sel) {
                    ink.setColor(Ui.ACCENT);
                    float in = line * 2, rad = ch * 0.16f;
                    c.drawRoundRect(x0 + in, y0 + in, x0 + cw - in, y0 + ch - in, rad, rad, ink);
                }
            } else if (sel) {
                ink.setColor(Ui.ACCENT);
                c.drawRect(x0, y0, x0 + cw, y0 + ch, ink);
            } else {
                ink.setColor(Ui.LINE);
                // Cadre de la case.
                c.drawRect(x0, y0, x0 + cw, y0 + line, ink);
                c.drawRect(x0, y0 + ch - line, x0 + cw, y0 + ch, ink);
                c.drawRect(x0, y0, x0 + line, y0 + ch, ink);
                c.drawRect(x0 + cw - line, y0, x0 + cw, y0 + ch, ink);
            }
            int fg = sel ? Ui.ON_ACCENT : Ui.INK;
            label.setColor(fg);
            value.setColor(fg);
            small.setColor(fg);
            float cx = x0 + cw / 2f;
            c.drawText(labels[i], cx, y0 + ch * 0.30f, label);
            if (moons != null) {
                float r = ch * 0.13f;
                MoonView.draw(c, cx, y0 + ch * 0.51f, r, moons[i], south, fg, Math.max(1, r * 0.12f));
            } else {
                c.drawText(values[i], cx, y0 + ch * 0.61f, value);
            }
            if (temps != null) c.drawText(temps[i], cx, y0 + ch * 0.88f, small);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_DOWN) return true;
        if (e.getAction() == MotionEvent.ACTION_UP && listener != null && labels != null) {
            int col = (int) (e.getX() / (getWidth() / 4f));
            int row = (int) (e.getY() / (getHeight() / 2f));
            int idx = Math.max(0, Math.min(3, col)) + 4 * Math.max(0, Math.min(1, row));
            if (idx < labels.length) listener.pick(idx);
            performClick();
            return true;
        }
        return super.onTouchEvent(e);
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }
}
