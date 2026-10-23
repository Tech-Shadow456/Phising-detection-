"""Evaluate model-only vs hybrid (model + rules) on a test CSV (text,label).
Used by the Research and Testing role.

Usage: python src/evaluate.py --test research/test_set.csv
Outputs: results/eval_metrics.json, results/eval_confusion.png, results/errors.csv
"""
import argparse
import json
import pandas as pd
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from sklearn.metrics import accuracy_score, precision_score, recall_score, f1_score, confusion_matrix, ConfusionMatrixDisplay
from scoring import analyze

ap = argparse.ArgumentParser()
ap.add_argument("--test", default="research/test_set.csv")
a = ap.parse_args()

df = pd.read_csv(a.test)
res = [analyze(t) for t in df["text"]]
df["hybrid_pred"] = [int(r["level"] != "Safe") for r in res]
df["model_pred"] = [int(r["model_prob"] >= 0.5) for r in res]
df["level"] = [r["level"] for r in res]
df["reasons"] = ["; ".join(r["reasons"]) for r in res]


def metrics(pred):
    y = df["label"]
    return {"accuracy": accuracy_score(y, pred), "precision": precision_score(y, pred, zero_division=0),
            "recall": recall_score(y, pred, zero_division=0), "f1": f1_score(y, pred, zero_division=0)}


out = {"model_only": metrics(df["model_pred"]), "hybrid": metrics(df["hybrid_pred"])}
safe = df[df["label"] == 0]
out["false_alarm_rate_hybrid"] = float((safe["hybrid_pred"] == 1).mean()) if len(safe) else None
print(json.dumps(out, indent=2))
json.dump(out, open("results/eval_metrics.json", "w"), indent=2)

ConfusionMatrixDisplay(confusion_matrix(df["label"], df["hybrid_pred"], labels=[0, 1]),
                       display_labels=["safe", "fraud"]).plot(cmap="Blues")
plt.title("Hybrid confusion matrix")
plt.savefig("results/eval_confusion.png", dpi=150, bbox_inches="tight")

errors = df[df["label"] != df["hybrid_pred"]]
errors.to_csv("results/errors.csv", index=False)
print(f"{len(errors)} errors saved to results/errors.csv (use these for error analysis)")
