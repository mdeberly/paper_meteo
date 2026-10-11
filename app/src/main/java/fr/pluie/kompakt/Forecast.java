package fr.pluie.kompakt;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Réponse Open-Meteo regroupée par jour.
 * Open-Meteo donne pour l'heure H le cumul de l'heure PRÉCÉDENTE (H-1 → H), pour la pluie
 * comme pour l'ensoleillement ; on range donc chaque valeur dans le créneau qui commence à H-1.
 */
public class Forecast {
    public final ZoneId zone;
    /** Faux pour un ancien cache enregistré avant l'ajout de l'ensoleillement. */
    public final boolean hasSun;
    private final Map<LocalDate, Day> byDate = new TreeMap<>();

    private Forecast(ZoneId zone, boolean hasSun) {
        this.zone = zone;
        this.hasSun = hasSun;
    }

    public static Forecast parse(String json) throws Exception {
        JSONObject o = new JSONObject(json);
        ZoneId zone;
        try {
            zone = ZoneId.of(o.getString("timezone"));
        } catch (Exception e) {
            zone = ZoneOffset.ofTotalSeconds(o.optInt("utc_offset_seconds", 0));
        }
        JSONObject hourly = o.getJSONObject("hourly");
        JSONArray times = hourly.getJSONArray("time");
        JSONArray precip = hourly.optJSONArray("precipitation");
        JSONArray sun = hourly.optJSONArray("sunshine_duration");
        Forecast f = new Forecast(zone, sun != null);
        for (int i = 0; i < times.length(); i++) {
            LocalDateTime slotStart = LocalDateTime.parse(times.getString(i)).minusHours(1);
            LocalDate d = slotStart.toLocalDate();
            Day day = f.byDate.get(d);
            if (day == null) {
                day = new Day(d);
                f.byDate.put(d, day);
            }
            int h = slotStart.getHour();
            put(day.rain, precip, i, h, 1);
            put(day.sun, sun, i, h, 1 / 60.0); // secondes → minutes
        }
        return f;
    }

    private static void put(Series s, JSONArray a, int i, int h, double factor) throws Exception {
        if (a != null && i < a.length() && !a.isNull(i)) {
            s.v[h] = a.getDouble(i) * factor;
            s.has[h] = true;
        }
    }

    public LocalDate today() {
        return LocalDate.now(zone);
    }

    public int currentHour() {
        return LocalDateTime.now(zone).getHour();
    }

    /** Hier + aujourd'hui + 6 jours = 8 jours. */
    public List<Day> window() {
        LocalDate today = today();
        List<Day> list = new ArrayList<>();
        for (int k = -1; k <= 6; k++) {
            LocalDate d = today.plusDays(k);
            Day day = byDate.get(d);
            list.add(day != null ? day : new Day(d));
        }
        return list;
    }

    /** Les mêmes 8 jours, sans données (avant le premier téléchargement). */
    public static List<Day> emptyWindow(LocalDate today) {
        List<Day> list = new ArrayList<>();
        for (int k = -1; k <= 6; k++) list.add(new Day(today.plusDays(k)));
        return list;
    }
}
