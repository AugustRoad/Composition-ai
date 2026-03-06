# Composition

An Android camera side project I built to experiment with two things:

1. Better manual camera controls with CameraX
2. A Python multi-agent backend (Google ADK) that can critique shots from live video frames
3. Image Overlay function and edge detection function to help users with camera shot alignment

The app runs like a normal camera app, but when AI mode is enabled it grabs recent preview frames and sends them with your prompt to the ADK agents.

## What It Can Do

- Camera modes: `Portrait`, `Photo`, `Video`, `Pro`
- Pro controls: ISO, shutter speed, white balance, focus, EV
- Composition helpers: grid lines, level indicator, focus square
- Overlay tools: edge-detected reference image with opacity slider
- AI mode: send a prompt + current frame and get feedback

## AI Agent Setup (High Level)

The backend lives in `photography_agents/` and is split into three agents:

- `OrchestratorAgent`: entry point, routes work to other agents
- `AnalysisAgent`: gives composition/lighting/settings/angle guidance
- `SearchAgent`: tries to infer location context and find similar photos

The Android app talks to the backend over HTTP from `MainActivity.kt`.

## Project Structure

```text
app/                     Android app (Kotlin + CameraX)
photography_agents/      Python ADK backend
startup.md               Cloud deployment notes
```

## Local Run

### 1. Start the Python agent backend

```bash
cd photography_agents
cp .env.example .env
# add GOOGLE_API_KEY in .env
pip install -r requirements.txt
adk web ./
```

### 2. Run Android app

- Open project in Android Studio
- Sync Gradle
- Run on emulator/device

Default endpoint in app points to local machine via emulator alias:

`http://10.0.2.2:8000/api/v1/invoke`

If using a physical device, replace it with your machine LAN IP (or Cloud Run URL).

## Using AI Mode

1. Open app and grant permissions
2. Tap the AI button to enable agent mode
3. Frame your shot
4. Enter prompt and send
5. Read response in the AI overlay

Example prompts:

- `How can I improve composition here?`
- `Is the lighting too harsh?`
- `Suggest settings for this indoor scene`

## Notes

- App is locked to portrait mode
- AI mode samples frames periodically to limit overhead
- If AI responses fail, check backend is running and endpoint matches

## Deploying to Google Cloud

See `startup.md` for Cloud Run steps.

## License

No license selected yet.
