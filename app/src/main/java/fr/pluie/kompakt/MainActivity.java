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

    private static final int TAB_SUMMARY = 0, TAB_WEATHER = 1, TAB_EPH = 2;
    private static final String[] TAB_KEYS = {"tab_summary", "tab_weather", "tab_eph"};

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
    /** À l'arrivée sur l'onglet Météo, placer la liste sur l'heure en cours. */
    private boolean positionList;

    private TextView placeView, titleView, totalView, statusView;
    private final TextView[] tabViews = new TextView[TAB_KEYS.length];
    /** Bandeau des 8 jours : fixe sur l'Éphéméride, dans la zone qui défile sur Météo. */
    private WeekStrip strip, weatherStrip;
    private LinearLayout weatherView;
    private ScrollView scroll;
    private DayView dayView;

    private LinearLayout ephView;
    private TextView saintView, feastView, riseView, setView, dayLengthView, phaseView, moonInfoView, nextPhasesView;
    private MoonView moonView;

    private LinearLayout summaryView;
    private TextView summaryText, soonView;
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
        L.init(lang);
        Fmt.fahrenheit = f;
        return lang + "/" + L.code() + "/" + f;
    }

    @Override
    protected void onResume() {
        super.onResume();
        boolean stale = System.currentTimeMillis() - prefs.getLong("fetched", 0) > STALE_MS;
        if (stale || (forecast != null && !forecast.complete)) refresh();
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
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        weatherStrip = new WeekStrip(this);
        weatherStrip.setOnPick(i -> { selected = i; render(); });
        content.addView(weatherStrip, stripParams());
        totalView = text(15, false);
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

        statusView = text(12, false);
        statusView.setPadding(0, dp(4), 0, 0);
        root.addView(statusView);

        setContentView(frame);
    }

    private LinearLayout.LayoutParams stripParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(104));
        lp.topMargin = dp(6);
        return lp;
    }

    private LinearLayout buildEphemeris() {
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setPadding(0, dp(6), 0, 0);

        TextView l = text(13, false);
        l.setText(L.t("feast_label"));
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

    private LinearLayout buildSummary() {
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);
        summaryText = text(18, false);
        summaryText.setLineSpacing(0, 1.1f);
        summaryText.setPadding(0, dp(4), 0, 0);
        p.addView(summaryText);
        p.addView(rule());
        TextView l = text(13, false);
        l.setText(L.t("next2h"));
        p.addView(l);
        soonView = text(18, true);
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
        return p;
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
        strip.setVisibility(tab == TAB_EPH ? View.VISIBLE : View.GONE);

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
            rainTotals[i] = x.rain.hasAny() ? Fmt.total(x.rain.total()) + " mm" : L.t("nd");
        }
        weatherStrip.setCells(labels, rainTotals, null, temps, false, selected);

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
            double rain = 0, sunMin = 0, tsum = 0, wsum = 0, wx = 0, wy = 0;
            for (int h = a; h < b; h++) {
                Weather.Hour x = hs.get(h);
                if (d.rain.has[h]) { hasRain = true; rain += d.rain.v[h]; }
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
            c.rain = hasRain ? Fmt.mm(rain) : L.t("nd");
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

        // En arrivant sur l'onglet, la liste se place sur l'heure en cours (aujourd'hui), sinon tout en haut.
        // En changeant de jour, la position ne bouge pas : le bandeau touché reste sous le doigt.
        if (positionList) {
            positionList = false;
            int row = Math.max(0, current - 1);
            scroll.post(() -> scroll.scrollTo(0, hour >= 0 ? dayView.getTop() + row * dayView.rowHeight() : 0));
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

        saintView.setText(Saints.of(d.date));
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
        if (loading) return;
        loading = true;
        statusView.setText(L.t("updating"));
        final String url = String.format(Locale.US,
                "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f"
                        + "&hourly=precipitation,sunshine_duration,cloud_cover,weather_code,temperature_2m,"
                        + "wind_speed_10m,wind_direction_10m"
                        + "&past_days=1&forecast_days=8&timezone=auto",
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
        box.setPadding(dp(20), dp(8), dp(20), 0);
        box.addView(input, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        new AlertDialog.Builder(this)
                .setTitle(L.t("place"))
                .setView(box)
                .setPositiveButton(L.t("search"), (dlg, w) -> searchPlace(input.getText().toString().trim()))
                .setNeutralButton(L.t("gps"), (dlg, w) -> useGps())
                .setNegativeButton(L.t("cancel"), null)
                .show();
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
        setPlace(String.format(Locale.ROOT, "GPS %.2f, %.2f", l.getLatitude(), l.getLongitude()),
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
