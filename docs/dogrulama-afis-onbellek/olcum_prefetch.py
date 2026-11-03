import os
import sys
import time
import subprocess
import re
import argparse
import json
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

def get_pss_kb():
    raw = run_adb(["shell", "dumpsys", "meminfo", "com.kaan.play"])
    # Match 'TOTAL:    152606' in App Summary or 'TOTAL   152606' in MEMINFO
    m = re.search(r'TOTAL:\s+(\d+)', raw)
    if not m:
        m = re.search(r'TOTAL\s+(\d+)', raw)
    return int(m.group(1)) if m else None

def get_prefetch_metrics():
    raw = run_adb(["shell", "am", "broadcast", "-a", "com.kaan.play.DUMP_PREFETCH_METRICS"])
    # Example: data="total=48,memHits=45,diskHits=3,netHits=0,hitRate=93,75,prefetchesTriggered=48,prefetchesSuccess=48"
    m = re.search(r'data="([^"]+)"', raw)
    if not m:
        return {}
    parts = m.group(1).split(",")
    data = {}
    for part in parts:
        if "=" in part:
            k, v = part.split("=", 1)
            # handle comma decimal in hitRate if any
            v = v.replace(",", ".")
            try:
                data[k] = float(v) if "." in v else int(v)
            except ValueError:
                data[k] = v
    return data

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

def run_single_iteration(flags=None, label="run"):
    if flags is None:
        flags = {}
    print(f"\n[{label}] Soguk acilis yapiliyor (flags={flags})...")
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

    pss_start = get_pss_kb()
    print(f"[{label}] Baslangic PSS: {pss_start} KB ({pss_start/1024:.1f} MB)" if pss_start else "PSS alinamadi")

    # gfxinfo ve prefetch sayacini sifirla
    run_adb(["shell", "dumpsys", "gfxinfo", "com.kaan.play", "reset"])
    run_adb(["shell", "am", "broadcast", "-a", "com.kaan.play.RESET_PREFETCH_METRICS"])
    time.sleep(0.5)

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

    print(f"[{label}] gfxinfo, prefetch metrikleri ve bitis PSS aliniyor...")
    raw_gfx = run_adb(["shell", "dumpsys", "gfxinfo", "com.kaan.play"])
    parsed_gfx = parse_gfxinfo(raw_gfx)

    prefetch_data = get_prefetch_metrics()
    pss_end = get_pss_kb()

    result = {
        **parsed_gfx,
        "pss_start_kb": pss_start,
        "pss_end_kb": pss_end,
        "pss_diff_kb": (pss_end - pss_start) if (pss_start and pss_end) else None,
        "pss_diff_pct": ((pss_end - pss_start) / pss_start * 100.0) if (pss_start and pss_end) else None,
        "prefetch": prefetch_data,
    }

    return raw_gfx, result

def run_experiment(flags=None, tag="exp", out_dir="docs/dogrulama-afis-onbellek"):
    out_path = Path(out_dir)
    out_path.mkdir(parents=True, exist_ok=True)

    print("=" * 60)
    print(f"DENEY BASLATILIYOR: tag={tag}, flags={flags}")
    print("=" * 60)

    # 1 warmup
    print("\n--- ISINMA KOSU (WARMUP) (RAPORA DAHIL EDILMEZ) ---")
    raw_w, parsed_w = run_single_iteration(flags=flags, label=f"{tag}_warmup")
    (out_path / f"gfxinfo_{tag}_warmup.txt").write_text(raw_w, encoding="utf-8")
    print(f"Isinma: Total={parsed_w['total_frames']}, Janky={parsed_w['janky_count']} ({parsed_w['janky_pct']}%), p95={parsed_w['p95']}ms, hitRate={parsed_w['prefetch'].get('hitRate', 0)}%")

    # 5 runs
    runs = []
    for r in range(1, 6):
        label = f"{tag}_run{r}"
        print(f"\n--- KOSU {r}/5 BASLIYOR ({label}) ---")
        raw, parsed = run_single_iteration(flags=flags, label=label)
        (out_path / f"gfxinfo_{label}.txt").write_text(raw, encoding="utf-8")
        parsed["run_index"] = r
        parsed["label"] = label
        runs.append(parsed)
        p_hit = parsed['prefetch'].get('hitRate', 0)
        p_tot = parsed['prefetch'].get('totalLoads', 0)
        p_mem = parsed['prefetch'].get('memoryCacheHits', 0)
        print(f"Kosu {r}: Total={parsed['total_frames']}, Janky={parsed['janky_count']} ({parsed['janky_pct']}%), p50={parsed['p50']}ms, p95={parsed['p95']}ms, p99={parsed['p99']}ms | hitRate={p_hit}% (mem={p_mem}/{p_tot}) | PSS: {parsed['pss_start_kb']} -> {parsed['pss_end_kb']} KB")

    # Sort by janky_pct for median
    sorted_by_janky = sorted(runs, key=lambda x: (x["janky_pct"] if x["janky_pct"] is not None else 999, x["p95"] if x["p95"] is not None else 999))
    median_run = sorted_by_janky[2]

    # Aggregate stats
    janky_pcts = [r["janky_pct"] for r in runs if r["janky_pct"] is not None]
    p50s = [r["p50"] for r in runs if r["p50"] is not None]
    p90s = [r["p90"] for r in runs if r["p90"] is not None]
    p95s = [r["p95"] for r in runs if r["p95"] is not None]
    p99s = [r["p99"] for r in runs if r["p99"] is not None]
    hit_rates = [r["prefetch"].get("hitRate", 0.0) for r in runs]
    pss_starts = [r["pss_start_kb"] for r in runs if r["pss_start_kb"] is not None]
    pss_ends = [r["pss_end_kb"] for r in runs if r["pss_end_kb"] is not None]

    summary = {
        "tag": tag,
        "flags": flags,
        "median_run": median_run,
        "all_runs": runs,
        "janky_pct_min": min(janky_pcts) if janky_pcts else None,
        "janky_pct_max": max(janky_pcts) if janky_pcts else None,
        "janky_pct_median": median_run["janky_pct"],
        "p50_median": median_run["p50"],
        "p90_median": median_run["p90"],
        "p95_median": median_run["p95"],
        "p95_min": min(p95s) if p95s else None,
        "p95_max": max(p95s) if p95s else None,
        "p99_median": median_run["p99"],
        "hit_rate_median": median_run["prefetch"].get("hitRate", 0.0),
        "hit_rate_min": min(hit_rates) if hit_rates else None,
        "hit_rate_max": max(hit_rates) if hit_rates else None,
        "pss_start_median_kb": sorted(pss_starts)[len(pss_starts)//2] if pss_starts else None,
        "pss_end_median_kb": sorted(pss_ends)[len(pss_ends)//2] if pss_ends else None,
    }

    (out_path / f"summary_{tag}.json").write_text(json.dumps(summary, indent=2), encoding="utf-8")
    return summary

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--mode", choices=["all", "A", "B"], default="all")
    args = parser.parse_args()

    dev_info = get_device_info()
    print("Cihaz Bilgileri:")
    for k, v in dev_info.items():
        print(f"  {k}: {v}")

    out_dir = "docs/dogrulama-afis-onbellek"
    os.makedirs(out_dir, exist_ok=True)
    Path(os.path.join(out_dir, "device_info.json")).write_text(json.dumps(dev_info, indent=2), encoding="utf-8")

    res_A = None
    res_B = None

    if args.mode in ["all", "A"]:
        # A: Yeni önbellek (bayraksız)
        res_A = run_experiment(flags={}, tag="expA_prefetch_on", out_dir=out_dir)

    if args.mode in ["all", "B"]:
        # B: play_flag_no_prefetch (önbelleksiz)
        res_B = run_experiment(flags={"play_flag_no_prefetch": True}, tag="expB_no_prefetch", out_dir=out_dir)

    if res_A and res_B:
        print("\n" + "=" * 60)
        print("KARSILASTIRMA VE KARAR RAPORU")
        print("=" * 60)
        print(f"Metrik                      | A (Prefetch ACiK)          | B (Prefetch KAPALi)        | Fark / Degisim")
        print("-" * 80)
        
        hit_A = res_A["hit_rate_median"]
        hit_B = res_B["hit_rate_median"]
        hit_diff = hit_A - hit_B
        print(f"Bellek Onbellegi Isabet %   | {hit_A:.1f}% (min-maks: {res_A['hit_rate_min']:.1f}-{res_A['hit_rate_max']:.1f}%) | {hit_B:.1f}% (min-maks: {res_B['hit_rate_min']:.1f}-{res_B['hit_rate_max']:.1f}%) | +{hit_diff:.1f}% (Hedef: >=+80%)")

        jank_A = res_A["janky_pct_median"]
        jank_B = res_B["janky_pct_median"]
        print(f"Janky Frames % (Ortanca)    | {jank_A:.2f}% (min-maks: {res_A['janky_pct_min']:.2f}-{res_A['janky_pct_max']:.2f}%) | {jank_B:.2f}% (min-maks: {res_B['janky_pct_min']:.2f}-{res_B['janky_pct_max']:.2f}%) | {jank_A - jank_B:+.2f}%")

        p95_A = res_A["p95_median"]
        p95_B = res_B["p95_median"]
        print(f"p95 Kare Suresi (Ortanca)   | {p95_A} ms (min-maks: {res_A['p95_min']}-{res_A['p95_max']} ms)    | {p95_B} ms (min-maks: {res_B['p95_min']}-{res_B['p95_max']} ms)    | {p95_A - p95_B:+d} ms")

        p99_A = res_A["p99_median"]
        p99_B = res_B["p99_median"]
        print(f"p99 Kare Suresi (Ortanca)   | {p99_A} ms                        | {p99_B} ms                        | {p99_A - p99_B:+d} ms")

        pss_start_A = res_A["pss_start_median_kb"]
        pss_end_A = res_A["pss_end_median_kb"]
        pss_growth_A = ((pss_end_A - pss_start_A) / pss_start_A * 100.0) if pss_start_A else 0.0

        pss_start_B = res_B["pss_start_median_kb"]
        pss_end_B = res_B["pss_end_median_kb"]
        pss_growth_B = ((pss_end_B - pss_start_B) / pss_start_B * 100.0) if pss_start_B else 0.0

        print(f"Toplam PSS RAM (Ortanca)    | {pss_start_A/1024:.1f} MB -> {pss_end_A/1024:.1f} MB (+{pss_growth_A:.1f}%) | {pss_start_B/1024:.1f} MB -> {pss_end_B/1024:.1f} MB (+{pss_growth_B:.1f}%) | Fark: {pss_growth_A - pss_growth_B:+.1f}% (Hedef: <=25%)")

        passed_hit = hit_diff >= 80.0
        passed_jank = (jank_A <= jank_B + 5.0) and (p95_A <= p95_B + 5.0)
        passed_pss = pss_growth_A <= 25.0

        print("\nKarar Kriterleri:")
        print(f"  1. Isabet orani B'ye gore en az %80 artmali: {'GECTI' if passed_hit else 'KALDI'} (+{hit_diff:.1f}%)")
        print(f"  2. Janky/p95 B'den kotu olmamali:            {'GECTI' if passed_jank else 'KALDI'}")
        print(f"  3. PSS artisi en cok %25 olmali:             {'GECTI' if passed_pss else 'KALDI'} (+{pss_growth_A:.1f}%)")

if __name__ == "__main__":
    main()
