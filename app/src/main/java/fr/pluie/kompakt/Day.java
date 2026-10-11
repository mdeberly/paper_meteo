package fr.pluie.kompakt;

import java.time.LocalDate;

/** Précipitations d'une journée, créneau par créneau (index 7 = de 7 h à 8 h). */
public class Day {
    public final LocalDate date;
    public final double[] mm = new double[24];
    public final boolean[] has = new boolean[24];

    public Day(LocalDate date) {
        this.date = date;
    }

    public boolean hasAny() {
        for (boolean b : has) if (b) return true;
        return false;
    }

    public boolean isComplete() {
        for (boolean b : has) if (!b) return false;
        return true;
    }

    public double total() {
        double t = 0;
        for (int i = 0; i < 24; i++) if (has[i]) t += mm[i];
        return t;
    }

    public double max() {
        double m = 0;
        for (int i = 0; i < 24; i++) if (has[i] && mm[i] > m) m = mm[i];
        return m;
    }
}
