#!/usr/bin/env bash
# Dependency vulnerability scan for CarbScan (§26).
#
# Resolves the release runtime classpath and checks every artifact that actually ships
# against OSV.dev, the same advisory database GitHub and Google use.
#
# Why not OWASP Dependency-Check: it needs an install this machine cannot perform (no admin
# rights; `winget install` blocks on an unanswerable UAC prompt). OSV's HTTP API needs no
# install and no API key, so the scan is reproducible on any machine with curl.
#
# Usage:  bash tools/dependency-scan.sh
# Exit:   0 = no known vulnerabilities, 1 = at least one, 2 = the scan itself failed.

set -euo pipefail

: "${JAVA_HOME:=C:/atools/jdk-21.0.12+8}"
: "${ANDROID_HOME:=C:/atools/sdk}"
export JAVA_HOME ANDROID_HOME

cd "$(dirname "$0")/.."
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
# Python here is a Windows binary and cannot see Git Bash's virtual /tmp, so hand it a native
# path. Harmless elsewhere: without cygpath, $work is already a real path.
winwork="$(command -v cygpath >/dev/null 2>&1 && cygpath -w "$work" || echo "$work")"

echo "Resolving releaseRuntimeClasspath..."
# Gradle prints the REQUESTED version, then "-> resolved" when conflict resolution upgrades it.
# Scanning the requested version reports vulnerabilities in artifacts that never ship - the
# left-hand side of every "->" is a version Gradle already discarded. Take the right-hand side.
./gradlew.bat :app:dependencies --configuration releaseRuntimeClasspath -q 2>&1 \
  | sed 's/ (\*)//; s/ (c)//; s/ (n)//' \
  | grep -oE "[a-zA-Z0-9._-]+:[a-zA-Z0-9._-]+:[0-9][a-zA-Z0-9._-]*( -> [0-9][a-zA-Z0-9._-]*)?" \
  | awk '{ if ($2=="->") { split($1,a,":"); print a[1]":"a[2]":"$3 } else print $1 }' \
  | sort -u > "$work/deps.txt"

awk -F: 'NF==3 {printf "%s:%s\t%s\n",$1,$2,$3}' "$work/deps.txt" | sort -u > "$work/pairs.txt"
count=$(wc -l < "$work/pairs.txt")
echo "Querying OSV.dev for $count resolved artifacts..."

awk -F'\t' 'BEGIN{print "{\"queries\":["}
  {if(NR>1)printf ",\n"; printf "{\"package\":{\"ecosystem\":\"Maven\",\"name\":\"%s\"},\"version\":\"%s\"}",$1,$2}
  END{print "]}"}' "$work/pairs.txt" > "$work/query.json"

curl -sf -X POST -H "Content-Type: application/json" \
  --data-binary @"$work/query.json" "https://api.osv.dev/v1/querybatch" > "$work/result.json" || {
    echo "ERROR: OSV query failed. This is a FAILED SCAN, not a clean result." >&2
    exit 2
  }

# A batch of empty results is indistinguishable from a silently-broken query, so assert that a
# known-vulnerable coordinate is still detected. Without this control, a change to OSV's request
# format would turn every future run into a false all-clear.
control=$(curl -sf -X POST -H "Content-Type: application/json" \
  -d '{"queries":[{"package":{"ecosystem":"Maven","name":"com.squareup.okhttp3:okhttp"},"version":"4.9.1"}]}' \
  "https://api.osv.dev/v1/querybatch" || echo '')
if ! echo "$control" | grep -q '"vulns"'; then
  echo "ERROR: control query returned no vulnerabilities for a known-vulnerable artifact." >&2
  echo "The scan is not working; treat this run as FAILED, not clean." >&2
  exit 2
fi

python -c "
import json, sys
res = json.load(open(r'$winwork/result.json'))['results']
pairs = [l.rstrip('\n').split('\t') for l in open(r'$winwork/pairs.txt')]
hits = [(n, v, [x['id'] for x in r['vulns']]) for (n, v), r in zip(pairs, res) if r.get('vulns')]
print('Artifacts scanned: %d' % len(pairs))
if not hits:
    print('No known vulnerabilities.')
    sys.exit(0)
print('VULNERABLE: %d' % len(hits))
for name, ver, ids in hits:
    print('  %s:%s  %s' % (name, ver, ', '.join(ids)))
    for i in ids:
        print('    https://osv.dev/vulnerability/%s' % i)
sys.exit(1)
"
