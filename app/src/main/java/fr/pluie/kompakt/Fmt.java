package fr.pluie.kompakt;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/** Mise en forme des nombres, durées, dates et températures, dans la langue choisie ({@link L}). */
final class Fmt {
    private Fmt() {}

    /** Températures affichées en degrés Fahrenheit plutôt qu'en Celsius. */
    static boolean fahrenheit;

    /** 0 → "–", 0,4 → "0,4", 12,3 → "12". */
    static String mm(double v) {
        if (v < 0.05) return "–";
        if (v >= 10) return String.format(L.locale(), "%.0f", v);
        return String.format(L.locale(), "%.1f", v);
    }

    static String total(double v) {
        return String.format(L.locale(), "%.1f", v);
    }

    /** Minutes de soleil dans l'heure : 0 → "–", 42,6 → "43". */
    static String minutes(double v) {
        if (v < 0.5) return "–";
        return String.format(L.locale(), "%.0f", v);
    }

    /** 45 → "45 min", 320 → "5 h 20". */
    static String duration(double minutes) {
        long t = Math.round(minutes);
        if (t < 60) return t + " min";
        return String.format(L.locale(), "%d h %02d", t / 60, t % 60);
    }

    // ---------------------------------------------------------------- températures

    /** Température arrondie dans l'unité choisie (entrée en °C). */
    static long temp(double celsius) {
        return Math.round(fahrenheit ? celsius * 9 / 5 + 32 : celsius);
    }

    static String unit() {
        return fahrenheit ? "°F" : "°C";
    }

    /** "15°" */
    static String deg(double celsius) {
        return temp(celsius) + "°";
    }

    /** "15 °C" ou "59 °F" */
    static String degUnit(double celsius) {
        return temp(celsius) + " " + unit();
    }

    // ---------------------------------------------------------------- dates

    /** Heure arrondie à la minute : "8 h 02", "8:02 am"… */
    static String clock(ZonedDateTime t) {
        ZonedDateTime r = t.plusSeconds(30).truncatedTo(ChronoUnit.MINUTES);
        return L.clock(r.getHour(), r.getMinute());
    }

    /** "sam. 18", "Sat 18"… */
    static String weekdayShort(LocalDate d) {
        return d.format(DateTimeFormatter.ofPattern("EEE d", L.locale()));
    }

    private static final String[] COMPASS_FALLBACK = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};

    /** Direction d'où vient le vent : 225 → "SO" (en français). */
    static String compass(double deg) {
        int i = (int) Math.round(((deg % 360) + 360) % 360 / 45.0) % 8;
        String[] c = L.t("compass").split("\\|");
        return c.length == 8 ? c[i] : COMPASS_FALLBACK[i];
    }

    static String cap(String s) {
        return s.isEmpty() ? s : s.substring(0, 1).toUpperCase(L.locale()) + s.substring(1);
    }

    /** index 0 = hier, 1 = aujourd'hui… */
    static String title(int index, LocalDate d) {
        String date = d.format(DateTimeFormatter.ofPattern(L.lang() == L.EN ? "MMMM d" : "d MMMM", L.locale()));
        if (index <= 2) return L.item("days_long", index) + " · " + date;
        return cap(d.format(DateTimeFormatter.ofPattern(L.lang() == L.EN ? "EEEE, MMMM d" : "EEEE d MMMM", L.locale())));
    }

    static String shortLabel(int index, LocalDate d) {
        if (index <= 2) return L.item("days_short", index);
        return cap(d.format(DateTimeFormatter.ofPattern("EEE d", L.locale())).replace(".", ""));
    }
}
