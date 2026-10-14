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
    public final Series wind = new Series();   // vent moyen à 10 m, km/h
    public final Series windDir = new Series(); // d'où vient le vent, degrés (0 = nord, 90 = est)
    public final Series direct = new Series(); // rayonnement solaire direct moyen de l'heure, W/m²
    public final Series vis = new Series();    // visibilité, m
    public final Series snow = new Series();   // chute de neige de l'heure, cm
    public final Series rh = new Series();     // humidité relative à 2 m, %
    public final Series dew = new Series();    // point de rosée à 2 m, °C
    public final Series lowCloud = new Series(); // couverture nuageuse basse, %
    public final Series highCloud = new Series(); // couverture nuageuse haute (voile), %
    /** Code temps WMO d'Open-Meteo (95 et plus = orage), -1 si inconnu. */
    public final int[] code = new int[24];

    public Day(LocalDate date) {
        this.date = date;
        Arrays.fill(code, -1);
    }
}
