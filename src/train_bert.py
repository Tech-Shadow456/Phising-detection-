"""OPTIONAL (Day 8): fine-tune DistilBERT. Run in Google Colab with a GPU.
Needs: pip install -r requirements-bert.txt
Usage: python src/train_bert.py
"""
import json
import numpy as np
import pandas as pd
from datasets import Dataset
from sklearn.model_selection import train_test_split
from sklearn.metrics import accuracy_score, precision_score, recall_score, f1_score
from transformers import AutoTokenizer, AutoModelForSequenceClassification, TrainingArguments, Trainer

NAME = "distilbert-base-uncased"
df = pd.read_csv("data/clean.csv")
tr, te = train_test_split(df, test_size=0.2, stratify=df["label"], random_state=42)

tok = AutoTokenizer.from_pretrained(NAME)
enc = lambda b: tok(b["text"], truncation=True, padding="max_length", max_length=128)
ds_tr = Dataset.from_pandas(tr.reset_index(drop=True)).map(enc, batched=True)
ds_te = Dataset.from_pandas(te.reset_index(drop=True)).map(enc, batched=True)

model = AutoModelForSequenceClassification.from_pretrained(NAME, num_labels=2)
args = TrainingArguments(output_dir="models/bert", num_train_epochs=3, per_device_train_batch_size=16,
                         learning_rate=2e-5, save_strategy="no", report_to="none")
trainer = Trainer(model=model, args=args, train_dataset=ds_tr)
trainer.train()

pred = np.argmax(trainer.predict(ds_te).predictions, axis=1)
y = te["label"].values
m = {"accuracy": accuracy_score(y, pred), "precision": precision_score(y, pred),
     "recall": recall_score(y, pred), "f1": f1_score(y, pred)}
print(json.dumps(m, indent=2))
json.dump(m, open("results/bert_metrics.json", "w"), indent=2)
trainer.save_model("models/bert")
tok.save_pretrained("models/bert")
print("Saved models/bert (do not commit; upload to Drive or Hugging Face)")
