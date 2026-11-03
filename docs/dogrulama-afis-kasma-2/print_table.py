from pathlib import Path
import re

out_path = Path("docs/dogrulama-afis-kasma-2")

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

exps = [
    ("expA_taban", "A: Taban (Varsayılan)"),
    ("expB_noimages", "B: noimages (Görsel Yok)"),
    ("expC_no_crossfade", "C: no_crossfade"),
    ("expD_image_size", "D: image_size (Açık Boyut)"),
    ("expE_no_hardware", "E: no_hardware (Yazılım Bitmap)"),
    ("expF_warm_cache", "F: warm_cache (Bellek Önbelleği Isınmış)"),
    ("expG_hoist_template", "G: hoist_template (DataStore Hoist)"),
]

for tag, desc in exps:
    print(f"\n==========================================")
    print(f"Deney {desc}")
    print(f"==========================================")
    warmup_f = out_path / f"gfxinfo_{tag}_warmup.txt"
    if warmup_f.exists():
        pw = parse_gfxinfo(warmup_f.read_text(encoding="utf-8"))
        print(f"  Isınma (atıldı): Total={pw['total_frames']}, Janky={pw['janky_count']} ({pw['janky_pct']}%), p95={pw['p95']}ms")

    runs = []
    for r in range(1, 6):
        rf = out_path / f"gfxinfo_{tag}_run{r}.txt"
        if rf.exists():
            p = parse_gfxinfo(rf.read_text(encoding="utf-8"))
            p["run_index"] = r
            runs.append(p)
            print(f"  Koşu {r}: Total={p['total_frames']}, Janky={p['janky_count']} ({p['janky_pct']}%), p50={p['p50']}ms, p90={p['p90']}ms, p95={p['p95']}ms, p99={p['p99']}ms")

    if runs:
        sorted_runs = sorted(runs, key=lambda x: (x["janky_pct"] if x["janky_pct"] is not None else 999, x["p95"] if x["p95"] is not None else 999))
        med = sorted_runs[2]
        min_total = min(x["total_frames"] for x in runs)
        max_total = max(x["total_frames"] for x in runs)
        min_janky = min(x["janky_pct"] for x in runs)
        max_janky = max(x["janky_pct"] for x in runs)
        min_p95 = min(x["p95"] for x in runs)
        max_p95 = max(x["p95"] for x in runs)
        print(f"  >> ORTANCA (Koşu {med['run_index']}): Total={med['total_frames']}, Janky={med['janky_count']} ({med['janky_pct']}%), p50={med['p50']}ms, p90={med['p90']}ms, p95={med['p95']}ms, p99={med['p99']}ms")
        print(f"  >> Min-Maks: Total={min_total}-{max_total}, Janky%={min_janky}%-{max_janky}%, p95={min_p95}ms-{max_p95}ms")
