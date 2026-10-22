"""Train the baseline text model (TF-IDF + Logistic Regression).

Usage: python src/train.py
Input : data/clean.csv   Output: models/text_model.joblib, results/baseline_metrics.json
"""
import json
import joblib
import pandas as pd
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from sklearn.model_selection import train_test_split
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.linear_model import LogisticRegression
from sklearn.pipeline import Pipeline
from sklearn.metrics import accuracy_score, precision_score, recall_score, f1_score, confusion_matrix, ConfusionMatrixDisplay

df = pd.read_csv("data/clean.csv")
X_tr, X_te, y_tr, y_te = train_test_split(df["text"], df["label"], test_size=0.2, stratify=df["label"], random_state=42)

model = Pipeline([
    ("tfidf", TfidfVectorizer(ngram_range=(1, 2), sublinear_tf=True, lowercase=True)),
    ("clf", LogisticRegression(class_weight="balanced", max_iter=1000)),
])
model.fit(X_tr, y_tr)
pred = model.predict(X_te)

m = {"accuracy": accuracy_score(y_te, pred), "precision": precision_score(y_te, pred),
     "recall": recall_score(y_te, pred), "f1": f1_score(y_te, pred)}
print(json.dumps(m, indent=2))
json.dump(m, open("results/baseline_metrics.json", "w"), indent=2)

ConfusionMatrixDisplay(confusion_matrix(y_te, pred), display_labels=["safe", "fraud"]).plot(cmap="Blues")
plt.title("Baseline confusion matrix")
plt.savefig("results/baseline_confusion.png", dpi=150, bbox_inches="tight")

joblib.dump(model, "models/text_model.joblib")
print("Saved models/text_model.joblib")
