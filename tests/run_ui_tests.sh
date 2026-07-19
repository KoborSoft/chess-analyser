#!/bin/bash
# Fekete dobozos UI-tesztsor a com.koborsoft.chessanalyser Android sakkalkalmazáshoz.
#
# Futtatás:  tests/run_ui_tests.sh [T01 T05 ...]     (paraméter nélkül: minden)
# Előfeltétel: adb-vel csatlakoztatott eszköz, telepített és indítható app.
# Az adb elérési útja az ADB környezeti változóval adható meg (különben PATH).
#
# A teszt kizárólag a felhasználói felületen keresztül dolgozik (input tap,
# uiautomator dump, screencap). A futás végén géppel olvasható összegzést ír:
# soronként "<teszt> SIKERES" vagy "<teszt> SIKERTELEN: <ok>".
# Bizonyítékok (képernyőképek): tests/evidence/

set -u
TESTS="$(cd "$(dirname "$0")" && pwd)"
EV="$TESTS/evidence"
mkdir -p "$EV"
ADB="${ADB:-$(command -v adb || echo adb)}"
PKG=com.koborsoft.chessanalyser
ACT=com.koborsoft.chessanalyser/.MainActivity
TMPD="$(mktemp -d)"
trap 'rm -rf "$TMPD"' EXIT

declare -a SUMMARY=()
declare -A SQX=() SQY=()
CUR=""

log() { echo "[$(date +%H:%M:%S)] $*"; }

# ---------- alap műveletek ----------

dump() {
  local i
  for i in 1 2 3 4 5; do
    if "$ADB" shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; then
      "$ADB" shell cat /sdcard/ui.xml >"$TMPD/ui.xml" 2>/dev/null
      [ -s "$TMPD/ui.xml" ] && return 0
    fi
    sleep 1
  done
  return 1
}

q() { python3 "$TESTS/ui_helper.py" "$@" <"$TMPD/ui.xml"; }

tap() { "$ADB" shell input tap "$1" "$2"; }

shot() { "$ADB" exec-out screencap -p >"$EV/${CUR}_$1.png"; }

# A tábla mezőinek koordinátáit egyszer olvassuk ki játékonként.
calibrate() {
  dump || return 1
  SQX=(); SQY=()
  local sq x y
  while read -r sq x y; do SQX[$sq]=$x; SQY[$sq]=$y; done < <(q allsq) || return 1
  [ "${#SQX[@]}" = 64 ]
}

tap_sq() { tap "${SQX[$1]}" "${SQY[$1]}"; sleep 0.35; }

mv() { tap_sq "$1"; tap_sq "$2"; sleep 0.4; }

# Lépés ellenőrzéssel és egyszeri újrapróbálással (szabálytalan lépéskísérlet
# után a kijelölés állapota bizonytalan, ezért az első koppintáspár elcsúszhat).
mv_verify() { # $1 honnan, $2 hova, $3 várt bábu
  mv "$1" "$2"; dump
  [ "$(piece_nodump "$2")" = "$3" ] && return 0
  mv "$1" "$2"; dump
  [ "$(piece_nodump "$2")" = "$3" ]
}

# wait_text <regex> <timeout_s> [ymin ymax] – feltételre várás időkorláttal
wait_text() {
  local re="$1" to="$2" ymin="${3:-}" ymax="${4:-}" t=0
  while [ "$t" -lt "$to" ]; do
    [ "$to" -ge 15 ] && keep_awake >/dev/null 2>&1
    dump || return 1
    if [ -n "$ymin" ]; then
      q find "$re" "$ymin" "$ymax" >/dev/null && return 0
    else
      q find "$re" >/dev/null && return 0
    fi
    sleep 1; t=$((t + 2))
  done
  return 1
}

# Állapotsor/lépéslista zóna (a fejléc "Sakk" címét és a felső snackbart kizárja)
SY1=400; SY2=2250
status_has() { dump && q find "$1" $SY1 $SY2 >/dev/null; }

piece() { dump >/dev/null 2>&1; q piece "$1"; }
piece_nodump() { q piece "$1"; }

ok() { log "$CUR SIKERES"; SUMMARY+=("$CUR SIKERES"); }
ko() {
  local msg="$1"
  if "$ADB" shell 'dumpsys window' 2>/dev/null | grep -q 'isKeyguardShowing=true'; then
    msg="$msg [KÉPERNYŐZÁR szakította meg — az eredmény érvénytelen]"
  fi
  shot "HIBA"
  log "$CUR SIKERTELEN: $msg (bizonyíték: $EV/${CUR}_HIBA.png)"
  SUMMARY+=("$CUR SIKERTELEN: $msg")
}

# ---------- új játék párbeszéd ----------

chip() { # látható opciógomb megnyomása pontos szövegre
  local xy
  dump || return 1
  xy=$(q find "^$1\$") || return 1
  tap $xy; sleep 0.4
}

chip_row() { # görgethető sorban lévő opció ($1 cél, $2 a sor bármely elemére illő regex)
  local i xy row rowy
  for i in 1 2 3 4 5 6; do
    dump || return 1
    if xy=$(q find "^$1\$"); then tap $xy; sleep 0.4; return 0; fi
    row=$(q find "$2") || return 1
    rowy=${row#* }
    if [ "$i" -le 2 ]; then
      "$ADB" shell input swipe 300 "$rowy" 1050 "$rowy" 350   # vissza az elejére
    else
      "$ADB" shell input swipe 1050 "$rowy" 300 "$rowy" 350   # tovább jobbra
    fi
    sleep 0.7
  done
  return 1
}

ELO_ROW='^(800|1100|1400|1700|2000|2300|2600|2850)$'
TIME_ROW='^(Óra nélkül|1\+0|3\+2|5\+0|10\+0|15\+10)$'

new_game_once() { # $1 ellenfél, $2 szín(-: kihagy), $3 Élő(-: kihagy), $4 időkontroll(-: kihagy)
  local xy
  dump || return 1
  # ha egy korábbi próbálkozás dialógusa nyitva maradt, azt használjuk
  if ! q find '^Ellenfél$' >/dev/null; then
    xy=$(q find '^Új játék$') || return 1
    tap $xy
    wait_text '^Ellenfél$' 8 || return 1
  fi
  chip "$1" || return 1
  [ "$2" != - ] && { chip "$2" || return 1; }
  [ "$3" != - ] && { chip_row "$3" "$ELO_ROW" || return 1; }
  [ "$4" != - ] && { chip_row "$4" "$TIME_ROW" || return 1; }
  dump || return 1
  xy=$(q find '^Indítás$') || return 1
  tap $xy
  wait_text 'lép' 10 $SY1 $SY2 || return 1
  # a dialógusnak be kellett záródnia
  q find '^Ellenfél$' >/dev/null && return 1
  calibrate
}

new_game() {
  local try
  for try in 1 2 3; do
    ensure_unlocked || return 1
    new_game_once "$@" && return 0
    log "új játék indítása nem sikerült (${try}. próbálkozás), újrapróbálás"
    app_foreground
  done
  return 1
}

app_foreground() {
  "$ADB" shell input keyevent KEYCODE_WAKEUP
  "$ADB" shell wm dismiss-keyguard >/dev/null 2>&1
  "$ADB" shell am start -n "$ACT" >/dev/null 2>&1
  sleep 2
}

# a kijelző ne aludjon el teszt közben (a SHIFT önmagában semmit sem csinál,
# de felhasználói aktivitásnak számít)
keep_awake() {
  "$ADB" shell input keyevent KEYCODE_WAKEUP
  "$ADB" shell input keyevent KEYCODE_SHIFT_LEFT
}

# Ha a készülék lezáródott (a felhasználó lezárta), feloldásra várunk.
# A deviceLocked jelzőt figyeljük: a wm dismiss-keyguard átmenetileg
# eltüntetheti a keyguardot, de biztonságos zárnál nem old fel valójában.
ensure_unlocked() {
  local t=0
  while ! "$ADB" shell 'dumpsys trust' 2>/dev/null | grep -q 'deviceLocked=0'; do
    [ $((t % 30)) = 0 ] && log "A KÉSZÜLÉK LEZÁRVA — kérem, oldja fel a folytatáshoz"
    sleep 15; t=$((t + 15))
    [ "$t" -ge 300 ] && { log "nem sikerült feloldani 5 perc alatt"; return 1; }
  done
  return 0
}

# ---------- forgatókönyvek ----------

t01_uj_jatek_opciok() {
  CUR=T01_uj_jatek_opciok
  local xy missing=""
  dump && xy=$(q find '^Új játék$') || { ko "nincs Új játék gomb"; return; }
  tap $xy
  wait_text '^Ellenfél$' 8 || { ko "nem nyílik meg az Új játék párbeszéd"; return; }
  local t
  for t in 'Gép' 'Két játékos' 'Világos' 'Sötét'; do
    q find "^$t\$" >/dev/null || missing="$missing $t"
  done
  shot "dialogus"
  # görgethető sorok: minden Élő-szint és időkontroll meglétének ellenőrzése
  local rowy row
  for t in 800 1100 1400 1700 2000 2300 2600 2850; do
    chip_row "$t" "$ELO_ROW" >/dev/null 2>&1 || missing="$missing Élő:$t"
  done
  for t in 'Óra nélkül' '1\+0' '3\+2' '5\+0' '10\+0' '15\+10'; do
    chip_row "$t" "$TIME_ROW" >/dev/null 2>&1 || missing="$missing Idő:$t"
  done
  shot "dialogus_gorgetve"
  dump; xy=$(q find '^Mégse$') && tap $xy; sleep 0.6
  if [ -z "$missing" ]; then ok; else ko "hiányzó opciók:$missing"; fi
}

t02_alapjatek_lepes() {
  CUR=T02_alapjatek_lepes
  new_game 'Két játékos' - - 'Óra nélkül' || { ko "nem indul új játék"; return; }
  dump
  local err=""
  [ "$(piece_nodump e1)" = '♔' ] || err="e1!=♔ "
  [ "$(piece_nodump d1)" = '♕' ] || err="${err}d1!=♕ "
  [ "$(piece_nodump e8)" = '♚' ] || err="${err}e8!=♚ "
  [ "$(piece_nodump a1)" = '♖' ] || err="${err}a1!=♖ "
  [ "$(piece_nodump h8)" = '♜' ] || err="${err}h8!=♜ "
  [ "$(piece_nodump e2)" = '♙' ] || err="${err}e2!=♙ "
  [ "$(piece_nodump e7)" = '♟' ] || err="${err}e7!=♟ "
  [ "$(q board | wc -l)" = 32 ] || err="${err}nem 32 bábu "
  q find '^Világos lép$' $SY1 $SY2 >/dev/null || err="${err}nincs 'Világos lép' "
  mv e2 e4
  dump
  [ "$(piece_nodump e4)" = '♙' ] || err="${err}e2-e4 nem hajtódott végre "
  q find '^Sötét lép$' $SY1 $SY2 >/dev/null || err="${err}nincs 'Sötét lép' e4 után "
  q find '1\. e4' $SY1 $SY2 >/dev/null || err="${err}lépéslistában nincs '1. e4' "
  shot "e4_utan"
  if [ -z "$err" ]; then ok; else ko "$err"; fi
}

t03_lepesjeloles() {
  CUR=T03_lepesjeloles
  new_game 'Két játékos' - - 'Óra nélkül' || { ko "nem indul új játék"; return; }
  local r3 r4 r5
  r3=$(q sqrect e3); r4=$(q sqrect e4); r5=$(q sqrect d5)
  [ -n "$r3" ] && [ -n "$r4" ] || { ko "nem található mező-geometria"; return; }
  "$ADB" exec-out screencap >"$TMPD/before.raw"
  tap_sq e2; sleep 0.5
  "$ADB" exec-out screencap >"$TMPD/after.raw"
  shot "e2_kijelolve"
  local b a
  b=$(python3 "$TESTS/pixel.py" "$TMPD/before.raw" $r3 $r4 $r5)
  a=$(python3 "$TESTS/pixel.py" "$TMPD/after.raw" $r3 $r4 $r5)
  local b3 b4 b5 a3 a4 a5
  b3=$(sed -n 1p <<<"$b"); b4=$(sed -n 2p <<<"$b"); b5=$(sed -n 3p <<<"$b")
  a3=$(sed -n 1p <<<"$a"); a4=$(sed -n 2p <<<"$a"); a5=$(sed -n 3p <<<"$a")
  tap_sq e2   # kijelölés megszüntetése
  if { [ "$b3" != "$a3" ] || [ "$b4" != "$a4" ]; } && [ "$b5" = "$a5" ]; then
    ok
  else
    ko "e2 kijelölésekor az e3/e4 mező nem kap jelölést (e3:$b3->$a3 e4:$b4->$a4 d5:$b5->$a5)"
  fi
}

t04_szabalytalan_es_sakk() {
  CUR=T04_szabalytalan_es_sakk
  new_game 'Két játékos' - - 'Óra nélkül' || { ko "nem indul új játék"; return; }
  local err=""
  mv e2 e5    # szabálytalan: gyalog 3 mezőt
  dump
  [ "$(piece_nodump e5)" = '.' ] && [ "$(piece_nodump e2)" = '♙' ] || err="e2-e5 szabálytalan lépés végrehajtódott "
  q find '^Világos lép$' $SY1 $SY2 >/dev/null || err="${err}szabálytalan lépés után nem Világos lép "
  mv_verify e2 e4 '♙' || err="${err}e4 nem hajtódott végre "
  mv_verify d7 d5 '♟' || err="${err}d5 nem hajtódott végre "
  mv_verify f1 b5 '♗' || err="${err}Bb5 nem hajtódott végre "
  q find '[Ss]akk' $SY1 $SY2 >/dev/null || err="${err}Bb5+ után nincs sakk kijelzés "
  q find 'Bb5\+' $SY1 $SY2 >/dev/null || err="${err}lépéslistában nincs Bb5+ "
  shot "sakk_allapot"
  mv g8 f6    # szabálytalan: nem hárítja a sakkot
  dump
  [ "$(piece_nodump f6)" = '.' ] || err="${err}sakkban hagyó Nf6 lépés végrehajtódott "
  mv_verify c7 c6 '♟' || err="${err}c6 hárítás nem hajtódott végre "
  if [ -z "$err" ]; then ok; else ko "$err"; fi
}

t05_matt() {
  CUR=T05_matt
  new_game 'Két játékos' - - 'Óra nélkül' || { ko "nem indul új játék"; return; }
  mv f2 f3; mv e7 e5; mv g2 g4; mv d8 h4   # susztermatt
  local err=""
  wait_text '[Mm]att' 6 $SY1 $SY2 || err="nincs matt kijelzés "
  shot "matt"
  mv a2 a3    # játék vége után nem lehet lépni
  dump
  [ "$(piece_nodump a3)" = '.' ] || err="${err}matt után még lehet lépni "
  if [ -z "$err" ]; then ok; else ko "$err"; fi
}

t06_patt() {
  CUR=T06_patt
  new_game 'Két játékos' - - 'Óra nélkül' || { ko "nem indul új játék"; return; }
  # Sam Loyd 10 lépéses patt-játszmája
  mv e2 e3; mv a7 a5; mv d1 h5; mv a8 a6; mv h5 a5; mv h7 h5
  mv a5 c7; mv a6 h6; mv h2 h4; mv f7 f6; mv c7 d7; mv e8 f7
  mv d7 b7; mv d8 d3; mv b7 b8; mv d3 h7; mv b8 c8; mv f7 g6; mv c8 e6
  shot "patt"
  if wait_text '[Pp]att|[Dd]öntetlen' 6 $SY1 $SY2; then ok; else ko "nincs patt/döntetlen kijelzés"; fi
}

t07_sanc() {
  CUR=T07_sanc
  new_game 'Két játékos' - - 'Óra nélkül' || { ko "nem indul új játék"; return; }
  mv e2 e4; mv e7 e5; mv g1 f3; mv b8 c6; mv f1 c4; mv f8 c5
  mv e1 g1   # rövid sánc
  dump
  local err=""
  [ "$(piece_nodump g1)" = '♔' ] || err="király nem g1-en "
  [ "$(piece_nodump f1)" = '♖' ] || err="${err}bástya nem f1-en "
  q find 'O-O' $SY1 $SY2 >/dev/null || err="${err}lépéslistában nincs O-O "
  shot "sanc"
  if [ -z "$err" ]; then ok; else ko "$err"; fi
}

t08_en_passant() {
  CUR=T08_en_passant
  new_game 'Két játékos' - - 'Óra nélkül' || { ko "nem indul új játék"; return; }
  mv e2 e4; mv a7 a6; mv e4 e5; mv d7 d5
  mv e5 d6   # en passant ütés
  dump
  local err=""
  [ "$(piece_nodump d6)" = '♙' ] || err="en passant ütés nem hajtódott végre (d6 üres) "
  [ "$(piece_nodump d5)" = '.' ] || err="${err}a leütött gyalog d5-ön maradt "
  shot "en_passant"
  if [ -z "$err" ]; then ok; else ko "$err"; fi
}

t09_promocio() {
  CUR=T09_promocio
  new_game 'Két játékos' - - 'Óra nélkül' || { ko "nem indul új játék"; return; }
  mv a2 a4; mv b7 b5; mv a4 b5; mv h7 h6; mv b5 b6; mv h6 h5
  mv b6 b7; mv h5 h4
  mv b7 a8   # ütés + gyalogátváltozás
  local err=""
  # A bábuválasztó "Átváltozás" címmel és bábu-glifekkel jelenik meg.
  if wait_text 'Átváltozás|Huszár|Knight' 6; then
    local xy
    xy=$(q find '♘|Huszár|Knight')
    shot "valaszto"
    tap $xy; sleep 0.8
  else
    shot "nincs_valaszto"
    err="nem jelent meg bábuválasztó "
  fi
  dump
  [ "$(piece_nodump a8)" = '♘' ] || err="${err}a8-on nem huszár áll: '$(piece_nodump a8)' "
  q find '=N' $SY1 $SY2 >/dev/null || err="${err}lépéslistában nincs =N "
  shot "promocio_utan"
  if [ -z "$err" ]; then ok; else ko "$err"; fi
}

t10_visszavonas_hh() {
  CUR=T10_visszavonas_hh
  new_game 'Két játékos' - - 'Óra nélkül' || { ko "nem indul új játék"; return; }
  mv e2 e4
  dump
  [ "$(piece_nodump e4)" = '♙' ] || { ko "előkészítő lépés nem sikerült"; return; }
  local xy
  xy=$(q find '^Visszavonás$') || { ko "nincs Visszavonás gomb"; return; }
  tap $xy; sleep 0.8
  dump
  local err=""
  [ "$(piece_nodump e2)" = '♙' ] && [ "$(piece_nodump e4)" = '.' ] || err="a lépés nem vonódott vissza "
  q find '^Világos lép$' $SY1 $SY2 >/dev/null || err="${err}nem Világos lép "
  q find '1\. e4' $SY1 $SY2 >/dev/null && err="${err}a lépéslista nem ürült "
  shot "undo_utan"
  if [ -z "$err" ]; then ok; else ko "$err"; fi
}

t11_haromszori_ismetles() {
  CUR=T11_haromszori_ismetles
  new_game 'Két játékos' - - 'Óra nélkül' || { ko "nem indul új játék"; return; }
  mv g1 f3; mv g8 f6; mv f3 g1; mv f6 g8
  mv g1 f3; mv g8 f6; mv f3 g1; mv f6 g8
  shot "ismetles"
  if wait_text 'smétl|[Dd]öntetlen' 6 $SY1 $SY2; then ok; else ko "háromszori ismétlés után nincs döntetlen"; fi
}

t12_gep_vilagossal() {
  CUR=T12_gep_vilagossal
  new_game 'Gép' 'Világos' 800 'Óra nélkül' || { ko "nem indul új játék"; return; }
  local err=""
  q find '^Világos lép$' $SY1 $SY2 >/dev/null || err="induláskor nem Világos lép "
  mv e2 e4
  # a gép (sötét) válaszára várunk: fekete bábu elhagyja a 7/8. sort
  local t=0 replied=1
  while [ $t -lt 30 ]; do
    keep_awake >/dev/null 2>&1
    dump
    if q board | grep -qE '^[a-h][1-6]=[♚♛♜♝♞♟]'; then replied=0; break; fi
    sleep 1; t=$((t + 2))
  done
  [ $replied = 0 ] || err="${err}a gép 30 mp alatt nem lépett "
  q find '^Világos lép$' $SY1 $SY2 >/dev/null || err="${err}gépi lépés után nem az emberé a kör "
  shot "gep_valaszolt"
  # visszavonás gép ellen: legfeljebb két gombnyomással álljon vissza az alapállás
  local xy i restored=1
  for i in 1 2; do
    dump; xy=$(q find '^Visszavonás$') || break
    tap $xy; sleep 1
    dump
    if [ "$(piece_nodump e4)" = '.' ] && [ "$(piece_nodump e2)" = '♙' ] \
       && ! q board | grep -qE '^[a-h][1-6]=[♚♛♜♝♞♟]'; then restored=0; break; fi
  done
  [ $restored = 0 ] || err="${err}visszavonás után nem állt vissza az alapállás "
  shot "undo_utan"
  if [ -z "$err" ]; then ok; else ko "$err"; fi
}

t13_gep_sotettel() {
  CUR=T13_gep_sotettel
  new_game 'Gép' 'Sötét' 2000 'Óra nélkül' || { ko "nem indul új játék"; return; }
  local err="" o1 o2 xy
  o1=$(q orient)
  # amíg a gép (világos) gondolkodik, a felületnek reagálnia kell: forgatás
  xy=$(q find '^Forgatás$') && { tap $xy; sleep 0.8; }
  dump; o2=$(q orient)
  [ "$o1" != "$o2" ] || err="a felület nem reagált (Forgatás) a gép gondolkodása alatt "
  # világos gépi megnyitó lépésre várunk
  local t=0 moved=1
  while [ $t -lt 30 ]; do
    keep_awake >/dev/null 2>&1
    dump
    if q board | grep -qE '^[a-h][3-6]=[♔♕♖♗♘♙]'; then moved=0; break; fi
    sleep 1; t=$((t + 2))
  done
  [ $moved = 0 ] || err="${err}a gép világossal 30 mp alatt nem lépett "
  q find '^Sötét lép$' $SY1 $SY2 >/dev/null || err="${err}gépi lépés után nem 'Sötét lép' "
  shot "gep_lepett"
  if [ -z "$err" ]; then ok; else ko "$err"; fi
}

t14_automatikus_mentes() {
  CUR=T14_automatikus_mentes
  new_game 'Két játékos' - - 'Óra nélkül' || { ko "nem indul új játék"; return; }
  mv e2 e4; mv e7 e5; mv g1 f3
  dump
  q find '2\. Nf3' $SY1 $SY2 >/dev/null || { ko "előkészítő lépések nem sikerültek"; return; }
  "$ADB" shell input keyevent 3; sleep 1.5          # HOME: kilépés
  "$ADB" shell am force-stop "$PKG"; sleep 1        # folyamat leállítása
  app_foreground
  wait_text 'lép' 10 $SY1 $SY2 || { ko "újraindítás után nem töltődik játék"; return; }
  calibrate
  local err=""
  [ "$(piece_nodump e4)" = '♙' ] || err="e4 gyalog hiányzik "
  [ "$(piece_nodump e5)" = '♟' ] || err="${err}e5 gyalog hiányzik "
  [ "$(piece_nodump f3)" = '♘' ] || err="${err}f3 huszár hiányzik "
  q find '2\. Nf3' $SY1 $SY2 >/dev/null || err="${err}lépéslista nem állt vissza "
  q find '^Sötét lép$' $SY1 $SY2 >/dev/null || err="${err}nem Sötét lép "
  shot "visszatoltve"
  if [ -z "$err" ]; then ok; else ko "$err"; fi
}

t15_pgn_megosztas() {
  CUR=T15_pgn_megosztas
  new_game 'Két játékos' - - 'Óra nélkül' || { ko "nem indul új játék"; return; }
  mv e2 e4; mv e7 e5
  dump
  local xy fx fy
  xy=$(q find '^Feladás$') || { ko "nincs Feladás gomb (viszonyítási pont)"; return; }
  fx=${xy% *}; fy=${xy#* }
  tap $((fx + 220)) "$fy"   # a Feladás melletti (felirat nélküli) megosztás gomb
  if wait_text 'Megosztás|Share' 8; then
    shot "megoszto_panel"
    "$ADB" shell input keyevent 4; sleep 1.2   # panel bezárása, sehova nem küldjük
    dump
    q find '^Forgatás$' >/dev/null || app_foreground
    ok
  else
    shot "nincs_panel"
    ko "a megosztó-panel nem jelent meg"
  fi
}

t16_ora_kijelzes() {
  CUR=T16_ora_kijelzes
  local err=""
  new_game 'Két játékos' - - '3\+2' || { ko "nem indul 3+2 játék"; return; }
  dump
  [ "$(q texts | grep -c '^3:00')" -ge 2 ] || err="3+2-nél nem 3:00-ról indul mindkét óra "
  shot "3plusz2"
  new_game 'Két játékos' - - '15\+10' || { ko "nem indul 15+10 játék"; return; }
  dump
  [ "$(q texts | grep -c '^15:00')" -ge 2 ] || err="${err}15+10-nél nem 15:00-ról indul mindkét óra "
  shot "15plusz10"
  if [ -z "$err" ]; then ok; else ko "$err"; fi
}

t17_forgatas() {
  CUR=T17_forgatas
  new_game 'Két játékos' - - 'Óra nélkül' || { ko "nem indul új játék"; return; }
  local o1 o2 o3 xy
  o1=$(q orient)
  xy=$(q find '^Forgatás$') || { ko "nincs Forgatás gomb"; return; }
  tap $xy; sleep 0.8; dump; o2=$(q orient)
  shot "forgatva"
  tap $xy; sleep 0.8; dump; o3=$(q orient)
  local err=""
  [ "$o1" = white ] || err="alapból nem világos van alul "
  [ "$o2" = black ] || err="${err}forgatás után nem fordult meg a tábla "
  [ "$o3" = white ] || err="${err}visszaforgatás nem működik "
  # a bábuknak a forgatás után is a helyükön kell lenniük
  calibrate
  [ "$(piece_nodump e1)" = '♔' ] || err="${err}forgatás után e1 nem ♔ "
  if [ -z "$err" ]; then ok; else ko "$err"; fi
}

t18_licencek() {
  CUR=T18_licencek
  dump
  local xy
  xy=$(q find '^Licencek$') || { ko "nincs Licencek gomb"; return; }
  tap $xy
  if wait_text 'Stockfish|GPL' 8; then
    shot "licencek"
    "$ADB" shell input keyevent 4; sleep 1
    dump; q find '^Forgatás$' >/dev/null || app_foreground
    ok
  else
    shot "nincs_licenc"
    "$ADB" shell input keyevent 4; sleep 1; app_foreground
    ko "a licencképernyőn nincs Stockfish/GPL információ"
  fi
}

t19_idotullepes() {
  CUR=T19_idotullepes
  new_game 'Két játékos' - - '1\+0' || { ko "nem indul 1+0 játék"; return; }
  dump
  [ "$(q texts | grep -c '^1:00')" -ge 2 ] || { ko "1+0-nál nem 1:00-ról indulnak az órák"; return; }
  mv e2 e4; mv e7 e5    # az óra biztosan járjon (világoson a sor)
  # legfeljebb 90 mp várakozás az időtúllépésre
  local t=0
  while [ $t -lt 90 ]; do
    keep_awake
    dump
    if q find '[Ii]dő|lejárt' $SY1 $SY2 >/dev/null; then
      shot "idotullepes"
      local err=""
      q find 'Sötét nyert' $SY1 $SY2 >/dev/null || err="nem Sötét nyert az időtúllépés után "
      mv a2 a3
      dump
      [ "$(piece_nodump a3)" = '.' ] || err="${err}időtúllépés után még lehet lépni "
      if [ -z "$err" ]; then ok; else ko "$err"; fi
      return
    fi
    sleep 3; t=$((t + 4))
  done
  shot "nincs_idotullepes"
  ko "az 1 perc letelte után nincs időtúllépés-kijelzés"
}

t20_angol_nyelv() {
  CUR=T20_angol_nyelv
  local orig
  orig=$("$ADB" shell cmd locale get-app-locales "$PKG" 2>/dev/null)
  log "eredeti app-nyelv: $orig"
  "$ADB" shell cmd locale set-app-locales "$PKG" --locales en-US
  "$ADB" shell am force-stop "$PKG"; sleep 1
  app_foreground
  local err=""
  wait_text 'New game|New Game' 10 || err="angol nyelven nincs 'New game' felirat "
  dump
  q find 'to move|moves|White|Black' >/dev/null || err="${err}nincs angol állapotszöveg "
  shot "angol"
  # visszaállítás magyarra (az app követi a rendszer nyelvét)
  "$ADB" shell cmd locale set-app-locales "$PKG" --locales ""
  "$ADB" shell am force-stop "$PKG"; sleep 1
  app_foreground
  wait_text 'Új játék' 10 || err="${err}visszaállítás után nincs magyar felirat "
  shot "magyar_vissza"
  if [ -z "$err" ]; then ok; else ko "$err"; fi
}

# ---------- futtatás ----------

ALL="t01_uj_jatek_opciok t02_alapjatek_lepes t03_lepesjeloles t04_szabalytalan_es_sakk \
t05_matt t06_patt t07_sanc t08_en_passant t09_promocio t10_visszavonas_hh \
t11_haromszori_ismetles t12_gep_vilagossal t13_gep_sotettel t14_automatikus_mentes \
t15_pgn_megosztas t16_ora_kijelzes t17_forgatas t18_licencek t19_idotullepes t20_angol_nyelv"

"$ADB" get-state >/dev/null 2>&1 || { echo "HIBA: nincs csatlakoztatott adb eszköz"; exit 2; }
app_foreground

if [ $# -gt 0 ]; then
  RUN=""
  for a in "$@"; do
    al=$(printf '%s' "$a" | tr '[:upper:]' '[:lower:]')
    for f in $ALL; do case "$f" in "$al"*) RUN="$RUN $f";; esac; done
  done
else
  RUN="$ALL"
fi

for f in $RUN; do
  log "=== $f ==="
  ensure_unlocked || { SUMMARY+=("$f SIKERTELEN: a készülék zárolva maradt"); continue; }
  keep_awake
  app_foreground
  "$f"
done

echo
echo "================ ÖSSZEGZÉS ================"
FAIL=0
for line in "${SUMMARY[@]}"; do
  echo "$line"
  case "$line" in *SIKERTELEN*) FAIL=1;; esac
done
exit $FAIL
