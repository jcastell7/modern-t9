#!/usr/bin/env python3
"""
Build a Modern T9 baseline dictionary.

Output — one entry per line, `word<TAB>weight`, highest weight first:

    the	1000000
    good	95000

Sources
-------
The recommended corpus is HermitDave's FrequencyWords (OpenSubtitles 2018):
    https://github.com/hermitdave/FrequencyWords
    Code MIT · data CC BY-SA 4.0 (derived from OPUS OpenSubtitles2018)

    ./fetch-wordlists.sh          # downloads en_full.txt and es_full.txt

Other usable `word<TAB>count` (or `word count`) sources:
    wordfreq (Python, CC BY-SA 4.0)     https://github.com/rspeer/wordfreq
    Norvig count_1w.txt                  https://norvig.com/ngrams/
    SUBTLEX-US / SUBTLEX-ESP             subtitle frequency norms
    Leipzig Corpora Collection           https://wortschatz.uni-leipzig.de/

Usage
-----
  ./build-dictionary.py --lang en --freq corpus/en_full.txt \
      -o ../app/src/main/assets/dict/en.txt
  ./build-dictionary.py --lang es --freq corpus/es_full.txt --region 419 \
      -o ../app/src/main/assets/dict/es.txt

Why a --region flag
-------------------
OpenSubtitles Spanish is a mix of Latin American and Peninsular usage. Measured on the
2018 corpus it leans Latin American on the strongest markers (`ustedes` 174k vs
`vosotros` 49k; `papa` 22k vs `patata` 2k) but not uniformly (`coche` still outranks
`carro`). `--region 419` boosts Latin American vocabulary and demotes Peninsular-only
forms, using the editable table below.
"""
from __future__ import annotations

import argparse
import re
import sys
import unicodedata
from pathlib import Path

# Words a T9 keypad can produce: letters only, plus an internal apostrophe.
# Accents are folded before this check, so "señor" and "café" pass.
WORD_RE = re.compile(r"^[a-z][a-z']*$")

# --- regional preference (Spanish, es-419) ----------------------------------------
# Multipliers applied after frequency scaling. Edit freely — this is a preference,
# not a correctness rule, and both forms stay in the dictionary either way.
LATAM_BOOST = 4.0
PENINSULAR_DEMOTE = 0.25

LATAM_PREFERRED = {
    "ustedes", "carro", "computador", "celular", "jugo", "papa", "papas",
    "manejar", "plata", "apartamento", "ascensor", "durazno", "frijoles",
    "camarones", "arete", "aretes", "boleto", "boletos", "parqueadero",
    "nevera", "closet", "clóset", "chaqueta", "saco", "lentes",
    "auto", "autos", "cuadra", "banqueta", "piscina",
    "pasto", "pileta", "congelador", "colectivo", "guagua", "chévere",
    "bacano", "ahorita", "platicar", "ducha", "licuadora",
    "aguacate", "camión", "camiones", "tomar", "agarrar", "botar",
}

PENINSULAR_ONLY = {
    "vosotros", "vosotras", "coche", "ordenador", "móvil", "zumo", "patata",
    "patatas", "conducir", "piso", "ascensor", "melocotón", "judías",
    "gambas", "pendiente", "pendientes", "billete", "aparcamiento",
    "nevera", "armario", "cazadora", "americana", "gafas", "acera",
    "piscina", "césped", "vale", "guay", "mola", "molar", "currar",
    "ducha", "batidora", "autobús", "coger", "chaval", "chavales",
    "tío", "tía", "hostia", "joder", "vale",
}


def fold(text: str) -> str:
    """Strip diacritics — the engine encodes 'señor' exactly as 'senor'."""
    decomposed = unicodedata.normalize("NFD", text)
    return "".join(c for c in decomposed if unicodedata.category(c) != "Mn")


def normalise(raw: str) -> str | None:
    """Validate a word, PRESERVING its accents.

    The folded form is only used to check the word is keypad-typeable; the accented
    spelling is what gets stored, so the keyboard can offer "mañana" rather than
    "manana".
    """
    w = raw.strip().lower()
    if not w or len(w) > 24:
        return None
    folded = fold(w)
    if not WORD_RE.match(folded):
        return None
    if len(folded) < 2 and folded not in ("a", "i", "y", "o", "e", "u"):
        return None
    return w


def load_frequencies(path: Path) -> dict[str, int]:
    """Accept `word<TAB>count` or `word count`.

    Spellings that differ only by accent are competing renderings of one word, so their
    counts are summed and the *most frequent surface form* wins the entry. In the
    OpenSubtitles corpus "mañana" hugely outweighs the unaccented "manana", so the
    accented spelling is the one kept — which is what the user should see offered.
    """
    groups: dict[str, dict[str, int]] = {}
    with path.open(encoding="utf-8", errors="replace") as fh:
        for line in fh:
            parts = line.rstrip("\n").split("\t")
            if len(parts) < 2:
                parts = line.split()
            if len(parts) < 2:
                continue
            w = normalise(parts[0])
            if not w:
                continue
            try:
                count = int(parts[1])
            except ValueError:
                continue
            surface = groups.setdefault(fold(w), {})
            surface[w] = surface.get(w, 0) + count

    freqs: dict[str, int] = {}
    for surfaces in groups.values():
        total = sum(surfaces.values())
        best = max(surfaces.items(), key=lambda kv: kv[1])[0]
        freqs[best] = total
    return freqs


def apply_region(freqs: dict[str, int], region: str) -> int:
    if region != "419":
        return 0
    touched = 0
    folded_latam = {fold(x) for x in LATAM_PREFERRED}
    folded_pen = {fold(x) for x in PENINSULAR_ONLY}
    for w in list(freqs):
        key = fold(w)
        if key in folded_latam:
            freqs[w] = int(freqs[w] * LATAM_BOOST)
            touched += 1
        elif key in folded_pen:
            freqs[w] = max(1, int(freqs[w] * PENINSULAR_DEMOTE))
            touched += 1
    return touched


def main() -> int:
    ap = argparse.ArgumentParser(
        description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--lang", required=True, help="language tag, e.g. en or es")
    ap.add_argument("--freq", type=Path, required=True,
                    help="'word<TAB>count' frequency list")
    ap.add_argument("--region", default="", help="regional preference, e.g. 419 for es-419")
    ap.add_argument("-o", "--output", type=Path, required=True)
    ap.add_argument("--limit", type=int, default=80000,
                    help="keep the N highest-weighted words (0 = all)")
    ap.add_argument("--min-count", type=int, default=3,
                    help="drop words seen fewer than N times (removes corpus typos)")
    args = ap.parse_args()

    if not args.freq.exists():
        print(f"error: {args.freq} not found — run ./fetch-wordlists.sh", file=sys.stderr)
        return 1

    freqs = load_frequencies(args.freq)
    if not freqs:
        print(f"error: no usable entries in {args.freq}", file=sys.stderr)
        return 1
    raw_total = len(freqs)

    freqs = {w: c for w, c in freqs.items() if c >= args.min_count}
    touched = apply_region(freqs, args.region)

    peak = max(freqs.values())
    entries = {w: max(1, round(c / peak * 1_000_000)) for w, c in freqs.items()}

    ordered = sorted(entries.items(), key=lambda kv: (-kv[1], kv[0]))
    if args.limit:
        ordered = ordered[: args.limit]

    args.output.parent.mkdir(parents=True, exist_ok=True)
    with args.output.open("w", encoding="utf-8") as out:
        out.write("# Modern T9 dictionary — word<TAB>weight, highest first\n")
        out.write(f"# language: {args.lang}{'-' + args.region if args.region else ''}\n")
        out.write(f"# source: {args.freq.name} (OpenSubtitles 2018, CC BY-SA 4.0)\n")
        out.write(f"# entries: {len(ordered)}\n")
        for w, weight in ordered:
            out.write(f"{w}\t{weight}\n")

    print(f"{args.lang}: {raw_total:,} raw -> {len(freqs):,} after min-count "
          f"-> {len(ordered):,} written to {args.output}")
    if touched:
        print(f"      regional preference '{args.region}' applied to {touched} words")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
