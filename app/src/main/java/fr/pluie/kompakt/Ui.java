package fr.pluie.kompakt;

/**
 * Palette de l'application, selon la variante :
 * E Ink — noir et blanc pur, ligne en cours inversée ;
 * couleur — thème clair aux tons chauds, contrasté sans être dur, traits fins.
 */
final class Ui {
    private Ui() {}

    static final boolean COLOR = Flavor.COLOR;

    /** Fond de l'écran. */
    static final int BG = COLOR ? 0xFFF7E8D5 : 0xFFFFFFFF;
    /** Texte principal. */
    static final int INK = COLOR ? 0xFF2E2622 : 0xFF000000;
    /** Texte secondaire (titres de colonnes, légendes). */
    static final int MUTED = COLOR ? 0xFF7B6F67 : 0xFF000000;
    /** Traits de séparation et cadres. */
    static final int LINE = COLOR ? 0xFFE2C9AF : 0xFF000000;
    /** Épaisseur des traits, en dp. */
    static final float LINE_DP = COLOR ? 1 : 2;
    /** Onglet et jour sélectionnés. */
    static final int ACCENT = COLOR ? 0xFFB84E24 : 0xFF000000;
    static final int ON_ACCENT = 0xFFFFFFFF;
    /** Ligne de l'heure en cours. */
    static final int HL_BG = COLOR ? 0xFFEFCBAA : 0xFF000000;
    static final int HL_FG = COLOR ? 0xFF2E2622 : 0xFFFFFFFF;
    /** Valeurs de pluie dans la liste. */
    static final int RAIN_TEXT = COLOR ? 0xFF2F6AAE : 0xFF000000;

    // Icônes (variante couleur).
    static final int SUN = 0xFFF0A030;
    static final int MOON = 0xFFD39B3A;
    static final int CLOUD = 0xFF8C8079;
    static final int CLOUD_FILL = 0xFFFFFFFF;
    static final int RAIN = 0xFF3D7CC9;
    static final int BOLT = 0xFFDB9A00;
    static final int SNOW = 0xFF5B8FD0;

    // Grande lune de l'éphéméride : partie éclairée, partie dans l'ombre, contour.
    static final int MOON_LIT = COLOR ? 0xFFF5E2A8 : 0xFFFFFFFF;
    static final int MOON_DARK = COLOR ? 0xFF7A6D64 : 0xFF000000;
    static final int MOON_EDGE = COLOR ? 0xFF8C8079 : 0xFF000000;
}
