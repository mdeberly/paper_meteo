package fr.pluie.kompakt;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    private static final double DEF_LAT = 49.6156, DEF_LON = 3.2194;
    private static final String DEF_NAME = "Chauny";
    private static final long STALE_MS = 30 * 60 * 1000L;
    private static final int REQ_LOC = 1;

    private SharedPreferences prefs;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());

    private Forecast forecast;
    private List<Day> days;
    private int selected = 1; // 0 = hier, 1 = aujourd'hui
    private boolean loading;

    private TextView placeView, titleView, totalView, statusView;
    private WeekStrip strip;
    private DayView dayView;

    // ---------------------------------------------------------------- cycle de vie

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("pluie", MODE_PRIVATE);
        buildUi();
        String cached = prefs.getString("json", null);
        if (cached != null) {
            try { forecast = Forecast.parse(cached); } catch (Exception ignored) { }
        }
        render();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (System.currentTimeMillis() - prefs.getLong("fetched", 0) > STALE_MS) refresh();
        else render(); // la date a pu changer depuis la dernière ouverture
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        io.shutdownNow();
    }

    // ---------------------------------------------------------------- interface

    private int dp(float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
    }

    private TextView text(float sizeSp, boolean bold) {
        TextView t = new TextView(this);
        t.setTextColor(Color.BLACK);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        t.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        return t;
    }

    private TextView button(String label) {
        TextView b = text(16, true);
        b.setText(label);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(12), dp(8), dp(12), dp(8));
        GradientDrawable border = new GradientDrawable();
        border.setColor(Color.WHITE);
        border.setStroke(dp(2), Color.BLACK);
        border.setCornerRadius(dp(6));
        b.setBackground(border);
        b.setClickable(true);
        return b;
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);
        root.setFitsSystemWindows(true);
        int m = dp(8);
        root.setPadding(m, m, m, m);

        // Ligne du haut : lieu (touchable) + bouton Actualiser.
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        placeView = text(20, true);
        placeView.setSingleLine(true);
        placeView.setPadding(0, dp(6), dp(8), dp(6));
        placeView.setOnClickListener(v -> chooseLocation());
        top.addView(placeView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView refresh = button("Actualiser");
        refresh.setOnClickListener(v -> refresh());
        top.addView(refresh);
        root.addView(top);

        // Les 8 jours.
        strip = new WeekStrip(this);
        strip.setOnPick(i -> { selected = i; render(); });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(96));
        lp.topMargin = dp(6);
        root.addView(strip, lp);

        // Titre du jour + cumul.
        titleView = text(19, true);
        titleView.setPadding(0, dp(8), 0, 0);
        root.addView(titleView);
        totalView = text(15, false);
        totalView.setPadding(0, 0, 0, dp(4));
        root.addView(totalView);

        // Grille horaire (toute la place restante, pas de défilement).
        dayView = new DayView(this);
        GestureDetector swipe = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent e) { return true; }
            @Override public boolean onFling(MotionEvent a, MotionEvent b, float vx, float vy) {
                if (Math.abs(vx) < Math.abs(vy)) return false;
                move(vx < 0 ? 1 : -1);
                return true;
            }
        });
        dayView.setOnTouchListener((v, e) -> swipe.onTouchEvent(e));
        root.addView(dayView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        statusView = text(12, false);
        statusView.setPadding(0, dp(4), 0, 0);
        root.addView(statusView);

        setContentView(root);
    }

    private void move(int delta) {
        if (days == null) return;
        int n = Math.max(0, Math.min(days.size() - 1, selected + delta));
        if (n != selected) { selected = n; render(); }
    }

    private void render() {
        placeView.setText(prefs.getString("name", DEF_NAME) + "  ▾");
        if (forecast == null) {
            titleView.setText("Aucune donnée");
            totalView.setText("Touchez « Actualiser » (connexion requise).");
            dayView.setDay(null, 2, -1);
            strip.setDays(null, 0);
            return;
        }
        days = forecast.window();
        selected = Math.max(0, Math.min(days.size() - 1, selected));

        // Échelle commune à toute la semaine pour comparer les jours (barre pleine ≥ 2 mm/h).
        double scale = 2;
        for (Day d : days) scale = Math.max(scale, d.max());

        Day d = days.get(selected);
        titleView.setText(Fmt.title(selected, d.date));
        if (!d.hasAny()) {
            totalView.setText("Pas de données pour ce jour");
        } else {
            String t = "Total " + Fmt.total(d.total()) + " mm";
            if (d.max() >= 0.05) t += "  ·  max " + Fmt.mm(d.max()) + " mm/h";
            if (!d.isComplete()) t += "  (partiel)";
            totalView.setText(t);
        }
        strip.setDays(days, selected);
        dayView.setDay(d, scale, selected == 1 ? forecast.currentHour() : -1);

        if (!loading) {
            long f = prefs.getLong("fetched", 0);
            String when = f == 0 ? "?" : new SimpleDateFormat("dd/MM HH:mm", Locale.FRENCH).format(new Date(f));
            statusView.setText("Mis à jour " + when + "  ·  « 07h » = de 7 h à 8 h  ·  Open-Meteo");
        }
    }

    // ---------------------------------------------------------------- données

    private double lat() { return Double.parseDouble(prefs.getString("lat", Double.toString(DEF_LAT))); }
    private double lon() { return Double.parseDouble(prefs.getString("lon", Double.toString(DEF_LON))); }

    private void refresh() {
        if (loading) return;
        loading = true;
        statusView.setText("Mise à jour…");
        final String url = String.format(Locale.US,
                "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f"
                        + "&hourly=precipitation&past_days=1&forecast_days=8&timezone=auto",
                lat(), lon());
        io.execute(() -> {
            try {
                String body = Http.get(url);
                Forecast f = Forecast.parse(body);
                ui.post(() -> {
                    forecast = f;
                    prefs.edit().putString("json", body).putLong("fetched", System.currentTimeMillis()).apply();
                    loading = false;
                    render();
                });
            } catch (Exception e) {
                ui.post(() -> {
                    loading = false;
                    render();
                    statusView.setText("Échec de la mise à jour (réseau ?) — données précédentes affichées");
                });
            }
        });
    }

    private void setPlace(String name, double la, double lo) {
        prefs.edit()
                .putString("name", name)
                .putString("lat", Double.toString(la))
                .putString("lon", Double.toString(lo))
                .putLong("fetched", 0)
                .apply();
        forecast = null;
        render();
        refresh();
    }

    // ---------------------------------------------------------------- choix du lieu

    private void chooseLocation() {
        EditText input = new EditText(this);
        input.setHint("Ville (ex. Chauny)");
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        input.setTextColor(Color.BLACK);
        LinearLayout box = new LinearLayout(this);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        box.addView(input, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        new AlertDialog.Builder(this)
                .setTitle("Lieu")
                .setView(box)
                .setPositiveButton("Rechercher", (dlg, w) -> searchPlace(input.getText().toString().trim()))
                .setNeutralButton("Position GPS", (dlg, w) -> useGps())
                .setNegativeButton("Annuler", null)
                .show();
    }

    private void searchPlace(String q) {
        if (q.isEmpty()) return;
        statusView.setText("Recherche de « " + q + " »…");
        io.execute(() -> {
            try {
                String url = "https://geocoding-api.open-meteo.com/v1/search?count=10&language=fr&format=json&name="
                        + URLEncoder.encode(q, "UTF-8");
                JSONArray res = new JSONObject(Http.get(url)).optJSONArray("results");
                int n = res == null ? 0 : res.length();
                String[] labels = new String[n], names = new String[n];
                double[] las = new double[n], los = new double[n];
                for (int i = 0; i < n; i++) {
                    JSONObject r = res.getJSONObject(i);
                    names[i] = r.optString("name");
                    String admin = r.optString("admin1", "");
                    String country = r.optString("country", "");
                    StringBuilder sb = new StringBuilder(names[i]);
                    if (!admin.isEmpty()) sb.append(" — ").append(admin);
                    if (!country.isEmpty()) sb.append(", ").append(country);
                    labels[i] = sb.toString();
                    las[i] = r.getDouble("latitude");
                    los[i] = r.getDouble("longitude");
                }
                ui.post(() -> {
                    render();
                    if (n == 0) {
                        Toast.makeText(this, "Aucun lieu trouvé", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    new AlertDialog.Builder(this)
                            .setTitle("Choisir le lieu")
                            .setItems(labels, (dlg, i) -> setPlace(names[i], las[i], los[i]))
                            .setNegativeButton("Annuler", null)
                            .show();
                });
            } catch (Exception e) {
                ui.post(() -> {
                    render();
                    Toast.makeText(this, "Recherche impossible (réseau ?)", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private boolean hasLocationPermission() {
        return checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void useGps() {
        if (!hasLocationPermission()) {
            requestPermissions(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOC);
            return;
        }
        locate();
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (code != REQ_LOC) return;
        if (hasLocationPermission()) locate();
        else Toast.makeText(this, "Localisation refusée", Toast.LENGTH_SHORT).show();
    }

    private void gpsPlace(Location l) {
        setPlace(String.format(Locale.FRENCH, "GPS %.2f ; %.2f", l.getLatitude(), l.getLongitude()),
                l.getLatitude(), l.getLongitude());
    }

    @SuppressLint("MissingPermission")
    @SuppressWarnings("deprecation")
    private void locate() {
        LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (lm == null) return;

        Location best = null;
        for (String p : lm.getProviders(true)) {
            Location l = lm.getLastKnownLocation(p);
            if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
        }
        if (best != null && System.currentTimeMillis() - best.getTime() < 60 * 60 * 1000L) {
            gpsPlace(best);
            return;
        }
        final Location fallback = best;

        String provider;
        if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) provider = LocationManager.NETWORK_PROVIDER;
        else if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) provider = LocationManager.GPS_PROVIDER;
        else {
            Toast.makeText(this, "Activez la localisation du téléphone", Toast.LENGTH_LONG).show();
            return;
        }
        statusView.setText("Recherche de la position…");

        if (Build.VERSION.SDK_INT >= 30) {
            lm.getCurrentLocation(provider, null, getMainExecutor(), loc -> {
                if (loc != null) gpsPlace(loc);
                else if (fallback != null) gpsPlace(fallback);
                else {
                    render();
                    Toast.makeText(this, "Position introuvable", Toast.LENGTH_SHORT).show();
                }
            });
        } else {
            lm.requestSingleUpdate(provider, new LocationListener() {
                @Override public void onLocationChanged(Location loc) { gpsPlace(loc); }
                @Override public void onStatusChanged(String p, int s, Bundle b) { }
                @Override public void onProviderEnabled(String p) { }
                @Override public void onProviderDisabled(String p) { }
            }, Looper.getMainLooper());
        }
    }
}
