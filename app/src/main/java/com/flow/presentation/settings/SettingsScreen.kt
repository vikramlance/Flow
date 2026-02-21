package com.flow.presentation.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.flow.ui.theme.NeonGreen
import com.flow.ui.theme.SurfaceDark

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var sliderPosition by remember(uiState.defaultTimerMinutes) {
        mutableFloatStateOf(uiState.defaultTimerMinutes.toFloat())
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", color = NeonGreen) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = NeonGreen)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceDark)
            )
        },
        containerColor = SurfaceDark
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(24.dp)
                .fillMaxSize()
        ) {
            Text("Focus Timer", style = MaterialTheme.typography.titleMedium, color = Color.White)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Default timer duration: ${sliderPosition.toInt()} min",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray
            )
            Slider(
                value = sliderPosition,
                onValueChange = { sliderPosition = it },
                onValueChangeFinished = { viewModel.saveDefaultTimerMinutes(sliderPosition.toInt()) },
                valueRange = 5f..90f,
                steps = 16, // 5-min increments
                colors = SliderDefaults.colors(thumbColor = NeonGreen, activeTrackColor = NeonGreen),
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("5 min", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
                Text("90 min", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
            }

            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider(color = Color.DarkGray)
            Spacer(modifier = Modifier.height(24.dp))

            Text("About", style = MaterialTheme.typography.titleMedium, color = Color.White)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Flow · v1.0", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
        }
    }
}
