package fr.pluie.kompakt;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Réponse Open-Meteo regroupée par jour.
 * Open-Meteo donne pour l'heure H le cumul de l'heure PRÉCÉDENTE (H-1 → H) ;
 * on range donc chaque valeur dans le créneau qui commence à H-1.
 */
public class Forecast {
    public final ZoneOffset zone;
    private final Map<LocalDate, Day> byDate = new TreeMap<>();

    private Forecast(ZoneOffset zone) {
        this.zone = zone;
    }

    public static Forecast parse(String json) throws Exception {
        JSONObject o = new JSONObject(json);
        Forecast f = new Forecast(ZoneOffset.ofTotalSeconds(o.optInt("utc_offset_seconds", 0)));
        JSONObject hourly = o.getJSONObject("hourly");
        JSONArray times = hourly.getJSONArray("time");
        JSONArray precip = hourly.getJSONArray("precipitation");
        for (int i = 0; i < times.length(); i++) {
            LocalDateTime slotStart = LocalDateTime.parse(times.getString(i)).minusHours(1);
            LocalDate d = slotStart.toLocalDate();
            Day day = f.byDate.get(d);
            if (day == null) {
                day = new Day(d);
                f.byDate.put(d, day);
            }
            if (i < precip.length() && !precip.isNull(i)) {
                int h = slotStart.getHour();
                day.mm[h] = precip.getDouble(i);
                day.has[h] = true;
            }
        }
        return f;
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
}
