package com.flow.presentation.timer

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.flow.ui.theme.NeonGreen
import com.flow.ui.theme.SurfaceDark

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimerPanel(
    onDismiss: () -> Unit,
    viewModel: TimerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var customMinutes by remember { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Start the foreground service when timer starts; stop it on dismiss
    LaunchedEffect(uiState.isRunning) {
        if (uiState.isRunning) {
            TimerForegroundService.start(context, uiState.remainingSeconds)
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            if (uiState.isRunning) TimerForegroundService.stop(context)
            viewModel.dismiss()
            onDismiss()
        },
        sheetState = sheetState,
        containerColor = SurfaceDark
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Focus Timer", style = MaterialTheme.typography.headlineSmall, color = NeonGreen)
            Spacer(modifier = Modifier.height(16.dp))

            if (!uiState.isRunning && !uiState.isPaused && !uiState.isFinished) {
                // Duration selection
                Text("Select Duration", style = MaterialTheme.typography.labelLarge, color = Color.White)
                Spacer(modifier = Modifier.height(12.dp))

                val presets = listOf(5, 10, 15, 20, 25, 30)
                val selectedMin = uiState.durationSeconds / 60
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    presets.take(3).forEach { min ->
                        FilterChip(
                            selected = selectedMin == min,
                            onClick = { viewModel.setDuration(min * 60); customMinutes = "" },
                            label = { Text("${min}m") },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = NeonGreen, selectedLabelColor = Color.Black),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    presets.drop(3).forEach { min ->
                        FilterChip(
                            selected = selectedMin == min,
                            onClick = { viewModel.setDuration(min * 60); customMinutes = "" },
                            label = { Text("${min}m") },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = NeonGreen, selectedLabelColor = Color.Black),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = customMinutes,
                    onValueChange = {
                        if (it.all { c -> c.isDigit() } && it.length <= 3) {
                            customMinutes = it
                            it.toIntOrNull()?.let { min -> if (min > 0) viewModel.setDuration(min * 60) }
                        }
                    },
                    label = { Text("Custom (minutes)") },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                        cursorColor = NeonGreen, focusedBorderColor = NeonGreen
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = { viewModel.start() },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = Color.Black),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Start", fontWeight = FontWeight.Bold) }

            } else {
                // Countdown display
                val mins = uiState.remainingSeconds / 60
                val secs = uiState.remainingSeconds % 60
                if (uiState.isFinished) {
                    Text("Time is up! 🎉", style = MaterialTheme.typography.headlineLarge, color = NeonGreen)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Take a break!", color = Color.Gray, style = MaterialTheme.typography.bodyLarge)
                } else {
                    Text(
                        text = "%02d:%02d".format(mins, secs),
                        style = MaterialTheme.typography.displayLarge,
                        color = NeonGreen, fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(if (uiState.isPaused) "Paused" else "Focusing…", color = Color.Gray)
                }
                Spacer(modifier = Modifier.height(24.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!uiState.isFinished) {
                        if (uiState.isRunning) {
                            Button(
                                onClick = { viewModel.pause(); TimerForegroundService.stop(context) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Gray),
                                modifier = Modifier.weight(1f)
                            ) { Text("Pause") }
                        } else {
                            Button(
                                onClick = { viewModel.resume(); TimerForegroundService.start(context, uiState.remainingSeconds) },
                                colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = Color.Black),
                                modifier = Modifier.weight(1f)
                            ) { Text("Resume") }
                        }
                    }
                    OutlinedButton(
                        onClick = { viewModel.reset(); TimerForegroundService.stop(context) },
                        modifier = Modifier.weight(1f)
                    ) { Text("Reset", color = Color.White) }
                }
            }
        }
    }
}
