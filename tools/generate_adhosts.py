#!/usr/bin/env python3
"""
EasyList ve Turkce reklam listelerinden:
1. Alan adi engelleme kurallarini (||alanadi^) ayiklayip app/src/main/assets/adhosts.txt dosyasina,
2. Yalnizca guvenli hdfilmcehennemi* element gizleme kurallarini ayiklayip app/src/main/assets/adhide.txt dosyasina
yazan yardimci betik.

Kurallar:
- TUM genel (## ile baslayan, alan adsiz) kurallar kaldirilmistir (video oynatici guvenligi icin).
- Oynatici konteynerini gizleme riski tasiyan [class*="money"], .ps-head-container ve div[class^="r_header_"] kurallari silinmistir.
- Yalnizca afis gizleme amacli 11 guvenli secici korunur.

Kaynaklar & Lisans:
- EasyList (https://easylist.to/): GNU GPL v3.0 / CC BY-SA 3.0
- Turkce Filtreler: GNU GPL v3.0 / CC BY-SA 3.0
"""

import argparse
import os
import re
import sys
import urllib.request

SOURCES = [
    ("EasyList", [
        "https://easylist-downloads.adblockplus.org/easylist.txt",
        "https://easylist.to/easylist/easylist.txt",
    ]),
    ("EasyList Adservers", [
        "https://raw.githubusercontent.com/easylist/easylist/master/easylist/easylist_adservers.txt",
    ]),
    ("EasyList Turkish", [
        "https://easylist-downloads.adblockplus.org/turkish-filters.txt",
    ]),
    ("Turk-AdFilter", [
        "https://raw.githubusercontent.com/omerdduran/turk-adfilter/main/turk-adfilter.txt",
    ]),
]

# Yalnizca ||alanadi^ bicimindeki alan adlari:
DOMAIN_RULE_REGEX = re.compile(r"^\|\|([a-zA-Z0-9][a-zA-Z0-9.-]*\.[a-zA-Z]{2,})\^$")

# Oynaticiyi gizleme riski tasiyan ve KESINLIKLE silinen 3 kural:
DANGEROUS_SELECTORS = {
    '[class*="money"]',
    ".ps-head-container",
    'div[class^="r_header_"]',
}

# hdfilmcehennemi afislerini gizlemek icin izin verilen guvenli seciciler:
SAFE_HDFILM_SELECTORS = [
    ".card-money",
    ".money",
    ".money--video",
    ".money-child",
    ".money-item",
    ".money-text",
    ".money-wrapper",
    ".rek-wrap",
    ".reklam-link",
    '.video_content > a[target="_blank"]',
    ".col > .alert-danger-50.alert",
]

def normalize_domain(domain: str) -> str:
    d = domain.strip().lower().rstrip(".")
    if d.startswith("www."):
        d = d[4:]
    return d

def fetch_url(urls: list[str]) -> str:
    for u in urls:
        try:
            req = urllib.request.Request(u, headers={"User-Agent": "Mozilla/5.0 (PlayAdblockGenerator/1.0)"})
            with urllib.request.urlopen(req, timeout=30) as resp:
                return resp.read().decode("utf-8", errors="ignore")
        except Exception as e:
            print(f"  Uyari ({u}): {e}", file=sys.stderr)
    return ""

def fetch_and_extract():
    unique_domains: set[str] = set()

    for name, urls in SOURCES:
        print(f"Indiriliyor: {name}...")
        content = fetch_url(urls)
        if not content:
            print(f"UYARI: {name} indirilemedi!", file=sys.stderr)
            continue

        host_count = 0
        for raw_line in content.splitlines():
            line = raw_line.strip()
            if not line or line.startswith("!") or line.startswith("[") or line.startswith("#"):
                continue

            # Alan Adi Engelleme Kurallari (||alanadi^)
            if line.startswith("@@") or "##" in line:
                continue
            m = DOMAIN_RULE_REGEX.match(line)
            if m:
                raw_domain = m.group(1)
                normalized = normalize_domain(raw_domain)
                if normalized and "." in normalized and "/" not in normalized and ":" not in normalized:
                    unique_domains.add(normalized)
                    host_count += 1

        print(f"  -> {host_count} host kurali alindi.")

    # Element gizleme: Genel kurallar YOKTUR. Yalnizca hdfilmcehennemi* icin guvenli 11 secici.
    hd_rules = [("hdfilmcehennemi.*", s) for s in sorted(SAFE_HDFILM_SELECTORS)]

    return sorted(unique_domains), hd_rules

def write_adhosts(out_path: str, domains: list[str]):
    print(f"\nDosyaya yaziliyor: {out_path} ({len(domains)} alan adi)...")
    with open(out_path, "w", encoding="utf-8", newline="\n") as f:
        f.write("# Play Web Reklam ve Izleyici Alan Adi Engelleme Listesi\n")
        f.write("# Kaynaklar:\n")
        for name, urls in SOURCES:
            f.write(f"# - {name}: {urls[0]}\n")
        f.write("# Lisans: EasyList (GNU GPL v3.0 / CC BY-SA 3.0)\n")
        f.write("# Uretim araci: tools/generate_adhosts.py\n")
        f.write("#\n")
        for d in domains:
            f.write(f"{d}\n")
    size_bytes = os.path.getsize(out_path)
    print(f"Tamamlandi: adhosts.txt -> {len(domains)} alan adi, {size_bytes} bayt ({size_bytes / 1024:.1f} KB).")

def write_adhide(out_path: str, domain_rules: list[tuple[str, str]]):
    print(f"\nDosyaya yaziliyor: {out_path} ({len(domain_rules)} kural)...")
    with open(out_path, "w", encoding="utf-8", newline="\n") as f:
        f.write("# Play Web Element Gizleme Kurallari\n")
        f.write("# Kaynaklar: EasyList & Turkce Filtreler (GNU GPL v3.0 / CC BY-SA 3.0)\n")
        f.write("# Uretim araci: tools/generate_adhosts.py\n")
        f.write("# NOT: Genel (alan adsiz) kurallar ve video oynatici riski tasiyan seciciler kaldirilmistir.\n")
        f.write(f"# Toplam: {len(domain_rules)} alan adina bagli kural\n")
        f.write("#\n")
        for d, s in domain_rules:
            f.write(f"{d}##{s}\n")

    size_bytes = os.path.getsize(out_path)
    print(f"Tamamlandi: adhide.txt -> {len(domain_rules)} kural, {size_bytes} bayt ({size_bytes / 1024:.1f} KB).")

def main():
    parser = argparse.ArgumentParser(description="Reklam engelleme ve element gizleme kurallari uretici")
    parser.add_argument("--hosts-only", action="store_true", help="Yalnizca adhosts.txt dosyasini guncelle")
    parser.add_argument("--hide-only", action="store_true", help="Yalnizca adhide.txt dosyasini guncelle")
    args = parser.parse_args()

    repo_root = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
    out_dir = os.path.join(repo_root, "app", "src", "main", "assets")
    os.makedirs(out_dir, exist_ok=True)

    domains, domain_rules = fetch_and_extract()

    do_hosts = not args.hide_only
    do_hide = not args.hosts_only

    if do_hosts:
        if not domains:
            print("HATA: Hic alan adi cikarilamadi!", file=sys.stderr)
            sys.exit(1)
        write_adhosts(os.path.join(out_dir, "adhosts.txt"), domains)

    if do_hide:
        if not domain_rules:
            print("HATA: Hic element gizleme kurali cikarilamadi!", file=sys.stderr)
            sys.exit(1)
        write_adhide(os.path.join(out_dir, "adhide.txt"), domain_rules)

if __name__ == "__main__":
    main()
