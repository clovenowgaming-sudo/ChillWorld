# Status run terbaru + error Gradle/Kotlin kalau gagal (cuma baca)
import os
import re
import subprocess

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")


def gh(args, timeout=180):
    r = subprocess.run(["gh"] + args, cwd=ROOT, capture_output=True, text=True, timeout=timeout)
    return r.stdout + r.stderr


info = gh(["run", "list", "--limit", "1", "--json", "databaseId,status,conclusion,headSha",
           "--jq", '.[0] | "\\(.databaseId) \\(.status) \\(.conclusion) \\(.headSha[0:7])"']).split()
print("Run terbaru: " + " ".join(info))
rid, status = info[0], info[1]
if status != "completed":
    print("Masih berjalan, cek lagi nanti.")
elif info[2] == "success":
    print("BUILD SUKSES.")
    print(gh(["run", "view", rid, "--json", "artifacts", "--jq", ".artifacts"]) if False else "")
else:
    log = gh(["run", "view", rid, "--log-failed"])
    pat = re.compile(r"(^e: |^w: .*\.kt|error: |What went wrong|Execution failed|Unresolved reference|Could not |No matching|SDK location|Task .* FAILED|^> )")
    seen = set()
    n = 0
    for line in log.splitlines():
        m = re.sub(r"^build\s+\S+\s+\S+Z\s?", "", line).strip()
        m = re.sub(r"^.*?\d{4}-\d\d-\d\dT[\d:.]+Z\s?", "", m)
        if pat.search(m) and m not in seen and "at org.gradle" not in m:
            seen.add(m)
            print(m[:250])
            n += 1
            if n >= 40:
                break
    if n == 0:
        print("(tidak ada baris cocok)")
