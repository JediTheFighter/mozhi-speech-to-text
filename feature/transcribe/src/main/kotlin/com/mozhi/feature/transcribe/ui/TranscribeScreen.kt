package com.mozhi.feature.transcribe.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.app.ActivityCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mozhi.core.designsystem.background.AuroraBackground
import com.mozhi.core.designsystem.components.ListenOrb
import com.mozhi.core.designsystem.theme.Mist
import com.mozhi.core.designsystem.theme.MistMuted
import com.mozhi.core.designsystem.theme.MonsoonTeal
import com.mozhi.core.designsystem.theme.NightRaised
import com.mozhi.feature.transcribe.MalayalamCopy
import com.mozhi.feature.transcribe.TranscribeUiState
import com.mozhi.feature.transcribe.TranscribeViewModel

@Composable
fun TranscribeRoute(
    onOpenModels: () -> Unit,
    viewModel: TranscribeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context as Activity
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val permanentlyDenied = !granted &&
            !ActivityCompat.shouldShowRequestPermissionRationale(
                activity,
                Manifest.permission.RECORD_AUDIO,
            )
        viewModel.onPermissionResult(granted, permanentlyDenied)
    }

    LaunchedEffect(state.catalogLoaded, state.selectedModelReady) {
        if (state.catalogLoaded && !state.selectedModelReady) {
            viewModel.onStartupIfModelMissing()
        }
    }

    TranscribeScreen(
        state = state,
        onToggleListen = {
            when {
                state.listening -> viewModel.onMicToggled(state.permissionPermanentlyDenied)
                !state.selectedModelReady -> viewModel.showModelPrompt()
                state.permissionNeeded -> launcher.launch(Manifest.permission.RECORD_AUDIO)
                else -> viewModel.onMicToggled(state.permissionPermanentlyDenied)
            }
        },
        onOpenModels = onOpenModels,
        onOpenSettings = {
            context.startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", context.packageName, null),
                ),
            )
        },
        onClearError = viewModel::clearError,
        onDismissPrompt = viewModel::dismissModelPrompt,
        onConfirmDownload = viewModel::downloadDefaultModel,
    )
}

@Composable
fun TranscribeScreen(
    state: TranscribeUiState,
    onToggleListen: () -> Unit,
    onOpenModels: () -> Unit,
    onOpenSettings: () -> Unit,
    onClearError: () -> Unit,
    onDismissPrompt: () -> Unit,
    onConfirmDownload: () -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    val clipboard = LocalClipboardManager.current
    LaunchedEffect(state.errorMessage) {
        val msg = state.errorMessage ?: return@LaunchedEffect
        snackbar.showSnackbar(msg)
        onClearError()
    }

    AuroraBackground(listening = state.listening) {
        Box(Modifier.fillMaxSize().statusBarsPadding()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 22.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            "മൊഴി",
                            style = MaterialTheme.typography.headlineMedium,
                            color = Mist,
                        )
                        Text(
                            MalayalamCopy.AppSubtitle,
                            color = MistMuted,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    IconButton(onClick = onOpenModels) {
                        Icon(
                            Icons.Outlined.Tune,
                            contentDescription = MalayalamCopy.Models,
                            tint = Mist,
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))
                LiveTranscriptCard(
                    committed = state.snapshot.committedText,
                    partial = state.snapshot.partialText,
                    listening = state.listening,
                    processing = state.snapshot.isProcessing,
                    onCopy = {
                        val text = state.snapshot.displayText
                        if (text.isNotBlank()) clipboard.setText(AnnotatedString(text))
                    },
                    modifier = Modifier.weight(1f),
                )

                Spacer(Modifier.height(12.dp))
                StatusRow(state)
                Box(Modifier.height(36.dp)) {
                    if (state.permissionPermanentlyDenied) {
                        TextButton(onClick = onOpenSettings) {
                            Text(MalayalamCopy.OpenMicSettings, color = MonsoonTeal)
                        }
                    }
                }
                Column(
                    Modifier.fillMaxWidth().padding(bottom = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    ListenOrb(
                        listening = state.listening,
                        audioLevel = state.snapshot.audioLevel,
                        onToggle = onToggleListen,
                    )
                    Spacer(Modifier.height(10.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        Text(
                            text = when {
                                state.listening -> MalayalamCopy.HintListening
                                !state.selectedModelReady -> MalayalamCopy.HintNoModel
                                state.permissionNeeded -> MalayalamCopy.HintPermission
                                else -> MalayalamCopy.HintTap
                            },
                            color = MistMuted,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                        )
                    }
                }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(16.dp))
        }
    }

    if (state.showModelPrompt) {
        AlertDialog(
            onDismissRequest = onDismissPrompt,
            title = { Text(MalayalamCopy.DialogTitle, color = Mist) },
            text = { Text(MalayalamCopy.DialogBody, color = MistMuted) },
            confirmButton = {
                Button(onClick = onConfirmDownload) { Text(MalayalamCopy.DialogOk) }
            },
            dismissButton = {
                TextButton(onClick = onDismissPrompt) { Text(MalayalamCopy.DialogCancel, color = Mist) }
            },
            containerColor = NightRaised,
        )
    }

    if (state.downloading) {
        Dialog(
            onDismissRequest = {},
            properties = DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
            ),
        ) {
            Column(
                Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(NightRaised)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator(color = MonsoonTeal)
                Spacer(Modifier.height(16.dp))
                Text(MalayalamCopy.LoaderTitle, style = MaterialTheme.typography.titleLarge, color = Mist)
                Spacer(Modifier.height(8.dp))
                Text(MalayalamCopy.LoaderBody, color = MistMuted, textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                LinearProgressIndicator(
                    progress = { state.downloadProgress ?: 0f },
                    modifier = Modifier.fillMaxWidth(),
                    color = MonsoonTeal,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "${(((state.downloadProgress ?: 0f) * 100).toInt()).coerceIn(0, 100)}%",
                    color = MonsoonTeal,
                )
            }
        }
    }
}

@Composable
private fun LiveTranscriptCard(
    committed: String,
    partial: String,
    listening: Boolean,
    processing: Boolean,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val placeholder = if (listening) MalayalamCopy.PlaceholderListening else MalayalamCopy.PlaceholderIdle
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.verticalGradient(
                    listOf(NightRaised.copy(alpha = 0.92f), Color(0xFF0E1528).copy(alpha = 0.88f)),
                ),
            )
            .padding(20.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                if (processing) MalayalamCopy.Decoding else if (listening) MalayalamCopy.Live else MalayalamCopy.Transcript,
                color = MonsoonTeal,
                style = MaterialTheme.typography.labelLarge,
            )
            IconButton(onClick = onCopy) {
                Icon(
                    Icons.Outlined.ContentCopy,
                    contentDescription = MalayalamCopy.Copy,
                    tint = Mist,
                )
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            AnimatedContent(
                targetState = committed to partial,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "transcript",
            ) { (c, p) ->
                val text = buildAnnotatedString {
                    if (c.isBlank() && p.isBlank()) {
                        withStyle(SpanStyle(color = MistMuted)) { append(placeholder) }
                    } else {
                        withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.Medium)) {
                            append(c)
                        }
                        if (c.isNotBlank() && p.isNotBlank()) append(" ")
                        withStyle(SpanStyle(color = MonsoonTeal.copy(alpha = 0.92f))) {
                            append(p)
                        }
                        if (listening) {
                            withStyle(SpanStyle(color = MonsoonTeal)) { append(" ▍") }
                        }
                    }
                }
                Text(text, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun StatusRow(state: TranscribeUiState) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = buildString {
                append(state.selectedModelName.ifBlank { MalayalamCopy.NoModel })
                append("  ·  ")
                append(MalayalamCopy.LocalWhisper)
            },
            color = MistMuted,
            style = MaterialTheme.typography.bodyMedium,
        )
        if (state.debugLine.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = state.debugLine,
                color = MonsoonTeal.copy(alpha = 0.85f),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 3,
            )
        }
    }
}
