"""S1M0N38/chess-cv (MLX, MIT) pieces.safetensors -> ONNX konverzió és validálás.

Lépések:
  1) numpy NHWC referencia-forward (az MLX SimpleCNN hű átirata) -> igazságforrás
  2) torch NCHW modell a helyesen átforgatott súlyokkal, egyezés-ellenőrzés a numpy-val
  3) ONNX export, ORT == torch ellenőrzés
  4) szemantikai teszt: ismert címkéjű mezők renderelése -> pontosság
"""
import os, glob, numpy as np
from safetensors.numpy import load_file
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.join(HERE, "chess-cv")
WPATH = "/home/kobor42/.cache/huggingface/hub/models--S1M0N38--chess-cv/snapshots/aa44d9b5d272cf020a42689694975a6e6d66f705/pieces.safetensors"
CLASSES = ["bB","bK","bN","bP","bQ","bR","wB","wK","wN","wP","wQ","wR","xx"]

W = load_file(WPATH)

# ---------- 1) numpy NHWC referencia (MLX szemantika) ----------
def conv_nhwc(x, w, b, pad=1):
    # x:(B,H,W,I)  w:(O,kH,kW,I)  -> (B,H,W,O), stride1, same-pad, cross-correlation
    B,H,Wd,I = x.shape; O,kH,kW,_ = w.shape
    xp = np.pad(x, ((0,0),(pad,pad),(pad,pad),(0,0)))
    out = np.zeros((B,H,Wd,O), np.float32)
    for i in range(kH):
        for j in range(kW):
            patch = xp[:, i:i+H, j:j+Wd, :]          # (B,H,W,I)
            out += np.tensordot(patch, w[:,i,j,:], axes=([3],[1]))  # (B,H,W,O)
    return out + b

def maxpool2(x):
    B,H,Wd,C = x.shape
    x = x[:, :H//2*2, :Wd//2*2, :].reshape(B, H//2,2, Wd//2,2, C)
    return x.max(axis=(2,4))

def relu(x): return np.maximum(x, 0)

def forward_np(x):  # x:(B,32,32,3) 0..1
    x = maxpool2(relu(conv_nhwc(x, W["conv1.weight"], W["conv1.bias"])))
    x = maxpool2(relu(conv_nhwc(x, W["conv2.weight"], W["conv2.bias"])))
    x = maxpool2(relu(conv_nhwc(x, W["conv3.weight"], W["conv3.bias"])))  # (B,4,4,64)
    B = x.shape[0]
    x = x.reshape(B, -1)                              # HWC flatten
    x = relu(x @ W["fc1.weight"].T + W["fc1.bias"])
    return x @ W["fc2.weight"].T + W["fc2.bias"]

# ---------- 2) torch NCHW modell ----------
import torch, torch.nn as nn

class SimpleCNN(nn.Module):
    def __init__(s, n=13):
        super().__init__()
        s.conv1 = nn.Conv2d(3,16,3,padding=1); s.conv2 = nn.Conv2d(16,32,3,padding=1)
        s.conv3 = nn.Conv2d(32,64,3,padding=1); s.pool = nn.MaxPool2d(2,2)
        s.fc1 = nn.Linear(1024,128); s.fc2 = nn.Linear(128,n)
    def forward(s, x):  # x:(B,3,32,32)
        x = s.pool(torch.relu(s.conv1(x)))
        x = s.pool(torch.relu(s.conv2(x)))
        x = s.pool(torch.relu(s.conv3(x)))            # (B,64,4,4)
        x = x.flatten(1)                              # CHW flatten
        x = torch.relu(s.fc1(x))
        return s.fc2(x)

m = SimpleCNN(); sd = {}
for c in ["conv1","conv2","conv3"]:
    sd[f"{c}.weight"] = torch.tensor(W[f"{c}.weight"].transpose(0,3,1,2).copy())  # OHWI->OIHW
    sd[f"{c}.bias"]   = torch.tensor(W[f"{c}.bias"])
# fc1 bemeneti oszlopok HWC(4,4,64) -> CHW(64,4,4) átrendezése
idx = np.zeros(1024, np.int64)
for cc in range(64):
    for hh in range(4):
        for ww in range(4):
            flat_t = cc*16 + hh*4 + ww          # torch CHW sorrend
            flat_m = hh*256 + ww*64 + cc        # mlx HWC sorrend
            idx[flat_t] = flat_m
sd["fc1.weight"] = torch.tensor(W["fc1.weight"][:, idx].copy())
sd["fc1.bias"]   = torch.tensor(W["fc1.bias"])
sd["fc2.weight"] = torch.tensor(W["fc2.weight"]); sd["fc2.bias"] = torch.tensor(W["fc2.bias"])
m.load_state_dict(sd); m.eval()

# egyezés-ellenőrzés numpy vs torch
rng = np.random.default_rng(0)
xt = rng.random((5,32,32,3)).astype(np.float32)
ref = forward_np(xt)
with torch.no_grad():
    out = m(torch.tensor(xt).permute(0,3,1,2)).numpy()
print("max |numpy - torch| =", np.abs(ref-out).max())
assert np.abs(ref-out).max() < 1e-3, "numpy/torch eltérés — hibás konverzió!"

# ---------- 3) ONNX export + ORT ellenőrzés ----------
onnx_path = os.path.join(HERE, "pieces.onnx")
torch.onnx.export(m, torch.tensor(xt).permute(0,3,1,2),
    onnx_path, input_names=["input"], output_names=["logits"],
    dynamic_axes={"input":{0:"batch"},"logits":{0:"batch"}}, opset_version=13)
import onnxruntime as ort
sess = ort.InferenceSession(onnx_path, providers=["CPUExecutionProvider"])
ort_out = sess.run(None, {"input": xt.transpose(0,3,1,2)})[0]
print("max |torch - onnx|  =", np.abs(out-ort_out).max())
assert np.abs(out-ort_out).max() < 2e-3
print("ONNX mentve:", onnx_path, "| méret:", os.path.getsize(onnx_path), "bytes")

# ---------- 4) szemantikai teszt: ismert mezők renderelése ----------
def softmax(z): e=np.exp(z-z.max(-1,keepdims=True)); return e/e.sum(-1,keepdims=True)
def render_square(board_png, piece_png, dark=False):
    # egyetlen valódi mező kivágása a tábláról (nem az egész tábla kicsinyítése)
    bd = Image.open(board_png).convert("RGBA")
    cell = bd.width // 8
    ox = cell if dark else 0            # (0,0)=világos, (cell,0)=sötét mező
    sq = bd.crop((ox, 0, ox+cell, cell)).resize((32,32))
    if piece_png:
        pc = Image.open(piece_png).convert("RGBA").resize((32,32))
        sq.alpha_composite(pc)
    return np.array(sq.convert("RGB"), np.float32)/255.0

piece_sets = sorted(glob.glob(os.path.join(REPO,"data/pieces/*")))[:6]
boards = sorted(glob.glob(os.path.join(REPO,"data/boards/*.png")))
# válasszunk pár egyszínű táblát (kis fájl = tömör szín) determinisztikusan
boards = [b for b in boards if os.path.getsize(b) < 2000][:4] or boards[:4]

tests, labels = [], []
for ps in piece_sets:
    for pc in CLASSES[:-1]:
        pcf = os.path.join(ps, pc+".png")
        if not os.path.exists(pcf): continue
        for bi, b in enumerate(boards):
            for dark in (False, True):     # világos és sötét mezőn is
                tests.append(render_square(b, pcf, dark)); labels.append(pc)
# üres mezők
for b in boards:
    for dark in (False, True):
        tests.append(render_square(b, None, dark)); labels.append("xx")

X = np.stack(tests).transpose(0,3,1,2).astype(np.float32)
logits = sess.run(None, {"input": X})[0]
prob = softmax(logits); pred = prob.argmax(1); conf = prob.max(1)
correct = sum(CLASSES[p]==l for p,l in zip(pred,labels))
print(f"\nSzemantikai teszt: {correct}/{len(labels)} helyes ({100*correct/len(labels):.1f}%)")
print(f"Átlagos konfidencia (helyes): {np.mean([c for p,l,c in zip(pred,labels,conf) if CLASSES[p]==l]):.3f}")
wrong = [(l, CLASSES[p], f'{c:.2f}') for p,l,c in zip(pred,labels,conf) if CLASSES[p]!=l]
if wrong: print("Hibák (valós→tipp,konf):", wrong[:15])
