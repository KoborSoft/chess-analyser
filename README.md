# Chess Analyser

Android sakkalkalmazás (Kotlin + Jetpack Compose): játék gép vagy másik ember
ellen, folyamatos elemzés Stockfish-motorral, lépésgráf elágazásokkal, sakkóra,
mentés/betöltés, valamint **offline állás-felismerés képernyőképről** (beépített
CNN, hálózat és API-kulcs nélkül).

An Android chess app (Kotlin + Jetpack Compose): play against the engine or
another person, continuous Stockfish analysis, a branching move graph, a clock,
save/load, and **offline position recognition from screenshots** (on-device CNN,
no network or API key).

## Modulok / Modules

- `core` — tiszta Kotlin (JVM) sakklogika, Androidtól független (lépésgenerálás,
  SAN/PGN, perft-hitelesítve). Pure-Kotlin chess logic, independent of Android.
- `app` — Android felület, motorillesztés, felismerő. Android UI, engine
  integration, recognizer.

## Build

Android Studio (vagy Gradle) + Android SDK. A `core` modul JVM-en tesztelhető
(perft): `./gradlew :core:test`. Az `app` modulhoz Android SDK kell.

A **Stockfish natív binárist** (`app/src/main/jniLibs/arm64-v8a/libstockfish.so`)
a repó nem tartalmazza (bináris, nem forrás). A `tools/build_stockfish.sh`
szkript állítja elő az Android NDK-val (a Stockfish forrásából). Enélkül az app
a beépített tartalék motorral (negamax) fut. / The Stockfish native binary is not
committed (it's a binary, not source); build it with `tools/build_stockfish.sh`
using the Android NDK; without it the app falls back to the built-in engine.

## Licenc / License

Az alkalmazás a **Stockfish** sakkmotort használja (GNU GPL v3), ezért a teljes
alkalmazás a **GNU General Public License v3** alatt áll — lásd a [LICENSE](LICENSE)
fájlt. A forráskód közzététele a GPLv3 kötelezettsége.

This application bundles the **Stockfish** engine (GNU GPL v3), so the whole app
is licensed under the **GNU General Public License v3** — see [LICENSE](LICENSE).

### Harmadik felek / Third-party

- **Stockfish** — sakkmotor, GNU GPL v3 — https://stockfishchess.org
- **chess-cv** (S1M0N38) — a bábufelismerő CNN modellje, MIT License —
  https://github.com/S1M0N38/chess-cv . A modell ONNX formátumban beépítve
  (`app/src/main/assets/pieces.onnx`), a készüléken fut (ONNX Runtime).
- **ONNX Runtime** (Microsoft) — MIT License.

A modell-export és a felismerő-prototípus szkriptjei: `tools/`.
