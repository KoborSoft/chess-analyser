"""Tábla-lokalizáció prototípus digitális screenshotra (OpenCV nélkül, numpy).

Ötlet: egy tengely-igazított sakktábla a világos/sötét mezők miatt szabályos,
egyenlő közű függőleges és vízszintes éleket ad. Az |x-gradiens| sorösszege
egy 1D jel, aminek 9 egyenlő közű csúcsa van a rács-vonalaknál (8 mező).
Autokorrelációval megkeressük a mezőméretet, majd a legjobb rács-igazítást.
"""
import sys, numpy as np
from PIL import Image

def to_gray(img):
    a = np.asarray(img.convert("RGB"), np.float32)
    return 0.299*a[:,:,0] + 0.587*a[:,:,1] + 0.114*a[:,:,2]

def grad_profiles(g):
    gx = np.abs(np.diff(g, axis=1)).sum(axis=0)   # x-menti él-profil (hossz W-1)
    gy = np.abs(np.diff(g, axis=0)).sum(axis=1)    # y-menti él-profil (hossz H-1)
    return gx, gy

def best_period(prof, pmin, pmax):
    """A legjobb periódus autokorrelációval a [pmin,pmax] tartományban."""
    p = prof - prof.mean()
    best, bestp = -1e18, pmin
    for period in range(pmin, pmax+1):
        s = 0.0; n = 0
        for k in range(1, 8):
            shift = period*k
            if shift >= len(p): break
            s += float(np.dot(p[:-shift], p[shift:])); n += 1
        if n >= 3 and s/n > best:
            best, bestp = s/n, period
    return bestp

def find_offset(prof, period, extent):
    """A legjobb kezdő-eltolás: 9 rács-vonal (0..8*period) összege maximális."""
    best, besto = -1e18, 0
    lo = 0
    for off in range(0, len(prof)-extent+1):
        idxs = (off + np.round(np.arange(9)*period).astype(int))
        idxs = idxs[idxs < len(prof)]
        val = prof[idxs].sum()
        if val > best: best, besto = val, off
    return besto

def locate_board(img):
    g = to_gray(img); H, W = g.shape
    gx, gy = grad_profiles(g)
    pmax = min(W, H)//8
    pmin = max(8, min(W, H)//40)
    per = best_period(gx, pmin, pmax)
    per2 = best_period(gy, pmin, pmax)
    period = (per+per2)//2 or per
    extent = period*8
    if extent >= W or extent >= H:   # a tábla nagyobb mint a kép? vágjuk vissza
        period = min(W, H)//8; extent = period*8
    ox = find_offset(gx, period, extent)
    oy = find_offset(gy, period, extent)
    return ox, oy, period

def slice_squares(img, ox, oy, period):
    """8×8 mező kivágása, mindegyik 32×32 RGB, 0..1 (fehér-alul nézet)."""
    out = np.zeros((64,3,32,32), np.float32)
    rgb = img.convert("RGB")
    k = 0
    for r in range(8):          # r=0 felső sor
        for c in range(8):
            box = (ox+c*period, oy+r*period, ox+(c+1)*period, oy+(r+1)*period)
            sq = rgb.crop(box).resize((32,32))
            out[k] = np.asarray(sq, np.float32).transpose(2,0,1)/255.0
            k += 1
    return out

if __name__ == "__main__":
    import onnxruntime as ort
    CLASSES = ["bB","bK","bN","bP","bQ","bR","wB","wK","wN","wP","wQ","wR","xx"]
    FEN = {"bB":"b","bK":"k","bN":"n","bP":"p","bQ":"q","bR":"r",
           "wB":"B","wK":"K","wN":"N","wP":"P","wQ":"Q","wR":"R","xx":"."}
    sess = ort.InferenceSession("pieces.onnx", providers=["CPUExecutionProvider"])
    for path in sys.argv[1:]:
        img = Image.open(path)
        ox, oy, period = locate_board(img)
        print(f"\n=== {path}  ({img.size[0]}x{img.size[1]}) ===")
        print(f"tábla: x={ox} y={oy} mezőméret={period} -> {period*8}x{period*8}px")
        X = slice_squares(img, ox, oy, period)
        logits = sess.run(None, {"input": X})[0]
        e = np.exp(logits-logits.max(1,keepdims=True)); prob = e/e.sum(1,keepdims=True)
        pred = prob.argmax(1); conf = prob.max(1)
        board = [FEN[CLASSES[p]] for p in pred]
        print("Felismert állás (felső sor elöl):")
        for r in range(8):
            row = "".join(board[r*8:r*8+8])
            cf  = " ".join(f"{conf[r*8+c]:.2f}" for c in range(8))
            print(f"  {row}   | {cf}")
        print(f"átlagos konfidencia: {conf.mean():.3f}  | min: {conf.min():.3f}")
