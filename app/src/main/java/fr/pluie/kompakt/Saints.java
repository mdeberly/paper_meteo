package fr.pluie.kompakt;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

/** Fête du jour du calendrier français (calendrier des Postes) et fêtes mobiles. */
final class Saints {
    private Saints() {}

    private static final String[][] BY_MONTH = {
            { // janvier
                    "Jour de l'An", "Basile", "Geneviève", "Odilon", "Édouard", "Mélaine", "Raymond", "Lucien",
                    "Alix", "Guillaume", "Paulin", "Tatiana", "Yvette", "Nina", "Rémi", "Marcel", "Roseline",
                    "Prisca", "Marius", "Sébastien", "Agnès", "Vincent", "Barnard", "François de Sales",
                    "Conversion de Paul", "Paule", "Angèle", "Thomas d'Aquin", "Gildas", "Martine", "Marcelle"},
            { // février
                    "Ella", "Présentation du Seigneur", "Blaise", "Véronique", "Agathe", "Gaston", "Eugénie",
                    "Jacqueline", "Apolline", "Arnaud", "Notre-Dame de Lourdes", "Félix", "Béatrice", "Valentin",
                    "Claude", "Julienne", "Alexis", "Bernadette", "Gabin", "Aimée", "Damien", "Isabelle", "Lazare",
                    "Modeste", "Roméo", "Nestor", "Honorine", "Romain", "Auguste"},
            { // mars
                    "Aubin", "Charles le Bon", "Guénolé", "Casimir", "Olive", "Colette", "Félicité",
                    "Jean de Dieu", "Françoise", "Vivien", "Rosine", "Justine", "Rodrigue", "Mathilde", "Louise",
                    "Bénédicte", "Patrice", "Cyrille", "Joseph", "Herbert", "Clémence", "Léa", "Victorien",
                    "Catherine de Suède", "Annonciation", "Larissa", "Habib", "Gontran", "Gwladys", "Amédée",
                    "Benjamin"},
            { // avril
                    "Hugues", "Sandrine", "Richard", "Isidore", "Irène", "Marcellin", "Jean-Baptiste de la Salle",
                    "Julie", "Gautier", "Fulbert", "Stanislas", "Jules", "Ida", "Maxime", "Paterne",
                    "Benoît-Joseph", "Anicet", "Parfait", "Emma", "Odette", "Anselme", "Alexandre", "Georges",
                    "Fidèle", "Marc", "Alida", "Zita", "Valérie", "Catherine de Sienne", "Robert"},
            { // mai
                    "Fête du Travail", "Boris", "Philippe et Jacques", "Sylvain", "Judith", "Prudence", "Gisèle",
                    "Désiré", "Pacôme", "Solange", "Estelle", "Achille", "Rolande", "Matthias", "Denise",
                    "Honoré", "Pascal", "Éric", "Yves", "Bernardin", "Constantin", "Émile", "Didier", "Donatien",
                    "Sophie", "Bérenger", "Augustin de Cantorbéry", "Germain", "Aymar", "Ferdinand",
                    "Visitation de la Vierge Marie"},
            { // juin
                    "Justin", "Blandine", "Kévin", "Clotilde", "Igor", "Norbert", "Gilbert", "Médard", "Diane",
                    "Landry", "Barnabé", "Guy", "Antoine de Padoue", "Élisée", "Germaine", "Jean-François Régis",
                    "Hervé", "Léonce", "Romuald", "Silvère", "Rodolphe", "Alban", "Audrey", "Jean-Baptiste",
                    "Prosper", "Anthelme", "Fernand", "Irénée", "Pierre et Paul", "Martial"},
            { // juillet
                    "Thierry", "Martinien", "Thomas", "Florent", "Antoine-Marie", "Mariette", "Raoul", "Thibaut",
                    "Amandine", "Ulrich", "Benoît", "Olivier", "Henri et Joël", "Fête nationale", "Donald",
                    "Notre-Dame du Mont-Carmel", "Charlotte", "Frédéric", "Arsène", "Marina", "Victor",
                    "Marie-Madeleine", "Brigitte", "Christine", "Jacques", "Anne et Joachim", "Nathalie",
                    "Samson", "Marthe", "Juliette", "Ignace de Loyola"},
            { // août
                    "Alphonse", "Julien Eymard", "Lydie", "Jean-Marie Vianney", "Abel", "Transfiguration",
                    "Gaëtan", "Dominique", "Amour", "Laurent", "Claire", "Clarisse", "Hippolyte", "Évrard",
                    "Assomption", "Armel", "Hyacinthe", "Hélène", "Jean Eudes", "Bernard", "Christophe",
                    "Fabrice", "Rose de Lima", "Barthélemy", "Louis", "Natacha", "Monique", "Augustin",
                    "Sabine", "Fiacre", "Aristide"},
            { // septembre
                    "Gilles", "Ingrid", "Grégoire", "Rosalie", "Raïssa", "Bertrand", "Reine",
                    "Nativité de la Vierge Marie", "Alain", "Inès", "Adelphe", "Apollinaire", "Aimé",
                    "Croix glorieuse", "Roland", "Édith", "Renaud", "Nadège", "Émilie", "Davy", "Matthieu",
                    "Maurice", "Constant", "Thècle", "Hermann", "Côme et Damien", "Vincent de Paul",
                    "Venceslas", "Michel, Gabriel et Raphaël", "Jérôme"},
            { // octobre
                    "Thérèse de l'Enfant-Jésus", "Léger", "Gérard", "François d'Assise", "Fleur", "Bruno",
                    "Serge", "Pélagie", "Denis", "Ghislain", "Firmin", "Wilfried", "Géraud", "Juste",
                    "Thérèse d'Avila", "Edwige", "Baudouin", "Luc", "René", "Adeline", "Céline", "Élodie",
                    "Jean de Capistran", "Florentin", "Crépin", "Dimitri", "Émeline", "Simon et Jude",
                    "Narcisse", "Bienvenue", "Quentin"},
            { // novembre
                    "Toussaint", "Défunts", "Hubert", "Charles", "Sylvie", "Bertille", "Carine", "Geoffroy",
                    "Théodore", "Léon", "Martin", "Christian", "Brice", "Sidoine", "Albert", "Marguerite",
                    "Élisabeth", "Aude", "Tanguy", "Edmond", "Présentation de Marie", "Cécile", "Clément",
                    "Flora", "Catherine", "Delphine", "Séverin", "Jacques de la Marche", "Saturnin", "André"},
            { // décembre
                    "Florence", "Viviane", "François-Xavier", "Barbara", "Gérald", "Nicolas", "Ambroise",
                    "Immaculée Conception", "Pierre Fourier", "Romaric", "Daniel", "Jeanne-Françoise de Chantal",
                    "Lucie", "Odile", "Ninon", "Alice", "Gaël", "Gatien", "Urbain", "Théophile",
                    "Pierre Canisius", "Françoise-Xavière", "Armand", "Adèle", "Noël", "Étienne", "Jean",
                    "Innocents", "David", "Roger", "Sylvestre"},
    };

    static String of(LocalDate d) {
        return BY_MONTH[d.getMonthValue() - 1][d.getDayOfMonth() - 1];
    }

    /** Fête mobile ou événement du jour (Pâques, fête des mères…), ou null. */
    static String feast(LocalDate d) {
        int y = d.getYear();
        LocalDate easter = easter(y);
        long k = d.toEpochDay() - easter.toEpochDay();
        if (k == -47) return "Mardi gras";
        if (k == -46) return "Mercredi des Cendres";
        if (k == -7) return "Rameaux";
        if (k == -2) return "Vendredi saint";
        if (k == 0) return "Pâques";
        if (k == 1) return "Lundi de Pâques";
        if (k == 39) return "Ascension";
        if (k == 49) return "Pentecôte";
        if (k == 50) return "Lundi de Pentecôte";

        LocalDate gm = LocalDate.of(y, 3, 1).with(TemporalAdjusters.firstInMonth(DayOfWeek.SUNDAY));
        if (d.equals(gm)) return "Fête des grands-mères";
        // Fête des mères : dernier dimanche de mai, repoussée d'une semaine si c'est la Pentecôte.
        LocalDate fm = LocalDate.of(y, 5, 1).with(TemporalAdjusters.lastInMonth(DayOfWeek.SUNDAY));
        if (fm.equals(easter.plusDays(49))) fm = fm.plusWeeks(1);
        if (d.equals(fm)) return "Fête des mères";
        LocalDate fp = LocalDate.of(y, 6, 1).with(TemporalAdjusters.dayOfWeekInMonth(3, DayOfWeek.SUNDAY));
        if (d.equals(fp)) return "Fête des pères";

        if (d.getMonthValue() == 5 && d.getDayOfMonth() == 8) return "Victoire 1945";
        if (d.getMonthValue() == 11 && d.getDayOfMonth() == 11) return "Armistice 1918";
        return null;
    }

    /** Dimanche de Pâques (algorithme de Meeus/Jones/Butcher, calendrier grégorien). */
    static LocalDate easter(int y) {
        int a = y % 19, b = y / 100, c = y % 100, d = b / 4, e = b % 4;
        int f = (b + 8) / 25, g = (b - f + 1) / 3, h = (19 * a + b - d - g + 15) % 30;
        int i = c / 4, k = c % 4, l = (32 + 2 * e + 2 * i - h - k) % 7, m = (a + 11 * h + 22 * l) / 451;
        int n = h + l - 7 * m + 114;
        return LocalDate.of(y, n / 31, n % 31 + 1);
    }
}
