# Status semua workflow untuk commit HEAD; kalau gagal, tampilkan error kompilasi yang relevan
import os
import re
import subprocess

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")


def gh(args, timeout=180):
    r = subprocess.run(["gh"] + args, cwd=ROOT, capture_output=True, text=True, timeout=timeout)
    return r.stdout + r.stderr


sha = subprocess.run(["git", "rev-parse", "HEAD"], cwd=ROOT, capture_output=True, text=True).stdout.strip()
rows = gh(["run", "list", "--commit", sha, "--json", "databaseId,workflowName,status,conclusion",
           "--jq", '.[] | "\\(.databaseId) \\(.workflowName) | \\(.status) | \\(.conclusion)"']).strip().splitlines()
print("HEAD " + sha[:7])
for r in rows:
    print(r)

pat = re.compile(r"(^e: |error: |What went wrong|Execution failed|Unresolved reference|Could not |No matching|Type mismatch|Overload resolution|None of the following|Cannot access|Platform declaration)")
for r in rows:
    parts = r.split(" | ")
    if len(parts) == 3 and parts[1] == "completed" and parts[2] == "failure":
        rid = r.split()[0]
        print("\n--- log gagal run %s ---" % rid)
        log = gh(["run", "view", rid, "--log-failed"])
        seen, n = set(), 0
        for line in log.splitlines():
            m = re.sub(r"^.*?\d{4}-\d\d-\d\dT[\d:.]+Z\s?", "", line).strip()
            if pat.search(m) and m not in seen and "at org.gradle" not in m:
                seen.add(m)
                print(m[:260])
                n += 1
                if n >= 40:
                    break
        if n == 0:
            print("(tidak ada baris cocok)")
