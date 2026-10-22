# Data

Put raw downloads in `data/raw/` (not uploaded to GitHub). Cleaned file goes to `data/clean.csv`
with two columns: `text`, `label` (0 = safe, 1 = phishing/spam).

Sources (free):
- UCI SMS Spam Collection (file name `SMSSpamCollection`, tab separated)
- A phishing SMS dataset from Kaggle (CSV with text + label columns)

Run: `python src/prepare_data.py --input data/raw/SMSSpamCollection`
