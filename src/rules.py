"""Rule-based red flags. Each flag has a plain-language reason shown to the user."""
import re
from urllib.parse import urlparse

URL_RE = re.compile(
    r"(https?://[^\s]+|www\.[^\s]+|\b(?:[a-z0-9-]+\.)+(?:com|in|net|org|xyz|top|click|ly|co|me|io|tk|ml|gq|cf|icu|work|info|link)\b(?:/[^\s]*)?)",
    re.I)
URGENT = ["urgent", "immediately", "act now", "within 24 hours", "last chance", "expires today",
          "blocked", "suspended", "verify now", "final notice", "deactivated", "will be closed"]
SENSITIVE = ["otp", "pin", "cvv", "password", "card number", "aadhaar", "pan card", "kyc", "bank details"]
MONEY = ["prize", "lottery", "winner", "you won", "reward", "cashback", "refund", "gift card", "claim now"]
BRANDS = ["sbi", "hdfc", "icici", "axis", "paytm", "phonepe", "amazon", "flipkart", "google", "microsoft",
          "whatsapp", "netflix", "fedex", "dhl", "india post", "paypal", "apple", "airtel", "jio"]
SHORTENERS = ["bit.ly", "tinyurl.com", "t.co", "goo.gl", "cutt.ly", "is.gd", "rb.gy", "shorturl.at", "tiny.cc"]
BAD_TLDS = (".xyz", ".top", ".click", ".gq", ".tk", ".ml", ".cf", ".icu", ".work")


def extract_urls(text):
    return [u.rstrip(".,);]") for u in URL_RE.findall(text)]


def host_of(url):
    if not url.lower().startswith("http"):
        url = "http://" + url
    return (urlparse(url).hostname or "").lower()


def find_flags(text):
    """Return list of (weight, reason)."""
    t = text.lower()
    flags = []
    if any(w in t for w in URGENT):
        flags.append((0.25, "uses urgent or threatening language"))
    warns_not_to_share = re.search(r"(do not|don't|never) share", t)
    if not warns_not_to_share and any(re.search(r"\b" + re.escape(w) + r"\b", t) for w in SENSITIVE):
        flags.append((0.35, "asks for sensitive details (OTP, PIN, card, KYC)"))
    if any(w in t for w in MONEY):
        flags.append((0.25, "promises a prize, refund or reward"))

    urls = extract_urls(text)
    for u in urls:
        h = host_of(u)
        if any(h == s or h.endswith("." + s) for s in SHORTENERS):
            flags.append((0.25, "contains a shortened link that hides the real address")); break
    for u in urls:
        h = host_of(u)
        if re.fullmatch(r"\d{1,3}(\.\d{1,3}){3}", h):
            flags.append((0.35, "link uses a raw IP address instead of a website name")); break
    for u in urls:
        if host_of(u).endswith(BAD_TLDS):
            flags.append((0.2, "link ends in a domain type often used by scams")); break
    if urls:
        for b in BRANDS:
            if re.search(r"\b" + re.escape(b) + r"\b", t) and not any(b.replace(" ", "") in host_of(u) for u in urls):
                flags.append((0.3, f"mentions {b.upper()} but the link is not from {b.upper()}")); break
    return flags


def rules_score(flags):
    return min(1.0, sum(w for w, _ in flags))
