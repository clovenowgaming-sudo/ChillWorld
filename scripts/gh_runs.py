# Lihat status run GitHub Actions terakhir (cuma baca)
import os
import subprocess

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
r = subprocess.run(
    ["gh", "run", "list", "--limit", "6",
     "--json", "databaseId,status,conclusion,headSha,displayTitle,createdAt",
     "--template",
     "{{range .}}{{.databaseId}} | {{.status}} | {{.conclusion}} | {{slice .headSha 0 7}} | {{.displayTitle}}\n{{end}}"],
    cwd=ROOT, capture_output=True, text=True, timeout=60)
print((r.stdout + r.stderr).strip() or "(kosong)")
