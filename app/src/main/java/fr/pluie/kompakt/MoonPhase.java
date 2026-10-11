package fr.pluie.kompakt;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static java.lang.Math.cos;
import static java.lang.Math.sin;

/**
 * Phases de la Lune calculées sur l'appareil (Jean Meeus, « Astronomical Algorithms », chap. 49 :
 * instants des phases principales à la minute près).
 */
final class MoonPhase {
    static final int NEW = 0, FIRST = 1, FULL = 2, LAST = 3;
    static final String[] MAIN = {"Nouvelle lune", "Premier quartier", "Pleine lune", "Dernier quartier"};

    private MoonPhase() {}

    static final class Event {
        final Instant t;
        final int type;

        Event(Instant t, int type) {
            this.t = t;
            this.type = type;
        }
    }

    static final class Info {
        /** Élongation 0..360° : 0 = nouvelle lune, 90 = premier quartier, 180 = pleine lune, 270 = dernier quartier. */
        final double elong;
        /** Fraction éclairée 0..1. */
        final double illum;
        /** Jours écoulés depuis la dernière nouvelle lune. */
        final double age;

        Info(double elong, double illum, double age) {
            this.elong = elong;
            this.illum = illum;
            this.age = age;
        }
    }

    /** Nom en toutes lettres de la phase intermédiaire correspondant à l'élongation. */
    static String name(double elong) {
        if (elong < 90) return "Premier croissant";
        if (elong < 180) return "Lune gibbeuse croissante";
        if (elong < 270) return "Lune gibbeuse décroissante";
        return "Dernier croissant";
    }

    /** État de la Lune à l'instant t. */
    static Info at(Instant t) {
        List<Event> ev = around(t);
        Event prev = null, next = null, lastNew = null;
        for (Event e : ev) {
            if (!e.t.isAfter(t)) {
                prev = e;
                if (e.type == NEW) lastNew = e;
            } else if (next == null) {
                next = e;
            }
        }
        double span = next.t.toEpochMilli() - prev.t.toEpochMilli();
        double frac = (t.toEpochMilli() - prev.t.toEpochMilli()) / span;
        double elong = 90 * prev.type + 90 * frac;
        double illum = (1 - cos(Math.toRadians(elong))) / 2;
        double age = (t.toEpochMilli() - lastNew.t.toEpochMilli()) / 86400000.0;
        return new Info(elong, illum, age);
    }

    /** Phase principale tombant ce jour-là (heure locale), ou null. */
    static Event onDay(LocalDate d, ZoneId zone) {
        Instant start = d.atStartOfDay(zone).toInstant();
        Instant end = d.plusDays(1).atStartOfDay(zone).toInstant();
        for (Event e : around(start)) {
            if (!e.t.isBefore(start) && e.t.isBefore(end)) return e;
        }
        return null;
    }

    /** Les n prochaines phases principales strictement après t. */
    static List<Event> after(Instant t, int n) {
        List<Event> out = new ArrayList<>();
        for (Event e : around(t)) {
            if (e.t.isAfter(t) && out.size() < n) out.add(e);
        }
        return out;
    }

    /** Phases principales de deux lunaisons avant à deux lunaisons après t, dans l'ordre. */
    static List<Event> around(Instant t) {
        double year = 1970 + t.toEpochMilli() / (365.2425 * 86400000.0);
        long k0 = (long) Math.floor((year - 2000) * 12.3685);
        List<Event> list = new ArrayList<>();
        for (long k = k0 - 2; k <= k0 + 2; k++) {
            for (int q = 0; q < 4; q++) {
                double jde = jde(k + q / 4.0, q);
                // JDE est en temps terrestre : on retire ΔT ≈ 69 s pour revenir au temps universel.
                long ms = Math.round((jde - 2440587.5) * 86400000.0) - 69000;
                list.add(new Event(Instant.ofEpochMilli(ms), q));
            }
        }
        return list; // déjà dans l'ordre chronologique
    }

    private static double jde(double k, int q) {
        double t = k / 1236.85, t2 = t * t, t3 = t2 * t, t4 = t3 * t;
        double jde = 2451550.09766 + 29.530588861 * k + 0.00015437 * t2 - 0.000000150 * t3 + 0.00000000073 * t4;
        double e = 1 - 0.002516 * t - 0.0000074 * t2;
        double m = rad(2.5534 + 29.10535670 * k - 0.0000014 * t2 - 0.00000011 * t3);
        double mp = rad(201.5643 + 385.81693528 * k + 0.0107582 * t2 + 0.00001238 * t3 - 0.000000058 * t4);
        double f = rad(160.7108 + 390.67050284 * k - 0.0016118 * t2 - 0.00000227 * t3 + 0.000000011 * t4);
        double om = rad(124.7746 - 1.56375588 * k + 0.0020672 * t2 + 0.00000215 * t3);

        double c;
        if (q == NEW || q == FULL) {
            boolean n = q == NEW;
            c = (n ? -0.40720 : -0.40614) * sin(mp)
                    + (n ? 0.17241 : 0.17302) * e * sin(m)
                    + (n ? 0.01608 : 0.01614) * sin(2 * mp)
                    + (n ? 0.01039 : 0.01043) * sin(2 * f)
                    + (n ? 0.00739 : 0.00734) * e * sin(mp - m)
                    + (n ? -0.00514 : -0.00515) * e * sin(mp + m)
                    + (n ? 0.00208 : 0.00209) * e * e * sin(2 * m)
                    - 0.00111 * sin(mp - 2 * f)
                    - 0.00057 * sin(mp + 2 * f)
                    + 0.00056 * e * sin(2 * mp + m)
                    - 0.00042 * sin(3 * mp)
                    + 0.00042 * e * sin(m + 2 * f)
                    + 0.00038 * e * sin(m - 2 * f)
                    - 0.00024 * e * sin(2 * mp - m)
                    - 0.00017 * sin(om)
                    - 0.00007 * sin(mp + 2 * m)
                    + 0.00004 * sin(2 * mp - 2 * f)
                    + 0.00004 * sin(3 * m)
                    + 0.00003 * sin(mp + m - 2 * f)
                    + 0.00003 * sin(2 * mp + 2 * f)
                    - 0.00003 * sin(mp + m + 2 * f)
                    + 0.00003 * sin(mp - m + 2 * f)
                    - 0.00002 * sin(mp - m - 2 * f)
                    - 0.00002 * sin(3 * mp + m)
                    + 0.00002 * sin(4 * mp);
        } else {
            c = -0.62801 * sin(mp)
                    + 0.17172 * e * sin(m)
                    - 0.01183 * e * sin(mp + m)
                    + 0.00862 * sin(2 * mp)
                    + 0.00804 * sin(2 * f)
                    + 0.00454 * e * sin(mp - m)
                    + 0.00204 * e * e * sin(2 * m)
                    - 0.00180 * sin(mp - 2 * f)
                    - 0.00070 * sin(mp + 2 * f)
                    - 0.00040 * sin(3 * mp)
                    - 0.00034 * e * sin(2 * mp - m)
                    + 0.00032 * e * sin(m + 2 * f)
                    + 0.00032 * e * sin(m - 2 * f)
                    - 0.00028 * e * e * sin(mp + 2 * m)
                    + 0.00027 * e * sin(2 * mp + m)
                    - 0.00017 * sin(om)
                    - 0.00005 * sin(mp - m - 2 * f)
                    + 0.00004 * sin(2 * mp + 2 * f)
                    - 0.00004 * sin(mp + m + 2 * f)
                    + 0.00004 * sin(mp - 2 * m)
                    + 0.00003 * sin(mp + m - 2 * f)
                    + 0.00003 * sin(3 * m)
                    + 0.00002 * sin(2 * mp - 2 * f)
                    + 0.00002 * sin(mp - m + 2 * f)
                    - 0.00002 * sin(3 * mp + m);
            double w = 0.00306 - 0.00038 * e * cos(m) + 0.00026 * cos(mp)
                    - 0.00002 * cos(mp - m) + 0.00002 * cos(mp + m) + 0.00002 * cos(2 * f);
            c += q == FIRST ? w : -w;
        }

        double[] a = {
                299.77 + 0.107408 * k - 0.009173 * t2, 251.88 + 0.016321 * k, 251.83 + 26.651886 * k,
                349.42 + 36.412478 * k, 84.66 + 18.206239 * k, 141.74 + 53.303771 * k, 207.14 + 2.453732 * k,
                154.84 + 7.306860 * k, 34.52 + 27.261239 * k, 207.19 + 0.121824 * k, 291.34 + 1.844379 * k,
                161.72 + 24.198154 * k, 239.56 + 25.513099 * k, 331.55 + 3.592518 * k};
        int[] coef = {325, 165, 164, 126, 110, 62, 60, 56, 47, 42, 40, 37, 35, 23};
        double extra = 0;
        for (int i = 0; i < a.length; i++) extra += coef[i] * 1e-6 * sin(rad(a[i]));

        return jde + c + extra;
    }

    private static double rad(double deg) {
        return Math.toRadians(deg % 360);
    }
}
