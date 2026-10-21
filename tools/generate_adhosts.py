#!/usr/bin/env python3
"""
EasyList ve Turkce reklam listelerinden yalnizca ||alanadi^ bicimindeki
alan adi engelleme kurallarini indirip ayiklayan ve
app/src/main/assets/adhosts.txt dosyasina sirali ve tekrarsiz yazan yardimci betik.

Kaynaklar & Lisans:
- EasyList (https://easylist.to/): GNU GPL v3.0 / CC BY-SA 3.0
- Turkce Filtreler: GNU GPL v3.0 / CC BY-SA 3.0
"""

import os
import re
import sys
import urllib.request

SOURCES = [
    ("EasyList", "https://easylist.to/easylist/easylist.txt"),
    ("EasyList Adservers", "https://raw.githubusercontent.com/easylist/easylist/master/easylist/easylist_adservers.txt"),
    ("EasyList Turkish", "https://easylist-downloads.adblockplus.org/turkish-filters.txt"),
    ("Turk-AdFilter", "https://raw.githubusercontent.com/omerdduran/turk-adfilter/main/turk-adfilter.txt"),
]

# Yalnizca ||alanadi^ bicimindeki alan adlari:
# Istisna (@@) ve element gizleme (##) haric tutulur.
DOMAIN_RULE_REGEX = re.compile(r"^\|\|([a-zA-Z0-9][a-zA-Z0-9.-]*\.[a-zA-Z]{2,})\^$")

def normalize_domain(domain: str) -> str:
    d = domain.strip().lower().rstrip(".")
    if d.startswith("www."):
        d = d[4:]
    return d

def fetch_and_extract() -> list[str]:
    unique_domains = set()

    for name, url in SOURCES:
        print(f"Indiriliyor: {name} ({url})...")
        try:
            req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0 (PlayAdblockGenerator/1.0)"})
            with urllib.request.urlopen(req, timeout=30) as resp:
                content = resp.read().decode("utf-8", errors="ignore")
        except Exception as e:
            print(f"UYARI: {name} indirilemedi: {e}", file=sys.stderr)
            continue

        count = 0
        for line in content.splitlines():
            line = line.strip()
            if not line or line.startswith("!") or line.startswith("#"):
                continue
            if line.startswith("@@") or "##" in line or "#?#" in line or "#@#" in line:
                continue
            m = DOMAIN_RULE_REGEX.match(line)
            if m:
                raw_domain = m.group(1)
                normalized = normalize_domain(raw_domain)
                if normalized and "." in normalized and "/" not in normalized and ":" not in normalized:
                    unique_domains.add(normalized)
                    count += 1
        print(f"  -> {count} kural alindi (toplam tekil: {len(unique_domains)})")

    return sorted(unique_domains)

def main():
    repo_root = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
    out_dir = os.path.join(repo_root, "app", "src", "main", "assets")
    os.makedirs(out_dir, exist_ok=True)
    out_path = os.path.join(out_dir, "adhosts.txt")

    domains = fetch_and_extract()
    if not domains:
        print("HATA: Hic alan adi cikarilamadi!", file=sys.stderr)
        sys.exit(1)

    print(f"\nDosyaya yaziliyor: {out_path} ({len(domains)} alan adi)...")
    with open(out_path, "w", encoding="utf-8", newline="\n") as f:
        f.write("# Play Web Reklam ve Izleyici Alan Adi Engelleme Listesi\n")
        f.write("# Kaynaklar:\n")
        for name, url in SOURCES:
            f.write(f"# - {name}: {url}\n")
        f.write("# Lisans: EasyList (GNU GPL v3.0 / CC BY-SA 3.0)\n")
        f.write("# Uretim araci: tools/generate_adhosts.py\n")
        f.write("#\n")
        for d in domains:
            f.write(f"{d}\n")

    size_bytes = os.path.getsize(out_path)
    print(f"Tamamlandi: {len(domains)} alan adi, {size_bytes} bayt ({size_bytes / 1024:.1f} KB).")

if __name__ == "__main__":
    main()
