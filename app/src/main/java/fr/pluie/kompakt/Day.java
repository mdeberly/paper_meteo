package fr.pluie.kompakt;

import java.time.LocalDate;

/** Une journée : pluie (mm) et ensoleillement (minutes), créneau par créneau. */
public class Day {
    public final LocalDate date;
    public final Series rain = new Series();
    public final Series sun = new Series();

    public Day(LocalDate date) {
        this.date = date;
    }
}
