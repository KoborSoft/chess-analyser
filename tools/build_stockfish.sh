#!/bin/bash
# Stockfish fordítása Androidra (arm64-v8a), make nélkül, közvetlen clang++ hívással.
set -e
TC=/home/kobor42/tools/android-ndk-r27c/toolchains/llvm/prebuilt/linux-x86_64/bin
SRC=/home/kobor42/tools/Stockfish-sf_17.1/src
JNILIBS=/home/kobor42/projects/sakk-pelda2/app/src/main/jniLibs
cd $SRC

echo "=== NNUE hálók letöltése ==="
for NET in nn-1c0000000000.nnue nn-37f18f62d772.nnue; do
  if [ ! -f $NET ]; then
    curl -sL -o $NET "https://data.stockfishchess.org/nn/$NET"
  fi
  ls -la $NET | awk '{print $9, $5, "bajt"}'
done

echo "=== arm64-v8a fordítás ==="
$TC/aarch64-linux-android24-clang++ \
  -std=c++17 -O3 -DNDEBUG -DUSE_PTHREADS -DIS_64BIT -DUSE_POPCNT -DUSE_NEON=8 \
  -fno-exceptions -fPIE \
  benchmark.cpp bitboard.cpp engine.cpp evaluate.cpp main.cpp memory.cpp \
  misc.cpp movegen.cpp movepick.cpp position.cpp score.cpp search.cpp \
  thread.cpp timeman.cpp tt.cpp tune.cpp uci.cpp ucioption.cpp \
  nnue/features/half_ka_v2_hm.cpp nnue/network.cpp nnue/nnue_accumulator.cpp \
  nnue/nnue_misc.cpp syzygy/tbprobe.cpp \
  -o stockfish-arm64 -static-libstdc++ -pie -lm 2>&1 | tail -10

$TC/llvm-strip stockfish-arm64
mkdir -p $JNILIBS/arm64-v8a
cp stockfish-arm64 $JNILIBS/arm64-v8a/libstockfish.so
echo "arm64 KESZ: $(stat -c%s $JNILIBS/arm64-v8a/libstockfish.so) bajt"
echo "=== STOCKFISH BUILD VEGE ==="
