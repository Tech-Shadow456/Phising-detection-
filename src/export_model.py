"""Export the trained text model + rules to JSON for the Android app.

Usage (after python src/train.py):
  python src/export_model.py
Output: android-app/app/src/main/assets/model.json
"""
import json
import os
import sys
import joblib

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import rules  # noqa: E402

ROOT = os.path.join(HERE, "..")
model = joblib.load(os.path.join(ROOT, "models", "text_model.joblib"))
vec, clf = model.named_steps["tfidf"], model.named_steps["clf"]

assert vec.ngram_range == (1, 2) and vec.sublinear_tf and vec.lowercase and vec.norm == "l2", \
    "Vectorizer settings changed: update Scorer.kt to match"

out = {
    "vocab": {t: int(i) for t, i in vec.vocabulary_.items()},
    "idf": [round(float(x), 6) for x in vec.idf_],
    "coef": [round(float(x), 6) for x in clf.coef_[0]],
    "intercept": float(clf.intercept_[0]),
    "url_regex": rules.URL_RE.pattern,
    "urgent": rules.URGENT,
    "sensitive": rules.SENSITIVE,
    "money": rules.MONEY,
    "brands": rules.BRANDS,
    "shorteners": rules.SHORTENERS,
    "bad_tlds": list(rules.BAD_TLDS),
}
dest = os.path.join(ROOT, "android-app", "app", "src", "main", "assets", "model.json")
os.makedirs(os.path.dirname(dest), exist_ok=True)
with open(dest, "w") as f:
    json.dump(out, f, separators=(",", ":"))
print(f"Exported {len(out['vocab'])} features to {os.path.normpath(dest)} ({os.path.getsize(dest)//1024} KB)")
