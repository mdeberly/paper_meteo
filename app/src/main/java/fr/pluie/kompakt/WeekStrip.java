package fr.pluie.kompakt;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;

import java.util.List;

/** Les 8 jours (hier → J+6) en 2 lignes de 4 cases, avec le cumul de chaque jour. Toucher une case l'ouvre. */
public class WeekStrip extends View {
    public interface OnPick { void pick(int index); }

    private List<Day> days;
    private int selected;
    private OnPick listener;

    private final Paint ink = new Paint();
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint value = new Paint(Paint.ANTI_ALIAS_FLAG);

    public WeekStrip(Context c) {
        super(c);
        ink.setColor(Color.BLACK);
        label.setTextAlign(Paint.Align.CENTER);
        label.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        value.setTextAlign(Paint.Align.CENTER);
        value.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
    }

    public void setOnPick(OnPick l) { listener = l; }

    public void setDays(List<Day> days, int selected) {
        this.days = days;
        this.selected = selected;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        c.drawColor(Color.WHITE);
        if (days == null) return;
        float w = getWidth(), h = getHeight();
        float cw = w / 4f, ch = h / 2f;
        float line = Math.max(1, h * 0.012f);
        label.setTextSize(ch * 0.32f);
        value.setTextSize(ch * 0.36f);

        for (int i = 0; i < days.size() && i < 8; i++) {
            float x0 = (i % 4) * cw, y0 = (i / 4) * ch;
            boolean sel = i == selected;
            ink.setColor(Color.BLACK);
            if (sel) {
                c.drawRect(x0, y0, x0 + cw, y0 + ch, ink);
            } else {
                // Cadre de la case.
                c.drawRect(x0, y0, x0 + cw, y0 + line, ink);
                c.drawRect(x0, y0 + ch - line, x0 + cw, y0 + ch, ink);
                c.drawRect(x0, y0, x0 + line, y0 + ch, ink);
                c.drawRect(x0 + cw - line, y0, x0 + cw, y0 + ch, ink);
            }
            int fg = sel ? Color.WHITE : Color.BLACK;
            label.setColor(fg);
            value.setColor(fg);
            Day d = days.get(i);
            float cx = x0 + cw / 2f;
            c.drawText(Fmt.shortLabel(i, d.date), cx, y0 + ch * 0.42f, label);
            String v = d.hasAny() ? Fmt.total(d.total()) + " mm" : "n/d";
            c.drawText(v, cx, y0 + ch * 0.84f, value);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_DOWN) return true;
        if (e.getAction() == MotionEvent.ACTION_UP && listener != null && days != null) {
            int col = (int) (e.getX() / (getWidth() / 4f));
            int row = (int) (e.getY() / (getHeight() / 2f));
            int idx = Math.max(0, Math.min(3, col)) + 4 * Math.max(0, Math.min(1, row));
            if (idx < days.size()) listener.pick(idx);
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
