# മൊഴി (Mozhi) — Malayalam speech to text

Malayalam transcription in Jetpack Compose. Listen UI is the same orb + live transcript card.
Speech is sent to **Gemini Flash** over HTTPS (Google AI Studio API) so Malayalam comes back
quickly instead of waiting on on-device Whisper.

## Architecture

```
app                UI host, Hilt, navigation
feature:transcribe Live listen UI, Gemini API key, permission flow
feature:models     Optional local GGML catalog (not required to listen)
domain             Use cases, models
data               Gemini STT client, DataStore, microphone pipeline
core:stt           whisper.cpp JNI (unused by the listen path)
core:audio         AudioRecord 16 kHz mono
core:translation   TranslationEngine (disabled / cloud stub)
core:designsystem  Aurora background, listen orb
core:common        AudioConfig
```

## Requirements

- Android Studio Ladybug+ / AGP 8.8
- JDK 17
- Phone or emulator with microphone and internet
- A [Google AI Studio](https://aistudio.google.com/apikey) API key

## Build

```bash
chmod +x gradlew
cp local.properties.example local.properties
# set sdk.dir, optionally GEMINI_API_KEY=...

./gradlew :domain:test
./gradlew :app:assembleDebug
```

Open the app, paste the Gemini API key (gear icon), grant the microphone, and speak Malayalam.
Keep the app in the foreground while Gemini transcribes (usually a couple of seconds).

Do not commit API keys. `local.properties` is gitignored. The in-app key is stored in DataStore.

## Permissions

- `RECORD_AUDIO` is requested in the listen flow.
- `INTERNET` is used for Gemini `generateContent` (inline WAV, 16 kHz PCM).
