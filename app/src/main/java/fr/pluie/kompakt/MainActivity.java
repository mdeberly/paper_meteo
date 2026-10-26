package fr.pluie.kompakt;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.location.Address;
import android.location.Geocoder;
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
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
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

    private static final int TAB_SUMMARY = 0, TAB_WEATHER = 1, TAB_EPH = 2, TAB_RADAR = 3;
    private static final String[] TAB_KEYS = {"tab_summary", "tab_weather", "tab_eph", "tab_radar"};

    private SharedPreferences prefs;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());

    private Forecast forecast;
    private List<Day> days;
    private int selected = 1; // 0 = hier, 1 = aujourd'hui
    private int tab;
    private boolean loading;
    /** Vrai pendant l'affichage des paramètres : au retour, on garde l'onglet en cours. */
    private boolean inSettings;
    /** Recréée après un changement de paramètres : on garde l'onglet en cours. */
    private boolean restored;
    /** Langue et unité en vigueur à la construction de l'écran. */
    private String config;
    /** À l'arrivée sur l'onglet Météo, remonter la liste tout en haut. */
    private boolean positionList;

    private TextView placeView, titleView, totalView, statusView;
    private final TextView[] tabViews = new TextView[TAB_KEYS.length];
    /** Bandeau des 8 jours : fixe sur l'Éphéméride, dans la zone qui défile sur Météo. */
    private WeekStrip strip, weatherStrip;
    private LinearLayout weatherView;
    /** Météo : rappel fixe du jour choisi et du lieu, au-dessus de la zone qui défile. */
    private TextView weatherTitle;
    private ScrollView scroll;
    private DayView dayView;

    private LinearLayout ephView;
    private TextView saintView, feastView, riseView, setView, dayLengthView, phaseView, moonInfoView, nextPhasesView;
    private MoonView moonView;

    private ScrollView summaryView;

    // Radar : carte, boutons, chargement en arrière-plan et animation.
    private static final int RADAR_MIN_ZOOM = 5, RADAR_MAX_ZOOM = 9;
    private static final long RADAR_STALE_MS = 10 * 60 * 1000L;
    private final ExecutorService radarIo = Executors.newSingleThreadExecutor();
    private LinearLayout radarPage;
    private RadarView radarView;
    private TextView playButton;
    private String radarKey = "";
    private long radarTime;
    private int radarGen;
    private boolean playing;
    private final Runnable player = new Runnable() {
        @Override public void run() {
            if (!playing || radarView.count() == 0) return;
            // Une seule lecture : arrêt sur la dernière image disponible.
            int next = radarView.index() + 1;
            radarView.show(next);
            if (next >= radarView.count() - 1) stopPlay();
            else ui.postDelayed(this, 800);
        }
    };
    private TextView summaryText, soonView;
    private CurrentView currentView;
    private NextHoursView nextHoursView;

    // ---------------------------------------------------------------- cycle de vie

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("pluie", MODE_PRIVATE);
        config = applySettings(prefs);
        if (state != null) {
            tab = state.getInt("tab", TAB_SUMMARY);
            selected = state.getInt("selected", 1);
            restored = true;
        }
        buildUi();
        String cached = prefs.getString("json", null);
        if (cached != null) {
            try { forecast = Forecast.parse(cached); } catch (Exception ignored) { }
        }
        render();
    }

    @Override
    protected void onStart() {
        super.onStart();
        // À chaque ouverture : le résumé du jour (sauf au retour des paramètres).
        if (inSettings) {
            inSettings = false;
            // Langue ou unité changée : on reconstruit l'écran (en gardant l'onglet).
            if (!applySettings(prefs).equals(config)) recreate();
            return;
        }
        if (restored) {
            restored = false;
            return;
        }
        tab = TAB_SUMMARY;
        selected = 1;
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("tab", tab);
        out.putInt("selected", selected);
    }

    /** Applique la langue et l'unité de température enregistrées ; renvoie leur combinaison. */
    static String applySettings(SharedPreferences p) {
        String lang = p.getString("lang", "auto");
        boolean f = p.getBoolean("fahrenheit", false);
        String cal = p.getString("calendar", "auto");
        L.init(lang);
        Fmt.fahrenheit = f;
        Saints.init(cal);
        return lang + "/" + L.code() + "/" + f + "/" + Saints.country;
    }

    @Override
    protected void onResume() {
        super.onResume();
        boolean stale = System.currentTimeMillis() - prefs.getLong("fetched", 0) > STALE_MS;
        if (stale || (forecast != null && !forecast.complete)) refresh();
        else render(); // la date a pu changer depuis la dernière ouverture
        // Lieu GPS enregistré sans nom de commune (versions précédentes ou réseau absent) : on le cherche.
        if (prefs.getString("name", DEF_NAME).startsWith("GPS ")) resolvePlaceName(lat(), lon());
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopPlay();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        io.shutdownNow();
        radarIo.shutdownNow();
    }

    // ---------------------------------------------------------------- interface

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

    private TextView button(String label) {
        TextView b = text(16, true);
        b.setText(label);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(12), dp(8), dp(12), dp(8));
        GradientDrawable border = new GradientDrawable();
        border.setColor(Ui.BG);
        border.setStroke(dp(Ui.COLOR ? 1.5f : 2), Ui.COLOR ? Ui.ACCENT : Ui.INK);
        border.setCornerRadius(dp(Ui.COLOR ? 18 : 6));
        if (Ui.COLOR) b.setTextColor(Ui.ACCENT);
        b.setBackground(border);
        b.setClickable(true);
        return b;
    }

    private View rule() {
        View v = new View(this);
        v.setBackgroundColor(Ui.LINE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                Math.max(1, dp(Ui.LINE_DP)));
        lp.topMargin = dp(6);
        lp.bottomMargin = dp(4);
        v.setLayoutParams(lp);
        return v;
    }

    private void buildUi() {
        // Le cadre absorbe les marges système ; la marge de 8 dp reste sur le contenu.
        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(Ui.BG);
        frame.setFitsSystemWindows(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int m = dp(8);
        root.setPadding(m, m, m, m);
        frame.addView(root, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Ligne du haut : lieu (touchable) + bouton Actualiser.
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        placeView = text(20, true);
        placeView.setSingleLine(true);
        placeView.setPadding(0, dp(4), dp(8), dp(4));
        placeView.setOnClickListener(v -> chooseLocation());
        top.addView(placeView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView refresh = button(L.t("refresh"));
        refresh.setOnClickListener(v -> refresh());
        top.addView(refresh);
        GearView gear = new GearView(this);
        gear.setOnClickListener(v -> {
            inSettings = true;
            startActivity(new Intent(this, SettingsActivity.class));
        });
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(dp(40), dp(40));
        glp.leftMargin = dp(8);
        top.addView(gear, glp);
        root.addView(top);

        // Onglets.
        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        GradientDrawable frameLine = new GradientDrawable();
        frameLine.setColor(Ui.BG);
        frameLine.setStroke(Math.max(1, dp(Ui.LINE_DP)), Ui.LINE);
        if (Ui.COLOR) frameLine.setCornerRadius(dp(20));
        tabs.setBackground(frameLine);
        for (int i = 0; i < TAB_KEYS.length; i++) {
            final int index = i;
            TextView t = text(16, true);
            t.setText(L.t(TAB_KEYS[i]));
            t.setGravity(Gravity.CENTER);
            t.setSingleLine(true);
            t.setPadding(dp(2), 0, dp(2), 0);
            t.setOnClickListener(v -> selectTab(index));
            tabViews[i] = t;
            // Largeur selon le texte, la place restante partagée à parts égales.
            tabs.addView(t, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT, 1));
        }
        // Texte des onglets réduit si les quatre ne tiennent pas sur la largeur.
        float needed = 0;
        for (TextView t : tabViews) needed += t.getPaint().measureText(t.getText().toString()) + dp(10);
        float room = getResources().getDisplayMetrics().widthPixels - dp(16) - dp(4);
        if (needed > room) {
            for (TextView t : tabViews) t.setTextSize(TypedValue.COMPLEX_UNIT_PX, t.getTextSize() * room / needed);
        }
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(40));
        tlp.topMargin = dp(6);
        root.addView(tabs, tlp);

        // Les 8 jours (Éphéméride).
        strip = new WeekStrip(this);
        strip.setOnPick(i -> { selected = i; render(); });
        root.addView(strip, stripParams());

        // Titre du jour (Résumé).
        titleView = text(19, true);
        titleView.setPadding(0, dp(6), 0, 0);
        root.addView(titleView);

        // Glisser franchement à gauche / à droite pour changer de jour.
        GestureDetector swipe = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent e) { return true; }
            @Override public boolean onFling(MotionEvent a, MotionEvent b, float vx, float vy) {
                if (Math.abs(vx) < 2 * Math.abs(vy)) return false;
                move(vx < 0 ? 1 : -1);
                return true;
            }
        });
        View.OnTouchListener swipeListener = (v, e) -> swipe.onTouchEvent(e);

        // Météo : bandeau des jours, cumuls, titres des colonnes et liste heure par heure,
        // le tout dans une seule zone qui défile verticalement (défilement continu).
        weatherView = new LinearLayout(this);
        weatherView.setOrientation(LinearLayout.VERTICAL);
        weatherTitle = text(17, true);
        weatherTitle.setSingleLine(true);
        weatherTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        weatherTitle.setPadding(0, dp(6), 0, dp(4));
        weatherView.addView(weatherTitle);
        View titleRule = new View(this);
        titleRule.setBackgroundColor(Ui.LINE);
        weatherView.addView(titleRule, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                Math.max(1, dp(Ui.LINE_DP))));
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        weatherStrip = new WeekStrip(this);
        weatherStrip.setOnPick(i -> { selected = i; render(); });
        LinearLayout.LayoutParams wlp = stripParams();
        wlp.height = dp(3 * 92); // 9 jours en 3 × 3, grandes cases avec une grande icône par jour
        content.addView(weatherStrip, wlp);
        totalView = text(19, false);
        totalView.setPadding(0, dp(6), 0, dp(4));
        content.addView(totalView);
        DayView header = new DayView(this, true);
        content.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, header.rowHeight()));
        scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(true);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setOnTouchListener((v, e) -> {
            swipe.onTouchEvent(e);
            return false; // le ScrollView garde la main pour défiler
        });
        dayView = new DayView(this, false);
        content.addView(dayView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        scroll.addView(content, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        weatherView.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        root.addView(weatherView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        ephView = buildEphemeris();
        ephView.setOnTouchListener(swipeListener);
        root.addView(ephView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        summaryView = buildSummary();
        root.addView(summaryView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        radarPage = buildRadar();
        root.addView(radarPage, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        statusView = text(12, false);
        statusView.setPadding(0, dp(4), 0, 0);
        root.addView(statusView);

        setContentView(frame);
    }

    private LinearLayout.LayoutParams stripParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3 * 46));
        lp.topMargin = dp(6);
        return lp;
    }

    private LinearLayout buildEphemeris() {
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setPadding(0, dp(6), 0, 0);

        TextView l = text(13, false);
        l.setText(L.t("nameday") + " · " + new Locale("", Saints.country).getDisplayCountry(L.locale()));
        p.addView(l);
        saintView = text(24, true);
        p.addView(saintView);
        feastView = text(16, false);
        p.addView(feastView);
        p.addView(rule());

        LinearLayout sun = new LinearLayout(this);
        sun.setOrientation(LinearLayout.HORIZONTAL);
        riseView = text(22, true);
        setView = text(22, true);
        sun.addView(labelled(L.t("sunrise"), riseView), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        sun.addView(labelled(L.t("sunset"), setView), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        p.addView(sun);
        dayLengthView = text(15, false);
        p.addView(dayLengthView);
        p.addView(rule());

        LinearLayout moon = new LinearLayout(this);
        moon.setOrientation(LinearLayout.HORIZONTAL);
        moonView = new MoonView(this);
        moon.addView(moonView, new LinearLayout.LayoutParams(dp(116), ViewGroup.LayoutParams.MATCH_PARENT));
        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(dp(12), 0, 0, 0);
        phaseView = text(19, true);
        moonInfoView = text(15, false);
        nextPhasesView = text(14, false);
        nextPhasesView.setPadding(0, dp(4), 0, 0);
        info.addView(phaseView);
        info.addView(moonInfoView);
        info.addView(nextPhasesView);
        moon.addView(info, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        p.addView(moon, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        return p;
    }

    /** Page Résumé : défile verticalement si le texte est long, les 12 heures gardant une hauteur lisible. */
    private ScrollView buildSummary() {
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);
        // Météo actuelle, au-dessus du résumé.
        currentView = new CurrentView(this);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(72));
        clp.topMargin = dp(4);
        p.addView(currentView, clp);
        p.addView(rule());
        summaryText = text(18, false);
        summaryText.setLineSpacing(0, 1.1f);
        summaryText.setPadding(0, dp(4), 0, 0);
        p.addView(summaryText);
        p.addView(rule());
        TextView l = text(20, false);
        l.setText(L.t("next2h"));
        p.addView(l);
        soonView = text(21, true);
        p.addView(soonView);
        p.addView(rule());
        // Les 12 prochaines heures, défilement horizontal continu.
        nextHoursView = new NextHoursView(this);
        HorizontalScrollView hscroll = new HorizontalScrollView(this);
        hscroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        hscroll.addView(nextHoursView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1);
        lp.topMargin = dp(2);
        p.addView(hscroll, lp);
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        sv.setVerticalScrollBarEnabled(false);
        sv.addView(p, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return sv;
    }

    private LinearLayout labelled(String label, TextView value) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        TextView l = text(13, false);
        l.setText(label);
        box.addView(l);
        box.addView(value);
        return box;
    }

    private void selectTab(int i) {
        if (i == tab) return;
        tab = i;
        positionList = tab == TAB_WEATHER;
        if (tab == TAB_RADAR) radarZoomLevel = RADAR_DEFAULT_ZOOM;
        render();
    }

    private void move(int delta) {
        if (days == null) return;
        int n = Math.max(0, Math.min(days.size() - 1, selected + delta));
        if (n != selected) { selected = n; render(); }
    }

    private ZoneId zone() {
        return forecast != null ? forecast.zone : ZoneId.systemDefault();
    }

    private void render() {
        placeView.setText(prefs.getString("name", DEF_NAME) + "  ▾");
        for (int i = 0; i < tabViews.length; i++) {
            boolean on = i == tab;
            if (Ui.COLOR) {
                // Couleur : onglet choisi en pastille arrondie.
                GradientDrawable pill = new GradientDrawable();
                pill.setColor(Ui.ACCENT);
                pill.setCornerRadius(dp(20));
                tabViews[i].setBackground(on ? pill : null);
            } else {
                tabViews[i].setBackgroundColor(on ? Ui.ACCENT : Color.TRANSPARENT);
            }
            tabViews[i].setTextColor(on ? Ui.ON_ACCENT : Ui.INK);
        }
        boolean grid = tab == TAB_WEATHER;
        weatherView.setVisibility(grid ? View.VISIBLE : View.GONE);
        ephView.setVisibility(tab == TAB_EPH ? View.VISIBLE : View.GONE);
        summaryView.setVisibility(tab == TAB_SUMMARY ? View.VISIBLE : View.GONE);
        if (tab == TAB_SUMMARY) summaryView.scrollTo(0, 0);
        strip.setVisibility(tab == TAB_EPH ? View.VISIBLE : View.GONE);
        radarPage.setVisibility(tab == TAB_RADAR ? View.VISIBLE : View.GONE);
        if (tab != TAB_RADAR) stopPlay();
        if (tab == TAB_RADAR) {
            titleView.setVisibility(View.GONE);
            renderRadar();
            return;
        }

        ZoneId zone = zone();
        LocalDate today = LocalDate.now(zone);
        days = forecast != null ? forecast.window() : Forecast.emptyWindow(today);
        selected = Math.max(0, Math.min(days.size() - 1, selected));
        Day d = days.get(selected);

        String[] labels = new String[days.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = Fmt.shortLabel(i, days.get(i).date);

        String[] temps = new String[days.size()];
        for (int i = 0; i < temps.length; i++) {
            Series t = days.get(i).temp;
            temps[i] = t.hasAny() ? Fmt.deg(t.low()) + " / " + Fmt.deg(t.high()) : "";
        }

        // Le titre n'est utile que sur le résumé : ailleurs, le bandeau montre le jour choisi.
        titleView.setVisibility(tab == TAB_SUMMARY ? View.VISIBLE : View.GONE);
        if (tab == TAB_SUMMARY) {
            titleView.setText(Fmt.title(1, today));
            renderSummary(zone);
            updateStatus("");
            return;
        }

        if (tab == TAB_EPH) {
            renderEphemeris(d, today, zone, labels, temps);
            statusView.setText(L.t("eph_status"));
            return;
        }

        String[] rainTotals = new String[days.size()];
        for (int i = 0; i < rainTotals.length; i++) {
            Day x = days.get(i);
            // Neige : hauteur tombée en cm ; sinon cumul de pluie en mm.
            rainTotals[i] = !x.rain.hasAny() ? L.t("nd")
                    : x.snow.total() >= 0.1 ? Fmt.total(x.snow.total()) + " cm"
                    : Fmt.total(x.rain.total()) + " mm";
        }
        int[] dayIcons = new int[days.size()];
        for (int i = 0; i < dayIcons.length; i++) {
            dayIcons[i] = forecast == null ? Weather.NONE
                    : Weather.dayKind(forecast, zone, lat(), lon(), days.get(i).date);
        }
        weatherStrip.setCells(labels, rainTotals, null, temps, dayIcons, false, selected);

        weatherTitle.setText(Fmt.title(selected, d.date) + "  ·  " + prefs.getString("name", DEF_NAME));
        if (forecast == null) {
            totalView.setText(L.t("no_data"));
            dayView.setCells(null);
        } else {
            renderWeather(d, zone);
        }
        updateStatus(L.t("legend") + " · ");
    }

    private void updateStatus(String legend) {
        if (loading) return;
        long f = prefs.getLong("fetched", 0);
        String when = f == 0 ? "?" : new SimpleDateFormat("dd/MM HH:mm", L.locale()).format(new Date(f));
        statusView.setText(L.f("updated", when) + " · " + legend + "Open-Meteo");
    }

    private void renderSummary(ZoneId zone) {
        currentView.setCurrent(forecast == null ? null : forecast.current);
        currentView.setVisibility(forecast != null && forecast.current != null ? View.VISIBLE : View.GONE);
        if (forecast == null) {
            summaryText.setText(L.t("no_data"));
            soonView.setText("");
            nextHoursView.setHours(null);
            return;
        }
        LocalDateTime now = LocalDateTime.now(zone);
        summaryText.setText(String.join("\n", Weather.summary(forecast, zone, lat(), lon(), now)));
        soonView.setText(Weather.nextTwoHours(forecast, zone, lat(), lon(), now));
        nextHoursView.setHours(Weather.hours(forecast, zone, lat(), lon(), now.toLocalDate(), now.getHour(), 12));
    }

    /** Créneaux de la liste : la nuit regroupée par 3 heures, puis heure par heure. */
    private static final int[][] SLOTS = {
            {0, 3}, {3, 6}, {6, 7}, {7, 8}, {8, 9}, {9, 10}, {10, 11}, {11, 12}, {12, 13}, {13, 14},
            {14, 15}, {15, 16}, {16, 17}, {17, 18}, {18, 19}, {19, 20}, {20, 21}, {21, 22}, {22, 23}, {23, 24}};

    private void renderWeather(Day d, ZoneId zone) {
        Sun sun = Sun.of(d.date, lat(), lon(), zone);
        if (!d.rain.hasAny()) {
            totalView.setText(L.t("no_day_data"));
        } else {
            String t = L.f("day_rain", Fmt.total(d.rain.total()));
            if (d.snow.total() >= 0.1) t += "  ·  " + L.f("day_snow", Fmt.total(d.snow.total()));
            if (d.sun.hasAny()) t += "  ·  " + L.f("day_sun", Fmt.duration(d.sun.total()), Fmt.duration(sun.dayMinutes()));
            if (!d.rain.isComplete()) t += "  " + L.t("partial");
            totalView.setText(t);
        }

        int hour = selected == 1 ? forecast.currentHour() : -1;
        List<Weather.Hour> hs = Weather.hours(forecast, zone, lat(), lon(), d.date, 0, 24);
        List<DayView.Cell> cells = new ArrayList<>();
        int current = 0;
        for (int[] slot : SLOTS) {
            int a = slot[0], b = slot[1];
            DayView.Cell c = new DayView.Cell();
            c.label = L.slot(a, b);
            c.highlight = hour >= a && hour < b;
            if (c.highlight) current = cells.size();
            boolean hasRain = false, hasSun = false, dark = true;
            int nights = 0, tn = 0, wn = 0;
            double rain = 0, sunMin = 0, tsum = 0, wsum = 0, wx = 0, wy = 0, snowCm = 0;
            for (int h = a; h < b; h++) {
                Weather.Hour x = hs.get(h);
                if (d.rain.has[h]) { hasRain = true; rain += d.rain.v[h]; }
                if (d.snow.has[h]) snowCm += d.snow.v[h];
                if (d.sun.has[h]) { hasSun = true; sunMin += d.sun.v[h]; }
                if (x.daylight > 0) dark = false;
                if (x.night) nights++;
                if (!Double.isNaN(x.temp)) { tsum += x.temp; tn++; }
                if (!Double.isNaN(x.wind)) {
                    wsum += x.wind;
                    wn++;
                    if (!Double.isNaN(x.windDir)) {
                        // Moyenne des directions par vecteurs (350° et 10° donnent 0°, pas 180°).
                        wx += x.wind * Math.sin(Math.toRadians(x.windDir));
                        wy += x.wind * Math.cos(Math.toRadians(x.windDir));
                    }
                }
            }
            c.rain = !hasRain ? L.t("nd") : snowCm >= 0.05 ? Fmt.mm(snowCm) + "\u00a0cm" : Fmt.mm(rain);
            c.sun = !hasSun ? L.t("nd") : dark ? "" : Fmt.minutes(sunMin);
            if (tn > 0) c.temp = Fmt.deg(tsum / tn);
            if (wn > 0) {
                c.wind = String.valueOf(Math.round(wsum / wn));
                if (wx != 0 || wy != 0) c.windDir = (Math.toDegrees(Math.atan2(wx, wy)) + 360) % 360;
            }
            c.icon = Weather.kindOf(hs.subList(a, b));
            c.iconNight = nights * 2 > b - a;
            cells.add(c);
        }
        dayView.setCells(cells);

        // En arrivant sur l'onglet, on part tout en haut (bandeau des jours visible).
        // En changeant de jour, la position ne bouge pas : le bandeau touché reste sous le doigt.
        if (positionList) {
            positionList = false;
            scroll.post(() -> scroll.scrollTo(0, 0));
        }
    }

    private void renderEphemeris(Day d, LocalDate today, ZoneId zone, String[] labels, String[] temps) {
        boolean south = lat() < 0;

        // Bandeau : la lune de chaque jour, vers 22 h.
        double[] moons = new double[days.size()];
        for (int i = 0; i < moons.length; i++) {
            moons[i] = MoonPhase.at(days.get(i).date.atTime(22, 0).atZone(zone).toInstant()).elong;
        }
        strip.setCells(labels, null, moons, temps, south, selected);

        // Fête du jour selon le pays choisi ; plusieurs prénoms : texte plus petit.
        String names = Saints.nameDay(d.date);
        saintView.setTextSize(TypedValue.COMPLEX_UNIT_SP, names.length() > 22 ? 18 : 24);
        saintView.setText(names);
        String feast = Saints.feast(d.date);
        feastView.setText(feast == null ? "" : feast);
        feastView.setVisibility(feast == null ? View.GONE : View.VISIBLE);

        Sun sun = Sun.of(d.date, lat(), lon(), zone);
        if (sun.state == Sun.NORMAL) {
            riseView.setText(Fmt.clock(sun.rise));
            setView.setText(Fmt.clock(sun.set));
            long diff = Math.round(sun.dayMinutes()) - Math.round(Sun.of(d.date.minusDays(1), lat(), lon(), zone).dayMinutes());
            String sign = diff > 0 ? "+" : diff < 0 ? "−" : "±";
            dayLengthView.setText(L.f("day_length", Fmt.duration(sun.dayMinutes()), sign + Math.abs(diff)));
        } else {
            riseView.setText("—");
            setView.setText("—");
            dayLengthView.setText(L.t(sun.state == Sun.ALWAYS_UP ? "sun_up" : "sun_down"));
        }

        // Lune : maintenant pour aujourd'hui, 22 h pour les autres jours.
        Instant ref = d.date.equals(today) ? Instant.now() : d.date.atTime(22, 0).atZone(zone).toInstant();
        MoonPhase.Info info = MoonPhase.at(ref);
        MoonPhase.Event event = MoonPhase.onDay(d.date, zone);
        moonView.setPhase(info.elong, south);
        phaseView.setText(event != null ? MoonPhase.mainName(event.type) : MoonPhase.name(info.elong));
        String moonInfo = L.f("moon_lit", Math.round(info.illum * 100));
        if (event != null) moonInfo = L.f("moon_exact", Fmt.clock(event.t.atZone(zone))) + "\n" + moonInfo;
        else moonInfo += "\n" + L.f("moon_age", L.days(Math.round(info.age)));
        moonInfoView.setText(moonInfo);

        StringBuilder next = new StringBuilder();
        Instant from = d.date.plusDays(1).atStartOfDay(zone).toInstant();
        for (MoonPhase.Event e : MoonPhase.after(from, 3)) {
            if (next.length() > 0) next.append('\n');
            next.append(MoonPhase.mainName(e.type)).append(L.colon()).append(Fmt.weekdayShort(e.t.atZone(zone).toLocalDate()));
        }
        nextPhasesView.setText(next);
    }

    // ---------------------------------------------------------------- données

    private double lat() { return Double.parseDouble(prefs.getString("lat", Double.toString(DEF_LAT))); }
    private double lon() { return Double.parseDouble(prefs.getString("lon", Double.toString(DEF_LON))); }

    private void refresh() {
        if (tab == TAB_RADAR) radarTime = 0; // « Actualiser » recharge aussi le radar
        if (loading) return;
        loading = true;
        statusView.setText(L.t("updating"));
        final String url = String.format(Locale.US,
                "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f"
                        + "&hourly=precipitation,sunshine_duration,cloud_cover,weather_code,temperature_2m,"
                        + "wind_speed_10m,wind_direction_10m,direct_radiation,visibility,snowfall,"
                        + "relative_humidity_2m,dew_point_2m,cloud_cover_low,cloud_cover_high"
                        + "&current=temperature_2m,apparent_temperature,weather_code,cloud_cover,precipitation,"
                        + "wind_speed_10m,wind_direction_10m,is_day,visibility,snowfall,relative_humidity_2m,dew_point_2m,"
                        + "cloud_cover_low"
                        + "&past_days=1&forecast_days=9&timezone=auto",
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
                    statusView.setText(L.t("update_failed"));
                });
            }
        });
    }

    // ---------------------------------------------------------------- radar

    private LinearLayout buildRadar() {
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);
        radarView = new RadarView(this);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1);
        mlp.topMargin = dp(4);
        p.addView(radarView, mlp);
        // Boutons : zoom −, image précédente, lecture / pause, image suivante, zoom +.
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        String[] labels = {"−", "◀", "▶", "▶▶", "+"};
        for (int i = 0; i < labels.length; i++) {
            TextView b = button(labels[i]);
            b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
            b.setPadding(0, dp(4), 0, dp(4));
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(0, dp(44), 1);
            if (i > 0) blp.leftMargin = dp(6);
            bar.addView(b, blp);
            final int k = i;
            b.setOnClickListener(v -> radarButton(k));
            if (i == 2) playButton = b;
        }
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.topMargin = dp(6);
        p.addView(bar, blp);
        return p;
    }

    /** Zoom du radar, remis à RADAR_DEFAULT_ZOOM (≈ 400 km de large) à chaque ouverture de l'onglet. */
    private static final int RADAR_DEFAULT_ZOOM = 7;
    private int radarZoomLevel = RADAR_DEFAULT_ZOOM;

    private int radarZoom() {
        return Math.max(RADAR_MIN_ZOOM, Math.min(RADAR_MAX_ZOOM, radarZoomLevel));
    }

    private void radarButton(int k) {
        switch (k) {
            case 0:
            case 4: {
                int z = radarZoom() + (k == 0 ? -1 : 1);
                if (z < RADAR_MIN_ZOOM || z > RADAR_MAX_ZOOM) return;
                radarZoomLevel = z;
                renderRadar();
                break;
            }
            case 1:
                stopPlay();
                radarView.show(radarView.index() - 1);
                break;
            case 3:
                stopPlay();
                radarView.show(radarView.index() + 1);
                break;
            default:
                if (playing) stopPlay();
                else if (radarView.count() > 1) {
                    // Depuis la dernière image, la lecture repart de la plus ancienne.
                    if (radarView.index() >= radarView.count() - 1) radarView.show(0);
                    playing = true;
                    playButton.setText("❚❚");
                    ui.postDelayed(player, 300);
                }
        }
    }

    private void stopPlay() {
        playing = false;
        ui.removeCallbacks(player);
        if (playButton != null) playButton.setText("▶");
    }

    private void renderRadar() {
        String name = prefs.getString("name", DEF_NAME);
        int z = radarZoom();
        radarView.setZone(zone());
        radarView.setPlace(lat(), lon(), name, z);
        statusView.setText(L.t("radar_status"));
        String key = lat() + "/" + lon() + "/" + Math.min(z, RadarData.MAX_RADAR_ZOOM) + "/" + z;
        if (!key.equals(radarKey) || System.currentTimeMillis() - radarTime > RADAR_STALE_MS) loadRadar(key);
    }

    private void loadRadar(String key) {
        radarKey = key;
        radarTime = System.currentTimeMillis();
        final int gen = ++radarGen;
        final double la = lat(), lo = lon();
        final int z = radarZoom();
        radarView.setMessage(L.t("radar_loading"));
        radarIo.execute(() -> {
            MapData m = null;
            try {
                m = MapData.get(this);
            } catch (Exception ignored) {
            }
            final MapData map = m;
            if (map != null) ui.post(() -> radarView.setMap(map));
            RadarData.Result r = RadarData.load(la, lo, z);
            ui.post(() -> {
                if (gen != radarGen) return;
                String msg = r.failed ? L.t("radar_failed") : "";
                if (r.failed) radarTime = 0; // réessayer à la prochaine ouverture
                stopPlay();
                radarView.setFrames(r.frames);
                radarView.setMessage(msg);
            });
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
        input.setHint(L.t("city_hint"));
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        input.setTextColor(Ui.INK);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        box.addView(input, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        ScrollView scroll = new ScrollView(this);
        scroll.addView(box);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(L.t("place"))
                .setView(scroll)
                .setPositiveButton(L.t("search"), (dlg, w) -> searchPlace(input.getText().toString().trim()))
                .setNeutralButton(L.t("gps"), (dlg, w) -> useGps())
                .setNegativeButton(L.t("cancel"), null)
                .create();
        fillFavorites(box, dialog);
        dialog.show();
    }

    // ---------------------------------------------------------------- favoris

    /** Lieux favoris enregistrés : [{"n": nom, "la": latitude, "lo": longitude}, …]. */
    private JSONArray favorites() {
        try {
            return new JSONArray(prefs.getString("favorites", "[]"));
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    private int favoriteIndex(JSONArray favs, double la, double lo) {
        for (int i = 0; i < favs.length(); i++) {
            JSONObject f = favs.optJSONObject(i);
            if (f != null && Math.abs(f.optDouble("la") - la) < 0.005 && Math.abs(f.optDouble("lo") - lo) < 0.005) return i;
        }
        return -1;
    }

    /** Sous la saisie : ajouter / retirer le lieu actuel des favoris, puis la liste des favoris (un appui pour choisir). */
    private void fillFavorites(LinearLayout box, AlertDialog dialog) {
        while (box.getChildCount() > 1) box.removeViewAt(1);
        JSONArray favs = favorites();
        String name = prefs.getString("name", DEF_NAME);
        double la = lat(), lo = lon();
        int cur = favoriteIndex(favs, la, lo);

        TextView toggle = text(17, false);
        toggle.setText(cur >= 0 ? "★  " + L.f("fav_remove", name) : "☆  " + L.f("fav_add", name));
        if (Ui.COLOR) toggle.setTextColor(Ui.ACCENT);
        toggle.setPadding(0, dp(12), 0, dp(12));
        toggle.setOnClickListener(v -> {
            JSONArray f = favorites();
            int i = favoriteIndex(f, la, lo);
            if (i >= 0) f.remove(i);
            else {
                try {
                    f.put(new JSONObject().put("n", name).put("la", la).put("lo", lo));
                } catch (Exception ignored) {
                }
            }
            prefs.edit().putString("favorites", f.toString()).apply();
            fillFavorites(box, dialog);
        });
        box.addView(toggle);

        if (favs.length() == 0) return;
        box.addView(rule());
        TextView title = text(14, true);
        title.setTextColor(Ui.MUTED);
        title.setText(L.t("favorites"));
        title.setPadding(0, dp(8), 0, dp(2));
        box.addView(title);
        for (int i = 0; i < favs.length(); i++) {
            JSONObject f = favs.optJSONObject(i);
            if (f == null) continue;
            String n = f.optString("n");
            double fla = f.optDouble("la"), flo = f.optDouble("lo");
            TextView row = text(20, i == cur);
            row.setText(n);
            row.setPadding(0, dp(10), 0, dp(10));
            row.setOnClickListener(v -> {
                dialog.dismiss();
                setPlace(n, fla, flo);
            });
            box.addView(row);
        }
    }

    private void searchPlace(String q) {
        if (q.isEmpty()) return;
        statusView.setText(L.f("searching", q));
        io.execute(() -> {
            try {
                String url = "https://geocoding-api.open-meteo.com/v1/search?count=10&format=json&language=" + L.code() + "&name="
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
                        Toast.makeText(this, L.t("no_place"), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    new AlertDialog.Builder(this)
                            .setTitle(L.t("choose_place"))
                            .setItems(labels, (dlg, i) -> setPlace(names[i], las[i], los[i]))
                            .setNegativeButton(L.t("cancel"), null)
                            .show();
                });
            } catch (Exception e) {
                ui.post(() -> {
                    render();
                    Toast.makeText(this, L.t("search_failed"), Toast.LENGTH_SHORT).show();
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
        else Toast.makeText(this, L.t("loc_denied"), Toast.LENGTH_SHORT).show();
    }

    private void gpsPlace(Location l) {
        double la = l.getLatitude(), lo = l.getLongitude();
        // Coordonnées affichées le temps de trouver le nom de la commune, puis remplacées par celui-ci.
        setPlace(String.format(Locale.ROOT, "GPS %.2f, %.2f", la, lo), la, lo);
        resolvePlaceName(la, lo);
    }

    /** Remplace en arrière-plan le nom « GPS … » par celui de la commune, si on le trouve. */
    private void resolvePlaceName(double la, double lo) {
        io.execute(() -> {
            String name = placeName(la, lo);
            if (name == null || name.isEmpty()) return;
            ui.post(() -> {
                // Seulement si le lieu n'a pas changé entre-temps.
                if (lat() != la || lon() != lo) return;
                prefs.edit().putString("name", name).apply();
                render();
            });
        });
    }

    /** Nom de la commune aux coordonnées données (OpenStreetMap, sinon géocodeur du système), ou null. */
    @SuppressWarnings("deprecation")
    private String placeName(double la, double lo) {
        try {
            String url = String.format(Locale.ROOT,
                    "https://nominatim.openstreetmap.org/reverse?format=jsonv2&zoom=13&lat=%.5f&lon=%.5f&accept-language=%s",
                    la, lo, L.code());
            JSONObject res = new JSONObject(Http.get(url));
            JSONObject a = res.optJSONObject("address");
            if (a != null) {
                for (String k : new String[]{"city", "town", "village", "municipality", "hamlet", "suburb"}) {
                    String v = a.optString(k, "");
                    if (!v.isEmpty()) return v;
                }
            }
            String v = res.optString("name", "");
            if (!v.isEmpty()) return v;
        } catch (Exception ignored) {
        }
        try {
            if (!Geocoder.isPresent()) return null;
            List<Address> list = new Geocoder(this, Locale.getDefault()).getFromLocation(la, lo, 1);
            if (list == null || list.isEmpty()) return null;
            Address a = list.get(0);
            return a.getLocality() != null ? a.getLocality() : a.getSubAdminArea();
        } catch (Exception e) {
            return null;
        }
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
            Toast.makeText(this, L.t("loc_off"), Toast.LENGTH_LONG).show();
            return;
        }
        statusView.setText(L.t("locating"));

        if (Build.VERSION.SDK_INT >= 30) {
            lm.getCurrentLocation(provider, null, getMainExecutor(), loc -> {
                if (loc != null) gpsPlace(loc);
                else if (fallback != null) gpsPlace(fallback);
                else {
                    render();
                    Toast.makeText(this, L.t("loc_none"), Toast.LENGTH_SHORT).show();
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
