package com.mozhi.feature.transcribe

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mozhi.domain.model.ListeningState
import com.mozhi.domain.model.TranscriptionSnapshot
import com.mozhi.domain.usecase.ObserveModelCatalogUseCase
import com.mozhi.domain.usecase.ObserveTranscriptionUseCase
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
    val permissionNeeded: Boolean = false,
    val permissionPermanentlyDenied: Boolean = false,
    val selectedModelReady: Boolean = false,
    val selectedModelName: String = "",
    val errorMessage: String? = null,
    val translationHint: String = "Cloud translation can be enabled later without changing this screen.",
    val translationEnabled: Boolean = false,
)

@HiltViewModel
class TranscribeViewModel @Inject constructor(
    @ApplicationContext private val app: Context,
    observeTranscription: ObserveTranscriptionUseCase,
    observeModels: ObserveModelCatalogUseCase,
    private val startListening: StartListeningUseCase,
    private val stopListening: StopListeningUseCase,
    translateTranscript: TranslateTranscriptUseCase,
) : ViewModel() {

    private val permissionDeniedForever = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)
    private val listeningState = MutableStateFlow(ListeningState.Idle)

    val uiState: StateFlow<TranscribeUiState> = combine(
        observeTranscription(),
        observeModels(),
        permissionDeniedForever,
        error,
        listeningState,
    ) { snap, models, deniedForever, err, listen ->
        val selected = models.firstOrNull { it.selected }
        TranscribeUiState(
            snapshot = snap,
            listeningState = when {
                snap.isListening -> ListeningState.Listening
                snap.isProcessing -> ListeningState.Processing
                err != null -> ListeningState.Error
                else -> listen
            },
            permissionNeeded = !hasMicPermission(),
            permissionPermanentlyDenied = deniedForever,
            selectedModelReady = selected?.downloaded == true,
            selectedModelName = selected?.model?.displayName.orEmpty(),
            errorMessage = err,
            translationEnabled = translateTranscript.isAvailable,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TranscribeUiState())

    fun onMicToggled(permanentlyDenied: Boolean) {
        permissionDeniedForever.value = permanentlyDenied
        val listening = uiState.value.snapshot.isListening
        viewModelScope.launch {
            if (listening) {
                stopListening()
                listeningState.value = ListeningState.Idle
                return@launch
            }
            if (!hasMicPermission()) {
                listeningState.value = ListeningState.RequestingPermission
                return@launch
            }
            if (!uiState.value.selectedModelReady) {
                error.value = "Download a local model first."
                return@launch
            }
            error.value = null
            listeningState.value = ListeningState.Listening
            runCatching { startListening() }
                .onFailure { t ->
                    listeningState.value = ListeningState.Error
                    error.value = t.message ?: "Could not start transcription"
                }
        }
    }

    fun onPermissionResult(granted: Boolean, permanentlyDenied: Boolean) {
        permissionDeniedForever.value = permanentlyDenied && !granted
        if (granted) onMicToggled(false)
        else error.update { "Microphone permission is required for speech to text." }
    }

    fun clearError() {
        error.value = null
    }

    private fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(app, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
}
