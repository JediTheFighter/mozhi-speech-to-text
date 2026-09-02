package com.mozhi.feature.models.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mozhi.core.designsystem.background.AuroraBackground
import com.mozhi.core.designsystem.theme.MistMuted
import com.mozhi.core.designsystem.theme.NightRaised
import com.mozhi.domain.model.ModelInstallState
import com.mozhi.feature.models.ModelsViewModel

@Composable
fun ModelsRoute(
    onBack: () -> Unit,
    viewModel: ModelsViewModel = hiltViewModel(),
) {
    val catalog by viewModel.catalog.collectAsStateWithLifecycle()
    ModelsScreen(
        catalog = catalog,
        onBack = onBack,
        onDownload = viewModel::downloadModel,
        onSelect = viewModel::selectModel,
    )
}

@Composable
fun ModelsScreen(
    catalog: List<ModelInstallState>,
    onBack: () -> Unit,
    onDownload: (String) -> Unit,
    onSelect: (String) -> Unit,
) {
    AuroraBackground {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                }
                Text("Local models", style = MaterialTheme.typography.titleLarge)
            }
            Text(
                "GGML Whisper files download from Hugging Face into app storage. Malayalam is forced at inference time. Fine-tuned Malayalam checkpoints need a one-time convert — see docs/MODELS.md.",
                color = MistMuted,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(12.dp))
            LazyColumn(
                contentPadding = PaddingValues(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(catalog, key = { it.model.id }) { item ->
                    ModelCard(item, onDownload, onSelect)
                }
            }
        }
    }
}

@Composable
private fun ModelCard(
    item: ModelInstallState,
    onDownload: (String) -> Unit,
    onSelect: (String) -> Unit,
) {
    val model = item.model
    Card(
        colors = CardDefaults.cardColors(containerColor = NightRaised.copy(alpha = 0.92f)),
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(model.displayName, style = MaterialTheme.typography.titleLarge)
            Text("${model.sizeLabel} · ${model.quantization} · ${model.huggingFaceRepo}", color = MistMuted)
            Spacer(Modifier.height(8.dp))
            Text(model.description, style = MaterialTheme.typography.bodyMedium)
            item.downloadProgress?.let { LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!item.downloaded) {
                    Button(onClick = { onDownload(model.id) }) { Text("Download") }
                } else if (item.selected) {
                    OutlinedButton(onClick = {}) { Text("Selected") }
                } else {
                    Button(onClick = { onSelect(model.id) }) { Text("Use this model") }
                }
            }
        }
    }
}
