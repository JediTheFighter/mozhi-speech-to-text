# Malayalam on-device models

Mozhi transcribes **on device** with [whisper.cpp](https://github.com/ggml-org/whisper.cpp).
The Android JNI layer loads a **GGML `.bin`** file. Hugging Face Transformers checkpoints
(`.safetensors`) cannot be loaded directly — convert them first.

## What to run on a phone

| Goal | Model | Size | Notes |
| --- | --- | --- | --- |
| Smallest start | [`ggerganov/whisper.cpp`](https://huggingface.co/ggerganov/whisper.cpp) `ggml-tiny-q5_1.bin` | ~31 MB | Multilingual Tiny, Q5_1. Mozhi forces `language=ml`. Best default. |
| Better accuracy, still small | same repo `ggml-base-q5_1.bin` | ~57 MB | In-app optional download. |
| Quality on flagship | `ggml-small-q5_1.bin` | ~181 MB | In-app optional download. |
| Malayalam-finetuned, minimum size | [`parambharat/whisper-tiny-ml`](https://huggingface.co/parambharat/whisper-tiny-ml) | ~75 MB FP32 → ~31 MB Q5 | Convert + quantize. Tiny ML WER is high; use as a size baseline, not the quality pick. |
| Malayalam-finetuned, still phone-sized | [`parambharat/whisper-base-ml`](https://huggingface.co/parambharat/whisper-base-ml) | ~142 MB → ~57 MB Q5 | Better than tiny-ml. |
| Best Malayalam quality that still fits some phones | [`adalat-ai/whisper-small-ml-rmft`](https://huggingface.co/adalat-ai/whisper-small-ml-rmft) | Small (~244 MB FP) → ~180 MB Q5 | Stronger Indic fine-tune (Vividh-ASR). |
| Highest quality, large | [`thennal/whisper-medium-ml`](https://huggingface.co/thennal/whisper-medium-ml) already converted: [`sujithatz/ggml-whisper-medium-ml`](https://huggingface.co/sujithatz/ggml-whisper-medium-ml) | Q4_0 424 MB / Q5_0 514 MB | Too large for a “minimum size” app. Use only on high-RAM devices. |
| Mobile ACFT Small | [`Athulkrishna/Jithjacob123-whisper-small-Malayalam-acft`](https://huggingface.co/Athulkrishna/Jithjacob123-whisper-small-Malayalam-acft) | GGML q5 after notebook | Fine-tuned for short utterances (FUTO/ACFT). Good if you convert the notebook output. |

**Recommended path for this app:** ship/download **Tiny Q5_1** immediately, then convert
`parambharat/whisper-base-ml` or `adalat-ai/whisper-small-ml-rmft` and drop the `.bin` into
`files/models/` (same folder the downloader uses) when you want Malayalam-specialized weights.

There is **no official sub-30 MB Malayalam-only Whisper** that beats multilingual Tiny after
quantization. Tiny (~31 MB Q5) is the practical minimum. Distil-Whisper is English-only.

## Convert a Hugging Face Malayalam Whisper to GGML

From a machine with Python + a GPU (Colab T4 is enough):

```bash
git clone https://github.com/ggml-org/whisper.cpp
cd whisper.cpp
python -m pip install torch transformers

# Example: smallest Malayalam fine-tune
python models/convert-h5-to-ggml.py \
  parambharat/whisper-tiny-ml \
  ./models \
  ./models/ggml-whisper-tiny-ml.bin

cmake -B build
cmake --build build --target quantize -j
./build/bin/quantize \
  ./models/ggml-whisper-tiny-ml.bin \
  ./models/ggml-whisper-tiny-ml-q5_1.bin \
  q5_1
```

Copy `ggml-whisper-tiny-ml-q5_1.bin` onto the phone (or add a catalog entry in
`SpeechModelCatalog` with a Hugging Face `resolve/main/...` URL once you host the bin).

For short dictation without looping, run [FUTO ACFT](https://github.com/futo-org/whisper-acft)
on the fine-tune **before** GGML conversion. That is what the Athulkrishna notebooks do.

## Why not Transformers / ONNX in-app?

PyTorch Transformers is too heavy for a Compose APK. ONNX Runtime is viable but still larger
than whisper.cpp Q5. whisper.cpp is the high-performance local path: greedy decode, 2–6
threads, 8-second sliding windows, Malayalam forced in the decoder.

## Cloud translation later

Keep STT local. Bind `CloudTranslationEngine` in `core/translation/di/TranslationModule.kt`
instead of `DisabledTranslationEngine`, and implement the HTTP call. The transcribe UI already
reads `TranslateTranscriptUseCase.isAvailable`.
