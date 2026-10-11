# Pluie — résumé, pluie, soleil et éphéméride pour Mudita Kompakt

Quatre onglets :

- **Résumé** (ouvert à chaque lancement) : quelques phrases qui décrivent le temps des heures à venir
  (la journée entière le matin, l'après-midi à partir de midi, la soirée à partir de 17 h, la nuit après 22 h),
  une phrase sur la pluie des 2 prochaines heures, et les 6 prochaines heures en icônes.
- **Pluie** : millimètres de pluie heure par heure, d'hier à J+6.
- **Soleil** : minutes d'ensoleillement heure par heure, avec une icône par heure (soleil, soleil voilé,
  très nuageux, couvert, pluie faible, forte pluie, orage ; lune la nuit). Les heures de nuit n'ont pas de valeur.
- **Éphéméride** : fête du jour (calendrier français, avec Pâques, fête des mères…), lever et coucher
  du soleil dans la ville choisie, durée du jour, phase de la Lune en toutes lettres avec la Lune dessinée,
  et les prochaines phases.

Pluie, Soleil et Éphéméride :

- Bandeau des 8 jours (cumul de pluie, durée d'ensoleillement ou petite lune) : toucher une case ouvre le jour.
- Les 24 heures du jour tiennent sur un seul écran (2 colonnes de 12), sans défilement.
- Glisser à gauche / à droite pour changer de jour.
- L'heure en cours est inversée (blanc sur noir). « 07h » = de 7 h à 8 h.
- Toucher le nom du lieu pour en chercher un autre ou utiliser la position GPS (Chauny par défaut).
- Noir et blanc pur, aucune animation, aucun service Google. Prévisions : Open-Meteo (gratuit, sans clé),
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
