# Composants tiers

## Fêtes du jour hors France — paquet npm « namedays » 5.0.1

Source : https://github.com/filiptammergard/namedays — données reprises dans
`app/src/main/java/fr/pluie/kompakt/NameDays.java` (Pologne, Allemagne, Tchéquie, Suède, Norvège, Danemark).

```
MIT License

Copyright (c) Filip Tammergård

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
```

## Nom de la commune pour la position GPS — Nominatim / OpenStreetMap

Service https://nominatim.openstreetmap.org (géocodage inverse, appelé une fois par localisation GPS).
Données © contributeurs OpenStreetMap, sous licence ODbL : https://www.openstreetmap.org/copyright

## Onglet Radar

- Images radar (passé et, si disponible, prévision immédiate) : RainViewer, API Weather Maps
  (https://www.rainviewer.com/api.html), gratuite pour un usage personnel, mention « RainViewer » affichée sur la carte.
- Contours des pays : Natural Earth (https://www.naturalearthdata.com), domaine public
  (`app/src/main/assets/map/borders.bin`).
- Villes : GeoNames (https://www.geonames.org), licence CC BY 4.0, via le fichier `cities.pbf` du paquet npm
  « all-the-cities » 3.1.0 (MIT) ; plus de 5 000 habitants en Europe, plus de 15 000 ailleurs
  (`app/src/main/assets/map/cities.tsv`). Les deux fichiers sont produits par `tools/make_map.py`.
- Code de l'onglet écrit pour Paper Meteo (aucun code repris de kRadar).
