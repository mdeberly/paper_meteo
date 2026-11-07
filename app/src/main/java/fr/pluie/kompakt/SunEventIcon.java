package fr.pluie.kompakt;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/** Petite icône de lever ou de coucher du soleil : demi-soleil sur l'horizon, flèche vers le haut ou le bas. */
final class SunEventIcon extends Drawable {
    private final boolean rise;
    private final int size;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

    SunEventIcon(boolean rise, int sizePx) {
        this.rise = rise;
        this.size = sizePx;
        setBounds(0, 0, sizePx, sizePx);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
    }

    @Override
    public void draw(Canvas c) {
        float s = size, x0 = getBounds().left, y0 = getBounds().top;
        float cx = x0 + s / 2, horizon = y0 + s * 0.72f, r = s * 0.24f, sw = Math.max(1.5f, s * 0.08f);
        int col = Ui.COLOR ? 0xFFF2B200 : Ui.INK;
        // Demi-soleil plein au-dessus de l'horizon, avec trois rayons.
        p.setStyle(Paint.Style.FILL);
        p.setColor(col);
        c.drawArc(new RectF(cx - r, horizon - r, cx + r, horizon + r), 180, 180, true, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(sw);
        for (int k = 0; k < 3; k++) {
            double a = Math.toRadians(225 + 45 * k);
            float dx = (float) Math.cos(a), dy = (float) Math.sin(a);
            c.drawLine(cx + dx * r * 1.45f, horizon + dy * r * 1.45f, cx + dx * r * 1.95f, horizon + dy * r * 1.95f, p);
        }
        // Horizon.
        p.setColor(Ui.INK);
        c.drawLine(x0 + s * 0.06f, horizon, x0 + s * 0.94f, horizon, p);
        // Flèche sous l'horizon : vers le haut au lever, vers le bas au coucher.
        float ay0 = horizon + s * 0.1f, ay1 = y0 + s * 0.98f, aw = s * 0.12f;
        float tip = rise ? ay0 : ay1, base = rise ? ay1 : ay0, back = rise ? tip + aw : tip - aw;
        c.drawLine(cx, base, cx, tip, p);
        c.drawLine(cx - aw, back, cx, tip, p);
        c.drawLine(cx + aw, back, cx, tip, p);
    }

    @Override public void setAlpha(int alpha) { p.setAlpha(alpha); }
    @Override public void setColorFilter(ColorFilter cf) { p.setColorFilter(cf); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    @Override public int getIntrinsicWidth() { return size; }
    @Override public int getIntrinsicHeight() { return size; }
}
