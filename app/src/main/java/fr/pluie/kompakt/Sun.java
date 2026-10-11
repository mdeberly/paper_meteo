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

    /** Vrai si le créneau [start, end) est entièrement de nuit. */
    boolean isNight(Instant start, Instant end) {
        if (state == ALWAYS_UP) return false;
        if (state == ALWAYS_DOWN) return true;
        return !end.isAfter(rise.toInstant()) || !start.isBefore(set.toInstant());
    }

    private static ZonedDateTime at(double jd, ZoneId zone) {
        return Instant.ofEpochMilli(Math.round((jd - 2440587.5) * 86400000.0)).atZone(zone);
    }

    private static double norm(double deg) {
        deg %= 360;
        return deg < 0 ? deg + 360 : deg;
    }
}
