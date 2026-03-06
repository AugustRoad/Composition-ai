# Deployment & Startup Guide

## 1. Host on Google Cloud (from Mac Terminal)

Authenticate your Google Cloud CLI and configure your target project:

```bash
gcloud auth login
gcloud config set project YOUR_PROJECT_ID
```

Enable the necessary APIs for deployment:

```bash
gcloud services enable run.googleapis.com cloudbuild.googleapis.com artifactregistry.googleapis.com
```

Navigate to your agent directory and deploy using the Google ADK CLI:

```bash
cd Composition/photography_agents

export GOOGLE_CLOUD_PROJECT="YOUR_PROJECT_ID"
export GOOGLE_CLOUD_LOCATION="us-central1"

adk deploy cloud_run \
  --project=$GOOGLE_CLOUD_PROJECT \
  --region=$GOOGLE_CLOUD_LOCATION \
  --service_name=photography-agents \
  --app_name=photography_agents \
  --no-allow-unauthenticated=false \
  --set-env-vars=GOOGLE_API_KEY="your_google_api_key" \
  ./
```

## 2. Connect the Android App

Once deployed, the terminal will output a secure Service URL (e.g., `https://photography-agents-xyz.run.app`). 

1. **Update Base URL**: In your `MainActivity.kt`, change the `agentEndpoint` variable from the local emulator URL `http://10.0.2.2:8000/api/v1/invoke` to your new Cloud Run Service URL appended with the endpoint path (e.g., `https://photography-agents-xyz.run.app/api/v1/invoke`).
2. **Hook up UI Components**: The existing `activity_main.xml` is fully configured for this. Ensure your Android logic handles the UI:
    * `btn_ai_mode`: Toggles the visibility of the agent interface and hooks the `ImageAnalysis` camera capture.
    * `et_ai_prompt`: Captures your text instructions for the agent (e.g., "Analyze the lighting").
    * `btn_ai_send`: Extracts the latest encoded video frame, packages it with your prompt, and sends the HTTP request to your Cloud Run endpoint.
    * `tv_ai_response`: Renders the textual feedback returned by the orchestrator agent and its sub-agents.
