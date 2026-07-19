# Döntések és tanulságok

## 2026-07-18 — Projektindítás

**Döntések:**

- **Platform:** natív Android, Kotlin + Jetpack Compose. Cél: fizetős app a
  Google Play Store-ban.
- **Motor:** Stockfish UCI protokollon (natív bináris, `libstockfish.so`
  néven a jniLibs-ben). Amíg a bináris nincs beépítve, egy beépített
  negamax motor (BuiltInEngine) biztosítja, hogy az app azonnal játszható
  legyen — így a fejlesztés nem függ az NDK-s fordítástól.
- **Licenc:** a Stockfish GPLv3, ezért az alkalmazás forráskódját közzé kell
  tenni. Ez az eladást nem akadályozza, de a licencképernyő és a
  forráskód-link kötelező.
- **Architektúra:** két modul. A `core` tiszta Kotlin (JVM), Androidtól
  független — így a sakklogika gyorsan, emulátor nélkül tesztelhető.
  Az állás (`Position`) megváltoztathatatlan objektum: minden lépés új
  példányt ad, ezért a visszavonás és az ismétlődés-vizsgálat triviális.
- **Szabálylogika sajátban:** a Stockfish csak ellenfél; a szabályokat a
  saját, perft-tesztekkel hitelesített kód kezeli, hogy a játékmenet a
  motortól függetlenül is helyes legyen.

**Tanulságok:**

- A perft-teszt már az első futáskor teljes értékű biztosítékot adott:
  öt ellenőrző állás (köztük a Kiwipete) minden értéke egyezett a
  referenciával — a sáncolás, en passant és átváltozás is helyes.
- A SAN-beolvasást érdemes a lépésgenerálásra visszavezetni (a szabályos
  lépések SAN-ját előállítjuk és összevetjük): kevés kód, és nem tud
  eltérni a lépésgenerálástól.

## 2026-07-18 — Első működő alkalmazásváz

**Döntések:**

- **UI első körben koppintásos:** a bábu kijelölése és a célmező koppintása
  a megbízhatóan tesztelhető alap; a drag-and-drop külön feladatként kerül rá.
- **Dokumentációs rend bevezetve** (CLAUDE.md): minden doksi a `docs/`
  mappában, `overview.md` + `history.md` kötelező, minden dokumentum mindig
  az aktuális állapotot tükrözi.
- **Mentés:** automatikus PGN-mentés SharedPreferences-be kilépéskor és
  minden lépés után; a PGN egyben a megosztási formátum is.

**Tanulságok:**

- Negamax gyökérhívásnál az `Int.MIN_VALUE` negálása túlcsordul; az
  alfa-béta ablakot `Int.MIN_VALUE + 1`-ről kell indítani.
- Android SDK nélküli gépen is teljes értékűen fejleszthető és tesztelhető
  a szabálylogika, ha külön, tiszta JVM-modulban van.

## 2026-07-18 — Tesztelési folyamat

**Döntések:**

- **Fekete dobozos tesztelő ügynök** (`.claude/agents/teszt-keszito.md`):
  a tesztkészítő ugyanazt a feladatleírást kapja, mint a fejlesztő, és abból
  — nem a kódból — vezeti le az elvárt viselkedést. A tesztprogram a valódi
  kezelőfelületen át, felhasználói használatot szimulálva tesztel (Androidon
  adb-vel), és géppel olvasható eredményösszegzést ad.
- A build memóriabeállításait a gép adottságaihoz kell igazítani
  (gradle.properties: Xmx-korlátok, workers.max) — a Gradle-démont kevés
  memóriánál a rendszer kilövi.

## 2026-07-18 — Fekete dobozos UI-tesztsor (tests/)

**Döntések:**

- **A tesztsor adb-alapú:** `tests/run_ui_tests.sh` a telepített appot valódi
  eszközön, koppintásokkal vezérli; a táblaállapotot a `uiautomator dump`
  hierarchiából olvassa vissza (a mezők View-ként, a bábuk Unicode-karakteres
  TextView-ként jelennek meg), a mezőkoordinátákat a koordinátafeliratokból
  (a–h, 1–8) számolja, így a tábla forgatása után is helyes.
- A lépésjelölés (kijelölt bábu lehetséges lépései) képpont-összehasonlítással
  ellenőrizhető (`tests/pixel.py`), mert a jelölés a Compose-vásznon rajzolódik.

**Tanulságok:**

- Szabálytalan lépéskísérlet után a kijelölés állapota a tesztből nem
  látható; a következő lépést ellenőrzéssel és újrapróbálással kell végezni
  (`mv_verify`), különben a koppintáspárok fáziseltolódásba kerülnek.

## 2026-07-19 — Stockfish beépítése és kézi motorbeállítások

**Döntések:**

- **Stockfish 17.1, csak arm64-v8a:** a mai készülékek mind 64 bitesek; a
  ritka 32 bites eszközökön a beépített motor játszik (automatikus
  tartalék), az APK pedig feleakkora. A bináris beágyazott NNUE hálókkal
  ~80 MB.
- **Kézi paraméterek:** az Új játék panel „Egyéni" fokozatán UCI_Elo,
  Skill Level és gondolkodási idő vagy mélység állítható; a becsült eredő
  erősséget tapasztalati képlet mutatja (Skill ≈ 850 + 115·szint; idő
  feleződése ≈ −60 Elo; mélységszint ≈ −45 Elo a 20-hoz képest).
- **A 800–1100-as fokozatok Skill Levellel** valósulnak meg, mert a
  UCI_Elo alsó határa 1320.

**Tanulságok:**

- `make` nélküli gépen a Stockfish közvetlen clang++ hívással is fordul
  (NDK toolchain + USE_NEON=8 + beágyazott .nnue fájlok a forráskönyvtárban).
- Az AGP alapból nem csomagolja ki a natív libeket (`extractNativeLibs`
  nélkül a bináris nem futtatható) — `packaging.jniLibs.useLegacyPackaging
  = true` kell, ha egy .so valójában futtatható program.
- A /tmp tmpfs (RAM-alapú) — a több GB-os NDK-t lemezre (~/tools) kell
  tenni, különben elfogy a memória.

## 2026-07-19 — Kényelmi funkciók és jelzések

**Döntések:**

- **Visszavonás animációja visszafelé** játszódik (a bábu a célmezőről
  csúszik vissza; sáncnál a bástya is).
- **Ikonos felület:** felül + (új játék), kamera (állás fotóról),
  fogaskerék (beállítások); alul visszavonás/forgatás/feladás/megosztás
  ikonok. A Licencek menü megszűnt — Súgó a beállításokban (útmutató,
  chess@koborsoft.com, licencinfó).
- **A mód és minden beállítás megőrződik** kilépéskor (SharedPreferences),
  így a gép újraindítás után is lép; a fogaskerék alatt játszma közben is
  átállítható az ellenfél, a szín és a motor.
- **Jelzések motor-pontszám alapján:** 4 zöld javaslat-nyíl és 3 piros
  veszély-nyíl (az ellenfél legerősebb válaszai); az árnyalat a legjobbhoz
  mért pontszám-lemaradással arányos (300 cp-nél éri el a leghalványabbat),
  nem fix rangsor szerint. Az elemzést külön Stockfish-folyamat végzi
  (MultiPV), hogy ne zavarja az ellenfél-motort; sakkban állva nincs
  veszély-elemzés (maga a sakk a jelzés).
- **Nyelvváltás** a beállításokban (🇭🇺/🇬🇧/automatikus), Activity-újraindítással.
- **Állás fotóról:** a felhasználó által beállított OpenAI-kompatibilis
  LLM-végpont ismeri fel az állást (FEN); a kamera gomb csak beállított
  kulcsnál aktív; a felismert állás azonnal játszható. INTERNET jogosultság
  emiatt került a manifestbe.

**Tanulságok:**

- A felhasználó elvárása: új funkcióknál implementálás ELŐTT tisztázó
  kérdések (AskUserQuestion) — a részletek utólagos javítgatása drágább.

## 2026-07-19 — Lépésgráf és folyamatos elemzés

**Döntések:**

- **Elágazó játszma-fa** (core/GameTree): visszalépés után az új lépés
  mellékágat nyit, a régi ág megmarad; a megjelenített ág lineáris
  játszmáját a fa útvonalából építjük újra (a szabály-logika változatlan).
  A mentés egyelőre csak az aktuális ágat őrzi meg (PGN).
- **Lépésgráf-nézet** a lépéslista helyén: Canvas-alapú, fentről lefelé,
  pötty + SAN felirat, koppintásra állás-ugrás; gép elleni játszmában a
  gépre váró csomópontnál „Lépj!" gomb (a gép csak kérésre lép vissza-
  ugrás után).
- **Folyamatos elemzés:** a nyilakhoz két teljes erejű Stockfish-folyamat
  fut párhuzamosan (javaslat + veszély), `go movetime 5000` mellett az
  info-sorokból 500 ms-onként frissül az eredmény; megszakításkor `stop`.
- **Nyíl-vizualizáció:** körvonal (zöldön fehér, piroson sötét) + a
  pontszám-lemaradással arányos vastagság és színmélység.

**Tanulságok:**

- Kotlin: másik modul publikus property-jére nem működik a smart cast —
  lokális változóba kell venni (`val move = node.move`).

## 2026-07-19 — Állás-felismerés: a vision-modell korlátai

**Döntések:**

- **Szerkeszthető előnézet** a fotó-felismerés után (a korábbi „azonnal
  játszható" helyett): a gép tippjét a felhasználó javítja (bábu-paletta,
  „ki lép", tábla-forgatás). Ez teszi a funkciót vállalhatóvá.
- Erősebb prompt: tábla-lokalizáció + tájolás a koordináta-címkékből +
  overlay-kihagyás; a modell 8×8 rácsot ad, ebből építjük a FEN-t.

**Tanulságok:**

- A GPT-4o (és a jelenlegi vision-LLM-ek) **nem megbízhatóak** sakktábla
  pontos leolvasásában. Mérés: tiszta, nagy, szabvány-tájolású tábla
  (kiindulóállás) → hibátlan; pici, beágyazott (screenshot-a-screenshotban),
  fekete-szemszögű, overlay-es tábla → menthetetlenül téveszt, prompttól
  függetlenül. Ez képességkorlát, nem prompt-hiba → emberi javítás kell.
- A felismerés minősége a bemeneten múlik: nagy, egyenes, akadálymentes,
  lehetőleg fehér-szemszögű tábla a jó bemenet.
- Modellek mérése ugyanazon a nehéz táblán (16 bábu): gpt-4o és gpt-4.1
  menthetetlen; gpt-5.2 gyenge; **gpt-5.6-luna 13/16** (a 3 hiba csak
  egy-egy mezős csúszás) — messze a legjobb, ez lett az OpenAI alapmodell.
  (gpt-5.6-sol: a szervizfiók-kulcsnak nincs rá jogosultsága.) A
  reasoning-modellek `max_completion_tokens`-t várnak, temperature nélkül.
- Dedikált megoldás (nem LLM) a jövőre: digitális képernyőképekhez CNN/
  template (tensorflow_chessbot) ~99% offline; valódi fotókhoz
  LiveChess2FEN CNN 92–99%. On-device TFLite modell lenne az ideális
  (ingyenes, offline, privát) — külön feladat.
- Valódi fotó teszt fizikai tábla nélkül: letöltött Wikimedia-fotón a
  gpt-5.6-luna a kiindulóállást ferde, 3D-s Staunton-fotón is helyesen
  adta — az élő-fotó út működik.

## 2026-07-19 — UI-javítások

**Döntések:**

- **A választók FilterChip helyett SegmentedButton** (SegChoice segéd):
  a kijelölt szegmens kitöltött + pipa, egyértelmű. A FilterChip fordított
  logikája (kijelölt = keret nélkül, nem kijelölt = vastag keret)
  összezavaró volt.

**Tanulságok:**

- A szerkesztő „Forgatás" gombja hibás volt: egyszerre forgatta a
  bábu-tömböt 180°-kal ÉS a nézetet, a kettő kioltotta egymást. Javítva:
  csak a bábukat forgatja (a nézetet nem).
- A gyalogátváltozás párbeszéde „Átváltozás" címmel és bábu-glifekkel jelenik
  meg — a tesztnek a ♘ glifre kell koppintania, nem „Huszár" feliratra.
- Valódi, személyes készüléken a képernyőzár a tesztfutás fő ellensége:
  ujjlenyomatos zár adb-vel nem oldható fel, a `wm dismiss-keyguard`
  átmenetileg fals „feloldva" állapotot mutathat; megbízható jelzés a
  `dumpsys trust` `deviceLocked` mezője. A tesztfuttató forgatókönyvenként
  ellenőrzi a zárat, és feloldásra várakozik.

## 2026-07-19 — Offline CNN felismerő: súly-export (1. mérföldkő)

**Döntések:**

- **Nem tanítunk saját CNN-t, kész MIT-licencű súlyt használunk fel.**
  Forrás: `S1M0N38/chess-cv` (HuggingFace, MIT) — mezőnkénti (32×32 RGB)
  bábuosztályozó, 13 osztály (`bB,bK,bN,bP,bQ,bR,wB,wK,wN,wP,wQ,wR,xx`),
  156k paraméter. Screenshotokra tanítva (chess.com/lichess stílusok).
- **Screenshot-először stratégia.** A tiszta digitális táblák felismerése a
  reális, licenc-tiszta cél; a valódi fotó (fizikai tábla) elhalasztva,
  mert az elérhető súlyok ott vagy licenc nélküliek, vagy AGPL-esek
  (YOLOv8/Ultralytics) — fizetős appba nem tehetők gond nélkül.
- **Következtetés on-device ONNX Runtime-mal.** A modell ONNX-ra exportálva
  mindössze ~17 KB.

**Tanulságok:**

- A nehéz rész NEM a bábufelismerés (kész, ~99%), hanem a **tábla
  megtalálása/kivágása a zajos képből** (beágyazott screenshot, overlay-ek).
  Ahol egy dialógus fizikailag eltakar egy bábut, ott SEMMILYEN felismerő
  nem olvas — ezért marad nélkülözhetetlen a szerkeszthető előnézet, és
  ezért kell a mezőnkénti **konfidencia** (softmax) a bizonytalan mezők
  megjelöléséhez.
- **MLX → ONNX konverzió buktatója:** az MLX NHWC elrendezésű. (1) A conv-
  súlyokat OHWI→OIHW forgatni kell. (2) Az `fc1` 1024 bemeneti oszlopa HWC
  sorrendű, a torch NCHW flatten viszont CHW — az oszlopokat át kell
  rendezni, különben a modell random pontosságú. Ellenőrzés MLX nélkül:
  numpy NHWC referencia-forward (az MLX hű átirata) == torch == ONNX
  (max eltérés ~1e-4), majd end-to-end szemantikai teszt renderelt, ismert
  címkéjű mezőkön: **583/584 (99.8%)**, átlagos konfidencia 0.998.
- **Teszt-tanulság:** egy tábla-mezőt KIVÁGNI kell a tábláról, nem az egész
  256px-es táblát 32px-re kicsinyíteni (különben a háttér 8×8-as
  mini-sakktábla lesz, és félrevisz). A javítás után ugrott 93%→99.8%-ra.

## 2026-07-19 — Offline CNN felismerő: Android-integráció és LLM elhagyása

**Döntések:**

- **Az online LLM-felismerőt teljesen elhagytuk; a felismerő kizárólag a
  beépített offline CNN.** Előny: a kamera kulcs és hálózat nélkül működik,
  ingyenes és privát, és megszűnik az API-kulcs biztonságos tárolásának
  kiadás előtti kötelezettsége. A `PositionRecognizer.kt` (LLM) és a
  beállítások LLM-szekciója törölve.
- **ONNX Runtime Android** (`com.microsoft.onnxruntime:onnxruntime-android:1.27.0`)
  a következtetéshez — ugyanaz a verzió, amivel a modellt exportáltuk. A 64
  mező egyetlen batch-ben fut (a modell `dynamic_axes`-szal a batch-dimenzión).
- **Hibajelzés: szabályossági ellenőrzés, nem konfidencia.** A `shot_ai`
  valós teszten kiderült, hogy a softmax-konfidencia GYENGE hibajelző: a
  ténylegesen hibás mezőt a modell magas konfidenciával tévesztette, a 0.91-es
  (egyetlen bizonytalan) mező pedig helyes volt. Ezért a felismert állást a
  `core` szabály-logikájával ellenőrizzük (király-darabszám, gyalog az
  1./8. soron) — ez fogta volna meg a hibát. A konfidencia csak halvány,
  másodlagos jelzés (bizonytalan mezők kiemelése).

**Tanulságok:**

- **A tábla lokalizációja a nehéz rész, nem a bábufelismerés.** A
  `BoardLocator` él-profil-autokorrelációval, OpenCV nélkül találja a rácsot;
  lekicsinyített képen (max 640 px) fut a sebességért, majd az eredeti
  felbontásból vágja a mezőket. Pythonban prototipizálva, majd Kotlinra
  portolva — a valós `shot_ai` screenshoton (1280×2772, beágyazott, fekete
  szemszög) is megtalálta a táblát.
- **Ne állítsuk „tökéletesnek" azt, ami szabálytalan.** A `shot_ai`
  felismerés `.K..qK..` felső sora két fehér királyt tartalmazott → szabálytalan,
  tehát NEM hibátlan. A pontosság kalibrált állítása fontos (a felhasználó
  korábban jogosan kifogásolta a túllécelést).
- **Build memória:** az onnxruntime AAR extra dexe miatt a D8 `mergeExtDexDebug`
  OOM-ozott 1280 MB Gradle-heappel a ~2 GB szabad RAM-os gépen. Megoldás:
  `org.gradle.jvmargs=-Xmx2048m`, a Kotlin-daemon heapje 512 MB-ra csökkentve.

## 2026-07-19 — CNN felismerő: telefonos hibakeresés és megbízhatóság-kapu

**Döntések:**

- **A „Fénykép" (élő kamera) gomb eltávolítva** a fotó-párbeszédből — csak
  galéria maradt. A felismerő digitális képernyőképekhez való; fizikai tábla
  élő fotózása a (nehéz, elhalasztott) eset, félrevezető lett volna felkínálni.
- **Megbízhatóság-kapu:** ha a tábla-lokalizáció sakktábla-korrelációja a
  `BOARD_CONF_MIN = 0.25` alá esik, nem dobunk zagyva állást, hanem őszinte
  üzenet: „Nem találtam sakktáblát…". Mérésben a jó táblák 0,39–0,52, a
  „nincs tiszta tábla" esetek 0,1 körül — a küszöb tisztán elválasztja.

**Tanulságok (két valós telefonos hiba):**

- **ONNX külső-adat csapda:** a torch dynamo-exporter a súlyokat külön
  `pieces.onnx.data` fájlba tette (a `.onnx` csak 17 KB volt!). Az ONNX Runtime
  az asszetből, bytes-ból betöltve ezt nem találta → `ORT_FAIL: External data
  path … does not exist`. Javítás: `onnx.save_model(..., save_as_external_data
  =False)` — egyetlen, önálló ~642 KB-os `.onnx`. Ellenőrzés: a modellt
  BYTES-ból betöltve kell tesztelni, ahogy Android is teszi.
- **Lokalizáció törékenysége:** az agresszív kicsinyítés (640 px) + az Android
  `createScaledBitmap` (bilineáris/NEAREST-szerű) más gradiens-profilt adott,
  mint a PIL — az eltolás-kereső EGY MEZŐVEL arrébb ugrott, a bal oszlop a
  háttérre esett → csupa téves „huszár". Javítás: (1) közel teljes felbontáson
  lokalizálni (`WORK_MAX = 1600`), (2) **sakktábla-ellenőrzés az eltolás
  egyértelműsítésére** (a 8×8 régiónak váltakozó világos/sötét mintázatúnak
  kell lennie). A `shot_ai` így a telefonon is (209,935) — a Python (207,935)-tel
  egyezően.
- **A checker-score kettős haszna:** egyszerre egyértelműsíti az eltolást ÉS
  megbízhatóság-jelző (kapu). Overlay-es/zsúfolt képen (a tábla csak a kép
  részét tölti ki, hint-ablak takar) a periódus-keresés harmonikust talál és a
  checker 0,1 alatt marad → a kapu elkapja.
- **Determinisztikus önteszt logcaton át** (beépített teszt-képekre) volt a
  hatékony hibakeresés kulcsa: a fotóválasztó vak UI-navigálása megbízhatatlan
  és zavaró volt (véletlen kép, kamera megnyílása), a napló viszont pontos.

## 2026-07-19 — Lokalizáció: több periódus-jelölt (a valós bemenet megoldása)

**Tanulság / javítás:**

- A felhasználó valódi bemenete egy chess-app screenshot, ahol a tábla kitölti
  a SZÉLESSÉGET, de csak a magasság középső részét (fent fejléc + „Solved with
  Hint" overlay, lent pontszám-sáv). Az egyetlen (átlagolt) periódus itt
  HARMONIKUST talált (fél cella), és a felismerés megbukott.
- **Megoldás:** ne egyetlen periódust becsüljünk, hanem TÖBB jelöltet (mindkét
  tengely autokorrelációs csúcsai + teljes-szélesség/magasság prior), és
  mindegyikhez a legjobb eltolást sakktábla-korrelációval keressük — a
  globálisan legjobb korrelációjú (periódus, eltolás) nyer. A valódi periódus
  magas checkert ad, a harmonikus nem. A checker mezőn belüli mintavétele
  ritkított a sebességért. Telefonon a felhasználó pontos képe: helyes,
  szabályos állás, ~210 ms.

## 2026-07-19 — Felismerés-tájolás: „a lépő van alul" szabály

**Döntések:**

- **A befotózott táblán a LÉPŐ fél van alul** — ez a felismerés tájolási
  szabálya. A CNN pixeleket olvas (nem mezőket), ezért a szemszöget alkalmazni
  KELL a mező-hozzárendeléshez: fehér jön → a kép teteje a 8. sor; fekete jön →
  a beolvasott (fehér-értelmezésű) táblát 180°-kal elforgatjuk (a bábuk a valós
  soraikra kerülnek, pl. felül lévő fehér paraszt → 2. kezdősor).
- **Két „forgatás" fogalom szétválasztva és egységesítve:**
  - *Nézet-forgatás* (kijelzés): a másik szemszögből mutat, a bábukat nem mozgatja.
    A fő tábla „Forgatás"-a ÉS mostantól a szerkesztő „Forgatás" gombja is EZ.
  - *Állás-forgatás* (a pozíció tényleges 180°-a): a szerkesztőben a „Ki lép"
    (Világos/Sötét) váltó csinálja — ez a tájolás vezérlője (a szabály szerint a
    lépő kerül alulra), a bábuk a másik szemszög szerinti valós mezőikre kerülnek.
- **A betöltött állás a fő táblán is a lépő szemszögéből** jelenik meg
  (`boardFlipped = sideToMove == BLACK`), hogy egyezzen a fotóval és a szerkesztővel.

**Tanulság:**

- A „fekete jön esetén elég a nézetet fordítani" intuíció a LÁTVÁNYRA igaz, de a
  pozícióra nem: a nézet nem rendel mezőket, a kamerából jövő raszter-olvasatot
  ténylegesen át kell forgatni, különben a bábuk rossz soron állnának.

## 2026-07-19 — Felismerés-UI redesign: egységes állásszerkesztő

**Döntések:**

- A korábbi szűk „felismerés → AlertDialog-előnézet" folyamat helyett EGY teljes
  képernyős **állásszerkesztő** (`PositionEditor`), ami a központi állásbeviteli
  felület. Belépő: a felső sávban „Szerkesztés" (Edit ikon), ami az AKTUÁLIS
  állással nyílik.
- Háromféle feltöltés egy helyen: (1) az aktuális állás szerkesztése (alap),
  (2) „Felismerés képről" gomb — akár többször, más képpel vagy újra ugyanazzal,
  (3) „Üres tábla" — nulláról. Végül „Betöltés".
- **A „Ki lép" váltó a felismerés tájolási tippje**, de a meglévő táblát NEM
  forgatja (normál szerkesztésnél az rossz lenne). Ha a tájolás téves, a
  felhasználó átállítja a „Ki lép"-et és újra felismer ugyanazzal a képpel.
- A régi `PhotoImportDialog` (külön „Fehér/Fekete jön" + galéria) törölve —
  a szín-választás és a felismerés is a szerkesztőbe került.

**Tanulság:**

- A felhasználó kérése: „ne csak hegeszd ami van". Az AlertDialog-alapú előnézet
  bővítése helyett a valódi megoldás egy teljes képernyős, újrahasznosítható
  szerkesztő, amiben a felismerés csak az egyik feltöltési mód. Ez oldotta fel a
  „Ki lép = tájolás vs. csak-kinek-a-köre" feszültséget is (a felismerés
  ismételhetősége miatt a tájolás újra-felismeréssel javítható).

## 2026-07-19 — Lokalizáció: méret-súlyozott periódusválasztás (harmonika ellen)

**Tanulság / javítás:**

- A több-periódusos lokalizáció néha egy FÉL-periódusú harmonikust választott,
  mert egy kis részterületen magasabb nyers sakktábla-korrelációt (checker) kapott
  a valódi, teljes táblánál → zagyva felismerés (a szabályossági ellenőrzés
  elkapta, de akkor is rossz).
- **Javítás:** a jelöltek közül nem a nyers checkert, hanem a MÉRETTEL súlyozott
  értéket (`checker × periódus`) maximalizáljuk. Így a valódi, nagy tábla nyer a
  fél-harmonikus felett (pl. 92×0,59=54 > 44×0,72=32). A `Board.confidence` marad
  a nyers checker (a „nincs tábla" kapuhoz). Ellenőrizve: a működő Feladványok-
  képen nincs regresszió (period=92, helyes állás).

## 2026-07-20 — Import: FEN/PGN beillesztés a szerkesztőben

**Döntések:**

- Az importra fókuszálunk (nem exportra), a betöltés (szerkesztő) felületén.
  Új „Beillesztés (FEN / PGN)" gomb a szerkesztőben → párbeszéd egy
  szövegmezővel (a vágólapról előtöltve). Auto-detektálás:
  - **FEN** (string-kódolás, egy állás) → a szerkesztő táblája feltöltődik
    (a felhasználó átnézheti, majd Betöltés). Részleges FEN (csak elhelyezés)
    kiegészül alapértékekkel.
  - **PGN** (teljes játszma) → a meglévő `importPgn`/`San.import` betölti a
    játszmát, a szerkesztő bezárul.
- A FEN a szerkesztő egyszerűsített állapotába tölt (tábla + ki lép); a
  sáncjog/en passant nem őrződik meg (állásbeállításnál ritkán kell).

**Ellenőrzés:** telefonon a `4k3/8/8/8/8/8/4P3/4K3` FEN pontosan betöltött
(fekete király e8, fehér gyalog e2, fehér király e1).

## 2026-07-20 — Import: fájlból (string-beillesztés helyett)

**Döntés:** A felhasználó fájlbetöltésben gondolkodik, nem stringekben. A
string-beillesztő párbeszéd helyett a szerkesztőben „Fájl betöltése (PGN / FEN)"
gomb → rendszer fájlválasztó (`OpenDocument`, `*/*`). A fájl tartalmát beolvassa
(`importFile`), majd az `importText` auto-detektál: PGN → teljes játszma, FEN →
a szerkesztő táblája. Telefonon igazolva: `test.pgn` (Ruy Lopez) betöltött a
lépésgráffal; a FEN-út korábban igazolva.
