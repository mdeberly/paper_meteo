package fr.pluie.kompakt;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Langue de l'application : textes traduits et formats propres à chaque langue.
 * Par défaut, la langue du téléphone (anglais si elle n'est pas proposée) ; elle peut être imposée
 * dans les paramètres.
 */
final class L {
    private L() {}

    static final String[] CODES = {"fr", "en", "de", "es", "it", "pt", "pl"};
    static final String[] NAMES = {"Français", "English", "Deutsch", "Español", "Italiano", "Português", "Polski"};
    static final int FR = 0, EN = 1, DE = 2, ES = 3, IT = 4, PT = 5, PL = 6;

    private static int lang = FR;
    private static Locale locale = Locale.FRENCH;

    /** @param pref "auto" ou un code de {@link #CODES} */
    static void init(String pref) {
        String code = pref == null || pref.equals("auto") ? Locale.getDefault().getLanguage() : pref;
        lang = EN;
        for (int i = 0; i < CODES.length; i++) if (CODES[i].equals(code)) lang = i;
        locale = Locale.forLanguageTag(CODES[lang]);
    }

    static int lang() { return lang; }

    static String code() { return CODES[lang]; }

    static Locale locale() { return locale; }

    static boolean french() { return lang == FR; }

    /** Texte traduit. */
    static String t(String key) {
        String[] v = T.get(key);
        if (v == null) return key;
        return v[lang];
    }

    /** Texte traduit avec {0}, {1}… remplacés. */
    static String f(String key, Object... args) {
        String s = t(key);
        for (int i = 0; i < args.length; i++) s = s.replace("{" + i + "}", String.valueOf(args[i]));
        return s;
    }

    /** Élément i d'une liste traduite (mots séparés par « | »). */
    static String item(String key, int i) {
        return t(key).split("\\|")[i];
    }

    // ---------------------------------------------------------------- heures

    /** Heure dans une phrase : « 15 h », « 3 pm », « 15 Uhr », « 15:00 »… */
    static String hour(int h) {
        h = ((h % 24) + 24) % 24;
        switch (lang) {
            case FR: return h == 0 ? "minuit" : h == 12 ? "midi" : h + " h";
            case EN: return h == 0 ? "midnight" : h == 12 ? "noon" : (h % 12) + " " + (h < 12 ? "am" : "pm");
            case DE: return h == 0 ? "Mitternacht" : h + " Uhr";
            case PT: return h + "h";
            default: return h + ":00";
        }
    }

    /** Heure courte pour une étiquette (rangée des prochaines heures). */
    static String hourShort(int h) {
        h = ((h % 24) + 24) % 24;
        switch (lang) {
            case FR: return h + " h";
            case EN: return (h % 12 == 0 ? 12 : h % 12) + " " + (h < 12 ? "am" : "pm");
            case PT: return h + "h";
            default: return h + ":00";
        }
    }

    /** Étiquette d'un créneau de la liste Météo : « 07h », « 0–3h », « 7 am », « 07:00 », « 0–3 »… */
    static String slot(int a, int b) {
        if (b - a == 1) {
            switch (lang) {
                case FR: return String.format(Locale.ROOT, "%02dh", a);
                case EN: return (a % 12 == 0 ? 12 : a % 12) + " " + (a < 12 ? "am" : "pm");
                case PT: return String.format(Locale.ROOT, "%02dh", a);
                default: return String.format(Locale.ROOT, "%02d:00", a);
            }
        }
        switch (lang) {
            case FR: case PT: return a + "–" + b + "h";
            case EN: return (a == 0 ? 12 : a) + "–" + b;
            default: return a + "–" + b;
        }
    }

    /** Heure et minutes : « 8 h 02 », « 8:02 am », « 8:02 ». */
    static String clock(int h, int m) {
        switch (lang) {
            case FR: return String.format(Locale.ROOT, "%d h %02d", h, m);
            case EN: return String.format(Locale.ROOT, "%d:%02d %s", h % 12 == 0 ? 12 : h % 12, m, h < 12 ? "am" : "pm");
            default: return String.format(Locale.ROOT, "%d:%02d", h, m);
        }
    }

    /** « 5 jours », « 1 day », « 2 dni »… */
    static String days(long n) {
        return n + " " + item("days", n == 1 ? 0 : 1);
    }

    /** Séparateur entre l'intitulé et la description (espace avant les deux-points en français). */
    static String colon() {
        return lang == FR ? " : " : ": ";
    }

    // ---------------------------------------------------------------- textes

    private static final Map<String, String[]> T = new HashMap<>();

    private static void put(String key, String fr, String en, String de, String es, String it, String pt, String pl) {
        T.put(key, new String[]{fr, en, de, es, it, pt, pl});
    }

    static {
        // Onglets, boutons, écran principal.
        put("tab_summary", "Résumé", "Summary", "Übersicht", "Resumen", "Riepilogo", "Resumo", "Podsumowanie");
        put("tab_weather", "Météo", "Weather", "Wetter", "Tiempo", "Meteo", "Tempo", "Pogoda");
        put("tab_eph", "Éphéméride", "Almanac", "Kalender", "Efemérides", "Effemeridi", "Efemérides", "Kalendarz");
        put("refresh", "Actualiser", "Refresh", "Aktualisieren", "Actualizar", "Aggiorna", "Atualizar", "Odśwież");
        put("settings", "Paramètres", "Settings", "Einstellungen", "Ajustes", "Impostazioni", "Definições", "Ustawienia");
        put("nd", "n/d", "n/a", "k. A.", "n/d", "n/d", "n/d", "b.d.");
        put("now_short", "Maint.", "Now", "Jetzt", "Ahora", "Ora", "Agora", "Teraz");
        put("next2h", "Dans les 2 prochaines heures", "In the next 2 hours", "In den nächsten 2 Stunden",
                "En las próximas 2 horas", "Nelle prossime 2 ore", "Nas próximas 2 horas", "W ciągu najbliższych 2 godzin");
        put("days_short", "Hier|Auj.|Dem.", "Yest.|Today|Tmrw", "Gestern|Heute|Morgen", "Ayer|Hoy|Mañana",
                "Ieri|Oggi|Domani", "Ontem|Hoje|Amanhã", "Wczoraj|Dziś|Jutro");
        put("days_long", "Hier|Aujourd'hui|Demain", "Yesterday|Today|Tomorrow", "Gestern|Heute|Morgen",
                "Ayer|Hoy|Mañana", "Ieri|Oggi|Domani", "Ontem|Hoje|Amanhã", "Wczoraj|Dzisiaj|Jutro");
        put("days", "jour|jours", "day|days", "Tag|Tage", "día|días", "giorno|giorni", "dia|dias", "dzień|dni");
        put("compass", "N|NE|E|SE|S|SO|O|NO", "N|NE|E|SE|S|SW|W|NW", "N|NO|O|SO|S|SW|W|NW", "N|NE|E|SE|S|SO|O|NO",
                "N|NE|E|SE|S|SO|O|NO", "N|NE|E|SE|S|SO|O|NO", "N|NE|E|SE|S|SW|W|NW");

        // Liste Météo.
        put("col_hour", "Heure", "Time", "Zeit", "Hora", "Ora", "Hora", "Godz.");
        put("col_sun", "Soleil", "Sun", "Sonne", "Sol", "Sole", "Sol", "Słońce");
        put("col_rain", "Pluie", "Rain", "Regen", "Lluvia", "Pioggia", "Chuva", "Deszcz");
        put("col_temp", "Temp.", "Temp.", "Temp.", "Temp.", "Temp.", "Temp.", "Temp.");
        put("col_wind", "Vent", "Wind", "Wind", "Viento", "Vento", "Vento", "Wiatr");
        put("day_rain", "Pluie {0} mm", "Rain {0} mm", "Regen {0} mm", "Lluvia {0} mm", "Pioggia {0} mm",
                "Chuva {0} mm", "Deszcz {0} mm");
        put("day_sun", "soleil {0} sur {1}", "sun {0} of {1}", "Sonne {0} von {1}", "sol {0} de {1}",
                "sole {0} su {1}", "sol {0} de {1}", "słońce {0} z {1}");
        put("partial", "(partiel)", "(partial)", "(unvollständig)", "(parcial)", "(parziale)", "(parcial)", "(niepełne)");
        put("no_day_data", "Pas de données pour ce jour", "No data for this day", "Keine Daten für diesen Tag",
                "Sin datos para este día", "Nessun dato per questo giorno", "Sem dados para este dia", "Brak danych dla tego dnia");
        put("no_data", "Aucune donnée : touchez « Actualiser » (connexion requise).",
                "No data: tap “Refresh” (connection required).",
                "Keine Daten: „Aktualisieren“ tippen (Verbindung nötig).",
                "Sin datos: toca «Actualizar» (requiere conexión).",
                "Nessun dato: tocca «Aggiorna» (serve la connessione).",
                "Sem dados: toque em «Atualizar» (requer ligação).",
                "Brak danych: dotknij „Odśwież” (wymaga połączenia).");
        put("updated", "Màj {0}", "Updated {0}", "Stand {0}", "Act. {0}", "Agg. {0}", "Atual. {0}", "Akt. {0}");
        put("legend", "« 07h » = de 7 h à 8 h", "“7 am” = 7 to 8 am", "„07:00“ = 7 bis 8 Uhr", "«07:00» = de 7 a 8",
                "«07:00» = dalle 7 alle 8", "«07h» = das 7h às 8h", "„07:00” = od 7 do 8");
        put("updating", "Mise à jour…", "Updating…", "Aktualisiere…", "Actualizando…", "Aggiornamento…",
                "A atualizar…", "Aktualizacja…");
        put("update_failed", "Échec de la mise à jour (réseau ?) — données précédentes affichées",
                "Update failed (network?) — showing previous data",
                "Aktualisierung fehlgeschlagen (Netz?) — alte Daten",
                "Error al actualizar (¿red?) — datos anteriores",
                "Aggiornamento non riuscito (rete?) — dati precedenti",
                "Falha na atualização (rede?) — dados anteriores",
                "Błąd aktualizacji (sieć?) — poprzednie dane");

        // Éphéméride.
        put("nameday", "Fête du jour", "Name day", "Namenstag", "Santo del día", "Onomastico", "Santo do dia", "Imieniny");
        put("nameday_calendar", "Calendrier des fêtes", "Name day calendar", "Namenstagskalender", "Calendario de santos",
                "Calendario degli onomastici", "Calendário de santos", "Kalendarz imienin");
        put("auto_country", "Automatique (pays du téléphone)", "Automatic (phone's country)", "Automatisch (Land des Telefons)",
                "Automático (país del teléfono)", "Automatico (paese del telefono)", "Automático (país do telefone)",
                "Automatycznie (kraj telefonu)");
        put("feels", "Ressenti {0}", "Feels like {0}", "Gefühlt {0}", "Sensación {0}", "Percepita {0}", "Sensação {0}",
                "Odczuwalna {0}");
        put("sunrise", "Lever du soleil", "Sunrise", "Sonnenaufgang", "Salida del sol", "Alba", "Nascer do sol", "Wschód słońca");
        put("sunset", "Coucher du soleil", "Sunset", "Sonnenuntergang", "Puesta del sol", "Tramonto", "Pôr do sol", "Zachód słońca");
        put("day_length", "Durée du jour {0}  ({1} min sur la veille)", "Day length {0}  ({1} min vs yesterday)",
                "Tageslänge {0}  ({1} Min. zum Vortag)", "Duración del día {0}  ({1} min respecto a ayer)",
                "Durata del giorno {0}  ({1} min rispetto a ieri)", "Duração do dia {0}  ({1} min face a ontem)",
                "Długość dnia {0}  ({1} min wobec wczoraj)");
        put("sun_up", "Soleil levé toute la journée", "The sun stays up all day", "Die Sonne geht nicht unter",
                "El sol no se pone", "Il sole non tramonta", "O sol não se põe", "Słońce nie zachodzi");
        put("sun_down", "Le soleil ne se lève pas", "The sun does not rise", "Die Sonne geht nicht auf",
                "El sol no sale", "Il sole non sorge", "O sol não nasce", "Słońce nie wschodzi");
        put("moon_lit", "Éclairée à {0} %", "{0}% illuminated", "{0} % beleuchtet", "Iluminada al {0} %",
                "Illuminata al {0}%", "Iluminada a {0}%", "Oświetlona w {0}%");
        put("moon_exact", "Exacte à {0}", "Exact at {0}", "Genau um {0}", "Exacta a las {0}", "Esatta alle {0}",
                "Exata às {0}", "Dokładnie o {0}");
        put("moon_age", "Âge : {0}", "Age: {0}", "Alter: {0}", "Edad: {0}", "Età: {0}", "Idade: {0}", "Wiek: {0}");
        put("moon_main", "Nouvelle lune|Premier quartier|Pleine lune|Dernier quartier",
                "New moon|First quarter|Full moon|Last quarter",
                "Neumond|Erstes Viertel|Vollmond|Letztes Viertel",
                "Luna nueva|Cuarto creciente|Luna llena|Cuarto menguante",
                "Luna nuova|Primo quarto|Luna piena|Ultimo quarto",
                "Lua nova|Quarto crescente|Lua cheia|Quarto minguante",
                "Nów|Pierwsza kwadra|Pełnia|Ostatnia kwadra");
        put("moon_between", "Premier croissant|Lune gibbeuse croissante|Lune gibbeuse décroissante|Dernier croissant",
                "Waxing crescent|Waxing gibbous|Waning gibbous|Waning crescent",
                "Zunehmende Sichel|Zunehmender Mond|Abnehmender Mond|Abnehmende Sichel",
                "Luna creciente|Gibosa creciente|Gibosa menguante|Luna menguante",
                "Luna crescente|Gibbosa crescente|Gibbosa calante|Luna calante",
                "Lua crescente|Gibosa crescente|Gibosa minguante|Lua minguante",
                "Sierp przybywający|Garb przybywający|Garb ubywający|Sierp ubywający");
        put("eph_status", "Soleil et lune calculés sur le téléphone, sans connexion",
                "Sun and moon computed on the phone, offline", "Sonne und Mond auf dem Telefon berechnet, offline",
                "Sol y luna calculados en el teléfono, sin conexión", "Sole e luna calcolati sul telefono, offline",
                "Sol e lua calculados no telefone, sem ligação", "Słońce i księżyc liczone w telefonie, offline");
        put("feasts", "Mardi gras|Mercredi des Cendres|Rameaux|Vendredi saint|Pâques|Lundi de Pâques|Ascension|Pentecôte|Lundi de Pentecôte",
                "Shrove Tuesday|Ash Wednesday|Palm Sunday|Good Friday|Easter|Easter Monday|Ascension Day|Pentecost|Whit Monday",
                "Fastnacht|Aschermittwoch|Palmsonntag|Karfreitag|Ostern|Ostermontag|Christi Himmelfahrt|Pfingsten|Pfingstmontag",
                "Martes de Carnaval|Miércoles de Ceniza|Domingo de Ramos|Viernes Santo|Pascua|Lunes de Pascua|Ascensión|Pentecostés|Lunes de Pentecostés",
                "Martedì grasso|Mercoledì delle Ceneri|Domenica delle Palme|Venerdì santo|Pasqua|Lunedì dell'Angelo|Ascensione|Pentecoste|Lunedì di Pentecoste",
                "Terça-feira de Carnaval|Quarta-feira de Cinzas|Domingo de Ramos|Sexta-feira Santa|Páscoa|Segunda-feira de Páscoa|Ascensão|Pentecostes|Segunda-feira de Pentecostes",
                "Ostatki|Środa Popielcowa|Niedziela Palmowa|Wielki Piątek|Wielkanoc|Poniedziałek Wielkanocny|Wniebowstąpienie|Zielone Świątki|Poniedziałek Zielonoświątkowy");

        // Choix du lieu.
        put("place", "Lieu", "Location", "Ort", "Lugar", "Luogo", "Local", "Miejsce");
        put("city_hint", "Ville (ex. Chauny)", "City (e.g. London)", "Stadt (z. B. Berlin)", "Ciudad (p. ej. Madrid)",
                "Città (es. Roma)", "Cidade (ex. Lisboa)", "Miasto (np. Warszawa)");
        put("search", "Rechercher", "Search", "Suchen", "Buscar", "Cerca", "Pesquisar", "Szukaj");
        put("gps", "Position GPS", "GPS location", "GPS-Standort", "Posición GPS", "Posizione GPS", "Posição GPS", "Pozycja GPS");
        put("cancel", "Annuler", "Cancel", "Abbrechen", "Cancelar", "Annulla", "Cancelar", "Anuluj");
        put("searching", "Recherche de « {0} »…", "Searching “{0}”…", "Suche „{0}“…", "Buscando «{0}»…",
                "Ricerca di «{0}»…", "A pesquisar «{0}»…", "Szukam „{0}”…");
        put("no_place", "Aucun lieu trouvé", "No place found", "Kein Ort gefunden", "Ningún lugar encontrado",
                "Nessun luogo trovato", "Nenhum local encontrado", "Nie znaleziono miejsca");
        put("choose_place", "Choisir le lieu", "Choose a place", "Ort wählen", "Elegir lugar", "Scegli il luogo",
                "Escolher o local", "Wybierz miejsce");
        put("search_failed", "Recherche impossible (réseau ?)", "Search failed (network?)", "Suche fehlgeschlagen (Netz?)",
                "Búsqueda imposible (¿red?)", "Ricerca non riuscita (rete?)", "Pesquisa impossível (rede?)",
                "Wyszukiwanie nieudane (sieć?)");
        put("loc_denied", "Localisation refusée", "Location permission denied", "Standortzugriff verweigert",
                "Ubicación denegada", "Posizione negata", "Localização recusada", "Odmowa dostępu do lokalizacji");
        put("loc_off", "Activez la localisation du téléphone", "Turn on the phone's location",
                "Standort des Telefons einschalten", "Activa la ubicación del teléfono", "Attiva la posizione del telefono",
                "Ative a localização do telefone", "Włącz lokalizację w telefonie");
        put("locating", "Recherche de la position…", "Finding your location…", "Standort wird gesucht…",
                "Buscando la posición…", "Ricerca della posizione…", "A procurar a posição…", "Ustalanie pozycji…");
        put("loc_none", "Position introuvable", "Location not found", "Standort nicht gefunden", "Posición no encontrada",
                "Posizione non trovata", "Posição não encontrada", "Nie ustalono pozycji");

        // Paramètres.
        put("temp_unit", "Unité de température", "Temperature unit", "Temperatureinheit", "Unidad de temperatura",
                "Unità di temperatura", "Unidade de temperatura", "Jednostka temperatury");
        put("language", "Langue", "Language", "Sprache", "Idioma", "Lingua", "Idioma", "Język");
        put("auto_lang", "Automatique (langue du téléphone)", "Automatic (phone language)", "Automatisch (Telefonsprache)",
                "Automático (idioma del teléfono)", "Automatica (lingua del telefono)", "Automático (idioma do telefone)",
                "Automatycznie (język telefonu)");
        put("about", "À propos", "About", "Über die App", "Acerca de", "Informazioni", "Sobre", "O aplikacji");
        put("version", "Version {0}", "Version {0}", "Version {0}", "Versión {0}", "Versione {0}", "Versão {0}", "Wersja {0}");
        put("credits",
                "Prévisions : Open-Meteo.com (CC BY 4.0). Fêtes PL, DE, CZ, SE, NO, DK : namedays © Filip Tammergård (MIT).",
                "Forecasts: Open-Meteo.com (CC BY 4.0). Name days PL, DE, CZ, SE, NO, DK: namedays © Filip Tammergård (MIT).",
                "Vorhersagen: Open-Meteo.com (CC BY 4.0). Namenstage PL, DE, CZ, SE, NO, DK: namedays © Filip Tammergård (MIT).",
                "Previsiones: Open-Meteo.com (CC BY 4.0). Santos PL, DE, CZ, SE, NO, DK: namedays © Filip Tammergård (MIT).",
                "Previsioni: Open-Meteo.com (CC BY 4.0). Onomastici PL, DE, CZ, SE, NO, DK: namedays © Filip Tammergård (MIT).",
                "Previsões: Open-Meteo.com (CC BY 4.0). Santos PL, DE, CZ, SE, NO, DK: namedays © Filip Tammergård (MIT).",
                "Prognozy: Open-Meteo.com (CC BY 4.0). Imieniny PL, DE, CZ, SE, NO, DK: namedays © Filip Tammergård (MIT).");
        put("about_text",
                "Application créée par l'IA Claude sur l'idée de Maximilien DEBERLY, Chauny, FRANCE.",
                "App created by the AI Claude from an idea by Maximilien DEBERLY, Chauny, FRANCE.",
                "App erstellt von der KI Claude nach einer Idee von Maximilien DEBERLY, Chauny, FRANKREICH.",
                "Aplicación creada por la IA Claude a partir de una idea de Maximilien DEBERLY, Chauny, FRANCIA.",
                "App creata dall'IA Claude da un'idea di Maximilien DEBERLY, Chauny, FRANCIA.",
                "Aplicação criada pela IA Claude a partir de uma ideia de Maximilien DEBERLY, Chauny, FRANÇA.",
                "Aplikacja stworzona przez AI Claude według pomysłu Maximiliena DEBERLY, Chauny, FRANCJA.");

        // Résumé écrit.
        put("parts", "ce matin|cet après-midi|ce soir|cette nuit|toute la journée",
                "this morning|this afternoon|this evening|tonight|all day",
                "heute Vormittag|heute Nachmittag|heute Abend|heute Nacht|den ganzen Tag",
                "esta mañana|esta tarde|esta noche|de madrugada|todo el día",
                "stamattina|oggi pomeriggio|stasera|stanotte|tutto il giorno",
                "esta manhã|esta tarde|esta noite|de madrugada|todo o dia",
                "dziś rano|dziś po południu|dziś wieczorem|w nocy|przez cały dzień");
        put("and", " et ", " and ", " und ", " y ", " e ", " e ", " i ");
        put("sky_day", "ciel couvert|ciel très nuageux avec de rares éclaircies|alternance de nuages et d'éclaircies|soleil souvent présent|grand soleil",
                "overcast|very cloudy with rare sunny spells|mix of clouds and sunny spells|mostly sunny|sunny",
                "bedeckt|stark bewölkt, selten Aufheiterungen|Wechsel von Wolken und Sonne|meist sonnig|sonnig",
                "cielo cubierto|muy nuboso con raros claros|alternancia de nubes y claros|sol casi todo el tiempo|pleno sol",
                "cielo coperto|molto nuvoloso con rare schiarite|alternanza di nuvole e schiarite|sole prevalente|pieno sole",
                "céu encoberto|muito nublado com raras abertas|alternância de nuvens e abertas|sol predominante|muito sol",
                "zachmurzenie całkowite|duże zachmurzenie, rzadkie przejaśnienia|na przemian chmury i przejaśnienia|przeważnie słonecznie|słonecznie");
        put("sky_night", "ciel couvert|ciel très nuageux|nuages et éclaircies|quelques nuages|ciel dégagé",
                "overcast|very cloudy|partly cloudy|a few clouds|clear sky",
                "bedeckt|stark bewölkt|wechselnd bewölkt|einige Wolken|klar",
                "cielo cubierto|muy nuboso|nubes y claros|algunas nubes|cielo despejado",
                "cielo coperto|molto nuvoloso|nuvole e schiarite|qualche nuvola|cielo sereno",
                "céu encoberto|muito nublado|nuvens e abertas|algumas nuvens|céu limpo",
                "zachmurzenie całkowite|duże zachmurzenie|chmury i przejaśnienia|niewielkie zachmurzenie|bezchmurnie");
        put("fog", "brouillard", "fog", "Nebel", "niebla", "nebbia", "nevoeiro", "mgła");
        put("dry", "temps sec", "dry", "trocken", "tiempo seco", "tempo asciutto", "tempo seco", "bez opadów");
        put("drops", "quelques gouttes possibles", "a few drops possible", "vereinzelt Tropfen", "algunas gotas posibles",
                "qualche goccia possibile", "algumas gotas possíveis", "możliwe pojedyncze krople");
        put("light_rain", "pluie faible", "light rain", "leichter Regen", "lluvia débil", "pioggia debole", "chuva fraca", "słaby deszcz");
        put("rain", "pluie", "rain", "Regen", "lluvia", "pioggia", "chuva", "deszcz");
        put("heavy_rain", "fortes pluies", "heavy rain", "starker Regen", "lluvias fuertes", "piogge forti", "chuva forte", "silny deszcz");
        put("storm", ", risque d'orage", ", risk of thunderstorms", ", Gewittergefahr", ", riesgo de tormenta",
                ", rischio di temporali", ", risco de trovoada", ", ryzyko burzy");
        put("at", " vers {0}", " around {0}", " gegen {0}", " hacia las {0}", " verso le {0}", " por volta das {0}", " około {0}");
        put("between", " entre {0} et {1}", " between {0} and {1}", " zwischen {0} und {1}", " entre las {0} y las {1}",
                " tra le {0} e le {1}", " entre as {0} e as {1}", " między {0} a {1}");
        put("temp_around", ", autour de {0}", ", around {0}", ", um {0}", ", alrededor de {0}", ", intorno a {0}",
                ", cerca de {0}", ", około {0}");
        put("temp_range", ", de {0} à {1}", ", {0} to {1}", ", {0} bis {1}", ", de {0} a {1}", ", da {0} a {1}",
                ", de {0} a {1}", ", od {0} do {1}");
        put("strong_wind", ", vent fort jusqu'à {0} km/h", ", strong wind up to {0} km/h", ", starker Wind bis {0} km/h",
                ", viento fuerte hasta {0} km/h", ", vento forte fino a {0} km/h", ", vento forte até {0} km/h",
                ", silny wiatr do {0} km/h");
        put("no_forecast", "Pas de prévision disponible pour les heures à venir.", "No forecast available for the coming hours.",
                "Keine Vorhersage für die nächsten Stunden.", "No hay previsión para las próximas horas.",
                "Nessuna previsione per le prossime ore.", "Sem previsão para as próximas horas.",
                "Brak prognozy na najbliższe godziny.");

        // Pluie dans les 2 heures.
        put("n2_nodata", "Pas de prévision de pluie disponible.", "No rain forecast available.",
                "Keine Regenvorhersage verfügbar.", "No hay previsión de lluvia.", "Nessuna previsione di pioggia.",
                "Sem previsão de chuva.", "Brak prognozy opadów.");
        put("n2_dry", "Pas de pluie prévue.", "No rain expected.", "Kein Regen erwartet.", "No se espera lluvia.",
                "Nessuna pioggia prevista.", "Não se prevê chuva.", "Nie przewiduje się deszczu.");
        put("n2_storm_now", "Risque d'orage en ce moment, jusqu'à au moins {0}.", "Risk of thunderstorms now, until at least {0}.",
                "Jetzt Gewittergefahr, mindestens bis {0}.", "Riesgo de tormenta ahora, hasta al menos las {0}.",
                "Rischio di temporali ora, almeno fino alle {0}.", "Risco de trovoada agora, pelo menos até às {0}.",
                "Ryzyko burzy teraz, co najmniej do {0}.");
        put("n2_drops_now", "Quelques gouttes sont possibles en ce moment, jusqu'à au moins {0}.",
                "A few drops possible now, until at least {0}.", "Jetzt vereinzelt Tropfen, mindestens bis {0}.",
                "Algunas gotas posibles ahora, hasta al menos las {0}.", "Qualche goccia possibile ora, almeno fino alle {0}.",
                "Algumas gotas possíveis agora, pelo menos até às {0}.", "Teraz możliwe krople, co najmniej do {0}.");
        put("n2_rain_now", "De la pluie est prévue en ce moment, jusqu'à au moins {0} ({1} mm).",
                "Rain expected now, until at least {0} ({1} mm).", "Jetzt Regen, mindestens bis {0} ({1} mm).",
                "Lluvia prevista ahora, hasta al menos las {0} ({1} mm).",
                "Pioggia prevista ora, almeno fino alle {0} ({1} mm).",
                "Chuva prevista agora, pelo menos até às {0} ({1} mm).", "Teraz deszcz, co najmniej do {0} ({1} mm).");
        put("n2_stop", "De la pluie est prévue en ce moment ; elle devrait cesser vers {0}.",
                "Rain expected now; it should stop around {0}.", "Jetzt Regen; er sollte gegen {0} aufhören.",
                "Lluvia prevista ahora; debería parar hacia las {0}.", "Pioggia prevista ora; dovrebbe smettere verso le {0}.",
                "Chuva prevista agora; deve parar por volta das {0}.", "Teraz deszcz; powinien ustać około {0}.");
        put("n2_storm_next", "Un orage est possible vers {0}.", "A thunderstorm is possible around {0}.",
                "Gegen {0} ist ein Gewitter möglich.", "Posible tormenta hacia las {0}.", "Possibile temporale verso le {0}.",
                "Possível trovoada por volta das {0}.", "Możliwa burza około {0}.");
        put("n2_heavy_next", "De fortes pluies sont attendues vers {0} ({1} mm).", "Heavy rain expected around {0} ({1} mm).",
                "Gegen {0} starker Regen erwartet ({1} mm).", "Lluvias fuertes hacia las {0} ({1} mm).",
                "Piogge forti verso le {0} ({1} mm).", "Chuva forte por volta das {0} ({1} mm).",
                "Silny deszcz około {0} ({1} mm).");
        put("n2_drops_next", "Quelques gouttes sont possibles vers {0}.", "A few drops possible around {0}.",
                "Gegen {0} vereinzelt Tropfen möglich.", "Algunas gotas posibles hacia las {0}.",
                "Qualche goccia possibile verso le {0}.", "Algumas gotas possíveis por volta das {0}.",
                "Możliwe krople około {0}.");
        put("n2_rain_next", "De la pluie est attendue vers {0} ({1} mm).", "Rain expected around {0} ({1} mm).",
                "Gegen {0} Regen erwartet ({1} mm).", "Lluvia prevista hacia las {0} ({1} mm).",
                "Pioggia prevista verso le {0} ({1} mm).", "Chuva prevista por volta das {0} ({1} mm).",
                "Deszcz około {0} ({1} mm).");
    }
}
