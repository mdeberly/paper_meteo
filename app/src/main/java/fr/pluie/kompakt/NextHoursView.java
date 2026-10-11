package fr.pluie.kompakt;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.View;

import java.util.List;

/** Les prochaines heures côte à côte : heure, icône, température, pluie. */
public class NextHoursView extends View {
    private List<Weather.Hour> hours;

    private final Paint ink = new Paint();
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint value = new Paint(Paint.ANTI_ALIAS_FLAG);

    public NextHoursView(Context c) {
        super(c);
        ink.setColor(Color.BLACK);
        label.setColor(Color.BLACK);
        label.setTextAlign(Paint.Align.CENTER);
        label.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        value.setColor(Color.BLACK);
        value.setTextAlign(Paint.Align.CENTER);
        value.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
    }

    public void setHours(List<Weather.Hour> hours) {
        this.hours = hours;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        c.drawColor(Color.WHITE);
        if (hours == null || hours.isEmpty()) return;
        float w = getWidth(), h = getHeight();
        int n = hours.size();
        float cw = w / n;
        float line = Math.max(1, w * 0.004f);
        float ts = Math.min(cw * 0.26f, h * 0.15f);
        label.setTextSize(ts);
        value.setTextSize(ts * 1.05f);
        float icon = Math.min(cw * 0.8f, h * 0.38f);

        for (int i = 0; i < n; i++) {
            Weather.Hour x = hours.get(i);
            float cx = cw * i + cw / 2f;
            if (i > 0) c.drawRect(cw * i - line / 2, h * 0.05f, cw * i + line / 2, h * 0.95f, ink);
            c.drawText(i == 0 ? "Maint." : x.h + " h", cx, ts * 1.1f, label);
            WeatherIcon.draw(c, x.kind, x.night, cx, ts * 1.5f + icon / 2f, icon, Color.BLACK, Color.WHITE);
            float y = ts * 1.5f + icon + ts * 1.2f;
            c.drawText(Double.isNaN(x.temp) ? "" : Math.round(x.temp) + "°", cx, y, value);
            c.drawText(!x.has ? "n/d" : x.mm >= 0.05 ? Fmt.mm(x.mm) + " mm" : "–", cx, y + ts * 1.3f, label);
        }
    }
}
