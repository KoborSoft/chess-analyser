# Sakk Android alkalmazás — feladatlista

Fizetős, Play Store-ba szánt Android sakkalkalmazás. Kotlin + Jetpack Compose,
Stockfish motor (GPLv3 — a forráskódot közzé kell tenni), teljes szabálykészlet,
sakkóra, mentés/betöltés, lépés-visszavonás.

Jelmagyarázat: `[ ]` teendő · `[x]` kész · `[~]` folyamatban

## 1. Projektváz
- [x] Gradle-alapú Android projekt szerkezete (Android Studióban buildelhető)
- [x] Modulfelosztás: `core` (tiszta Kotlin sakklogika) + `app` (Android UI)
- [ ] Gradle wrapper generálása (`gradle wrapper` az első gépi buildnél)

## 2. Sakklogika (core modul, tiszta Kotlin) — KÉSZ
- [x] Táblaábrázolás, állapot (FEN be/ki)
- [x] Lépésgenerálás minden bábura
- [x] Speciális szabályok: sáncolás, en passant, gyalogátváltozás
- [x] Sakk, matt, patt felismerése
- [x] Döntetlen szabályok: 50 lépés, háromszori ismétlés, anyaghiány
- [x] Algebrai jelölés (SAN) ki- és beolvasás, PGN export/import
- [x] Perft-tesztek — 2026-07-18-án lefuttatva, mind az 5 ellenőrző állás
      minden értéke egyezik a referenciával (kezdőállás d1–d4: 20 / 400 /
      8902 / 197281; Kiwipete, végjáték-, átváltozás- és tükrözött állás)

## 3. Felhasználói felület (app modul)
- [x] Sakktábla Compose-ban: koppintásos lépés, lehetséges lépések jelölése,
      utolsó lépés és sakk kiemelése, koordináták, tábla forgatása
- [x] Lépésanimáció (a bábu csúszva mozog, sáncnál a bástya is)
- [x] Utolsó lépés kiemelése a lépő fél szerinti árnyalattal
      (világos lépése arany, sötété kék), a mező alapszínéhez külön
      hangolt 4 árnyalattal
- [x] Játékos-sávok a tábla felett/alatt: levett bábuk és anyagi
      pontkülönbség (+N; gyalog 1, huszár/futó 3, bástya 5, vezér 9),
      időkontrollnál ugyanitt a sakkóra
- [x] Visszavonásnál az animáció visszafelé játssza a lépést
- [x] Ikonos gombok: felső sáv (+ új játék, fogaskerék beállítások),
      alsó sor (visszavonás, forgatás, feladás, PGN-megosztás)
- [x] Játszma közbeni beállítások (fogaskerék): ellenfél típusa/színe és
      motorparaméterek azonnali érvénnyel; a mód és a beállítások
      kilépés után is megőrződnek (a gép újraindítás után is lép)
- [x] Kapcsolható javaslat-jelzés: a 4 legjobb lépés zöld nyíllal, számmal
      feliratozva; kapcsolója az alsó ikonsorban (villanykörte)
- [x] Kapcsolható veszély-jelzés: az ellenfél 3 legerősebb lépése piros
      nyíllal, számmal; kapcsolója az alsó ikonsorban (figyelmeztető jel)
- [x] Nyíl-feliratok a normalizált képlettel: javulás a statikus
      értékeléshez képest, a legjobb lépés javulásához arányosítva ×10
      (legjobb = 10); rontó lépésnél valódi változás gyalogban; matt: M3,
      mattot kapó: -M3; a szín és a vastagság is az erősséget követi
- [x] Értékelés-csík a tábla tetején: világos/sötét arány + Stockfish-
      érték számmal (statikus eval, minden állásnál frissül)
- [x] Folyamatos elemzés: mindig teljes erővel, két külön
      Stockfish-folyamat (javaslat + veszély párhuzamosan), 0,5 mp-től
      5 mp-ig félmásodpercenként frissülő nyilakkal
- [x] Lépésgráf a lépéslista helyén: fentről lefelé haladó fő ág,
      visszalépés után új lépésre oldalra nyíló mellékág; pötty + SAN
      felirat, az aktuális csomópont kiemelve; görgethető, koppintásra az
      állás átáll
- [x] Analízis be/ki kapcsoló a felső sávban (állapota megjegyezve):
      bekapcsolva gráf + értékelés-csík + jelzés-kapcsolók, az óra ÁLL;
      kikapcsolva tiszta játékmód: fut az óra, elemzés és analízis-elemek
      nélkül; gép ellen analízis módban is lehet játszani
- [x] Analízis módban a felhasználó mindkét fél helyett léphet: a gráfon
      gépre váró állásra ugorva ő lép a gép helyett (nincs „Lépj" gomb);
      a saját lépése után a gép automatikusan válaszol
- [x] Súgó a beállításokban (Licencek menü helyett): útmutató,
      kapcsolat (chess@koborsoft.com), licencinformációk
- [x] Nyelvváltás a beállításokban legördülő menüvel: 🇭🇺 / 🇬🇧 /
      automatikus (a korábbi lapkás elrendezés szétnyomta a szöveget)
- [x] Állás-felismerés képernyőképről beépített, offline CNN-nel: a kamera
      ikon a felső sávban mindig aktív (nincs kulcs, nincs hálózat); a kép a
      galériából jön (az élő „Fénykép" gomb eltávolítva — a felismerő
      digitális képernyőképekhez való, nem fizikai tábla fotózásához);
      „Fehér jön / Fekete jön" választó. Ingyenes és privát.
- [x] Megbízhatóság-kapu: ha a tábla-lokalizáció sakktábla-korrelációja a
      küszöb alá esik (nincs tiszta tábla a képen), őszinte „Nem találtam
      sakktáblát…" üzenet a zagyva állás helyett.
- [x] CNN-modell: S1M0N38/chess-cv (MIT-licenc) mezőnkénti (32×32 RGB)
      bábuosztályozó, ONNX-ra exportálva (~17 KB), ONNX Runtime Androidon
      futtatva egyetlen batch-ben (64 mező). Beépítve:
      `app/src/main/assets/pieces.onnx`; export-szkript a fejlesztőgépen.
- [x] Tábla-lokalizáció klasszikus CV-vel (`BoardLocator`): a szabályos 8×8
      világos/sötét rács megkeresése él-profil autokorrelációval, OpenCV
      nélkül; lekicsinyített képen fut a sebességért, majd az eredeti
      felbontásból vágja a mezőket. Beágyazott/zajos screenshoton is megtalálja
      a táblát.
- [x] Szabályossági ellenőrzés a felismerés után (`core` alapján): két király,
      hiányzó király vagy gyalog az 1./8. soron → „gyanús felismerés" jelzés.
      Erősebb hibajelző, mint a softmax-konfidencia. A bizonytalan mezők (alacsony
      konfidencia) a szerkesztőben borostyán kerettel kiemelve.
- [x] Szerkeszthető előnézet a felismerés után: a gép tippjét a felhasználó
      koppintással javíthatja (bábu-paletta + törlő, „ki lép", tábla-
      forgatás), majd indul a játék. A tiszta, digitális táblák felismerése
      pontos; beágyazott/overlay-es képnél (ahol egy dialógus takarhat bábut)
      marad a kézi javítás.
- [x] A szerkesztőben tab a felismert állás és a forráskép között
- [ ] Automatikus tájolás felismeréskor: a CNN raszter-sorrendben olvas, ezért
      fekete-szemszögű képnél az állás 180°-kal elforgatva töltődik be (szabályos,
      de tükrözött — a felhasználónak kézzel kell forgatnia). A régi LLM a
      koordináta-címkékből (1–8 / h–a) tájolt; ezt pótolni kell (címke-detektálás
      vagy gyalogirány-heurisztika), hogy alapból jó szemszögben álljon.
- [ ] Fotó-felismerő (fizikai tábla, ferde szög) fontolóra vétele: licenc-
      tiszta modell keresése (a jelenlegi jelöltek AGPL-esek vagy licenc
      nélküliek); a screenshot-felismerőtől külön kiértékelő.
- [x] A gombsor vízszintesen görgethető, nem lóg le a képernyőről
- [ ] Bábuhúzás (drag-and-drop) a koppintás mellé
- [x] Lépéslista SAN jelöléssel, lépés-visszavonás (undo)
- [ ] Lépések közti visszalépegetés (korábbi állás megtekintése)
- [x] Játékmódok: ember–gép (szín + nehézség), ember–ember
- [x] Sakkóra (1+0, 3+2, 5+0, 10+0, 15+10)
- [ ] Egyéni időkontroll megadása
- [x] Automatikus mentés kilépéskor, visszatöltés induláskor
- [x] PGN megosztása (rendszer megosztási menü)
- [x] PGN/FEN importálás felülete: a szerkesztőben fájl- és string-betöltés
      (auto-detektált FEN vagy PGN), a kép-felismerés mellé
- [x] Material 3, világos/sötét téma
- [x] Álló (portrait) módra zárva: a program kötelezően álló tájolásban
      használandó (a manifestben `screenOrientation="portrait"`), így nem törik
      el az elrendezés fekvőben
- [x] Magyar és angol fordítás
- [x] Alkalmazásnév: Chess Analyser; adaptív ikon (matt-kék mini
      sakktábla nagyítóval)
- [x] Alkalmazás-azonosító (Play Store identitás):
      com.koborsoft.chessanalyser — a teljes kódbázis csomagnevei is erre
      lettek átnevezve
- [x] Egységes matt-kék arculat: táblaszínek, utolsó-lépés kiemelések
      (borostyán/lila), Material-téma és ikon egy palettából

## 4. Gépi ellenfél
- [x] Beépített tartalék motor (negamax, alfa-béta) — Stockfish nélküli
      eszközökön (pl. 32 bites) ez játszik
- [x] UCI protokoll kezelése külön szálon (StockfishEngine)
- [x] Stockfish 17.1 lefordítva Androidra (NDK r27c, arm64-v8a, beágyazott
      NNUE hálókkal, ~80 MB) és beépítve:
      `app/src/main/jniLibs/arm64-v8a/libstockfish.so`;
      fordítószkript: `/home/kobor42/tools/build_stockfish.sh`
- [x] A nehézség beállítása közvetlenül a motorparaméterekkel történik az
      Új játék panelen, 4 mindig aktív csúszkával: UCI_Elo (1320–3190,
      maximumon = nincs korlát), Skill Level (0–20, 20 = teljes erő),
      idő lépésenként (100–3000 ms) és mélység-korlát (1–30, maximumon =
      nincs korlát) — a keresés annál a korlátnál áll meg, amelyik előbb
      teljesül; alul folyamatosan frissülő becsült eredő erősség
      (tapasztalati képlet)
- [ ] armeabi-v7a (32 bites) Stockfish-fordítás, ha szükség lesz rá
- [x] Licencinformáció képernyő (GPLv3, Stockfish forrásmegjelölés)

## 5. Kiadásra felkészítés
- [x] Release build alapkonfiguráció (minify + proguard)
- [ ] Aláírókulcs és aláírt App Bundle készítése
- [ ] Adatvédelmi nyilatkozat szövege (nem gyűjtünk adatot)
- [x] Forráskód közzététele (GPLv3-kötelezettség a Stockfish miatt):
      publikus repó — https://github.com/koborsoft/chess-analyser (LICENSE:
      GPLv3; a súgó és a licenc-képernyő is erre a címre mutat)
- [ ] Hangeffektek (jogtiszta), beállítási lehetőséggel

## 6. Jövőbeli feladatok (a felhasználó intézi)
- [ ] **Google Play fejlesztői fiók regisztrációja** (egyszeri 25 USD díj)
- [ ] Áruházi adatlap: leírás, képernyőképek, grafikák
- [ ] Ár beállítása (a Google 15% jutalékot von le)
- [ ] Alkalmazás feltöltése és megjelentetése

## Megjegyzések
- A fejlesztőgépen nincs Android SDK: az `app` modul buildje és a kézi
  tesztelés a felhasználó gépén, Android Studióban történik. A `core` modul
  helyben tesztelhető (JVM, perft).
