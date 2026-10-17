package fr.pluie.kompakt;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
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

    private void showAbout() {
        TextView body = text(17, false);
        body.setText(L.t("about_text") + "\n\nwww.deberly.fr\n\n" + L.f("version", versionName())
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
