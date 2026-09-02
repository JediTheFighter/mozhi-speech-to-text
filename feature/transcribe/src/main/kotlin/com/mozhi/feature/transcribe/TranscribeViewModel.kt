package com.mozhi.feature.transcribe

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mozhi.core.common.MozhiLog
import com.mozhi.domain.model.ListeningState
import com.mozhi.domain.model.TranscriptionSnapshot
import com.mozhi.domain.usecase.ObserveGeminiApiKeyUseCase
import com.mozhi.domain.usecase.ObserveTranscriptionUseCase
import com.mozhi.domain.usecase.SaveGeminiApiKeyUseCase
import com.mozhi.domain.usecase.StartListeningUseCase
import com.mozhi.domain.usecase.StopListeningUseCase
import com.mozhi.domain.usecase.TranslateTranscriptUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TranscribeUiState(
    val snapshot: TranscriptionSnapshot = TranscriptionSnapshot(),
    val listeningState: ListeningState = ListeningState.Idle,
    val sessionActive: Boolean = false,
    val permissionNeeded: Boolean = false,
    val permissionPermanentlyDenied: Boolean = false,
    val selectedModelReady: Boolean = false,
    val catalogLoaded: Boolean = true,
    val selectedModelName: String = MalayalamCopy.GeminiEngine,
    val showModelPrompt: Boolean = false,
    val downloading: Boolean = false,
    val downloadProgress: Float? = null,
    val errorMessage: String? = null,
    val translationEnabled: Boolean = false,
    val apiKeyDraft: String = "",
    val debugLine: String = "open logcat filter MozhiSTT — then tap the mic",
) {
    val listening: Boolean get() = sessionActive
}

private data class SessionChrome(
    val active: Boolean = false,
    val showPrompt: Boolean = false,
)

@HiltViewModel
class TranscribeViewModel @Inject constructor(
    @ApplicationContext private val app: Context,
    observeTranscription: ObserveTranscriptionUseCase,
    observeGeminiApiKey: ObserveGeminiApiKeyUseCase,
    private val startListening: StartListeningUseCase,
    private val stopListening: StopListeningUseCase,
    private val saveGeminiApiKey: SaveGeminiApiKeyUseCase,
    translateTranscript: TranslateTranscriptUseCase,
) : ViewModel() {

    private val permissionDeniedForever = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)
    private val chrome = MutableStateFlow(SessionChrome())
    private val apiKeyDraft = MutableStateFlow("")
    private var sessionGeneration = 0
    private var startupPromptShown = false

    init {
        MozhiLog.i("TranscribeViewModel created")
        viewModelScope.launch {
            observeGeminiApiKey().collect { key ->
                if (apiKeyDraft.value.isBlank() && key.isNotBlank()) {
                    apiKeyDraft.value = key
                }
            }
        }
    }

    val uiState: StateFlow<TranscribeUiState> = combine(
        combine(observeTranscription(), observeGeminiApiKey(), permissionDeniedForever) { snap, apiKey, deniedForever ->
            Triple(snap, apiKey, deniedForever)
        },
        combine(error, chrome, apiKeyDraft) { err, session, draft ->
            Triple(err, session, draft)
        },
    ) { left, right ->
        val (snap, apiKey, deniedForever) = left
        val (err, session, draft) = right
        val keyReady = apiKey.isNotBlank()
        TranscribeUiState(
            snapshot = snap,
            listeningState = when {
                session.active || snap.isListening -> ListeningState.Listening
                snap.isProcessing -> ListeningState.Processing
                err != null -> ListeningState.Error
                else -> ListeningState.Idle
            },
            sessionActive = session.active,
            permissionNeeded = !hasMicPermission(),
            permissionPermanentlyDenied = deniedForever,
            selectedModelReady = keyReady,
            catalogLoaded = true,
            selectedModelName = MalayalamCopy.GeminiEngine,
            showModelPrompt = session.showPrompt,
            errorMessage = err,
            translationEnabled = translateTranscript.isAvailable,
            apiKeyDraft = draft,
            debugLine = "orb=${if (session.active) "stop" else "mic"} snapListen=${snap.isListening} proc=${snap.isProcessing} ${snap.debugLine}",
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TranscribeUiState())

    fun onStartupIfModelMissing() {
        if (startupPromptShown) return
        startupPromptShown = true
        if (!uiState.value.selectedModelReady) {
            chrome.update { it.copy(showPrompt = true) }
        }
    }

    fun showModelPrompt() {
        chrome.update { it.copy(showPrompt = true) }
    }

    fun dismissModelPrompt() {
        chrome.update { it.copy(showPrompt = false) }
    }

    fun onApiKeyDraftChange(value: String) {
        apiKeyDraft.value = value
    }

    fun saveApiKey() {
        val value = apiKeyDraft.value.trim()
        if (value.isBlank()) {
            error.value = MalayalamCopy.KeyMissing
            return
        }
        viewModelScope.launch {
            saveGeminiApiKey(value)
            chrome.update { it.copy(showPrompt = false) }
        }
    }

    fun onMicToggled(permanentlyDenied: Boolean) {
        permissionDeniedForever.value = permanentlyDenied
        MozhiLog.i(
            "mic tap active=${chrome.value.active} snapListening=${uiState.value.snapshot.isListening} ready=${uiState.value.selectedModelReady}",
        )
        if (chrome.value.active) {
            stopSession()
            return
        }
        if (!uiState.value.selectedModelReady) {
            showModelPrompt()
            return
        }
        if (!hasMicPermission()) {
            return
        }
        startSession()
    }

    fun onPermissionResult(granted: Boolean, permanentlyDenied: Boolean) {
        permissionDeniedForever.value = permanentlyDenied && !granted
        if (granted) {
            if (!uiState.value.selectedModelReady) showModelPrompt()
            else startSession()
        } else {
            error.value = MalayalamCopy.PermissionDenied
        }
    }

    fun clearError() {
        error.value = null
    }

    private fun startSession() {
        val generation = ++sessionGeneration
        chrome.update { it.copy(active = true) }
        error.value = null
        MozhiLog.i("startSession gen=$generation")
        viewModelScope.launch {
            runCatching { startListening() }
                .onFailure { t ->
                    MozhiLog.e("startSession failed gen=$generation", t)
                    if (generation == sessionGeneration) {
                        chrome.update { it.copy(active = false) }
                        error.value = t.message ?: MalayalamCopy.StartFailed
                    }
                }
                .onSuccess {
                    MozhiLog.i("startSession engine ready gen=$generation current=$sessionGeneration")
                    if (generation != sessionGeneration) {
                        runCatching { stopListening() }
                    }
                }
        }
    }

    private fun stopSession() {
        sessionGeneration++
        chrome.update { it.copy(active = false) }
        MozhiLog.i("stopSession gen=$sessionGeneration")
        viewModelScope.launch {
            runCatching { stopListening() }
                .onFailure { MozhiLog.e("stopSession failed", it) }
        }
    }

    private fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(app, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
}
