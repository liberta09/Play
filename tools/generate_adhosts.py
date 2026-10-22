#!/usr/bin/env python3
"""
EasyList ve Turkce reklam listelerinden:
1. Alan adi engelleme kurallarini (||alanadi^) ayiklayip app/src/main/assets/adhosts.txt dosyasina,
2. Element gizleme kurallarini (##) ayiklayip app/src/main/assets/adhide.txt dosyasina
sirali ve tekrarsiz yazan yardimci betik.

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

# hdfilmcehennemi vb. sitelerde bahis/kumar afis kapsayicilarini garanti altina alan ek kurallar
# (Sayfanin kendi icerigini, oynaticiyi, menuyu veya bolum listesini etkilemez)
SUPPLEMENTAL_HIDE_RULES = [
    ("hdfilmcehennemi.*", ".card-money"),
    ("hdfilmcehennemi.*", '[class^="money"]'),
    ("hdfilmcehennemi.*", '[class*="money"]'),
    ("hdfilmcehennemi.*", ".rek-wrap"),
    ("hdfilmcehennemi.*", ".reklam-link"),
    ("hdfilmcehennemi.*", ".money--video"),
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
    generic_hide: set[str] = set()
    domain_hide: set[tuple[str, str]] = set()

    for name, urls in SOURCES:
        print(f"Indiriliyor: {name}...")
        content = fetch_url(urls)
        if not content:
            print(f"UYARI: {name} indirilemedi!", file=sys.stderr)
            continue

        host_count = 0
        hide_count = 0

        for raw_line in content.splitlines():
            line = raw_line.strip()
            if not line or line.startswith("!") or line.startswith("[") or line.startswith("# ") or line.startswith("#\t"):
                continue

            # --- 1. Element Gizleme Kurallari (##) ---
            # Istisna kurallari (#@#), betik/oznitelik kurallari (#?#, #$#, #%#) HARIC
            if "##" in line and "#@#" not in line and "#?#" not in line and "#$#" not in line and "#%#" not in line:
                parts = line.split("##", 1)
                dom_part = parts[0].strip()
                selector = parts[1].strip()

                # Procedural scriptlet'ler (+js(...), :-abp-...) CSS olmadigi icin haric tutulur
                if selector and not selector.startswith("+js(") and not selector.startswith(":-abp-"):
                    if not dom_part:
                        # (a) Genel kural (alan adi belirtilmemis)
                        generic_hide.add(selector)
                        hide_count += 1
                    else:
                        # (b) Alan adina ozgu kural
                        for d in dom_part.split(","):
                            d = d.strip().lower()
                            if d and not d.startswith("~"):
                                domain_hide.add((d, selector))
                                hide_count += 1
                continue

            # --- 2. Alan Adi Engelleme Kurallari (||alanadi^) ---
            if line.startswith("@@") or "##" in line:
                continue
            m = DOMAIN_RULE_REGEX.match(line)
            if m:
                raw_domain = m.group(1)
                normalized = normalize_domain(raw_domain)
                if normalized and "." in normalized and "/" not in normalized and ":" not in normalized:
                    unique_domains.add(normalized)
                    host_count += 1

        print(f"  -> {host_count} host kurali, {hide_count} element gizleme kurali alindi.")

    # Ek kurallari ekle
    for d, s in SUPPLEMENTAL_HIDE_RULES:
        domain_hide.add((d, s))

    return sorted(unique_domains), sorted(generic_hide), sorted(domain_hide)

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

def write_adhide(out_path: str, generic_rules: list[str], domain_rules: list[tuple[str, str]]):
    # Genel kural sayisi cok buyukse (20.000 uzeri) uyar/sinirla
    limit_note = ""
    if len(generic_rules) > 20000:
        print(f"UYARI: Genel kural sayisi {len(generic_rules)} > 20000. Ilk 20000 kural aliniyor.")
        generic_rules = generic_rules[:20000]
        limit_note = " (20.000 ile sinirlandi)"

    # hdfilmcehennemi kural sayisini raporla
    hd_rules = [(d, s) for (d, s) in domain_rules if "hdfilmcehennemi" in d]
    print(f"\nhdfilmcehennemi icin toplam {len(hd_rules)} kural bulundu.")

    print(f"Dosyaya yaziliyor: {out_path} ({len(generic_rules)} genel, {len(domain_rules)} alan adina ozgu kural)...")
    with open(out_path, "w", encoding="utf-8", newline="\n") as f:
        f.write("# Play Web Element Gizleme Kurallari\n")
        f.write("# Kaynaklar:\n")
        for name, urls in SOURCES:
            f.write(f"# - {name}: {urls[0]}\n")
        f.write("# Lisans: EasyList & Turkce Filtreler (GNU GPL v3.0 / CC BY-SA 3.0)\n")
        f.write("# Uretim araci: tools/generate_adhosts.py\n")
        f.write(f"# Toplam: {len(generic_rules)} genel{limit_note}, {len(domain_rules)} alan adina ozgu kural\n")
        f.write("#\n")
        f.write("# --- GENEL KURALLAR (Tum alan adlari icin) ---\n")
        for s in generic_rules:
            f.write(f"##{s}\n")
        f.write("#\n")
        f.write("# --- ALAN ADINA OZGU KURALLAR ---\n")
        for d, s in domain_rules:
            f.write(f"{d}##{s}\n")

    size_bytes = os.path.getsize(out_path)
    print(f"Tamamlandi: adhide.txt -> {len(generic_rules)} genel + {len(domain_rules)} alan ozgu kural, {size_bytes} bayt ({size_bytes / 1024:.1f} KB).")

def main():
    parser = argparse.ArgumentParser(description="Reklam engelleme ve element gizleme kurallari uretici")
    parser.add_argument("--hosts-only", action="store_true", help="Yalnizca adhosts.txt dosyasini guncelle")
    parser.add_argument("--hide-only", action="store_true", help="Yalnizca adhide.txt dosyasini guncelle")
    args = parser.parse_args()

    repo_root = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
    out_dir = os.path.join(repo_root, "app", "src", "main", "assets")
    os.makedirs(out_dir, exist_ok=True)

    domains, generic_rules, domain_rules = fetch_and_extract()

    do_hosts = not args.hide_only
    do_hide = not args.hosts_only

    if do_hosts:
        if not domains:
            print("HATA: Hic alan adi cikarilamadi!", file=sys.stderr)
            sys.exit(1)
        write_adhosts(os.path.join(out_dir, "adhosts.txt"), domains)

    if do_hide:
        if not generic_rules and not domain_rules:
            print("HATA: Hic element gizleme kurali cikarilamadi!", file=sys.stderr)
            sys.exit(1)
        write_adhide(os.path.join(out_dir, "adhide.txt"), generic_rules, domain_rules)

if __name__ == "__main__":
    main()
