package fr.pluie.kompakt;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Temps qu'il fait heure par heure (pour les icônes) et résumé écrit des heures à venir. */
final class Weather {
    private Weather() {}

    // Types d'icône.
    static final int NONE = -1, CLEAR = 0, FEW_CLOUDS = 1, MANY_CLOUDS = 2, OVERCAST = 3,
            LIGHT_RAIN = 4, HEAVY_RAIN = 5, STORM = 6;

    /** Une heure de prévision (créneau de h à h+1, heure locale). */
    static final class Hour {
        LocalDate date;
        int h;
        boolean has;          // données disponibles
        boolean night;        // moins d'une demi-heure de jour dans le créneau
        double daylight;      // minutes de jour dans le créneau
        double mm;
        double temp = Double.NaN;
        double wind = Double.NaN, windDir = Double.NaN;
        /** 0 = couvert … 1 = dégagé, -1 si inconnu. */
        double clear = -1;
        boolean wet, heavy, storm;
        int kind = NONE;
    }

    /** {@code count} heures à partir de {@code date} à {@code startHour} (peut déborder sur le lendemain). */
    static List<Hour> hours(Forecast f, ZoneId zone, double lat, double lon, LocalDate date, int startHour, int count) {
        List<Hour> out = new ArrayList<>();
        Map<LocalDate, Sun> suns = new HashMap<>();
        LocalDateTime t = date.atStartOfDay().plusHours(startHour);
        for (int i = 0; i < count; i++, t = t.plusHours(1)) {
            Hour x = new Hour();
            x.date = t.toLocalDate();
            x.h = t.getHour();
            Sun sun = suns.get(x.date);
            if (sun == null) {
                sun = Sun.of(x.date, lat, lon, zone);
                suns.put(x.date, sun);
            }
            Instant a = t.atZone(zone).toInstant();
            double daylight = sun.daylightMinutes(a, a.plusSeconds(3600));
            x.night = daylight < 30;
            x.daylight = daylight;

            Day d = f == null ? null : f.day(x.date);
            if (d != null && d.rain.has[x.h]) {
                x.has = true;
                x.mm = d.rain.v[x.h];
                if (d.temp.has[x.h]) x.temp = d.temp.v[x.h];
                if (d.wind.has[x.h]) x.wind = d.wind.v[x.h];
                if (d.windDir.has[x.h]) x.windDir = d.windDir.v[x.h];
                int code = d.code[x.h];
                // Ensoleillement rapporté à la durée du jour dans l'heure ; la couverture nuageuse
                // corrige le soleil bas du matin et du soir, que l'ensoleillement mesure mal.
                double c = -1;
                if (daylight >= 10 && d.sun.has[x.h]) c = Math.min(1, d.sun.v[x.h] / daylight);
                if (d.cloud.has[x.h]) c = Math.max(c, 1 - d.cloud.v[x.h] / 100);
                x.clear = c;
                x.storm = code >= 95;
                x.heavy = x.mm >= 2.5 || code == 65 || code == 67 || code == 75 || code == 82 || code == 86;
                x.wet = x.storm || x.heavy || x.mm >= 0.1
                        || (code >= 51 && code <= 67) || (code >= 71 && code <= 77) || (code >= 80 && code <= 86);
                x.kind = classify(x.storm, x.heavy, x.wet, c);
            }
            out.add(x);
        }
        return out;
    }

    private static int classify(boolean storm, boolean heavy, boolean wet, double clear) {
        if (storm) return STORM;
        if (heavy) return HEAVY_RAIN;
        if (wet) return LIGHT_RAIN;
        if (clear >= 0.75) return CLEAR;
        if (clear >= 0.45) return FEW_CLOUDS;
        if (clear >= 0.15) return MANY_CLOUDS;
        if (clear >= 0) return OVERCAST;
        return NONE;
    }

    /** Icône d'un groupe d'heures (ex. 0–3h) : le temps le plus marquant, sinon le ciel moyen. */
    static int kindOf(List<Hour> hs) {
        boolean any = false, storm = false, heavy = false, wet = false;
        double sum = 0;
        int n = 0;
        for (Hour x : hs) {
            if (!x.has) continue;
            any = true;
            storm |= x.storm;
            heavy |= x.heavy;
            wet |= x.wet;
            if (x.clear >= 0) {
                sum += x.clear;
                n++;
            }
        }
        if (!any) return NONE;
        return classify(storm, heavy, wet, n > 0 ? sum / n : -1);
    }

    // ---------------------------------------------------------------- résumé écrit

    private static final int MORNING = 0, AFTERNOON = 1, EVENING = 2, NIGHT = 3, ALL_DAY = 4;

    private static final class Part {
        final int label;
        final List<Hour> hours;
        int sky = -1;
        boolean nightly, dry = true;

        Part(int label, List<Hour> hours) {
            this.label = label;
            this.hours = hours;
            double sum = 0;
            int n = 0, nights = 0;
            for (Hour x : hours) {
                if (x.night) nights++;
                if (x.wet) dry = false;
                if (x.clear >= 0) {
                    sum += x.clear;
                    n++;
                }
            }
            nightly = nights * 2 > hours.size();
            if (n > 0) {
                double c = sum / n;
                sky = c >= 0.75 ? 4 : c >= 0.5 ? 3 : c >= 0.25 ? 2 : c >= 0.08 ? 1 : 0;
            }
        }
    }

    /**
     * Phrases décrivant le reste de la période en cours : la journée entière le matin,
     * l'après-midi, la soirée, ou la nuit.
     */
    static List<String> summary(Forecast f, ZoneId zone, double lat, double lon, LocalDateTime now) {
        LocalDate d = now.toLocalDate();
        int h = now.getHour();
        List<Part> parts = new ArrayList<>();
        if (h < 12) {
            parts.add(new Part(MORNING, hours(f, zone, lat, lon, d, h, 12 - h)));
            parts.add(new Part(AFTERNOON, hours(f, zone, lat, lon, d, 12, 6)));
            parts.add(new Part(EVENING, hours(f, zone, lat, lon, d, 18, 6)));
        } else if (h < 17) {
            parts.add(new Part(AFTERNOON, hours(f, zone, lat, lon, d, h, 18 - h)));
        } else if (h < 22) {
            parts.add(new Part(EVENING, hours(f, zone, lat, lon, d, h, 24 - h)));
        } else {
            parts.add(new Part(NIGHT, hours(f, zone, lat, lon, d, h, 24 + 7 - h)));
        }

        List<String> out = new ArrayList<>();
        int i = 0;
        while (i < parts.size()) {
            // Regroupe les périodes consécutives au temps identique et sec (« ce matin et cet après-midi »).
            int j = i + 1;
            Part p = parts.get(i);
            while (j < parts.size() && p.dry && parts.get(j).dry
                    && parts.get(j).sky == p.sky && parts.get(j).nightly == p.nightly) j++;
            List<Hour> hs = new ArrayList<>();
            for (int k = i; k < j; k++) hs.addAll(parts.get(k).hours);
            String label = j - i == 3 ? L.item("parts", ALL_DAY)
                    : j - i == 2 ? L.item("parts", parts.get(i).label) + L.t("and") + L.item("parts", parts.get(i + 1).label)
                    : L.item("parts", p.label);
            String s = describe(label, p, hs);
            if (s != null) out.add(s);
            i = j;
        }
        if (out.isEmpty()) out.add(L.t("no_forecast"));
        return out;
    }

    private static String describe(String label, Part p, List<Hour> hs) {
        boolean any = false;
        for (Hour x : hs) any |= x.has;
        if (!any || p.sky < 0) return null;

        StringBuilder s = new StringBuilder(Fmt.cap(label)).append(L.colon());
        s.append(L.item(p.nightly ? "sky_night" : "sky_day", p.sky)).append(", ").append(rain(hs));

        double min = Double.MAX_VALUE, max = -Double.MAX_VALUE;
        for (Hour x : hs) {
            if (Double.isNaN(x.temp)) continue;
            min = Math.min(min, x.temp);
            max = Math.max(max, x.temp);
        }
        if (min <= max) {
            long lo = Fmt.temp(min), hi = Fmt.temp(max);
            s.append(lo == hi ? L.f("temp_around", Fmt.degUnit(min)) : L.f("temp_range", lo, Fmt.degUnit(max)));
        }
        double wind = 0;
        for (Hour x : hs) if (x.wind > wind) wind = x.wind;
        if (wind >= 40) s.append(L.f("strong_wind", Math.round(wind)));
        return s.append('.').toString();
    }

    /** " vers 15 h" ou " entre 14 h et 16 h" pour les heures first..last de hs. */
    private static String when(List<Hour> hs, int first, int last) {
        return first == last ? L.f("at", L.hour(hs.get(first).h))
                : L.f("between", L.hour(hs.get(first).h), L.hour(hs.get(last).h + 1));
    }

    /** "temps sec", "pluie faible entre 14 h et 16 h (1,2 mm)"… */
    private static String rain(List<Hour> hs) {
        int first = -1, last = -1, wet = 0, firstStorm = -1, lastStorm = -1;
        double total = 0, max = 0;
        for (int i = 0; i < hs.size(); i++) {
            Hour x = hs.get(i);
            if (!x.wet) continue;
            if (first < 0) first = i;
            last = i;
            wet++;
            total += x.mm;
            max = Math.max(max, x.mm);
            if (x.storm) {
                if (firstStorm < 0) firstStorm = i;
                lastStorm = i;
            }
        }
        if (wet == 0) return L.t("dry");

        String when = wet < hs.size() * 0.75 ? when(hs, first, last) : "";
        String amount = total >= 0.5 ? " (" + Fmt.total(total) + "\u00a0mm)" : "";
        if (firstStorm >= 0) return L.t("rain") + when + amount + L.t("storm") + when(hs, firstStorm, lastStorm);
        if (max >= 4 || total >= 10) return L.t("heavy_rain") + when + amount;
        if (total < 0.5) return L.t("drops") + when;
        if (max < 1) return L.t("light_rain") + when + amount;
        return L.t("rain") + when + amount;
    }

    /** Une phrase sur la pluie d'ici deux heures (créneau en cours + suivant). */
    static String nextTwoHours(Forecast f, ZoneId zone, double lat, double lon, LocalDateTime now) {
        List<Hour> hs = hours(f, zone, lat, lon, now.toLocalDate(), now.getHour(), 2);
        Hour a = hs.get(0), b = hs.get(1);
        if (!a.has || !b.has) return L.t("n2_nodata");
        String until = L.hour(now.getHour() + 2);
        String next = L.hour(b.h);
        if (!a.wet && !b.wet) return L.t("n2_dry");
        if (a.wet && b.wet) {
            double mm = a.mm + b.mm;
            if (a.storm || b.storm) return L.f("n2_storm_now", until);
            if (mm < 0.5) return L.f("n2_drops_now", until);
            return L.f("n2_rain_now", until, Fmt.total(mm));
        }
        if (a.wet) return L.f("n2_stop", next);
        if (b.storm) return L.f("n2_storm_next", next);
        if (b.heavy) return L.f("n2_heavy_next", next, Fmt.total(b.mm));
        if (b.mm < 0.5) return L.f("n2_drops_next", next);
        return L.f("n2_rain_next", next, Fmt.total(b.mm));
    }
}
