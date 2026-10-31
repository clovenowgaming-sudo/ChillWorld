import urllib.request, socket, sys
socket.setdefaulttimeout(10)
out = ["python " + sys.version.split()[0]]
for url in ["https://ambientcg.com/get?file=Grass001_1K-JPG.zip", "https://example.com"]:
    try:
        req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
        with urllib.request.urlopen(req, timeout=10) as r:
            out.append("OK %s status=%s type=%s" % (url, r.status, r.headers.get("Content-Type")))
    except Exception as e:
        out.append("ERR %s -> %r" % (url, e))
open("scripts_diag.txt", "w").write("\n".join(out) + "\n")
print("\n".join(out))
