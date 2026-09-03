package com.mozhi.feature.transcribe.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ScrollState
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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

    TranscribeScreen(
        state = state,
        onToggleListen = {
            when {
                state.listening -> viewModel.onMicToggled(state.permissionPermanentlyDenied)
                !state.selectedModelReady -> viewModel.onMicToggled(state.permissionPermanentlyDenied)
                state.permissionNeeded -> launcher.launch(Manifest.permission.RECORD_AUDIO)
                else -> viewModel.onMicToggled(state.permissionPermanentlyDenied)
            }
        },
        onOpenSettings = {
            context.startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", context.packageName, null),
                ),
            )
        },
        onClearTranscript = viewModel::clearTranscript,
    )
}

@Composable
fun TranscribeScreen(
    state: TranscribeUiState,
    onToggleListen: () -> Unit,
    onOpenSettings: () -> Unit,
    onClearTranscript: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val view = LocalView.current
    val keepAwake = state.listening || state.snapshot.isProcessing
    DisposableEffect(keepAwake) {
        val window = (view.context as? Activity)?.window
        if (keepAwake) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    AuroraBackground(listening = state.listening) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 22.dp),
        ) {
            Text(
                "മൊഴി",
                style = MaterialTheme.typography.headlineMedium,
                color = Mist,
                modifier = Modifier.padding(top = 8.dp),
            )

            Spacer(Modifier.height(18.dp))
            LiveTranscriptCard(
                committed = state.snapshot.committedText,
                listening = state.listening,
                processing = state.snapshot.isProcessing,
                onCopy = {
                    val text = state.snapshot.displayText
                    if (text.isNotBlank()) clipboard.setText(AnnotatedString(text))
                },
                onClear = onClearTranscript,
                modifier = Modifier.weight(1f),
            )

            Spacer(Modifier.height(12.dp))
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
                        .height(52.dp),
                    contentAlignment = Alignment.TopCenter,
                ) {
                val promptError = state.errorMessage
                Text(
                    text = when {
                        !promptError.isNullOrBlank() -> promptError
                        state.snapshot.isProcessing -> ""
                        state.listening -> MalayalamCopy.HintListening
                        !state.selectedModelReady -> MalayalamCopy.HintNoModel
                        state.permissionNeeded -> MalayalamCopy.HintPermission
                        else -> MalayalamCopy.HintTap
                    },
                        color = if (!promptError.isNullOrBlank()) Color(0xFFFF8A80) else MistMuted,
                        textAlign = TextAlign.Center,
                        maxLines = 3,
                    )
                }
            }
        }
    }
}

@Composable
private fun LiveTranscriptCard(
    committed: String,
    listening: Boolean,
    processing: Boolean,
    onCopy: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState()
    LaunchedEffect(committed, processing) {
        scroll.animateScrollTo(scroll.maxValue)
    }
    val placeholder = if (listening) MalayalamCopy.PlaceholderListening else MalayalamCopy.PlaceholderIdle
    val status = when {
        processing -> MalayalamCopy.Decoding
        listening -> MalayalamCopy.Live
        else -> MalayalamCopy.Transcript
    }
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
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                status,
                color = MonsoonTeal,
                style = MaterialTheme.typography.labelLarge,
            )
            Row {
                IconButton(onClick = onCopy, enabled = committed.isNotBlank()) {
                    Icon(
                        Icons.Outlined.ContentCopy,
                        contentDescription = MalayalamCopy.Copy,
                        tint = if (committed.isNotBlank()) Mist else MistMuted.copy(alpha = 0.4f),
                    )
                }
                IconButton(onClick = onClear, enabled = committed.isNotBlank() && !processing) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = MalayalamCopy.Clear,
                        tint = if (committed.isNotBlank() && !processing) Mist else MistMuted.copy(alpha = 0.4f),
                    )
                }
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .verticalScrollbar(scroll),
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(scroll)
                    .padding(end = 10.dp)
                    .animateContentSize(tween(320, easing = FastOutSlowInEasing)),
            ) {
                if (committed.isBlank() && !processing) {
                    Text(
                        placeholder,
                        color = MistMuted,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                } else if (committed.isNotBlank()) {
                    Text(
                        committed,
                        color = Color.White,
                        fontWeight = FontWeight.Medium,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                AnimatedVisibility(
                    visible = processing,
                    enter = fadeIn(tween(220)) + expandVertically(tween(280)),
                    exit = fadeOut(tween(180)) + shrinkVertically(tween(180)),
                ) {
                    TranscribingLoader(Modifier.padding(top = if (committed.isNotBlank()) 16.dp else 8.dp))
                }
            }
        }
    }
}

@Composable
private fun TranscribingLoader(modifier: Modifier = Modifier) {
    val pulse = rememberInfiniteTransition(label = "transcribe-pulse")
    val alpha by pulse.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "loader-alpha",
    )
    Row(
        modifier.graphicsLayer { this.alpha = alpha },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        repeat(3) { index ->
            val delay = index * 120
            val dot by pulse.animateFloat(
                initialValue = 0.55f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(650, delayMillis = delay, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "dot-$index",
            )
            Box(
                Modifier
                    .size((7 + 3 * dot).dp)
                    .clip(CircleShape)
                    .background(MonsoonTeal.copy(alpha = 0.35f + 0.5f * dot)),
            )
        }
        Text(
            MalayalamCopy.Decoding,
            color = MonsoonTeal,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private fun Modifier.verticalScrollbar(
    state: ScrollState,
    color: Color = MonsoonTeal.copy(alpha = 0.55f),
    thickness: Dp = 3.dp,
): Modifier = drawWithContent {
    drawContent()
    val max = state.maxValue
    if (max <= 0) return@drawWithContent
    val view = size.height
    val bar = (view * view / (view + max)).coerceIn(28.dp.toPx(), view * 0.45f)
    val y = state.value / max.toFloat() * (view - bar)
    drawRoundRect(
        color = color,
        topLeft = Offset(size.width - thickness.toPx(), y),
        size = Size(thickness.toPx(), bar),
        cornerRadius = CornerRadius(thickness.toPx()),
    )
}
