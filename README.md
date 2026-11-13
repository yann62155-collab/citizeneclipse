# Citizen Éclipse — NeoForge 21.1.215 / Minecraft 1.21.1 / Java 21

Mod ID : `citizeneclipse` — à installer **sur le serveur ET chez les joueurs** (rendu du ciel + casque).

## Compiler

```
gradlew build
```
Le JAR se trouve dans `build/libs/citizeneclipse-1.0.0.jar`.
`gradle.properties` est réglé sur `neo_version=21.1.215` (rien d'autre à changer).

## Installation sur BoxToPlay (FTP)

1. Déposer le JAR dans `mods/` du serveur (et dans le dépôt de Citizen Updater pour les joueurs).
2. **Ouvrir le port 30015** dans le panel BoxToPlay (comme le 30022 pour l'Updater).
3. Démarrer le serveur : `config/citizeneclipse/config.json` et `eclipses.json` sont créés.
4. Calendrier : **http://mc312.boxtoplay.com:30015**
   (agenda téléphone : http://mc312.boxtoplay.com:30015/eclipses.ics)

## Déroulé d'une éclipse

| Phase | Durée par défaut | Ce qui se passe |
|---|---|---|
| Approche | 45 min | Le soleil se lève en ~10 s s'il fait nuit puis reste figé ; **la lune glisse vers le soleil**, barre de boss dorée |
| Totale | 15 min | Ciel noir + couronne, titre à l'écran, **vagues de monstres** autour de chaque joueur (dès 90 % de l'approche) + spawn naturel autorisé en plein jour |

> Les joueurs en **créatif / spectateur** ne déclenchent pas de vagues. Difficulté **Paisible** = aucun monstre.
| Éloignement | 10 min | La lune repart, le ciel s'éclaircit |
| Fin | — | Monstres d'éclipse retirés, cycle jour/nuit restauré |

Regarder le soleil sans casque pendant l'éclipse → nausée + cécité courte + message dans la barre d'action.
Le **casque d'éclipse** protège (il s'use lentement pendant l'observation).

### Craft du casque d'éclipse
```
Cuir      Lingot de fer  Cuir
Verre teinté    —    Verre teinté
```

## Créatures d'éclipse

Elles apparaissent **en groupes** (1 à 3), ne brûlent pas au soleil et **partent en fumée automatiquement à la fin de l'éclipse**
(même celles restées dans des chunks non chargés : elles disparaissent dès qu'elles sont rechargées).

| Créature | Apparence | Vie | Particularité |
|---|---|---|---|
| Zombie d'ombre | zombie noir, yeux rouges lumineux | 40 | armure 6, résiste au recul |
| Archer du néant | squelette violet sombre, casque de fer | 34 | rapide |
| Araignée des ténèbres | araignée noire 1,4× plus grande | 32 | venin, toujours agressive en plein jour |
| Spectre d'éclipse | grand phantom violet | 30 | fond sur les joueurs depuis le ciel |
| **Colosse d'éclipse** | **zombie mutant géant (~4,5 blocs)** rouge sombre | 140 | frappe à 14, quasi insensible au recul |

### Grand Moine ténébreux et ses disciples

- **Grand Moine** : un seul par éclipse, **1000 PV**, grand (1,6×), robe noire, yeux violets, barre de boss.
  Il arrive **entouré de 4 moines disciples** et en rappelle de nouveaux toutes les 90 s (jamais plus de 4 en vie).
- **Moines disciples** : robe pourpre, yeux rouges, taille normale, **200 PV chacun**, **4 maximum en même temps**.
  Ils suivent le Grand Moine, attaquent les joueurs qui s'approchent, se défendent et lancent une spirale d'ombre plus faible
  (5 s minimum entre deux sorts).
- **Pouvoirs du Grand Moine** (aucun ne détruit de bloc) :
  - **Cercle des ténèbres** : cercle de flammes d'âme au sol (1,5 s pour fuir), puis onde sur 8 blocs : 8 dégâts, projection, Wither, Obscurité.
  - **Spirale d'ombre** : double hélice violette qui poursuit sa cible (4 à 28 blocs) : 7 dégâts, lenteur, lévitation courte.
  - **5 secondes minimum entre deux sorts.** Sous 50 % de vie il enrage (plus de dégâts, deux spirales).
- **Position annoncée dans le chat toutes les minutes** (Grand Moine, PV, nombre de disciples).
- **Quand le Grand Moine tombe** : message avec le nom du vainqueur, puis **cinématique lancée automatiquement**
  (`moine.commandeCinematique` dans la config, vide tant que la vidéo n'est pas prête).
  **Il ne revient plus jamais** : aux éclipses suivantes, seuls ses 4 disciples apparaissent.
- Commandes admin : `/eclipse moine` (le fait apparaître près de soi pendant une éclipse), `/eclipse moine disciples`,
  `/eclipse moine statut`, `/eclipse moine reinitialiser` (le Grand Moine pourra revenir — utile après un test).

**Mutants** : 15 % des zombies, archers et araignées d'éclipse apparaissent en version mutante,
1,9× plus grande, avec plus de vie et de dégâts (`chanceMutantPourcent`, `tailleMutant` dans la config).

## Planification automatique

- Au moins **1 éclipse par semaine** (lundi → dimanche), tirée au hasard **dans les heures d'ouverture** :
  lun–ven 19h00 → 00h30, sam–dim 18h00 → 03h00. L'éclipse entière (approche comprise) tient dans le créneau.
- Les 2 semaines à venir sont toujours planifiées. Si un admin annule l'éclipse de la semaine, une nouvelle date est tirée.

## Annonces (sans spam)

- À la connexion : **une seule ligne** avec la date de la prochaine éclipse + lien cliquable vers le calendrier.
- Rappels dans le chat à **60, 30, 10 et 1 minute(s)** (un seul message par palier, pour tout le monde).
- Messages de début / totale / fin. Tout le reste passe par la barre de boss.

## Commandes

| Commande | Qui |
|---|---|
| `/eclipse` ou `/eclipse prochaine` | tout le monde |
| `/eclipse calendrier` | tout le monde (lien cliquable) |
| `/eclipse liste` | admin (OP 2) |
| `/eclipse lancer [approcheMin] [dureeMin]` | admin — éclipse sur un coup de tête (approche 1 min par défaut) |
| `/eclipse planifier 2026-11-20 21h30 [dureeMin]` | admin |
| `/eclipse deplacer <id> 2026-11-21 22h00` | admin |
| `/eclipse annuler <id>` | admin |
| `/eclipse arreter` | admin — interrompt l'éclipse en cours |
| `/eclipse generer` | admin — force le minimum hebdomadaire |
| `/eclipse moine` (`disciples`, `statut`, `reinitialiser`) | admin — Grand Moine / disciples (voir plus haut) |
| `/eclipse vague` | admin — force une vague autour de soi (même en créatif) et dit ce qui bloque |
| `/eclipse statut` / `/eclipse recharger` | admin |

## Configuration (`config/citizeneclipse/config.json`)

Tout est modifiable puis `/eclipse recharger` (sauf le port web : redémarrage).
Principales clés : `horairesOuverture`, `eclipsesAutoParSemaine`, `approcheMinutes`, `dureeTotaleMinutes`,
`forcerJour`, `web.port`, `web.urlPublique`, `creatures.monstresMaxParJoueur`, `creatures.types`,
`vision.casquesProtecteurs`, `annonces.rappelsMinutes`.

## Compatibilité

- Aucune dépendance externe, aucun mixin. Le site web utilise uniquement `java.net`.
- Les monstres sont créés via les événements NeoForge (`finalizeMobSpawn`), donc visibles par les autres mods.
- Seuls les monstres marqués `citizeneclipse.mob` sont retirés à la fin.
