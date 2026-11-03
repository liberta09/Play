import os
import sys
import time
import subprocess
import re
import argparse
from pathlib import Path

def find_adb():
    local_app_data = os.environ.get("LOCALAPPDATA", "")
    default_sdk_adb = os.path.join(local_app_data, "Android", "Sdk", "platform-tools", "adb.exe")
    if os.path.exists(default_sdk_adb):
        return default_sdk_adb
    import shutil
    which_adb = shutil.which("adb")
    if which_adb:
        return which_adb
    return "adb"

ADB_BIN = find_adb()
DEVICE = "127.0.0.1:5555"

def run_adb(args, timeout=30):
    cmd = [ADB_BIN, "-s", DEVICE] + args
    res = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, timeout=timeout)
    return res.stdout

def parse_gfxinfo(raw_text):
    total_frames = None
    janky_count = None
    janky_pct = None
    p50 = None
    p90 = None
    p95 = None
    p99 = None

    m = re.search(r'Total frames rendered:\s+(\d+)', raw_text)
    if m:
        total_frames = int(m.group(1))

    m = re.search(r'Janky frames:\s+(\d+)\s+\(([\d.]+)%\)', raw_text)
    if m:
        janky_count = int(m.group(1))
        janky_pct = float(m.group(2))

    m = re.search(r'50th percentile:\s+(\d+)ms', raw_text)
    if m:
        p50 = int(m.group(1))

    m = re.search(r'90th percentile:\s+(\d+)ms', raw_text)
    if m:
        p90 = int(m.group(1))

    m = re.search(r'95th percentile:\s+(\d+)ms', raw_text)
    if m:
        p95 = int(m.group(1))

    m = re.search(r'99th percentile:\s+(\d+)ms', raw_text)
    if m:
        p99 = int(m.group(1))

    return {
        "total_frames": total_frames,
        "janky_count": janky_count,
        "janky_pct": janky_pct,
        "p50": p50,
        "p90": p90,
        "p95": p95,
        "p99": p99,
    }

def run_single_iteration(no_glow=False, no_images=False, label="run"):
    print(f"\n[{label}] Soguk acilis yapiliyor (no_glow={no_glow}, no_images={no_images})...")
    run_adb(["shell", "am", "force-stop", "com.kaan.play"])
    time.sleep(1.0)

    start_cmd = [
        "shell", "am", "start", "-n", "com.kaan.play/.MainActivity",
        "--es", "play_announcements_endpoint", "http://127.0.0.1:9999/none"
    ]
    if no_glow:
        start_cmd.extend(["--ez", "play_flag_no_glow", "true"])
    if no_images:
        start_cmd.extend(["--ez", "play_flag_no_images", "true"])

    run_adb(start_cmd)

    print(f"[{label}] Ana sayfanin yuklenmesi bekleniyor (sabit 20 sn)...")
    time.sleep(20.0)

    print(f"[{label}] Ilk karta geciliyor...")
    # Hero uzerinden ilk kart satirina gec
    run_adb(["shell", "input", "keyevent", "DPAD_DOWN"])
    time.sleep(0.5)

    print(f"[{label}] gfxinfo sifirlaniyor...")
    run_adb(["shell", "dumpsys", "gfxinfo", "com.kaan.play", "reset"])

    print(f"[{label}] D-pad senaryosu baslatiliyor (12x SAG, 6x ASAGI, 12x SOL, 6x YUKARI)...")
    for i in range(12):
        run_adb(["shell", "input", "keyevent", "DPAD_RIGHT"])
        time.sleep(0.4)

    for i in range(6):
        run_adb(["shell", "input", "keyevent", "DPAD_DOWN"])
        time.sleep(0.4)

    for i in range(12):
        run_adb(["shell", "input", "keyevent", "DPAD_LEFT"])
        time.sleep(0.4)

    for i in range(6):
        run_adb(["shell", "input", "keyevent", "DPAD_UP"])
        time.sleep(0.4)

    print(f"[{label}] gfxinfo aliniyor...")
    raw = run_adb(["shell", "dumpsys", "gfxinfo", "com.kaan.play"])
    parsed = parse_gfxinfo(raw)

    return raw, parsed

def run_suite(no_glow=False, no_images=False, tag="default", out_dir="docs/dogrulama-afis-kasma"):
    out_path = Path(out_dir)
    out_path.mkdir(parents=True, exist_ok=True)

    print(f"==================================================")
    print(f"SUITE BASLATILIYOR: no_glow={no_glow}, no_images={no_images}, tag={tag}")
    print(f"==================================================")

    # 1 warmup
    print("\n--- ISINMA KOSU (WARMUP) BASLIYOR (RAPORA DAHIL EDILMEZ) ---")
    raw_w, parsed_w = run_single_iteration(no_glow=no_glow, no_images=no_images, label=f"{tag}_warmup")
    (out_path / f"gfxinfo_{tag}_warmup.txt").write_text(raw_w, encoding="utf-8")
    print(f"Isinma Sonucu: Total={parsed_w['total_frames']}, Janky={parsed_w['janky_count']} ({parsed_w['janky_pct']}%), p95={parsed_w['p95']}ms")

    # 3 runs
    runs = []
    for r in range(1, 4):
        label = f"{tag}_run{r}"
        print(f"\n--- KOSU {r}/3 BASLIYOR ({label}) ---")
        raw, parsed = run_single_iteration(no_glow=no_glow, no_images=no_images, label=label)
        (out_path / f"gfxinfo_{label}.txt").write_text(raw, encoding="utf-8")
        parsed["run_index"] = r
        parsed["label"] = label
        runs.append(parsed)
        print(f"Kosu {r} Sonucu: Total={parsed['total_frames']}, Janky={parsed['janky_count']} ({parsed['janky_pct']}%), p50={parsed['p50']}ms, p90={parsed['p90']}ms, p95={parsed['p95']}ms, p99={parsed['p99']}ms")

    # Ortanca (median) sec: janky_pct veya p95'e gore sirala
    sorted_by_janky = sorted(runs, key=lambda x: (x["janky_pct"] if x["janky_pct"] is not None else 999, x["p95"] if x["p95"] is not None else 999))
    median_run = sorted_by_janky[1] # 3 elemanli listenin ortancasi

    print(f"\n==================================================")
    print(f"SONUC OZETI ({tag}, no_glow={no_glow}, no_images={no_images}):")
    for r in runs:
        print(f"Run {r['run_index']}: Total={r['total_frames']}, Janky={r['janky_count']} ({r['janky_pct']}%), p50={r['p50']}ms, p90={r['p90']}ms, p95={r['p95']}ms, p99={r['p99']}ms")
    print(f"ORTANCA KOSU (Run {median_run['run_index']}): Janky={median_run['janky_pct']}%, p50={median_run['p50']}ms, p90={median_run['p90']}ms, p95={median_run['p95']}ms, p99={median_run['p99']}ms")
    print(f"==================================================")

    return runs, median_run

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--no-glow", action="store_true", help="Glow ve scale kapat (ADIM 2 bayragi)")
    parser.add_argument("--no-images", action="store_true", help="Gorsel yuklemeyi kapat (ADIM 3 Supheli 2 bayragi)")
    parser.add_argument("--tag", type=str, default="measure", help="Etiket (dosya adi oneki)")
    parser.add_argument("--out-dir", type=str, default="docs/dogrulama-afis-kasma", help="Cikti dizini")
    args = parser.parse_args()

    run_suite(no_glow=args.no_glow, no_images=args.no_images, tag=args.tag, out_dir=args.out_dir)
