# Paper Meteo — météo, résumé et éphéméride

Deux variantes, compilées à partir du même code :

- **E Ink** (`PaperMeteo-EInk.apk`) : pour le Mudita Kompakt et les autres écrans E Ink — noir et blanc pur,
  aucune animation. Même identifiant que les premières versions : s'installe par-dessus.
- **Couleur** (`PaperMeteo-Color.apk`) : pour les téléphones Android à écran couleur — thème clair aux tons
  chauds, icônes en couleur, sobre et contrasté. Application distincte, installable à côté de la version E Ink.

Le code commun est dans `app/src/main` ; ce qui distingue les variantes : `app/src/eink` et `app/src/color`
(classe `Flavor`, thème, icône), et la palette `Ui.java`.

Trois onglets :

- **Résumé** (ouvert à chaque lancement) : la météo actuelle (icône, température réelle et ressentie, vent),
  puis quelques phrases qui décrivent le temps des heures à venir
  (la journée entière le matin, l'après-midi à partir de midi, la soirée à partir de 17 h, la nuit après 22 h),
  une phrase sur la pluie des 2 prochaines heures, et les 12 prochaines heures en icônes (défilement horizontal).
- **Météo** : d’hier à J+7, une liste qui défile, une ligne par heure (la nuit regroupée en 0–3h et 3–6h) :
  icône météo (soleil, nuages, pluie, orage, neige faible / modérée / forte, brouillard seul ou avec soleil voilé ; lune la nuit), minutes de soleil, millimètres de pluie,
  température, vitesse du vent en km/h et sa direction. Le bandeau des jours défile avec la liste ;
  seuls le lieu et les onglets restent fixes. La liste s'ouvre sur l'heure en cours.
- **Éphéméride** : fête du jour selon le pays (France, Pologne, Allemagne, Tchéquie, Suède, Norvège, Danemark ;
  celui du téléphone par défaut, ou à choisir dans les paramètres), fêtes mobiles (Pâques…), lever et coucher
  du soleil dans la ville choisie, durée du jour, phase de la Lune en toutes lettres avec la Lune dessinée,
  et les prochaines phases.

Et aussi :

- Bandeau des 9 jours (hier à J+7, en 3 × 3) (icône du temps de la journée et cumul de pluie, ou petite lune ; températures mini / maxi) : toucher une case ouvre le jour ;
  glisser franchement à gauche / à droite change aussi de jour.
- L'heure en cours est inversée (blanc sur noir). « 07h » = de 7 h à 8 h.
- Toucher le nom du lieu pour en chercher un autre ou utiliser la position GPS (Chauny par défaut).
- Roue crantée en haut à droite : paramètres — unité de température (Celsius par défaut, ou Fahrenheit),
  langue (celle du téléphone par défaut ; français, anglais, allemand, espagnol, italien, portugais, polonais),
  À propos.
- Noir et blanc pur, aucun service Google. Prévisions : Open-Meteo (gratuit, sans clé),
  qui s'appuie sur les modèles de Météo-France pour la France ; dernières données gardées hors connexion.
  Lever/coucher du soleil et phases de la Lune sont calculés sur le téléphone, sans connexion.
  Durée d'ensoleillement : estimée heure par heure à partir du rayonnement solaire direct (voir `Forecast.java`).
  Fêtes hors France : paquet « namedays » (MIT), voir `THIRD_PARTY.md`.

## Obtenir l'APK sans rien installer (GitHub)

1. Créer un dépôt **privé** sur github.com (la clé de signature est dans le projet).
2. Y envoyer tout le contenu de ce dossier (bouton « Add file → Upload files », dossiers compris).
   Si l'onglet Actions reste vide (le dossier caché `.github` n'a pas été envoyé) : « Add file → Create new file »,
   nommer le fichier `.github/workflows/build.yml` et y coller le contenu du fichier du même nom.
3. Onglet **Actions** → « Compiler l'APK » se lance tout seul (≈ 5 min).
4. Ouvrir l'exécution terminée → section **Artifacts** → télécharger `PaperMeteo-EInk` ou `PaperMeteo-Color`
   (un zip contenant l'APK).

Versions figées : onglet **Releases** du dépôt (ex. `v1.0-kompakt`). Pour en créer une, pousser une branche
`release/<nom>` : la compilation crée l'étiquette `<nom>` et la Release avec les APK.

## Ou compiler sur son PC

Avec Android Studio installé : ouvrir le dossier, puis `./gradlew assembleRelease`
(sous Windows : `gradlew.bat assembleRelease`). Les APK sortent dans `app/build/outputs/apk/eink/release/`
et `app/build/outputs/apk/color/release/`.

## Installer sur le Kompakt

Via **Mudita Center** (gestion des applications → installer un APK), ou en ADB :
`adb install -r PaperMeteo-EInk.apk`.
