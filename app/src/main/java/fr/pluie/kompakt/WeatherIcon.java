package fr.pluie.kompakt;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;

/**
 * Icônes météo dessinées au trait, en deux couleurs (encre {@code fg} sur fond {@code bg}),
 * pour rester nettes en E Ink et lisibles en inversé.
 */
final class WeatherIcon {
    private WeatherIcon() {}

    /** Dessine l'icône {@code kind} (voir {@link Weather}) dans un carré de côté {@code size} centré en (cx, cy). */
    static void draw(Canvas c, int kind, boolean night, float cx, float cy, float size, int fg, int bg) {
        if (kind == Weather.NONE) return;
        float u = size / 2f;
        float stroke = Math.max(1.5f, size * 0.07f);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        // E Ink : tout à l'encre fg. Couleur : une teinte par élément (soleil, lune, nuage, pluie, éclair).
        int sunC = Ui.COLOR ? Ui.SUN : fg, moonC = Ui.COLOR ? Ui.MOON : fg;
        int cloudC = Ui.COLOR ? Ui.CLOUD : fg, cloudFill = Ui.COLOR ? Ui.CLOUD_FILL : bg;
        int rainC = Ui.COLOR ? Ui.RAIN : fg, boltC = Ui.COLOR ? Ui.BOLT : fg, snowC = Ui.COLOR ? Ui.SNOW : fg;
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);

        switch (kind) {
            case Weather.CLEAR:
                body(c, p, night, cx, cy, u * 0.9f, night ? moonC : sunC, bg, stroke);
                break;
            case Weather.FEW_CLOUDS:
                body(c, p, night, cx - u * 0.25f, cy - u * 0.25f, u * 0.68f, night ? moonC : sunC, bg, stroke);
                cloud(c, p, cx - u * 0.12f, cy + u * 0.02f, u * 1.1f, cloudC, cloudFill, stroke);
                break;
            case Weather.MANY_CLOUDS:
                body(c, p, night, cx - u * 0.38f, cy - u * 0.4f, u * 0.58f, night ? moonC : sunC, bg, stroke);
                cloud(c, p, cx - u * 0.82f, cy - u * 0.28f, u * 1.75f, cloudC, cloudFill, stroke);
                break;
            case Weather.FOG: {
                // Brouillard seul : quatre bandes horizontales, sans astre.
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(stroke * 1.4f);
                p.setColor(cloudC);
                float[][] fogBands = {{-0.8f, 0.55f, -0.52f}, {-0.6f, 0.8f, -0.17f}, {-0.8f, 0.7f, 0.18f}, {-0.55f, 0.8f, 0.53f}};
                for (float[] b : fogBands) c.drawLine(cx + b[0] * u, cy + b[2] * u, cx + b[1] * u, cy + b[2] * u, p);
                break;
            }
            case Weather.FOG_SUN: {
                // Soleil (ou lune) voilé par trois bandes de brouillard ; chaque bande est détourée
                // de la couleur du fond pour bien se détacher de l'astre.
                body(c, p, night, cx - u * 0.2f, cy - u * 0.38f, u * 0.62f, night ? moonC : sunC, bg, stroke);
                float[][] bands = {{-0.78f, 0.62f, 0.02f}, {-0.62f, 0.78f, 0.36f}, {-0.78f, 0.5f, 0.7f}};
                for (int pass = 0; pass < 2; pass++) {
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeWidth(pass == 0 ? stroke * 3.2f : stroke * 1.4f);
                    p.setColor(pass == 0 ? bg : cloudC);
                    for (float[] b : bands) c.drawLine(cx + b[0] * u, cy + b[2] * u, cx + b[1] * u, cy + b[2] * u, p);
                }
                break;
            }
            case Weather.OVERCAST:
                cloud(c, p, cx - u * 0.95f, cy - u * 0.55f, u * 1.9f, cloudC, cloudFill, stroke);
                break;
            default: {
                // Nuage en haut, précipitations dessous ; la nuit, la lune dépasse derrière le nuage.
                if (night) body(c, p, true, cx - u * 0.52f, cy - u * 0.6f, u * 0.6f, night ? moonC : sunC, bg, stroke);
                float w = night ? u * 1.6f : u * 1.75f;
                float top = night ? cy - u * 0.8f : cy - u * 0.92f;
                cloud(c, p, night ? cx - u * 0.62f : cx - u * 0.8f, top, w, cloudC, cloudFill, stroke);
                float yb = top + w * 0.62f; // bas du nuage
                p.setColor(kind == Weather.STORM ? boltC : rainC);
                boolean snow = kind == Weather.SNOW_LIGHT || kind == Weather.SNOW_MODERATE || kind == Weather.SNOW_HEAVY;
                if (snow) {
                    // Flocons sous le nuage : 2 (faible), 3 (modérée) ou 5 sur deux rangs (forte).
                    float[][] flakes = kind == Weather.SNOW_LIGHT ? new float[][]{{-0.3f, 0.32f}, {0.3f, 0.32f}}
                            : kind == Weather.SNOW_MODERATE ? new float[][]{{-0.48f, 0.32f}, {0f, 0.32f}, {0.48f, 0.32f}}
                            : new float[][]{{-0.48f, 0.22f}, {0f, 0.22f}, {0.48f, 0.22f}, {-0.24f, 0.62f}, {0.24f, 0.62f}};
                    float fr = u * (kind == Weather.SNOW_HEAVY ? 0.15f : 0.18f);
                    p.setColor(snowC);
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeWidth(Math.max(1f, stroke * 0.8f));
                    for (float[] f : flakes) flake(c, p, cx + f[0] * u, yb + f[1] * u + fr * 0.4f, fr);
                } else if (kind == Weather.STORM) {
                    Path bolt = new Path();
                    bolt.moveTo(cx + u * 0.12f, yb + u * 0.02f);
                    bolt.lineTo(cx - u * 0.24f, yb + u * 0.42f);
                    bolt.lineTo(cx - u * 0.02f, yb + u * 0.42f);
                    bolt.lineTo(cx - u * 0.18f, yb + u * 0.8f);
                    bolt.lineTo(cx + u * 0.3f, yb + u * 0.3f);
                    bolt.lineTo(cx + u * 0.07f, yb + u * 0.3f);
                    bolt.lineTo(cx + u * 0.24f, yb + u * 0.02f);
                    bolt.close();
                    p.setStyle(Paint.Style.FILL);
                    c.drawPath(bolt, p);
                } else {
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeWidth(stroke);
                    boolean heavy = kind == Weather.HEAVY_RAIN;
                    float[] xs = heavy ? new float[]{-0.5f, -0.17f, 0.16f, 0.49f} : new float[]{-0.3f, 0.22f};
                    float y0 = yb + u * (heavy ? 0.12f : 0.18f), y1 = yb + u * (heavy ? 0.7f : 0.48f);
                    float slant = u * (heavy ? 0.18f : 0.1f);
                    for (float x : xs) c.drawLine(cx + x * u + slant, y0, cx + x * u, y1, p);
                }
            }
        }
    }

    /** Flocon de neige : trois traits croisés à 60°. */
    private static void flake(Canvas c, Paint p, float x, float y, float r) {
        for (int i = 0; i < 3; i++) {
            double a = Math.PI / 3 * i + Math.PI / 2;
            float dx = (float) Math.cos(a) * r, dy = (float) Math.sin(a) * r;
            c.drawLine(x - dx, y - dy, x + dx, y + dy, p);
        }
    }

    /** Soleil (disque + rayons) ou croissant de lune, dans un cercle de rayon r. */
    private static void body(Canvas c, Paint p, boolean moon, float x, float y, float r, int fg, int bg, float stroke) {
        p.setColor(fg);
        if (moon) {
            p.setStyle(Paint.Style.FILL);
            c.drawCircle(x, y, r * 0.8f, p);
            p.setColor(bg);
            c.drawCircle(x + r * 0.38f, y - r * 0.26f, r * 0.68f, p);
            return;
        }
        p.setStyle(Paint.Style.FILL);
        c.drawCircle(x, y, r * 0.5f, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(stroke);
        for (int i = 0; i < 8; i++) {
            double a = Math.PI / 4 * i;
            float dx = (float) Math.cos(a), dy = (float) Math.sin(a);
            c.drawLine(x + dx * r * 0.72f, y + dy * r * 0.72f, x + dx * r, y + dy * r, p);
        }
    }

    /** Nuage rempli de bg et cerné de fg, de largeur w (hauteur 0,62 w), coin haut-gauche en (l, t). */
    private static void cloud(Canvas c, Paint p, float l, float t, float w, int fg, int bg, float stroke) {
        Path shape = new Path();
        shape.addCircle(l + 0.55f * w, t + 0.30f * w, 0.28f * w, Path.Direction.CW);
        shape.addCircle(l + 0.27f * w, t + 0.40f * w, 0.19f * w, Path.Direction.CW);
        shape.addCircle(l + 0.80f * w, t + 0.43f * w, 0.17f * w, Path.Direction.CW);
        Path base = new Path();
        base.addRoundRect(l + 0.07f * w, t + 0.38f * w, l + 0.97f * w, t + 0.60f * w, 0.11f * w, 0.11f * w,
                Path.Direction.CW);
        shape.op(base, Path.Op.UNION); // un seul contour : pas de traits à l'intérieur du nuage
        p.setStyle(Paint.Style.FILL);
        p.setColor(bg);
        c.drawPath(shape, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(stroke);
        p.setColor(fg);
        c.drawPath(shape, p);
    }
}
