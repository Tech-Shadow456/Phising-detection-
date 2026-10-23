"""Link-checker backend. Opens links SAFELY (no scripts, size/time limits, public IPs only).

Run:  uvicorn backend.app:app --reload        (from the repo root)
Test: http://127.0.0.1:8000/check?url=https://example.com
Optional env keys (free): GSB_KEY (Google Safe Browsing), VT_KEY (VirusTotal)
"""
import base64
import ipaddress
import os
import re
import socket
import sys
import time
from datetime import datetime, timezone
from urllib.parse import urljoin, urlparse

import requests
from fastapi import FastAPI

sys.path.append(os.path.join(os.path.dirname(__file__), "..", "src"))
from rules import SHORTENERS, BAD_TLDS, BRANDS, host_of  # noqa: E402

app = FastAPI(title="PreClick Guard link checker")
CACHE, TTL = {}, 3600
HEADERS = {"User-Agent": "Mozilla/5.0 (PreClickGuard checker)"}
DOWNLOAD_EXT = (".apk", ".exe", ".msi", ".zip", ".rar", ".scr", ".bat")
MAX_BYTES, TIMEOUT, MAX_HOPS = 200_000, 5, 5


def is_public(host):
    try:
        for info in socket.getaddrinfo(host, None):
            if not ipaddress.ip_address(info[4][0]).is_global:
                return False
        return True
    except Exception:
        return False


def safe_fetch(url):
    """Follow redirects manually, checking every hop is a public address."""
    hops, resp = [url], None
    for _ in range(MAX_HOPS + 1):
        host = host_of(url)
        if not host or not is_public(host):
            return None, hops, "blocked: address is not a public website"
        try:
            resp = requests.get(url, headers=HEADERS, timeout=TIMEOUT, stream=True, allow_redirects=False)
        except Exception:
            return None, hops, "website did not respond"
        if resp.status_code in (301, 302, 303, 307, 308):
            url = urljoin(url, resp.headers.get("Location", ""))
            hops.append(url)
            continue
        return resp, hops, None
    return resp, hops, "too many redirects"


def domain_age_days(host):
    parts = host.split(".")
    domain = ".".join(parts[-2:]) if len(parts) >= 2 else host
    try:
        r = requests.get(f"https://rdap.org/domain/{domain}", timeout=TIMEOUT)
        for e in r.json().get("events", []):
            if e.get("eventAction") == "registration":
                d = datetime.fromisoformat(e["eventDate"].replace("Z", "+00:00"))
                return (datetime.now(timezone.utc) - d).days
    except Exception:
        pass
    return None


def safe_browsing(url):
    key = os.getenv("GSB_KEY")
    if not key:
        return None
    body = {"client": {"clientId": "preclick", "clientVersion": "1.0"},
            "threatInfo": {"threatTypes": ["MALWARE", "SOCIAL_ENGINEERING", "UNWANTED_SOFTWARE"],
                           "platformTypes": ["ANY_PLATFORM"], "threatEntryTypes": ["URL"],
                           "threatEntries": [{"url": url}]}}
    try:
        r = requests.post(f"https://safebrowsing.googleapis.com/v4/threatMatches:find?key={key}", json=body, timeout=TIMEOUT)
        return bool(r.json().get("matches"))
    except Exception:
        return None


def virustotal(url):
    """Returns (bad_count, known). known=False means no history."""
    key = os.getenv("VT_KEY")
    if not key:
        return None
    uid = base64.urlsafe_b64encode(url.encode()).decode().strip("=")
    try:
        r = requests.get(f"https://www.virustotal.com/api/v3/urls/{uid}", headers={"x-apikey": key}, timeout=TIMEOUT)
        if r.status_code == 404:
            return 0, False
        stats = r.json()["data"]["attributes"]["last_analysis_stats"]
        return stats.get("malicious", 0) + stats.get("suspicious", 0), True
    except Exception:
        return None


def check_link(url):
    if not url.lower().startswith("http"):
        url = "http://" + url
    notes, points = [], 0.0
    resp, hops, err = safe_fetch(url)
    final = hops[-1]
    host = host_of(final)

    if err:
        notes.append(err.capitalize()); points += 0.4
    if len(hops) > 1:
        notes.append(f"Redirects {len(hops) - 1}x to {host}")
        if any(host_of(url) == s or host_of(url).endswith("." + s) for s in SHORTENERS):
            points += 0.15
    if host.endswith(BAD_TLDS):
        notes.append("Uses a domain type common in scams"); points += 0.2

    if resp is not None:
        ctype = resp.headers.get("Content-Type", "").lower()
        cdisp = resp.headers.get("Content-Disposition", "").lower()
        path = urlparse(final).path.lower()
        if "android.package" in ctype or path.endswith(DOWNLOAD_EXT) or "attachment" in cdisp:
            notes.append("Tries to download a file (possible APK or malware)"); points += 0.5
        elif "html" in ctype:
            html = next(resp.iter_content(MAX_BYTES), b"").decode("utf-8", "ignore").lower()
            if 'type="password"' in html or "type='password'" in html:
                notes.append("Page asks for a password"); points += 0.25
            if re.search(r"\botp\b|card number|cvv", html):
                notes.append("Page asks for OTP or card details"); points += 0.3
            for b in BRANDS:
                if re.search(r"\b" + re.escape(b) + r"\b", html) and b.replace(" ", "") not in host:
                    notes.append(f"Page imitates {b.upper()} but is not their site"); points += 0.3; break
        resp.close()

    age = domain_age_days(host)
    if age is not None and age < 30:
        notes.append(f"New website ({age} days old)"); points += 0.3
    elif age is None:
        notes.append("Could not find website age")

    if safe_browsing(final):
        notes.append("Flagged by Google Safe Browsing"); points += 0.6
    vt = virustotal(final)
    if vt:
        bad, known = vt
        if bad > 0:
            notes.append(f"Flagged by {bad} security engines"); points += 0.5
        elif not known:
            notes.append("No online history found"); points += 0.1

    risk = min(1.0, points)
    level = "High risk" if risk >= 0.6 else "Be careful" if risk >= 0.3 else "Looks safe"
    if not notes:
        notes.append("No problems found")
    return {"url": url, "final_url": final, "risk": round(risk, 2), "level": level, "notes": notes[:5]}


@app.get("/check")
def check(url: str):
    now = time.time()
    if url in CACHE and now - CACHE[url][0] < TTL:
        return CACHE[url][1]
    result = check_link(url)
    CACHE[url] = (now, result)
    return result


@app.get("/")
def root():
    return {"status": "ok", "use": "/check?url=<link>"}
