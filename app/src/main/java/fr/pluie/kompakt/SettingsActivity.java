package fr.pluie.kompakt;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Paramètres : pour l'instant, seulement « À propos ». */
public class SettingsActivity extends Activity {

    private static final String ABOUT = "Application créée par l'IA Claude sur l'idée de Maximilien DEBERLY, "
            + "Chauny, FRANCE.\n\nwww.deberly.fr";

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

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(Color.WHITE);
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
        title.setText("Paramètres");
        top.addView(title);
        root.addView(top, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));
        root.addView(rule());

        TextView about = text(18, false);
        about.setText("À propos");
        about.setGravity(Gravity.CENTER_VERTICAL);
        about.setPadding(dp(4), 0, dp(4), 0);
        about.setOnClickListener(v -> showAbout());
        root.addView(about, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));
        root.addView(rule());

        setContentView(frame);
    }

    private android.view.View rule() {
        android.view.View v = new android.view.View(this);
        v.setBackgroundColor(Color.BLACK);
        v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(2)));
        return v;
    }

    private void showAbout() {
        TextView body = text(17, false);
        body.setText(ABOUT);
        body.setPadding(dp(24), dp(12), dp(24), 0);
        body.setLinkTextColor(Color.BLACK);
        Linkify.addLinks(body, Linkify.WEB_URLS);
        body.setMovementMethod(LinkMovementMethod.getInstance());
        new AlertDialog.Builder(this)
                .setTitle("Paper Meteo")
                .setView(body)
                .setPositiveButton("OK", null)
                .show();
    }
}
