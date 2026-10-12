package fr.pluie.kompakt;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/** Roue crantée (accès aux paramètres), dessinée en noir. */
public class GearView extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

    public GearView(Context c) {
        super(c);
        setClickable(true);
        setContentDescription(L.t("settings"));
    }

    @Override
    protected void onDraw(Canvas c) {
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        float r = Math.min(getWidth(), getHeight()) * 0.42f;
        int teeth = 8;
        Path gear = new Path();
        // Contour : alternance de dents (rayon r) et de creux (rayon 0,76 r).
        for (int i = 0; i < teeth * 4; i++) {
            double a = Math.PI * 2 * i / (teeth * 4) - Math.PI / (teeth * 4);
            float rr = (i % 4 == 0 || i % 4 == 1) ? r : r * 0.76f;
            float x = cx + (float) Math.cos(a) * rr, y = cy + (float) Math.sin(a) * rr;
            if (i == 0) gear.moveTo(x, y);
            else gear.lineTo(x, y);
        }
        gear.close();
        p.setColor(Color.BLACK);
        p.setStyle(Paint.Style.FILL);
        c.drawPath(gear, p);
        p.setColor(Color.WHITE);
        c.drawCircle(cx, cy, r * 0.34f, p);
    }
}
