"""Combine the text model and rule flags into a verdict with reasons.

Usage: python src/scoring.py "Your SBI account is blocked. Share OTP at bit.ly/xyz"
"""
import os
import sys
import joblib
import numpy as np
from rules import find_flags, rules_score, extract_urls

_model = None


MODEL_PATH = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "models", "text_model.joblib")


def load_model(path=MODEL_PATH):
    global _model
    if _model is None:
        _model = joblib.load(path)
    return _model


def model_probability(text):
    return float(load_model().predict_proba([text])[0][1])


def top_words(text, n=3):
    """Words that pushed the score toward 'fraud' (works for TF-IDF + Logistic Regression)."""
    pipe = load_model()
    vec, clf = pipe.named_steps["tfidf"], pipe.named_steps["clf"]
    x = vec.transform([text])
    names = np.array(vec.get_feature_names_out())
    contrib = x.toarray()[0] * clf.coef_[0]
    idx = np.argsort(contrib)[::-1][:n]
    return [names[i] for i in idx if contrib[i] > 0 and " " not in names[i]]


def analyze(text):
    p = model_probability(text)
    flags = find_flags(text)
    rs = rules_score(flags)
    risk = 0.7 * p + 0.3 * rs
    if rs >= 0.6:
        risk = max(risk, 0.6)
    level = "Phishing" if risk >= 0.6 else "Suspicious" if risk >= 0.35 else "Safe"
    reasons = [r for _, r in flags]
    if level != "Safe" and not reasons:
        words = top_words(text)
        if words:
            reasons.append("message wording is similar to known scams (" + ", ".join(words) + ")")
    return {"level": level, "risk": round(risk, 3), "model_prob": round(p, 3),
            "rules_score": round(rs, 3), "reasons": reasons, "links": extract_urls(text)}


def _cap(s):
    return s[:1].upper() + s[1:]


def explain(text):
    r = analyze(text)
    if r["level"] == "Safe":
        return "Safe"
    return f"{r['level'].upper()}: " + _cap("; ".join(r["reasons"]))


if __name__ == "__main__":
    msg = " ".join(sys.argv[1:]) or "Your account is blocked. Send OTP now at bit.ly/abc"
    print(analyze(msg))
    print(explain(msg))
