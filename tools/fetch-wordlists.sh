#!/usr/bin/env bash
# Fetch the open frequency corpora used to build the baseline dictionaries.
#
#   HermitDave / FrequencyWords  — OpenSubtitles 2018 word frequencies
#   https://github.com/hermitdave/FrequencyWords
#   Code: MIT.  Data: CC BY-SA 4.0  (derived from OPUS OpenSubtitles2018).
#
# Downloads to ./corpus/ , which is gitignored — the data is regenerable, and
# redistributing it would carry the CC BY-SA share-alike obligation.
set -euo pipefail

BASE="https://raw.githubusercontent.com/hermitdave/FrequencyWords/master/content/2018"
OUT="$(dirname "$0")/corpus"
mkdir -p "$OUT"

fetch() {
    local lang="$1" variant="$2"
    local url="$BASE/$lang/${lang}_${variant}.txt"
    local dest="$OUT/${lang}_${variant}.txt"
    if [[ -s "$dest" ]]; then
        echo "  have  $(basename "$dest")"
        return
    fi
    echo "  fetch $(basename "$dest")"
    curl -fsSL -o "$dest" "$url"
}

echo "Fetching OpenSubtitles 2018 frequency lists..."
fetch en full
fetch es full

echo
echo "Done. Files in $OUT:"
ls -lh "$OUT" | tail -n +2 | awk '{printf "  %-20s %s\n", $9, $5}'
echo
echo "Next:  modern-t9/tools/build-dictionary.py --lang en --freq modern-t9/tools/corpus/en_full.txt -o modern-t9/app/src/main/assets/dict/en.txt"
echo "       modern-t9/tools/build-dictionary.py --lang es --freq modern-t9/tools/corpus/es_full.txt -o modern-t9/app/src/main/assets/dict/es.txt"
