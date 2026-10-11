package fr.pluie.kompakt;

import java.time.LocalDate;
import java.util.Arrays;

/** Une journée, créneau par créneau (index 7 = de 7 h à 8 h). */
public class Day {
    public final LocalDate date;
    public final Series rain = new Series();   // mm
    public final Series sun = new Series();    // minutes d'ensoleillement
    public final Series cloud = new Series();  // couverture nuageuse, %
    public final Series temp = new Series();   // °C
    /** Code temps WMO d'Open-Meteo (95 et plus = orage), -1 si inconnu. */
    public final int[] code = new int[24];

    public Day(LocalDate date) {
        this.date = date;
        Arrays.fill(code, -1);
    }
}
