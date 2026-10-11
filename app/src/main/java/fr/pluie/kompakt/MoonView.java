package fr.pluie.kompakt;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/** La Lune dessinée en noir et blanc : partie éclairée en blanc, partie dans l'ombre en noir. */
public class MoonView extends View {
    private double elong = -1;
    private boolean south;

    public MoonView(Context c) {
        super(c);
    }

    /** @param elong 0 = nouvelle lune, 90 = premier quartier, 180 = pleine lune, 270 = dernier quartier */
    public void setPhase(double elong, boolean south) {
        this.elong = elong;
        this.south = south;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        c.drawColor(Color.WHITE);
        if (elong < 0) return;
        float stroke = Math.max(2, getWidth() * 0.015f);
        float r = Math.min(getWidth(), getHeight()) / 2f - stroke;
        draw(c, getWidth() / 2f, getHeight() / 2f, r, elong, south, Color.BLACK, stroke);
    }

    /**
     * Dessine la Lune telle qu'on la voit depuis l'hémisphère nord (croissant à droite quand elle croît),
     * ou inversée depuis l'hémisphère sud. Le contour prend la couleur {@code outline}.
     */
    static void draw(Canvas c, float cx, float cy, float r, double elong, boolean south, int outline, float stroke) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        boolean waxing = elong < 180;
        double e = waxing ? elong : 360 - elong;       // 0..180, éclairée à droite
        float rx = (float) (r * Math.abs(Math.cos(Math.toRadians(e))));

        c.save();
        if (waxing == south) c.scale(-1, 1, cx, cy);   // éclairée à gauche
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.BLACK);
        c.drawCircle(cx, cy, r, p);

        // Partie éclairée : demi-disque droit, rogné (croissant) ou complété (gibbeuse) par le terminateur.
        Path lit = new Path();
        lit.arcTo(new RectF(cx - r, cy - r, cx + r, cy + r), -90, 180, true);
        lit.arcTo(new RectF(cx - rx, cy - r, cx + rx, cy + r), 90, e < 90 ? -180 : 180, false);
        lit.close();
        p.setColor(Color.WHITE);
        c.drawPath(lit, p);
        c.restore();

        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(stroke);
        p.setColor(outline);
        c.drawCircle(cx, cy, r, p);
    }
}
