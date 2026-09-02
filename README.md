# മൊഴി (Mozhi) — Malayalam speech to text

Malayalam transcription in Jetpack Compose. Listen UI is the same orb + transcript card.
Speech is recorded on the phone, then sent once to **Gemini Flash** when you tap stop.

## Gemini API key

Put a [Google AI Studio](https://aistudio.google.com/apikey) key in gitignored `local.properties`:

```
sdk.dir=/Users/YOU/Library/Android/sdk
GEMINI_API_KEY=AIza...
```

Then **rebuild and reinstall** (`./gradlew :app:assembleDebug`). The key is baked into `BuildConfig` at compile time. There is no in-app paste dialog.

Typical AI Studio keys start with `AIza` and are ~39 characters.

## Build

```bash
chmod +x gradlew
cp local.properties.example local.properties
# set sdk.dir and GEMINI_API_KEY

./gradlew :domain:test
./gradlew :app:assembleDebug
```

Grant the microphone, tap the orb, speak Malayalam, tap stop. Keep the app open until the transcript card fills or shows an error.

Do not commit API keys.

## Permissions

- `RECORD_AUDIO` is requested in the listen flow.
- `INTERNET` is used for one Gemini `generateContent` call per listen session (inline WAV, 16 kHz PCM).
