package com.flow.presentation.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.flow.ui.theme.NeonGreen
import com.flow.ui.theme.SurfaceDark

private val steps = listOf(
    OnboardingStep(
        title     = "Welcome to Flow! 🌟",
        body      = "Flow helps you build habits through gamified task management.\nTap a task card to cycle:\nTODO → In Progress ⏳ → Completed ✅"
    ),
    OnboardingStep(
        title     = "Track Your Progress 📈",
        body      = "Your dashboard colour shows today's completion:\n🟢 Green (≥100%) · 🟡 Yellow (≥50%) · 🟠 Orange (<50%)"
    ),
    OnboardingStep(
        title     = "Focus & Streaks 🔥",
        body      = "Use the Focus Timer ⏱ for deep-work sessions.\nMark a task as Recurring to start tracking your daily 🌱 streak."
    ),
    OnboardingStep(
        title     = "Ready to Start? 🚀",
        body      = "Long-press any card to edit or delete it.\nTap 📊 Stats to see your contribution heatmap.\nLet's build some momentum!"
    )
)

private data class OnboardingStep(val title: String, val body: String)

/**
 * T050 — Multi-step onboarding overlay (4 steps).
 * Calls [onComplete] on the final step "Let's Go!" button press.
 * Calls [onDismiss] if the user taps outside the dialog; defaults to [onComplete] for
 * backward compatibility (outside-tap = treated as "dismiss/skip").
 */
@Composable
fun OnboardingFlow(
    onComplete: () -> Unit,
    onDismiss: () -> Unit = onComplete
) {
    var currentStep by remember { mutableIntStateOf(0) }
    val step = steps[currentStep]
    val isLast = currentStep == steps.lastIndex

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Step dots
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    steps.indices.forEach { i ->
                        Box(
                            modifier = Modifier
                                .size(if (i == currentStep) 10.dp else 6.dp)
                                .padding(top = if (i == currentStep) 0.dp else 2.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = if (i == currentStep) NeonGreen else Color.Gray.copy(alpha = 0.4f),
                                modifier = Modifier.fillMaxSize()
                            ) {}
                        }
                    }
                }

                Text(step.title, style = MaterialTheme.typography.headlineSmall, color = NeonGreen)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    step.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = { if (isLast) onComplete() else currentStep++ },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = Color.Black),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isLast) "Let's Go!" else "Next →")
                }

                if (currentStep > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    TextButton(onClick = { currentStep-- }) {
                        Text("Back", color = Color.Gray)
                    }
                }
            }
        }
    }
}
