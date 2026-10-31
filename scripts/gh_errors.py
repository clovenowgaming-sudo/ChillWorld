# Filter baris error penting dari log run gagal (cuma baca)
import os
import re
import subprocess
import sys

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")


def gh(args, timeout=180):
    r = subprocess.run(["gh"] + args, cwd=ROOT, capture_output=True, text=True, timeout=timeout)
    return r.stdout + r.stderr


ids = gh(["run", "list", "--limit", "6", "--json", "databaseId,conclusion",
          "--jq", '.[] | select(.conclusion=="failure") | .databaseId']).split()
rid = ids[0]
print("Run gagal terbaru: " + rid)
log = gh(["run", "view", rid, "--log-failed"])
pat = re.compile(r"(\be: |error:|error\b.*\.kt|What went wrong|Execution failed|FAILED|Caused by|Unresolved|ERROR:|AAPT|SDK location|license)", re.I)
seen = set()
count = 0
for line in log.splitlines():
    # buang prefix "build  UNKNOWN STEP  timestamp"
    m = re.sub(r"^build\s+UNKNOWN STEP\s+\S+Z\s?", "", line)
    if pat.search(m) and "at org.gradle" not in m and m not in seen:
        seen.add(m)
        print(m[:300])
        count += 1
        if count >= 60:
            break
if count == 0:
    print("(tidak ada baris cocok)")
