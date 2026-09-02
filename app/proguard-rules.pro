-keep class com.mozhi.core.stt.whisper.WhisperLib { *; }
-keep class com.mozhi.core.stt.whisper.WhisperSegmentListener { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
