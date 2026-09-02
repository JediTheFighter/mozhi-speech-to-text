package com.mozhi.feature.models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mozhi.domain.model.ModelInstallState
import com.mozhi.domain.usecase.DownloadSpeechModelUseCase
import com.mozhi.domain.usecase.ObserveModelCatalogUseCase
import com.mozhi.domain.usecase.SelectSpeechModelUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ModelsViewModel @Inject constructor(
    observeCatalog: ObserveModelCatalogUseCase,
    private val download: DownloadSpeechModelUseCase,
    private val select: SelectSpeechModelUseCase,
) : ViewModel() {

    val catalog: StateFlow<List<ModelInstallState>> = observeCatalog()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun downloadModel(id: String) {
        viewModelScope.launch { download(id) }
    }

    fun selectModel(id: String) {
        viewModelScope.launch { select(id) }
    }
}
