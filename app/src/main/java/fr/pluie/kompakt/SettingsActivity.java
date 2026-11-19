package fr.pluie.kompakt;

import android.app.Activity;
import android.app.AlertDialog;
import android.Manifest;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Paramètres : unité de température, langue, à propos. */
public class SettingsActivity extends Activity {

    private SharedPreferences prefs;

    private int dp(float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
    }

    private TextView text(float sizeSp, boolean bold) {
        TextView t = new TextView(this);
        t.setTextColor(Ui.INK);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        t.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        return t;
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("pluie", MODE_PRIVATE);
        MainActivity.applySettings(prefs);

        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(Ui.BG);
        frame.setFitsSystemWindows(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(8), dp(8), dp(8), dp(8));
        frame.addView(root, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Barre du haut : retour + titre.
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = text(24, true);
        back.setText("‹");
        back.setPadding(dp(4), 0, dp(16), 0);
        back.setOnClickListener(v -> finish());
        top.addView(back);
        TextView title = text(20, true);
        title.setText(L.t("settings"));
        top.addView(title);
        top.setOnClickListener(v -> finish());
        root.addView(top, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));
        root.addView(rule());

        boolean f = prefs.getBoolean("fahrenheit", false);
        root.addView(row(L.t("temp_unit"), f ? "Fahrenheit (°F)" : "Celsius (°C)", v -> chooseUnit()));
        root.addView(rule());

        String lang = prefs.getString("lang", "auto");
        String langValue = L.t("auto_lang") + " — " + L.NAMES[L.lang()];
        for (int i = 0; i < L.CODES.length; i++) if (L.CODES[i].equals(lang)) langValue = L.NAMES[i];
        root.addView(row(L.t("language"), langValue, v -> chooseLanguage()));
        root.addView(rule());

        String cal = prefs.getString("calendar", "auto");
        String calValue = cal.equals("auto") ? L.t("auto_country") + " — " + countryName(Saints.country) : countryName(cal);
        root.addView(row(L.t("nameday_calendar"), calValue, v -> chooseCalendar()));
        root.addView(rule());

        root.addView(row(L.t("widget_place"), L.t(prefs.getBoolean("widget_gps", false) ? "widget_gps" : "widget_app"),
                v -> chooseWidgetPlace()));
        root.addView(rule());
        root.addView(row(L.t("upd_row"), L.f("upd_value", versionName()), v -> checkUpdate()));
        root.addView(rule());
        root.addView(row(L.t("about"), null, v -> showAbout()));
        root.addView(rule());

        setContentView(frame);
    }

    /** Une ligne touchable : intitulé, et valeur en dessous. */
    private View row(String label, String value, View.OnClickListener click) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.VERTICAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(4), dp(10), dp(4), dp(10));
        r.setMinimumHeight(dp(56));
        TextView l = text(18, false);
        l.setText(label);
        r.addView(l);
        if (value != null) {
            TextView v = text(15, true);
            v.setText(value);
            r.addView(v);
        }
        r.setOnClickListener(click);
        return r;
    }

    private View rule() {
        View v = new View(this);
        v.setBackgroundColor(Ui.LINE);
        v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(Ui.LINE_DP))));
        return v;
    }

    // ---------------------------------------------------------------- mise à jour de l'appli

    private AlertDialog busy;

    /** Recherche d'une nouvelle version sur GitHub, sans quitter l'appli. */
    private void checkUpdate() {
        busy = new AlertDialog.Builder(this).setMessage(L.t("upd_searching")).setCancelable(false).show();
        new Thread(() -> {
            Updater.Release r = null;
            try { r = Updater.latest(); } catch (Exception ignored) { }
            final Updater.Release rel = r;
            runOnUiThread(() -> {
                if (isFinishing()) return;
                busy.dismiss();
                String mine = versionName();
                if (rel == null) {
                    message(L.t("upd_failed"));
                } else if (rel.apkUrl == null || !Updater.newer(rel.version, mine)) {
                    message(L.f("upd_none", mine));
                } else {
                    new AlertDialog.Builder(this)
                            .setTitle(L.t("upd_row"))
                            .setMessage(L.f("upd_available", rel.version, mine))
                            .setPositiveButton(L.t("upd_install"), (d, w) -> installUpdate(rel))
                            .setNegativeButton(L.t("upd_later"), null)
                            .show();
                }
            });
        }).start();
    }

    private void message(String m) {
        new AlertDialog.Builder(this).setTitle(L.t("upd_row")).setMessage(m).setPositiveButton("OK", null).show();
    }

    /** Téléchargement puis installation (après l'autorisation d'Android « Installer des applis inconnues »). */
    private void installUpdate(Updater.Release rel) {
        if (!getPackageManager().canRequestPackageInstalls()) {
            new AlertDialog.Builder(this)
                    .setTitle(L.t("upd_row"))
                    .setMessage(L.t("upd_permission"))
                    .setPositiveButton("OK", (d, w) -> startActivity(new android.content.Intent(
                            android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            android.net.Uri.parse("package:" + getPackageName()))))
                    .setNegativeButton(L.t("cancel"), null)
                    .show();
            return;
        }
        busy = new AlertDialog.Builder(this).setMessage(L.f("upd_downloading", 0)).setCancelable(false).show();
        new Thread(() -> {
            try {
                java.io.File apk = Updater.download(this, rel.apkUrl,
                        pc -> runOnUiThread(() -> busy.setMessage(L.f("upd_downloading", pc))));
                runOnUiThread(() -> {
                    busy.dismiss();
                    try {
                        Updater.install(this, apk);
                    } catch (Exception e) {
                        message(L.f("upd_install_failed", String.valueOf(e.getMessage())));
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    busy.dismiss();
                    message(L.t("upd_failed"));
                });
            }
        }).start();
    }

    /** Retour de l'installateur d'Android (confirmation à afficher, ou erreur). */
    @Override
    protected void onNewIntent(android.content.Intent i) {
        super.onNewIntent(i);
        if (!Updater.ACTION_STATUS.equals(i.getAction())) return;
        String err = Updater.onStatus(this, i);
        if (err != null) message(L.f("upd_install_failed", err));
    }

    /** Lieu des widgets : celui choisi dans l'appli, ou la position GPS (suivie au fil des déplacements). */
    private void chooseWidgetPlace() {
        String[] items = {L.t("widget_app"), L.t("widget_gps")};
        int current = prefs.getBoolean("widget_gps", false) ? 1 : 0;
        new AlertDialog.Builder(this)
                .setTitle(L.t("widget_place"))
                .setSingleChoiceItems(items, current, (dlg, i) -> {
                    prefs.edit().putBoolean("widget_gps", i == 1).apply();
                    dlg.dismiss();
                    if (i == 1) {
                        askLocation();
                    } else {
                        Widgets.updateAll(this);
                        recreate();
                    }
                })
                .setNegativeButton(L.t("cancel"), null)
                .show();
    }

    private static final int REQ_LOC = 7, REQ_BG = 8;

    /** Position : d'abord pendant l'utilisation, puis « toujours » pour que les widgets suivent les déplacements. */
    private void askLocation() {
        boolean fg = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        if (!fg) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOC);
            return;
        }
        if (Build.VERSION.SDK_INT >= 29 && checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            new AlertDialog.Builder(this)
                    .setTitle(L.t("widget_place"))
                    .setMessage(L.t("widget_bg_explain"))
                    .setPositiveButton("OK", (d, w) -> requestPermissions(
                            new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION}, REQ_BG))
                    .setNegativeButton(L.t("cancel"), (d, w) -> done())
                    .show();
            return;
        }
        done();
    }

    /** Fin du choix « position GPS » : mise à jour des widgets et de l'écran des réglages. */
    private void done() {
        Widgets.refreshAsync(this);
        recreate();
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        boolean granted = results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED;
        if (code == REQ_LOC && granted) askLocation();
        else if (code == REQ_LOC) done(); // refusé : les widgets gardent le lieu de l'appli
        else if (code == REQ_BG) done();
    }

    private void chooseUnit() {
        String[] items = {"Celsius (°C)", "Fahrenheit (°F)"};
        int current = prefs.getBoolean("fahrenheit", false) ? 1 : 0;
        new AlertDialog.Builder(this)
                .setTitle(L.t("temp_unit"))
                .setSingleChoiceItems(items, current, (dlg, i) -> {
                    prefs.edit().putBoolean("fahrenheit", i == 1).apply();
                    dlg.dismiss();
                    recreate();
                })
                .setNegativeButton(L.t("cancel"), null)
                .show();
    }

    private void chooseLanguage() {
        String[] items = new String[L.CODES.length + 1];
        items[0] = L.t("auto_lang");
        System.arraycopy(L.NAMES, 0, items, 1, L.NAMES.length);
        String lang = prefs.getString("lang", "auto");
        int current = 0;
        for (int i = 0; i < L.CODES.length; i++) if (L.CODES[i].equals(lang)) current = i + 1;
        new AlertDialog.Builder(this)
                .setTitle(L.t("language"))
                .setSingleChoiceItems(items, current, (dlg, i) -> {
                    prefs.edit().putString("lang", i == 0 ? "auto" : L.CODES[i - 1]).apply();
                    dlg.dismiss();
                    recreate(); // les textes de cet écran passent dans la nouvelle langue
                })
                .setNegativeButton(L.t("cancel"), null)
                .show();
    }

    /** Numéro de version de l'application (versionName de build.gradle). */
    private String versionName() {
        try {
            String v = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            return v != null ? v : "?";
        } catch (Exception e) {
            return "?";
        }
    }

    private static String countryName(String code) {
        return new java.util.Locale("", code).getDisplayCountry(L.locale());
    }

    private void chooseCalendar() {
        String[] items = new String[Saints.COUNTRIES.length + 1];
        items[0] = L.t("auto_country");
        for (int i = 0; i < Saints.COUNTRIES.length; i++) items[i + 1] = countryName(Saints.COUNTRIES[i]);
        String cal = prefs.getString("calendar", "auto");
        int current = 0;
        for (int i = 0; i < Saints.COUNTRIES.length; i++) if (Saints.COUNTRIES[i].equals(cal)) current = i + 1;
        new AlertDialog.Builder(this)
                .setTitle(L.t("nameday_calendar"))
                .setSingleChoiceItems(items, current, (dlg, i) -> {
                    prefs.edit().putString("calendar", i == 0 ? "auto" : Saints.COUNTRIES[i - 1]).apply();
                    dlg.dismiss();
                    recreate();
                })
                .setNegativeButton(L.t("cancel"), null)
                .show();
    }

    /** Page de la dernière version publiée, pour vérifier qu'on est à jour. */
    private static final String RELEASES_URL = "https://github.com/mdeberly/paper_meteo/releases/latest";

    private void showAbout() {
        TextView body = text(17, false);
        body.setText(L.t("about_text") + "\n\nwww.deberly.fr\n\n" + L.f("version", versionName())
                + "\n" + L.t("latest_release") + "\n" + RELEASES_URL
                + "\n\n" + L.t("credits"));
        body.setPadding(dp(24), dp(12), dp(24), 0);
        body.setLinkTextColor(Ui.COLOR ? Ui.ACCENT : Ui.INK);
        Linkify.addLinks(body, Linkify.WEB_URLS);
        body.setMovementMethod(LinkMovementMethod.getInstance());
        new AlertDialog.Builder(this)
                .setTitle("Paper Meteo")
                .setView(body)
                .setPositiveButton("OK", null)
                .show();
    }
}
