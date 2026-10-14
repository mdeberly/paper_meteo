package fr.pluie.kompakt;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.Instant;
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
    /** Faux pour un ancien cache enregistré avant l'ajout de l'ensoleillement, des nuages, etc. */
    public final boolean complete;
    private final Map<LocalDate, Day> byDate = new TreeMap<>();

    /** Conditions actuelles (bloc « current » d'Open-Meteo), ou null. */
    public Current current;

    public static final class Current {
        public double temp = Double.NaN, feels = Double.NaN, wind = Double.NaN, windDir = Double.NaN;
        public double cloud = -1, precip, visibility = Double.NaN, snowfall;
        public int code = -1;
        public boolean day = true;
    }

    private Forecast(ZoneId zone, boolean complete) {
        this.zone = zone;
        this.complete = complete;
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
        JSONArray cloud = hourly.optJSONArray("cloud_cover");
        JSONArray temp = hourly.optJSONArray("temperature_2m");
        JSONArray code = hourly.optJSONArray("weather_code");
        JSONArray wind = hourly.optJSONArray("wind_speed_10m");
        JSONArray windDir = hourly.optJSONArray("wind_direction_10m");
        JSONArray direct = hourly.optJSONArray("direct_radiation");
        JSONArray vis = hourly.optJSONArray("visibility");
        JSONArray snow = hourly.optJSONArray("snowfall");
        JSONObject cur = o.optJSONObject("current");
        Forecast f = new Forecast(zone, sun != null && cloud != null && temp != null && code != null
                && wind != null && windDir != null && direct != null && vis != null && snow != null && cur != null);
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
            put(day.cloud, cloud, i, h, 1);
            put(day.temp, temp, i, h, 1);
            put(day.wind, wind, i, h, 1);
            put(day.windDir, windDir, i, h, 1);
            put(day.direct, direct, i, h, 1);
            put(day.vis, vis, i, h, 1);
            put(day.snow, snow, i, h, 1);
            if (code != null && i < code.length() && !code.isNull(i)) day.code[h] = code.getInt(i);
        }
        if (o.has("latitude") && direct != null) {
            double lat = o.getDouble("latitude"), lon = o.getDouble("longitude");
            for (Day day : f.byDate.values()) estimateSunshine(day, zone, lat, lon);
        }
        if (cur != null) {
            Current c = new Current();
            c.temp = cur.optDouble("temperature_2m", Double.NaN);
            c.feels = cur.optDouble("apparent_temperature", Double.NaN);
            c.wind = cur.optDouble("wind_speed_10m", Double.NaN);
            c.windDir = cur.optDouble("wind_direction_10m", Double.NaN);
            c.cloud = cur.optDouble("cloud_cover", -1);
            c.precip = cur.optDouble("precipitation", 0);
            c.code = cur.optInt("weather_code", -1);
            c.visibility = cur.optDouble("visibility", Double.NaN);
            c.snowfall = cur.optDouble("snowfall", 0);
            c.day = cur.optInt("is_day", 1) == 1;
            f.current = c;
        }
        return f;
    }

    /**
     * Durée d'ensoleillement estimée à partir du rayonnement direct moyen de l'heure.
     *
     * Open-Meteo calcule sunshine_duration en comptant le temps où le direct dépasse 120 W/m² (définition OMM),
     * mais à chaque pas du modèle : le résultat est souvent « toute l'heure ou rien ». Le rayonnement direct
     * moyen, lui, baisse en proportion du temps où le soleil est caché : rapporté au direct par ciel clair
     * à cette heure et à cet endroit, il donne la part de l'heure ensoleillée (0,5 → environ 30 min).
     * Soleil trop bas (direct par ciel clair < 25 W/m²) : on garde la valeur d'Open-Meteo.
     * La couverture nuageuse sert d'estimation complémentaire ; on retient la plus haute.
     */
    private static void estimateSunshine(Day day, ZoneId zone, double lat, double lon) {
        for (int h = 0; h < 24; h++) {
            if (!day.direct.has[h]) continue;
            Instant start = day.date.atTime(h, 0).atZone(zone).toInstant();
            double clear = 0, daylight = 0;
            for (int k = 0; k < 6; k++) {           // moyenne sur l'heure, toutes les 10 minutes
                double el = Sun.elevation(start.plusSeconds(300 + 600L * k), lat, lon);
                clear += Sun.clearSkyDirect(el) / 6;
                if (el > -0.833) daylight += 10;
            }
            if (clear < 25) continue;
            // Au sens de l'OMM, un soleil voilé compte déjà comme ensoleillé (direct > 120 W/m²) :
            // dès 60 % du direct par ciel clair, l'heure entière est comptée ensoleillée.
            double byDirect = 60 * Math.min(1, day.direct.v[h] / clear / 0.6);
            // Deuxième estimation, par la couverture nuageuse (40 % de nuages → environ 36 min de soleil).
            double byCloud = day.cloud.has[h] ? 60 * (1 - day.cloud.v[h] / 100) : 0;
            // On garde la plus haute des estimations, sans jamais descendre sous la valeur d'Open-Meteo.
            double model = day.sun.has[h] ? day.sun.v[h] : 0;
            day.sun.v[h] = Math.min(daylight, Math.max(model, Math.max(byDirect, byCloud)));
            day.sun.has[h] = true;
        }
    }

    private static void put(Series s, JSONArray a, int i, int h, double factor) throws Exception {
        if (a != null && i < a.length() && !a.isNull(i)) {
            s.v[h] = a.getDouble(i) * factor;
            s.has[h] = true;
        }
    }

    /** La journée demandée, ou null si elle n'est pas dans les données. */
    public Day day(LocalDate d) {
        return byDate.get(d);
    }

    public LocalDate today() {
        return LocalDate.now(zone);
    }

    public int currentHour() {
        return LocalDateTime.now(zone).getHour();
    }

    /** Hier + aujourd'hui + 7 jours = 9 jours. */
    public List<Day> window() {
        LocalDate today = today();
        List<Day> list = new ArrayList<>();
        for (int k = -1; k <= 7; k++) {
            LocalDate d = today.plusDays(k);
            Day day = byDate.get(d);
            list.add(day != null ? day : new Day(d));
        }
        return list;
    }

    /** Les mêmes 9 jours, sans données (avant le premier téléchargement). */
    public static List<Day> emptyWindow(LocalDate today) {
        List<Day> list = new ArrayList<>();
        for (int k = -1; k <= 7; k++) list.add(new Day(today.plusDays(k)));
        return list;
    }
}
