"""Clean raw data into data/clean.csv (columns: text,label).

Usage:
  python src/prepare_data.py --input data/raw/SMSSpamCollection
  python src/prepare_data.py --input data/raw/SMSSpamCollection --extra data/raw/phishing.csv --extra-text-col text --extra-label-col label
"""
import argparse
import pandas as pd

ap = argparse.ArgumentParser()
ap.add_argument("--input", required=True, help="UCI SMSSpamCollection file (label<TAB>text)")
ap.add_argument("--extra", help="optional extra CSV with text and label columns")
ap.add_argument("--extra-text-col", default="text")
ap.add_argument("--extra-label-col", default="label")
ap.add_argument("--out", default="data/clean.csv")
a = ap.parse_args()

df = pd.read_csv(a.input, sep="\t", header=None, names=["label", "text"], encoding="latin-1")
df["label"] = df["label"].map({"ham": 0, "spam": 1})
frames = [df[["text", "label"]]]

if a.extra:
    ex = pd.read_csv(a.extra, encoding="latin-1")
    ex = ex.rename(columns={a.extra_text_col: "text", a.extra_label_col: "label"})[["text", "label"]]
    if ex["label"].dtype == object:
        ex["label"] = ex["label"].str.lower().map(
            {"ham": 0, "safe": 0, "legitimate": 0, "spam": 1, "phishing": 1, "smishing": 1, "fraud": 1})
    frames.append(ex)

data = pd.concat(frames, ignore_index=True).dropna()
data["text"] = data["text"].astype(str).str.strip()
data = data[data["text"].str.len() > 0].drop_duplicates(subset="text")
data["label"] = data["label"].astype(int)
data.to_csv(a.out, index=False)
print(f"Saved {len(data)} rows to {a.out}")
print(data["label"].value_counts().rename({0: "safe", 1: "fraud"}))
