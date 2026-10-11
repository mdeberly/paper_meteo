# Pluie — précipitations heure par heure pour Mudita Kompakt

Affiche les millimètres de pluie heure par heure pour **hier, aujourd'hui et les 6 jours suivants**.

- Bandeau des 8 jours avec le cumul de chacun : toucher une case ouvre le jour.
- Les 24 heures du jour tiennent sur un seul écran (2 colonnes de 12), sans défilement.
- Glisser à gauche / à droite sur la grille pour changer de jour.
- L'heure en cours est inversée (blanc sur noir). « 07h » = pluie tombée entre 7 h et 8 h.
- Toucher le nom du lieu pour en chercher un autre ou utiliser la position GPS (Chauny par défaut).
- Noir et blanc pur, aucune animation, aucun service Google. Données : Open-Meteo (gratuit, sans clé),
  qui s'appuie sur les modèles de Météo-France pour la France. Dernières données gardées hors connexion.

## Obtenir l'APK sans rien installer (GitHub)

1. Créer un dépôt **privé** sur github.com (la clé de signature est dans le projet).
2. Y envoyer tout le contenu de ce dossier (bouton « Add file → Upload files », dossiers compris).
   Si l'onglet Actions reste vide (le dossier caché `.github` n'a pas été envoyé) : « Add file → Create new file »,
   nommer le fichier `.github/workflows/build.yml` et y coller le contenu du fichier du même nom.
3. Onglet **Actions** → « Compiler l'APK » se lance tout seul (≈ 5 min).
4. Ouvrir l'exécution terminée → section **Artifacts** → télécharger `Pluie-apk` (un zip contenant `Pluie.apk`).

## Ou compiler sur son PC

Avec Android Studio installé : ouvrir le dossier, puis `./gradlew assembleRelease`
(sous Windows : `gradlew.bat assembleRelease`). L'APK sort dans `app/build/outputs/apk/release/`.

## Installer sur le Kompakt

Via **Mudita Center** (gestion des applications → installer un APK), ou en ADB :
`adb install -r Pluie.apk`.
