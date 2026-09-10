# Study Bot

A Streamlit-based study assistant that can ingest PDF notes, build a local FAISS knowledge base, and answer questions using Google Generative AI.

## Run locally

1. Create and activate a Python virtual environment.
2. Install the required project dependencies.
3. Copy `.streamlit/secrets.toml.example` to `.streamlit/secrets.toml` and add your Google API key.
4. Start the app:

```powershell
streamlit run app.py
```

Local credentials, profiles, and document indexes are intentionally excluded from version control.


## Deploy to Streamlit Community Cloud

1. Push this repository to GitHub.
2. In [Streamlit Community Cloud](https://share.streamlit.io/), create an app using the `main` branch and `app.py` as the entrypoint.
3. In **Advanced settings** > **Secrets**, add:

```toml
GOOGLE_API_KEY = "your-google-api-key"
```

The app stores uploaded documents, the FAISS index, and profiles on local disk. On Community Cloud, that data is temporary and may reset after a restart or redeploy. Use managed cloud storage and a database before relying on it for persistent or multi-user data.
