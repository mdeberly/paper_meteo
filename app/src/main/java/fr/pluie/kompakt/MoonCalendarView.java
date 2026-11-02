package fr.pluie.kompakt;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;

/**
 * Calendrier lunaire d'un mois : en tête « ‹ novembre 2026 › », initiales des jours, puis chaque jour
 * avec son numéro et la Lune telle qu'elle sera vers 22 h. Aujourd'hui est en gras, le jour choisi
 * encadré ; toucher un jour le choisit. Légende des quatre phases principales en bas.
 * Navigation de janvier de l'année en cours à décembre de l'année suivante.
 */
public class MoonCalendarView extends View {
    public interface OnPick { void pick(LocalDate d); }

    private YearMonth month = YearMonth.now();
    private LocalDate today = LocalDate.now(), selected = LocalDate.now();
    private ZoneId zone = ZoneId.systemDefault();
    private boolean south;
    private OnPick listener;

    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);

    public MoonCalendarView(Context c) {
        super(c);
        text.setColor(Ui.INK);
        text.setTextAlign(Paint.Align.CENTER);
        line.setStyle(Paint.Style.STROKE);
        line.setColor(Ui.INK);
    }

    void setOnPick(OnPick l) { listener = l; }

    /** Affiche le mois du jour choisi. */
    void set(LocalDate today, LocalDate selected, ZoneId zone, boolean south) {
        this.today = today;
        this.selected = selected;
        this.zone = zone;
        this.south = south;
        this.month = clamp(YearMonth.from(selected));
        requestLayout();
        invalidate();
    }

    private YearMonth first() { return YearMonth.of(today.getYear(), 1); }
    private YearMonth last() { return YearMonth.of(today.getYear() + 1, 12); }

    private YearMonth clamp(YearMonth m) {
        return m.isBefore(first()) ? first() : m.isAfter(last()) ? last() : m;
    }

    private float dp(float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    private float sp(float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, getResources().getDisplayMetrics());
    }

    // Hauteurs : en-tête du mois, initiales, une ligne par semaine, légende.
    private float headH() { return dp(44); }
    private float namesH() { return dp(26); }
    private float rowH() { return dp(60); }
    private float legendH() { return dp(36); }

    private DayOfWeek firstDay() {
        // Semaine du dimanche en anglais, du lundi dans les autres langues de l'appli.
        return L.lang() == L.EN ? DayOfWeek.SUNDAY : DayOfWeek.MONDAY;
    }

    /** Colonne (0..6) du 1er du mois. */
    private int offset() {
        return (month.atDay(1).getDayOfWeek().getValue() - firstDay().getValue() + 7) % 7;
    }

    private int rows() {
        return (offset() + month.lengthOfMonth() + 6) / 7;
    }

    @Override
    protected void onMeasure(int w, int h) {
        setMeasuredDimension(MeasureSpec.getSize(w), Math.round(headH() + namesH() + rows() * rowH() + legendH()));
    }

    @Override
    protected void onDraw(Canvas c) {
        c.drawColor(Ui.BG);
        float w = getWidth(), cw = w / 7;

        // Mois et flèches.
        text.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        text.setTextSize(sp(21));
        String title = Fmt.cap(month.atDay(1).format(DateTimeFormatter.ofPattern("LLLL yyyy", L.locale())));
        c.drawText(title, w / 2, headH() * 0.66f, text);
        text.setTextSize(sp(26));
        if (month.isAfter(first())) c.drawText("‹", cw / 2, headH() * 0.68f, text);
        if (month.isBefore(last())) c.drawText("›", w - cw / 2, headH() * 0.68f, text);

        // Initiales des jours.
        text.setTextSize(sp(16));
        for (int i = 0; i < 7; i++) {
            DayOfWeek d = firstDay().plus(i);
            String n = d.getDisplayName(TextStyle.NARROW_STANDALONE, L.locale()).toUpperCase(L.locale());
            c.drawText(n, cw * i + cw / 2, headH() + namesH() * 0.7f, text);
        }

        // Jours.
        float top = headH() + namesH(), rh = rowH(), r = Math.min(cw, rh) * 0.3f;
        float stroke = Math.max(1, dp(1.2f));
        for (int day = 1; day <= month.lengthOfMonth(); day++) {
            LocalDate d = month.atDay(day);
            int k = offset() + day - 1;
            float x = cw * (k % 7) + cw / 2, y0 = top + rh * (k / 7);
            boolean isToday = d.equals(today);
            text.setTypeface(Typeface.create("sans-serif", isToday ? Typeface.BOLD : Typeface.NORMAL));
            text.setTextSize(sp(isToday ? 17 : 16));
            c.drawText(String.valueOf(day), x, y0 + rh * 0.3f, text);
            double elong = MoonPhase.at(d.atTime(22, 0).atZone(zone).toInstant()).elong;
            MoonView.draw(c, x, y0 + rh * 0.68f, r, elong, south, Ui.MOON_EDGE, stroke);
            if (d.equals(selected)) {
                line.setStrokeWidth(dp(2));
                c.drawRect(x - cw / 2 + dp(2), y0 + dp(2), x + cw / 2 - dp(2), y0 + rh - dp(1), line);
            }
        }

        // Légende des phases principales.
        float ly = getHeight() - legendH() / 2;
        String[] names = L.t("moon_short").split("\\|");
        double[] el = {0, 90, 180, 270};
        text.setTypeface(Typeface.DEFAULT);
        text.setTextSize(sp(14));
        text.setTextAlign(Paint.Align.LEFT);
        float lr = dp(8), gap = dp(5), total = 0;
        for (String n : names) total += lr * 2 + gap + text.measureText(n) + dp(14);
        if (total > w) {
            // Légende trop large : texte réduit pour tenir sur la ligne.
            text.setTextSize(sp(14) * (w - names.length * (lr * 2 + gap + dp(14))) / (total - names.length * (lr * 2 + gap + dp(14))));
            total = 0;
            for (String n : names) total += lr * 2 + gap + text.measureText(n) + dp(14);
        }
        float x = Math.max(0, (w - total + dp(14)) / 2);
        for (int i = 0; i < 4 && i < names.length; i++) {
            MoonView.draw(c, x + lr, ly, lr, el[i], south, Ui.MOON_EDGE, stroke);
            x += lr * 2 + gap;
            c.drawText(names[i], x, ly + sp(5), text);
            x += text.measureText(names[i]) + dp(14);
        }
        text.setTextAlign(Paint.Align.CENTER);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_DOWN) return true;
        if (e.getAction() != MotionEvent.ACTION_UP) return super.onTouchEvent(e);
        float w = getWidth(), cw = w / 7, x = e.getX(), y = e.getY();
        if (y < headH()) {
            // Flèches : mois précédent / suivant.
            if (x < cw * 1.5f && month.isAfter(first())) month = month.minusMonths(1);
            else if (x > w - cw * 1.5f && month.isBefore(last())) month = month.plusMonths(1);
            else return true;
            requestLayout();
            invalidate();
            return true;
        }
        float top = headH() + namesH();
        int row = (int) ((y - top) / rowH()), col = (int) (x / cw);
        if (y < top || row >= rows() || col > 6) return true;
        int day = row * 7 + col - offset() + 1;
        if (day < 1 || day > month.lengthOfMonth()) return true;
        selected = month.atDay(day);
        invalidate();
        if (listener != null) listener.pick(selected);
        return true;
    }
}
