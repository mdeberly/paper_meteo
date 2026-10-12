package fr.pluie.kompakt;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Lever et coucher du soleil calculés sur l'appareil (équation du lever de soleil, précision ≈ 1 min).
 * Aucune connexion nécessaire.
 */
final class Sun {
    static final int NORMAL = 0, ALWAYS_UP = 1, ALWAYS_DOWN = -1;

    final int state;
    final ZonedDateTime rise, set;

    private Sun(int state, ZonedDateTime rise, ZonedDateTime set) {
        this.state = state;
        this.rise = rise;
        this.set = set;
    }

    static Sun of(LocalDate d, double lat, double lon, ZoneId zone) {
        double n = d.toEpochDay() - 10957;          // jours depuis J2000 (midi UTC de ce jour)
        double jStar = n - lon / 360.0;              // midi solaire moyen
        double m = norm(357.5291 + 0.98560028 * jStar);
        double mr = Math.toRadians(m);
        double c = 1.9148 * Math.sin(mr) + 0.0200 * Math.sin(2 * mr) + 0.0003 * Math.sin(3 * mr);
        double lambda = Math.toRadians(norm(m + c + 180 + 102.9372));
        double transit = 2451545.0 + jStar + 0.0053 * Math.sin(mr) - 0.0069 * Math.sin(2 * lambda);
        double sinDec = Math.sin(lambda) * Math.sin(Math.toRadians(23.4397));
        double cosDec = Math.cos(Math.asin(sinDec));
        double phi = Math.toRadians(lat);
        double cosW = (Math.sin(Math.toRadians(-0.833)) - Math.sin(phi) * sinDec) / (Math.cos(phi) * cosDec);
        if (cosW < -1) return new Sun(ALWAYS_UP, null, null);
        if (cosW > 1) return new Sun(ALWAYS_DOWN, null, null);
        double w = Math.toDegrees(Math.acos(cosW)) / 360.0;
        return new Sun(NORMAL, at(transit - w, zone), at(transit + w, zone));
    }

    /** Durée du jour en minutes. */
    double dayMinutes() {
        if (state == ALWAYS_UP) return 24 * 60;
        if (state == ALWAYS_DOWN) return 0;
        return (set.toInstant().toEpochMilli() - rise.toInstant().toEpochMilli()) / 60000.0;
    }

    /** Minutes de jour dans le créneau [start, end). */
    double daylightMinutes(Instant start, Instant end) {
        if (state == ALWAYS_UP) return (end.toEpochMilli() - start.toEpochMilli()) / 60000.0;
        if (state == ALWAYS_DOWN) return 0;
        long a = Math.max(start.toEpochMilli(), rise.toInstant().toEpochMilli());
        long b = Math.min(end.toEpochMilli(), set.toInstant().toEpochMilli());
        return Math.max(0, b - a) / 60000.0;
    }

    /** Vrai si le créneau [start, end) est entièrement de nuit. */
    boolean isNight(Instant start, Instant end) {
        if (state == ALWAYS_UP) return false;
        if (state == ALWAYS_DOWN) return true;
        return !end.isAfter(rise.toInstant()) || !start.isBefore(set.toInstant());
    }

    /** Hauteur du soleil au-dessus de l'horizon, en degrés, à l'instant t (précision ≈ 0,1°). */
    static double elevation(Instant t, double lat, double lon) {
        double d = t.toEpochMilli() / 86400000.0 + 2440587.5 - 2451545.0;   // jours depuis J2000
        double g = Math.toRadians(norm(357.529 + 0.98560028 * d));          // anomalie moyenne
        double q = norm(280.459 + 0.98564736 * d);                          // longitude moyenne
        double l = Math.toRadians(q + 1.915 * Math.sin(g) + 0.020 * Math.sin(2 * g));
        double e = Math.toRadians(23.439 - 0.00000036 * d);                 // obliquité
        double ra = Math.atan2(Math.cos(e) * Math.sin(l), Math.cos(l));
        double dec = Math.asin(Math.sin(e) * Math.sin(l));
        double gmst = norm(280.46061837 + 360.98564736629 * d);             // temps sidéral, degrés
        double ha = Math.toRadians(gmst + lon) - ra;                        // angle horaire
        double phi = Math.toRadians(lat);
        return Math.toDegrees(Math.asin(Math.sin(phi) * Math.sin(dec) + Math.cos(phi) * Math.cos(dec) * Math.cos(ha)));
    }

    /**
     * Rayonnement direct reçu sur une surface horizontale par ciel parfaitement clair (W/m²),
     * modèle de Meinel avec la masse d'air de Kasten et Young.
     */
    static double clearSkyDirect(double elevationDeg) {
        if (elevationDeg <= 0) return 0;
        double am = 1 / (Math.sin(Math.toRadians(elevationDeg)) + 0.50572 * Math.pow(elevationDeg + 6.07995, -1.6364));
        double dni = 1361 * Math.pow(0.7, Math.pow(am, 0.678));
        return dni * Math.sin(Math.toRadians(elevationDeg));
    }

    private static ZonedDateTime at(double jd, ZoneId zone) {
        return Instant.ofEpochMilli(Math.round((jd - 2440587.5) * 86400000.0)).atZone(zone);
    }

    private static double norm(double deg) {
        deg %= 360;
        return deg < 0 ? deg + 360 : deg;
    }
}
