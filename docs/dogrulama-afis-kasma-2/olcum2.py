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

def run_adb(args, timeout=60):
    cmd = [ADB_BIN, "-s", DEVICE] + args
    res = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, timeout=timeout)
    return res.stdout

def get_device_info():
    info = {}
    info["size"] = run_adb(["shell", "wm", "size"]).strip()
    info["density"] = run_adb(["shell", "wm", "density"]).strip()
    info["model"] = run_adb(["shell", "getprop", "ro.product.model"]).strip()
    info["qemu_gles"] = run_adb(["shell", "getprop", "ro.kernel.qemu.gles"]).strip()
    egl = run_adb(["shell", "getprop", "ro.hardware.egl"]).strip()
    info["egl"] = egl
    sf = run_adb(["shell", "dumpsys", "SurfaceFlinger"])
    gles_match = re.search(r'GLES:\s+([^\r\n]+)', sf)
    info["gles_renderer"] = gles_match.group(1).strip() if gles_match else "unknown"
    return info

def parse_gfxinfo(raw_text):
    total_matches = re.findall(r'Total frames rendered:\s+(\d+)', raw_text)
    janky_matches = re.findall(r'Janky frames:\s+(\d+)\s+\(([\d.]+)%\)', raw_text)
    p50_matches = re.findall(r'50th percentile:\s+(\d+)ms', raw_text)
    p90_matches = re.findall(r'90th percentile:\s+(\d+)ms', raw_text)
    p95_matches = re.findall(r'95th percentile:\s+(\d+)ms', raw_text)
    p99_matches = re.findall(r'99th percentile:\s+(\d+)ms', raw_text)

    total_frames = int(total_matches[-1]) if total_matches else None
    janky_count = int(janky_matches[-1][0]) if janky_matches else None
    janky_pct = float(janky_matches[-1][1]) if janky_matches else None
    p50 = int(p50_matches[-1]) if p50_matches else None
    p90 = int(p90_matches[-1]) if p90_matches else None
    p95 = int(p95_matches[-1]) if p95_matches else None
    p99 = int(p99_matches[-1]) if p99_matches else None

    return {
        "total_frames": total_frames,
        "janky_count": janky_count,
        "janky_pct": janky_pct,
        "p50": p50,
        "p90": p90,
        "p95": p95,
        "p99": p99,
    }

def run_single_iteration(flags=None, warm_cache=False, label="run"):
    if flags is None:
        flags = {}
    print(f"\n[{label}] Soguk acilis yapiliyor (flags={flags}, warm_cache={warm_cache})...")
    run_adb(["shell", "am", "force-stop", "com.kaan.play"])
    time.sleep(1.0)

    start_cmd = [
        "shell", "am", "start", "-n", "com.kaan.play/.MainActivity",
        "--es", "play_announcements_endpoint", "http://127.0.0.1:9999/none"
    ]
    for flag_name, val in flags.items():
        if val:
            start_cmd.extend(["--ez", flag_name, "true"])

    run_adb(start_cmd)

    print(f"[{label}] Ana sayfanin yuklenmesi bekleniyor (sabit 20 sn)...")
    time.sleep(20.0)

    print(f"[{label}] Ilk karta geciliyor...")
    run_adb(["shell", "input", "keyevent", "DPAD_DOWN"])
    time.sleep(0.5)

    if warm_cache:
        print(f"[{label}] Bellek onbellegi isitiliyor (1 tam dolasim yapiliyor)...")
        for _ in range(14):
            run_adb(["shell", "input", "keyevent", "DPAD_RIGHT"])
            time.sleep(0.20)
        for _ in range(4):
            run_adb(["shell", "input", "keyevent", "DPAD_DOWN"])
            time.sleep(0.20)
        for _ in range(14):
            run_adb(["shell", "input", "keyevent", "DPAD_LEFT"])
            time.sleep(0.20)
        for _ in range(4):
            run_adb(["shell", "input", "keyevent", "DPAD_UP"])
            time.sleep(0.20)
        time.sleep(1.0)
        print(f"[{label}] Bellek onbellegi isitildi, baslangic kartina donuldu.")

    print(f"[{label}] gfxinfo sifirlaniyor...")
    run_adb(["shell", "dumpsys", "gfxinfo", "com.kaan.play", "reset"])

    print(f"[{label}] Genisletilmis D-pad senaryosu baslatiliyor (7 dongu: 14 SAG, 4 ASAGI, 14 SOL, 4 YUKARI)...")
    # 7 dongu x 36 tus = 252 keyevent (~170 kare hedefi)
    for cycle in range(7):
        for _ in range(14):
            run_adb(["shell", "input", "keyevent", "DPAD_RIGHT"])
            time.sleep(0.20)
        for _ in range(4):
            run_adb(["shell", "input", "keyevent", "DPAD_DOWN"])
            time.sleep(0.20)
        for _ in range(14):
            run_adb(["shell", "input", "keyevent", "DPAD_LEFT"])
            time.sleep(0.20)
        for _ in range(4):
            run_adb(["shell", "input", "keyevent", "DPAD_UP"])
            time.sleep(0.20)

    print(f"[{label}] gfxinfo aliniyor...")
    raw = run_adb(["shell", "dumpsys", "gfxinfo", "com.kaan.play"])
    parsed = parse_gfxinfo(raw)

    return raw, parsed

def run_experiment(flags=None, warm_cache=False, tag="exp", out_dir="docs/dogrulama-afis-kasma-2"):
    out_path = Path(out_dir)
    out_path.mkdir(parents=True, exist_ok=True)

    print(f"==================================================")
    print(f"DENEY BASLATILIYOR: tag={tag}, flags={flags}, warm_cache={warm_cache}")
    print(f"==================================================")

    # 1 warmup
    print("\n--- ISINMA KOSU (WARMUP) (RAPORA DAHIL EDILMEZ) ---")
    raw_w, parsed_w = run_single_iteration(flags=flags, warm_cache=warm_cache, label=f"{tag}_warmup")
    (out_path / f"gfxinfo_{tag}_warmup.txt").write_text(raw_w, encoding="utf-8")
    print(f"Isinma: Total={parsed_w['total_frames']}, Janky={parsed_w['janky_count']} ({parsed_w['janky_pct']}%), p95={parsed_w['p95']}ms")

    # 5 runs
    runs = []
    for r in range(1, 6):
        label = f"{tag}_run{r}"
        print(f"\n--- KOSU {r}/5 BASLIYOR ({label}) ---")
        raw, parsed = run_single_iteration(flags=flags, warm_cache=warm_cache, label=label)
        (out_path / f"gfxinfo_{label}.txt").write_text(raw, encoding="utf-8")
        parsed["run_index"] = r
        parsed["label"] = label
        runs.append(parsed)
        print(f"Kosu {r}: Total={parsed['total_frames']}, Janky={parsed['janky_count']} ({parsed['janky_pct']}%), p50={parsed['p50']}ms, p90={parsed['p90']}ms, p95={parsed['p95']}ms, p99={parsed['p99']}ms")

    # Sort to find median (element index 2 in 5-element list)
    sorted_by_janky = sorted(runs, key=lambda x: (
        x["janky_pct"] if x["janky_pct"] is not None else 999,
        x["p95"] if x["p95"] is not None else 999
    ))
    median_run = sorted_by_janky[2] # 5 elemanli listenin tam ortancasi

    # Min and Max stats across the 5 runs
    min_total = min(r["total_frames"] for r in runs if r["total_frames"] is not None)
    max_total = max(r["total_frames"] for r in runs if r["total_frames"] is not None)
    min_janky_pct = min(r["janky_pct"] for r in runs if r["janky_pct"] is not None)
    max_janky_pct = max(r["janky_pct"] for r in runs if r["janky_pct"] is not None)
    min_p50 = min(r["p50"] for r in runs if r["p50"] is not None)
    max_p50 = max(r["p50"] for r in runs if r["p50"] is not None)
    min_p90 = min(r["p90"] for r in runs if r["p90"] is not None)
    max_p90 = max(r["p90"] for r in runs if r["p90"] is not None)
    min_p95 = min(r["p95"] for r in runs if r["p95"] is not None)
    max_p95 = max(r["p95"] for r in runs if r["p95"] is not None)
    min_p99 = min(r["p99"] for r in runs if r["p99"] is not None)
    max_p99 = max(r["p99"] for r in runs if r["p99"] is not None)

    summary = {
        "tag": tag,
        "runs": runs,
        "median": median_run,
        "min_max": {
            "total_frames": (min_total, max_total),
            "janky_pct": (min_janky_pct, max_janky_pct),
            "p50": (min_p50, max_p50),
            "p90": (min_p90, max_p90),
            "p95": (min_p95, max_p95),
            "p99": (min_p99, max_p99),
        }
    }

    print(f"\n==================================================")
    print(f"DENEY SONUC OZETI: {tag}")
    print(f"ORTANCA (Run {median_run['run_index']}): Total={median_run['total_frames']}, Janky={median_run['janky_pct']}%, p50={median_run['p50']}ms, p90={median_run['p90']}ms, p95={median_run['p95']}ms, p99={median_run['p99']}ms")
    print(f"MIN/MAKS:")
    print(f"  Total: {min_total} - {max_total}")
    print(f"  Janky %: {min_janky_pct}% - {max_janky_pct}%")
    print(f"  p50: {min_p50}ms - {max_p50}ms")
    print(f"  p90: {min_p90}ms - {max_p90}ms")
    print(f"  p95: {min_p95}ms - {max_p95}ms")
    print(f"  p99: {min_p99}ms - {max_p99}ms")
    print(f"==================================================")

    return summary

def load_saved_experiment(tag, out_dir="docs/dogrulama-afis-kasma-2"):
    out_path = Path(out_dir)
    runs = []
    for r in range(1, 6):
        f = out_path / f"gfxinfo_{tag}_run{r}.txt"
        if not f.exists():
            return None
        raw = f.read_text(encoding="utf-8")
        parsed = parse_gfxinfo(raw)
        parsed["run_index"] = r
        parsed["label"] = f"{tag}_run{r}"
        runs.append(parsed)

    sorted_by_janky = sorted(runs, key=lambda x: (
        x["janky_pct"] if x["janky_pct"] is not None else 999,
        x["p95"] if x["p95"] is not None else 999
    ))
    median_run = sorted_by_janky[2]

    min_total = min(r["total_frames"] for r in runs if r["total_frames"] is not None)
    max_total = max(r["total_frames"] for r in runs if r["total_frames"] is not None)
    min_janky_pct = min(r["janky_pct"] for r in runs if r["janky_pct"] is not None)
    max_janky_pct = max(r["janky_pct"] for r in runs if r["janky_pct"] is not None)
    min_p50 = min(r["p50"] for r in runs if r["p50"] is not None)
    max_p50 = max(r["p50"] for r in runs if r["p50"] is not None)
    min_p90 = min(r["p90"] for r in runs if r["p90"] is not None)
    max_p90 = max(r["p90"] for r in runs if r["p90"] is not None)
    min_p95 = min(r["p95"] for r in runs if r["p95"] is not None)
    max_p95 = max(r["p95"] for r in runs if r["p95"] is not None)
    min_p99 = min(r["p99"] for r in runs if r["p99"] is not None)
    max_p99 = max(r["p99"] for r in runs if r["p99"] is not None)

    return {
        "tag": tag,
        "runs": runs,
        "median": median_run,
        "min_max": {
            "total_frames": (min_total, max_total),
            "janky_pct": (min_janky_pct, max_janky_pct),
            "p50": (min_p50, max_p50),
            "p90": (min_p90, max_p90),
            "p95": (min_p95, max_p95),
            "p99": (min_p99, max_p99),
        }
    }

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--exp", type=str, default="A",
                        help="Hangi deney calistirilacak (A..G veya virgulle ayrilmis or. C,D,E,F,G veya all)")
    parser.add_argument("--out-dir", type=str, default="docs/dogrulama-afis-kasma-2", help="Cikti dizini")
    args = parser.parse_args()

    info = get_device_info()
    print("CIHAZ BILGILERI:")
    for k, v in info.items():
        print(f"  {k}: {v}")

    exp_configs = {
        "A": {"tag": "expA_taban", "flags": {}, "warm_cache": False},
        "B": {"tag": "expB_noimages", "flags": {"play_flag_no_images": True}, "warm_cache": False},
        "C": {"tag": "expC_no_crossfade", "flags": {"play_flag_no_crossfade": True}, "warm_cache": False},
        "D": {"tag": "expD_image_size", "flags": {"play_flag_image_size": True}, "warm_cache": False},
        "E": {"tag": "expE_no_hardware", "flags": {"play_flag_no_hardware": True}, "warm_cache": False},
        "F": {"tag": "expF_warm_cache", "flags": {}, "warm_cache": True},
        "G": {"tag": "expG_hoist_template", "flags": {"play_flag_hoist_template": True}, "warm_cache": False},
    }

    if args.exp == "all":
        target_exps = ["A", "B", "C", "D", "E", "F", "G"]
    elif "," in args.exp:
        target_exps = [x.strip() for x in args.exp.split(",") if x.strip()]
    else:
        target_exps = [args.exp.strip()]

    all_keys = ["A", "B", "C", "D", "E", "F", "G"]
    results = {}
    for k in all_keys:
        if k in target_exps:
            cfg = exp_configs[k]
            res = run_experiment(flags=cfg["flags"], warm_cache=cfg["warm_cache"], tag=cfg["tag"], out_dir=args.out_dir)
            results[k] = res
        else:
            saved = load_saved_experiment(exp_configs[k]["tag"], out_dir=args.out_dir)
            if saved:
                results[k] = saved

    print("\n\n" + "="*115)
    print("BUTUN DENEYLERIN KARSILASTIRMALI TABLOSU")
    print("="*115)
    print(f"{'Deney':<22} | {'Ortanca Janky %':<16} | {'Janky % Min-Maks':<18} | {'p50':<6} | {'p90':<6} | {'p95 (Ortanca)':<14} | {'p95 Min-Maks':<14} | {'p99':<6}")
    print("-" * 115)
    for k in all_keys:
        if k in results:
            r = results[k]
            med = r["median"]
            mm = r["min_max"]
            tag = r["tag"]
            print(f"{tag:<22} | {med['janky_pct']:>6.2f}%         | {mm['janky_pct'][0]:>5.2f}% - {mm['janky_pct'][1]:>5.2f}%   | {med['p50']:>4}ms | {med['p90']:>4}ms | {med['p95']:>6}ms       | {mm['p95'][0]:>4}ms - {mm['p95'][1]:>4}ms | {med['p99']:>4}ms")
    print("="*115)

if __name__ == "__main__":
    main()
