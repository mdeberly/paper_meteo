# Pluie — pluie, soleil et éphéméride pour Mudita Kompakt

Trois onglets, pour **hier, aujourd'hui et les 6 jours suivants** :

- **Pluie** : millimètres de pluie heure par heure.
- **Soleil** : minutes d'ensoleillement heure par heure (les heures de nuit restent vides).
- **Éphéméride** : fête du jour (calendrier français, avec Pâques, fête des mères…), lever et coucher
  du soleil dans la ville choisie, durée du jour, phase de la Lune en toutes lettres avec la Lune dessinée,
  et les prochaines phases.

Commun aux trois onglets :

- Bandeau des 8 jours (cumul de pluie, durée d'ensoleillement ou petite lune) : toucher une case ouvre le jour.
- Les 24 heures du jour tiennent sur un seul écran (2 colonnes de 12), sans défilement.
- Glisser à gauche / à droite pour changer de jour.
- L'heure en cours est inversée (blanc sur noir). « 07h » = de 7 h à 8 h.
- Toucher le nom du lieu pour en chercher un autre ou utiliser la position GPS (Chauny par défaut).
- Noir et blanc pur, aucune animation, aucun service Google. Pluie et soleil : Open-Meteo (gratuit, sans clé),
  qui s'appuie sur les modèles de Météo-France pour la France ; dernières données gardées hors connexion.
  Lever/coucher du soleil et phases de la Lune sont calculés sur le téléphone, sans connexion.

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
