# Fejlesztési szabályok

## Dokumentáció

- Minden fejlesztési feladathoz tartozik egy `docs/` mappa.
- A `docs/` mappában mindig legyen:
  - `overview.md` — a projekt áttekintése: mi ez, hogyan épül fel, hogyan kell
    buildelni és tesztelni.
  - `history.md` — a meghozott döntések és a tanulságok rögzítése,
    dátumozott bejegyzésekkel.
- Minden egyéb dokumentum is a `docs/` mappába kerül.
- Minden dokumentum mindig az **aktuális állapotot** tartalmazza — elavult
  tartalmat frissíteni vagy törölni kell, nem megjegyzésekkel jelölni.
- Soha ne írj megjegyzéseket régi, rossz megoldásokról — mindig a jó, működő
  megoldásra fókuszálj. A tanulságok helye a `history.md`.

## Projekt

- Android sakkalkalmazás: Kotlin + Jetpack Compose, két modul:
  - `core` — tiszta Kotlin (JVM) sakklogika, Androidtól független.
  - `app` — Android felület és motorillesztés.
- A sakklogika minden változtatása után futtatni kell a perft-teszteket
  (`core/src/test/kotlin/.../PerftTest.kt`).
- A Stockfish GPLv3 licencű: a beépítése forráskód-közzétételi
  kötelezettséggel jár, a licencinformációt az app-ban fel kell tüntetni.
- Nyelv: a kód angol azonosítókkal, a kommentek és a dokumentáció magyarul.
