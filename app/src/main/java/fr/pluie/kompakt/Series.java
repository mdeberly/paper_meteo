package fr.pluie.kompakt;

/** Une grandeur heure par heure sur une journée (index 7 = de 7 h à 8 h). */
public class Series {
    public final double[] v = new double[24];
    public final boolean[] has = new boolean[24];

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
        for (int i = 0; i < 24; i++) if (has[i]) t += v[i];
        return t;
    }

    public double max() {
        double m = 0;
        for (int i = 0; i < 24; i++) if (has[i] && v[i] > m) m = v[i];
        return m;
    }
}
