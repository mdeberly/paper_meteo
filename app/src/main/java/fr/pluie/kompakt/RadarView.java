package fr.pluie.kompakt;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.View;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Carte de l'onglet Radar : fond (pays, villes), pluie de l'image choisie, lieu au centre.
 * En haut l'heure de l'image, en bas la frise des images et la légende.
 * Pas de déplacement ni de pincement : le zoom se règle par les boutons + et −.
 */
public class RadarView extends View {

    private double cx, cy;
    private String place = "";
    private int zoom = 7;
    private MapData map;
    private List<RadarData.Frame> frames = new ArrayList<>();
    private int index = -1;
    private String message = "";
    private ZoneId zone = ZoneId.systemDefault();

    private Bitmap base;
    private String baseKey = "";

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint halo = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint image = new Paint(Paint.FILTER_BITMAP_FLAG);

    private static final int SEA = Ui.COLOR ? 0xFFDCE7EC : 0xFFFFFFFF;
    private static final int LAND = Ui.COLOR ? 0xFFFBF3E8 : 0xFFFFFFFF;
    private static final int BORDER = Ui.COLOR ? 0xFFA08A76 : 0xFF000000;

    public RadarView(Context c) {
        super(c);
        text.setColor(Ui.INK);
        halo.setColor(Ui.COLOR ? LAND : 0xFFFFFFFF);
        halo.setStyle(Paint.Style.STROKE);
        halo.setStrokeJoin(Paint.Join.ROUND);
    }

    private float dp(float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    private float sp(float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, getResources().getDisplayMetrics());
    }

    void setPlace(double lat, double lon, String name, int zoom) {
        this.cx = MapData.x(lon);
        this.cy = MapData.y(lat);
        this.place = name;
        this.zoom = zoom;
        invalidate();
    }

    void setMap(MapData m) {
        map = m;
        baseKey = "";
        invalidate();
    }

    void setZone(ZoneId z) {
        zone = z;
    }

    /** Nouvelles images : on se place sur la dernière image radar (le présent). */
    void setFrames(List<RadarData.Frame> f) {
        frames = new ArrayList<>(f);
        frames.sort((a, b) -> Long.compare(a.time, b.time));
        index = nowIndex();
        invalidate();
    }

    void setMessage(String m) {
        message = m == null ? "" : m;
        invalidate();
    }

    int count() {
        return frames.size();
    }

    int index() {
        return index;
    }

    void show(int i) {
        if (frames.isEmpty()) return;
        index = Math.max(0, Math.min(frames.size() - 1, i));
        invalidate();
    }

    /** Dernière image du radar (avant les prévisions). */
    int nowIndex() {
        int k = -1;
        for (int i = 0; i < frames.size(); i++) if (!frames.get(i).forecast) k = i;
        return k >= 0 ? k : frames.isEmpty() ? -1 : 0;
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        c.drawColor(Ui.BG);
        float headH = dp(30), footH = Ui.COLOR ? dp(58) : dp(28);
        RectF box = new RectF(0, headH, w, h - footH);
        float scale = (float) (256.0 * (1 << zoom) * w / 512.0); // pixels écran par unité Mercator
        float mx = box.centerX(), my = box.centerY();

        // Fond de carte, mis en cache tant que lieu, zoom et taille ne changent pas.
        String key = cx + "/" + cy + "/" + zoom + "/" + w + "/" + h + "/" + (map != null);
        if (!key.equals(baseKey) || base == null) {
            if (base != null) base.recycle();
            base = Bitmap.createBitmap((int) w, (int) box.height(), Bitmap.Config.ARGB_8888);
            drawBase(new Canvas(base), w, box.height(), scale);
            baseKey = key;
        }
        c.drawBitmap(base, 0, box.top, null);

        c.save();
        c.clipRect(box);
        RadarData.Frame f = index >= 0 && index < frames.size() ? frames.get(index) : null;
        if (f != null) {
            float l = (float) (mx + (f.cx - f.half - cx) * scale), t = (float) (my + (f.cy - f.half - cy) * scale);
            float r = (float) (mx + (f.cx + f.half - cx) * scale), b = (float) (my + (f.cy + f.half - cy) * scale);
            c.drawBitmap(f.image, null, new RectF(l, t, r, b), image);
        }
        drawCities(c, box, scale);
        drawCenter(c, mx, my);
        // Mention des sources, en bas à gauche de la carte.
        text.setTypeface(Typeface.DEFAULT);
        text.setTextSize(sp(10));
        text.setTextAlign(Paint.Align.LEFT);
        String credit = "Radar RainViewer · Natural Earth";
        outlined(c, credit, dp(4), box.bottom - dp(4), dp(2.5f));
        c.restore();

        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(Math.max(1, dp(Ui.LINE_DP)));
        line.setColor(Ui.LINE);
        c.drawRect(box, line);

        drawHeader(c, f, w, headH);
        drawFooter(c, w, h - footH, footH);
    }

    private void drawBase(Canvas c, float w, float h, float scale) {
        c.drawColor(map == null ? LAND : SEA);
        if (map == null) return;
        double halfX = w / 2.0 / scale, halfY = h / 2.0 / scale;
        double l = cx - halfX, r = cx + halfX, t = cy - halfY, b = cy + halfY;
        Path p = new Path();
        p.setFillType(Path.FillType.EVEN_ODD);
        for (MapData.Ring ring : map.rings) {
            if (ring.maxX < l || ring.minX > r || ring.maxY < t || ring.minY > b) continue;
            float[] xy = ring.xy;
            p.moveTo((float) (w / 2 + (xy[0] - cx) * scale), (float) (h / 2 + (xy[1] - cy) * scale));
            for (int i = 2; i < xy.length; i += 2) {
                p.lineTo((float) (w / 2 + (xy[i] - cx) * scale), (float) (h / 2 + (xy[i + 1] - cy) * scale));
            }
            p.close();
        }
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(LAND);
        c.drawPath(p, fill);
        line.setStyle(Paint.Style.STROKE);
        line.setColor(BORDER);
        line.setStrokeWidth(Ui.COLOR ? dp(1) : Math.max(1, dp(0.8f)));
        c.drawPath(p, line);
    }

    /** Villes : les plus importantes d'abord, sans chevauchement, plus nombreuses quand on zoome. */
    private void drawCities(Canvas c, RectF box, float scale) {
        if (map == null) return;
        text.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        text.setTextSize(sp(12));
        text.setTextAlign(Paint.Align.LEFT);
        int maxRank = zoom >= 8 ? 10 : zoom - 1;
        List<RectF> used = new ArrayList<>();
        float mx = box.centerX(), my = box.centerY();
        used.add(new RectF(mx - dp(8), my - dp(8), mx + dp(8) + centerLabelWidth(), my + dp(8)));
        used.add(new RectF(box.left, box.bottom - dp(16), box.right, box.bottom)); // mention des sources
        float dot = dp(2.5f);
        int shown = 0;
        for (MapData.City city : map.cities) {
            if (city.rank > maxRank) continue;
            float x = (float) (mx + (city.x - cx) * scale), y = (float) (my + (city.y - cy) * scale);
            if (!box.contains(x, y)) continue;
            float tw = text.measureText(city.name);
            RectF r = new RectF(x - dot, y - sp(12), x + dot * 2 + tw, y + dot * 2);
            if (r.right > box.right - dp(2)) r.offset(-(tw + dot * 3), 0);
            boolean free = true;
            for (RectF u : used) if (RectF.intersects(u, r)) { free = false; break; }
            if (!free) continue;
            used.add(r);
            fill.setStyle(Paint.Style.FILL);
            fill.setColor(Ui.INK);
            c.drawCircle(x, y, dot, fill);
            boolean left = r.left < x - dot * 2;
            text.setTextAlign(left ? Paint.Align.RIGHT : Paint.Align.LEFT);
            outlined(c, city.name, left ? x - dot * 2 : x + dot * 2, y + sp(4), dp(2.5f));
            if (++shown >= 40) break;
        }
        text.setTextAlign(Paint.Align.LEFT);
    }

    private float centerLabelWidth() {
        text.setTextSize(sp(15));
        float tw = text.measureText(place);
        text.setTextSize(sp(12));
        return tw + dp(6);
    }

    /** Le lieu choisi : point cerclé et nom en gras. */
    private void drawCenter(Canvas c, float x, float y) {
        float r = dp(6);
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(halo.getColor());
        c.drawCircle(x, y, r + dp(2), fill);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(dp(2));
        line.setColor(Ui.COLOR ? Ui.ACCENT : Ui.INK);
        c.drawCircle(x, y, r, line);
        fill.setColor(Ui.COLOR ? Ui.ACCENT : Ui.INK);
        c.drawCircle(x, y, dp(2.2f), fill);
        text.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        text.setTextSize(sp(15));
        text.setTextAlign(Paint.Align.LEFT);
        outlined(c, place, x + r + dp(4), y + sp(5), dp(3));
    }

    /** Texte détouré de la couleur du fond, lisible sur la carte et sur la pluie. */
    private void outlined(Canvas c, String s, float x, float y, float width) {
        halo.setTextSize(text.getTextSize());
        halo.setTypeface(text.getTypeface());
        halo.setTextAlign(text.getTextAlign());
        halo.setStrokeWidth(width);
        c.drawText(s, x, y, halo);
        int color = text.getColor();
        c.drawText(s, x, y, text);
        text.setColor(color);
    }

    private void drawHeader(Canvas c, RadarData.Frame f, float w, float hh) {
        text.setTextAlign(Paint.Align.LEFT);
        text.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        text.setTextSize(sp(17));
        float y = hh * 0.66f;
        String s;
        if (f == null) {
            s = message;
        } else {
            String clock = Fmt.clock(Instant.ofEpochSecond(f.time).atZone(zone));
            long now = System.currentTimeMillis() / 1000;
            if (f.forecast) {
                s = clock + "  ·  " + L.f("radar_in", Fmt.duration(Math.max(0, (f.time - now) / 60.0)));
            } else if (index == nowIndex()) {
                s = clock + "  ·  " + L.t("radar_last");
            } else {
                s = clock + "  ·  " + L.f("radar_ago", Fmt.duration((now - f.time) / 60.0));
            }
            if (!message.isEmpty()) s += "  ·  " + message;
        }
        float tw = text.measureText(s);
        if (tw > w) text.setTextSize(sp(17) * w / tw);
        c.drawText(s, 0, y, text);
    }

    /** Frise des images : passé rempli, prévision en creux, image affichée agrandie. */
    private void drawFooter(Canvas c, float w, float top, float fh) {
        int n = frames.size();
        float y = top + dp(14);
        if (n > 0) {
            float step = (w - dp(16)) / Math.max(1, n - 1), x0 = dp(8);
            int now = nowIndex();
            line.setColor(Ui.MUTED);
            line.setStrokeWidth(Math.max(1, dp(1)));
            c.drawLine(x0, y, x0 + step * (n - 1), y, line);
            for (int i = 0; i < n; i++) {
                float x = x0 + step * i;
                boolean cur = i == index;
                float r = cur ? dp(6) : dp(3.5f);
                int col = Ui.COLOR ? Ui.ACCENT : Ui.INK;
                fill.setStyle(Paint.Style.FILL);
                fill.setColor(Ui.BG);
                c.drawCircle(x, y, r, fill);
                if (frames.get(i).forecast && !cur) {
                    line.setColor(col);
                    line.setStrokeWidth(dp(1.5f));
                    c.drawCircle(x, y, r, line);
                } else {
                    fill.setColor(col);
                    c.drawCircle(x, y, r, fill);
                }
                if (i == now && now < n - 1) {
                    // Trait vertical entre le radar et la prévision.
                    line.setColor(Ui.INK);
                    line.setStrokeWidth(dp(1.5f));
                    c.drawLine(x + step / 2, y - dp(8), x + step / 2, y + dp(8), line);
                }
            }
        }
        if (Ui.COLOR) drawLegend(c, w, top + dp(30));
    }

    /** Couleur seulement : bande des 6 couleurs, chacune avec son intensité en dessous. */
    private void drawLegend(Canvas c, float w, float top) {
        String[] names = L.t("radar_levels").split("\\|");
        int n = Math.min(names.length, RadarData.RAIN_COLORS.length - 1);
        float seg = w / n, bh = dp(8);
        text.setTypeface(Typeface.DEFAULT);
        text.setTextAlign(Paint.Align.CENTER);
        fill.setStyle(Paint.Style.FILL);
        for (int i = 0; i < n; i++) {
            float l = seg * i;
            fill.setColor(LAND);
            c.drawRect(l, top, l + seg, top + bh, fill);
            fill.setColor(RadarData.RAIN_COLORS[i + 1]);
            c.drawRect(l, top, l + seg, top + bh, fill);
            text.setTextSize(sp(11));
            float tw = text.measureText(names[i]);
            if (tw > seg - dp(2)) text.setTextSize(sp(11) * (seg - dp(2)) / tw);
            c.drawText(names[i], l + seg / 2, top + bh + sp(13), text);
        }
        text.setTextAlign(Paint.Align.LEFT);
    }
}
