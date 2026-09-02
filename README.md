# മൊഴി (Mozhi) — Malayalam speech to text

On-device Malayalam transcription in Jetpack Compose. Audio never leaves the phone for STT.
Cloud translation is a later, optional module.

## Architecture

```
app                UI host, Hilt, navigation
feature:transcribe Live listen UI, permission flow
feature:models     Hugging Face GGML downloads
domain             Use cases, models, TranscriptMerger
data               Repositories, OkHttp downloader, DataStore
core:stt           whisper.cpp JNI + sliding-window streaming
core:audio         AudioRecord 16 kHz mono
core:translation   TranslationEngine (disabled / cloud stub)
core:designsystem  Aurora background, listen orb
core:common        AudioConfig
```

## Requirements

- Android Studio Ladybug+ / AGP 8.8
- JDK 17, NDK + CMake (SDK Manager)
- Phone or emulator with microphone (`arm64-v8a` or `x86_64`)

## Build

```bash
chmod +x gradlew scripts/setup-native.sh
# Optional: vendor whisper.cpp instead of CMake FetchContent
./scripts/setup-native.sh

# Point at your SDK
cp local.properties.example local.properties

./gradlew :domain:test
./gradlew :app:assembleDebug
```

First native build fetches [whisper.cpp v1.7.5](https://github.com/ggml-org/whisper.cpp) and
compiles `libmozhi-whisper.so`. Then open the app, download **Whisper Tiny Q5_1** (~31 MB from
Hugging Face), grant the microphone permission, and speak Malayalam.

## Permissions

- `RECORD_AUDIO` is requested in the listen flow with rationale and a Settings deep link when
  permanently denied.
- `INTERNET` is only for model download (HTTPS Hugging Face). Transcription is local.

## Custom Malayalam models

See [docs/MODELS.md](docs/MODELS.md) for Hugging Face picks, sizes, and GGML conversion.

## Cloud translation later

Replace the Hilt bind in `core/translation` from `DisabledTranslationEngine` to
`CloudTranslationEngine` and implement the API client. Domain and UI stay unchanged.
