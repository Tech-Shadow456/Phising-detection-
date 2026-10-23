# PreClick Guard - AI Phishing Message Detection for Mobile Users
Samsung Innovation Campus 2.0 | Project SIC 2.0-23

An Android-focused AI guard that scans incoming messages, checks links safely in a backend
before the user clicks, and explains in plain words why something is dangerous.

## How it works
1. **Text model** (TF-IDF + Logistic Regression, optional DistilBERT) scores each message.
2. **Rule flags** add reasons: urgency, OTP/card requests, prizes, shortened or fake-brand links.
3. **Link checker backend** follows redirects safely, detects file downloads and login forms,
   checks domain age and reputation, and returns a short note per link.
4. **Android app** (ON/OFF button, always-on service) shows alerts and link notes. See `android-app/`.

## Repo structure
```
data/        datasets (raw files are not uploaded)
src/         prepare_data.py, train.py, scoring.py, rules.py, evaluate.py, train_bert.py
backend/     app.py (FastAPI link checker)
research/    phishing research notes and test_set.csv
results/     metrics, confusion matrices, errors.csv
models/      trained model files
android-app/ Android Studio project
presentation/ slides
```

## Setup
```
git clone <your-repo-link>
cd phishing-message-detection
pip install -r requirements.txt
```

## Train (baseline)
```
python src/prepare_data.py --input data/raw/SMSSpamCollection
python src/train.py
python src/scoring.py "Your SBI account is blocked. Share OTP at bit.ly/abc"
```

## Evaluate (Research and Testing)
```
python src/evaluate.py --test research/test_set.csv
```

## Run the link checker
```
export GSB_KEY=your_google_safe_browsing_key   # optional, free
export VT_KEY=your_virustotal_key              # optional, free
uvicorn backend.app:app --reload
```
Open `http://127.0.0.1:8000/check?url=https://example.com`.
Never test suspicious links on a personal device. Use official test pages or links you create.

## Optional: DistilBERT (Google Colab, GPU)
```
pip install -r requirements-bert.txt
python src/train_bert.py
```

## Team
| Role | Member |
|---|---|
| Team Leader | |
| Data Lead | |
| Model Builder | |
| Research and Testing | |
| Presentation and Demo | |

## Limits
Android only. Calls are checked by number only (no audio). No free review source exists for random links,
so reputation signals are used. Prototype: Google restricts accessibility permission for store apps.
