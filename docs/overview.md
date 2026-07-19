# Sakk — Android alkalmazás

Fizetős, Google Play Store-ba szánt sakkalkalmazás. Kotlin + Jetpack Compose,
Stockfish gépi ellenféllel (amíg a Stockfish bináris nincs beépítve, beépített
tartalék motor játszik).

## Felépítés

```
core/   Tiszta Kotlin (JVM) sakklogika — Androidtól független
        - Piece.kt          bábuk, mezők, Move
        - Position.kt       állás, FEN, lépés-végrehajtás, támadásvizsgálat
        - MoveGenerator.kt  szabályos lépések generálása, perft
        - Game.kt           játszma, történet, visszavonás, játékvég-szabályok
        - San.kt            SAN/UCI jelölés, PGN export-import
app/    Android felület és motorillesztés
        - engine/           ChessEngine interfész, BuiltInEngine (tartalék),
                            StockfishEngine (UCI, libstockfish.so)
        - GameViewModel.kt  játékállapot, sakkóra, mentés
        - ui/               Compose képernyők (tábla, lépéslista, dialógusok)
docs/   Dokumentáció (ez a mappa)
```

## Build és teszt

- **Build:** Android Studióban megnyitni a projekt gyökerét, majd Run.
  Parancssorból: `./gradlew :app:assembleDebug` (első alkalommal
  `gradle wrapper` szükséges, ha a wrapper még hiányzik).
- **Sakklogika tesztek:** `./gradlew :core:test` — perft-tesztek ismert
  értékekkel (kezdőállás d1–d4: 20 / 400 / 8902 / 197281, továbbá Kiwipete
  és más ellenőrző állások) és szabálytesztek (matt, patt, FEN, PGN, undo).
- **Fekete dobozos UI-tesztek:** `tests/run_ui_tests.sh` — a telepített appot
  adb-n keresztül, valódi felhasználót szimulálva teszteli (koppintás,
  `uiautomator dump`, képernyőkép). Előfeltétel: csatlakoztatott, feloldott
  eszköz telepített appal; az adb útvonala az `ADB` környezeti változóval
  adható meg. Paraméter nélkül minden forgatókönyv fut, vagy megadhatók
  forgatókönyv-előtagok (pl. `tests/run_ui_tests.sh T05 T09`). A futás végén
  géppel olvasható összegzést ír; a hibák képernyőképei a `tests/evidence/`
  mappába kerülnek. Segédprogramok: `tests/ui_helper.py` (UI-fa elemzése,
  táblageometria), `tests/pixel.py` (képpont-ellenőrzés).
- Követelmények: JDK 17, Android SDK (compileSdk 35), minSdk 26.

## Stockfish beépítése

1. Stockfish forrás letöltése és fordítása Android NDK-val
   (arm64-v8a és armeabi-v7a).
2. A binárisokat `libstockfish.so` néven az
   `app/src/main/jniLibs/<abi>/` mappákba kell tenni.
3. Az app indításkor automatikusan észleli és használja; ha nincs jelen,
   a beépített motor játszik.
4. GPLv3: az alkalmazás forráskódját közzé kell tenni, a licencképernyőn
   fel kell tüntetni a Stockfish szerzőit és a forráskód linkjét.

## Feladatok

Az aktuális feladatlista: [FELADATOK.md](FELADATOK.md)
