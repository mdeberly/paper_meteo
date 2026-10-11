package fr.pluie.kompakt;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

final class Fmt {
    private Fmt() {}

    static final Locale FR = Locale.FRENCH;
    private static final DateTimeFormatter LONG = DateTimeFormatter.ofPattern("EEEE d MMMM", FR);
    private static final DateTimeFormatter SHORT = DateTimeFormatter.ofPattern("EEE d", FR);

    /** 0 → "–", 0,4 → "0,4", 12,3 → "12". */
    static String mm(double v) {
        if (v < 0.05) return "–";
        if (v >= 10) return String.format(FR, "%.0f", v);
        return String.format(FR, "%.1f", v);
    }

    static String total(double v) {
        return String.format(FR, "%.1f", v);
    }

    static String cap(String s) {
        return s.isEmpty() ? s : s.substring(0, 1).toUpperCase(FR) + s.substring(1);
    }

    /** index 0 = hier, 1 = aujourd'hui… */
    static String title(int index, LocalDate d) {
        String date = d.format(DateTimeFormatter.ofPattern("d MMMM", FR));
        switch (index) {
            case 0: return "Hier · " + date;
            case 1: return "Aujourd'hui · " + date;
            case 2: return "Demain · " + date;
            default: return cap(d.format(LONG));
        }
    }

    static String shortLabel(int index, LocalDate d) {
        switch (index) {
            case 0: return "Hier";
            case 1: return "Auj.";
            case 2: return "Dem.";
            default: return cap(d.format(SHORT).replace(".", ""));
        }
    }
}
