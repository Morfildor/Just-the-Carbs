#!/usr/bin/env python3
"""Capture Search-a-licious responses for the search benchmarks, as replayable fixtures.

    python tools/search-benchmark/capture.py            # every set, every configuration
    python tools/search-benchmark/capture.py turkish    # one set

Each (query set, language configuration) pair becomes one gzipped JSON file under
app/src/test/resources/search/benchmark/, which SearchBenchmarkTest replays through the production
SearchALiciousDataSource. Re-running this refreshes the fixtures from the live service; the test's
recorded numbers then need re-reading, because the service's own ranking moves over time.

What is trimmed, and why it does not change a ranking:

- Only the fields the app requests or ranks by are kept, plus the English, German and French names
  (so a benchmark can ask what local matching would gain from them without another capture).
- `nutriments` keeps `carbohydrates_100g` alone: it is the only nutrient the app reads.
- `countries_tags` keeps only the countries a benchmark is measured from. The ranking compares one
  device country against this list, so dropping the others cannot move a result.

Nothing here is used by the app. Standard library only, so it runs without installing anything.
"""
import datetime
import gzip
import json
import pathlib
import sys
import time
import urllib.request

URL = "https://search.openfoodfacts.org/search"
USER_AGENT = "Just the Carbs/benchmark (albinogorillassupport@gmail.com)"
ROOT = pathlib.Path(__file__).resolve().parents[2]
OUT = ROOT / "app/src/test/resources/search/benchmark"

PAGE_SIZE = 50
NAME_FIELDS = ["product_name", "product_name_nl", "product_name_tr",
               "product_name_en", "product_name_de", "product_name_fr"]
FIELDS = ["code"] + NAME_FIELDS + ["brands", "quantity", "nutriments",
                                   "countries_tags", "unique_scans_n"]
KEPT_COUNTRIES = {"en:turkey", "en:netherlands", "en:belgium", "en:germany"}

# (query set, configuration name, langs sent)
CAPTURES = [
    ("turkish", "nl-en-de-fr", ["nl", "en", "de", "fr"]),
    ("turkish", "tr-en-nl-de-fr", ["tr", "en", "nl", "de", "fr"]),
    ("turkish", "tr-en", ["tr", "en"]),
    ("dutch", "nl-en-de-fr", ["nl", "en", "de", "fr"]),
    ("dutch", "nl-en-de-fr-tr", ["nl", "en", "de", "fr", "tr"]),
]


def read_queries(name):
    lines = (OUT / f"{name}-queries.tsv").read_text(encoding="utf-8").splitlines()
    return [line.split("\t")[0] for line in lines if line.strip() and not line.startswith("#")]


def search(query, langs):
    body = json.dumps({"q": escape(query), "langs": langs, "page_size": PAGE_SIZE,
                       "fields": FIELDS}).encode("utf-8")
    request = urllib.request.Request(URL, data=body, headers={
        "Content-Type": "application/json", "User-Agent": USER_AGENT})
    with urllib.request.urlopen(request, timeout=30) as response:
        return json.loads(response.read())


LUCENE_SPECIALS = set('+-&|!(){}[]^"~*?:\\/')


def escape(text):
    """Mirror of SearchALiciousQuery.escape, so the captured page is the one the app would get."""
    return "".join("\\" + c if c in LUCENE_SPECIALS else c for c in text)


def trim(hit):
    kept = {key: hit[key] for key in FIELDS if key in hit and key != "nutriments"}
    carbs = (hit.get("nutriments") or {}).get("carbohydrates_100g")
    if carbs is not None:
        kept["nutriments"] = {"carbohydrates_100g": carbs}
    if "countries_tags" in kept:
        kept["countries_tags"] = [c for c in kept["countries_tags"] or [] if c in KEPT_COUNTRIES]
    return kept


def main(selected):
    OUT.mkdir(parents=True, exist_ok=True)
    for query_set, config, langs in CAPTURES:
        if selected and query_set not in selected:
            continue
        responses = {}
        for query in read_queries(query_set):
            data = search(query, langs)
            responses[query] = {
                "count": data.get("count"),
                "timed_out": data.get("timed_out"),
                "hits": [trim(h) for h in data.get("hits", [])],
            }
            time.sleep(0.3)
        document = {
            "captured": datetime.date.today().isoformat(),
            "langs": langs,
            "page_size": PAGE_SIZE,
            "responses": responses,
        }
        path = OUT / f"{query_set}-{config}.json.gz"
        payload = json.dumps(document, ensure_ascii=False, sort_keys=True, indent=0).encode("utf-8")
        # mtime=0 keeps the file byte-identical when the content is.
        with open(path, "wb") as raw, gzip.GzipFile(fileobj=raw, mode="wb", mtime=0) as out:
            out.write(payload)
        print(f"{path.name}: {len(responses)} queries, {path.stat().st_size} bytes")


if __name__ == "__main__":
    main(set(sys.argv[1:]))
