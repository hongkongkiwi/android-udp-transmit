package com.udptrigger.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties

/**
 * Tutorial step information
 */
data class TutorialStep(
    val title: String,
    val description: String,
    val targetKey: String = "" // For identifying the target element
)

/**
 * Complete onboarding tutorial for UDP Trigger
 */
val onboardingSteps = listOf(
    TutorialStep(
        title = "Welcome to UDP Trigger",
        description = "This app sends UDP packets with ultra-low latency when you press any key (volume, media, or keyboard).\n\nPerfect for:\n• Show control systems\n• OBS Studio triggering\n• Audio/video automation\n• IoT device control"
    ),
    TutorialStep(
        title = "Connect to Destination",
        description = "First, configure your destination:\n\n1. Enter the IP address or hostname\n2. Set the port number\n3. Tap the Connect button\n\nThe connection indicator will show green when connected."
    ),
    TutorialStep(
        title = "Press TRIGGER or Any Key",
        description = "Once connected:\n\n• Tap the TRIGGER button\n• OR press any hardware key (volume, media, keyboard)\n\nThe app sends UDP packets as fast as possible!"
    ),
    TutorialStep(
        title = "Monitor Latency",
        description = "The latency bar chart shows:\n\n• Green = Excellent (<1ms)\n• Yellow/Orange = Moderate (1-10ms)\n• Red = Poor (>25ms)\n\nThis helps you verify the low-latency performance."
    ),
    TutorialStep(
        title = "Use Presets",
        description = "Quickly switch between destinations:\n\n• Tap preset chips for fast access\n• Or use the Load Preset dropdown\n• Save your own presets for common setups"
    ),
    TutorialStep(
        title = "Settings & Advanced",
        description = "Explore advanced features:\n\n• Background Service - keep app running when screen is off\n• Burst Mode - send multiple packets\n• Scheduled Triggers - automated sending\n• Listen Mode - receive UDP packets\n\nAccess these from the Settings panel."
    ),
    TutorialStep(
        title = "You're Ready!",
        description = "You're all set to trigger UDP packets with minimal latency!\n\nTap anywhere to dismiss this tutorial.\n\nYou can access this tutorial again from the About menu."
    )
)

/**
 * Check if onboarding should be shown
 */
fun shouldShowOnboarding(context: Context): Boolean {
    val prefs = context.getSharedPreferences("udp_trigger_prefs", Context.MODE_PRIVATE)
    return !prefs.getBoolean("onboarding_completed", false)
}

/**
 * Mark onboarding as completed
 */
fun markOnboardingCompleted(context: Context) {
    val prefs = context.getSharedPreferences("udp_trigger_prefs", Context.MODE_PRIVATE)
    prefs.edit().putBoolean("onboarding_completed", true).apply()
}

/**
 * Reset onboarding (for testing or re-showing)
 */
fun resetOnboarding(context: Context) {
    val prefs = context.getSharedPreferences("udp_trigger_prefs", Context.MODE_PRIVATE)
    prefs.edit().putBoolean("onboarding_completed", false).apply()
}

/**
 * Onboarding tutorial overlay with highlighted spots
 */
@Composable
fun OnboardingTutorial(
    currentStep: Int,
    onDismiss: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val step = onboardingSteps.getOrNull(currentStep)
    if (step == null) {
        content()
        return
    }

    Box(modifier = modifier) {
        // Main content (dimmed during tutorial)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .alpha(0.4f)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        ) {
            content()
        }

        // Tutorial popup
        TutorialPopup(
            step = step,
            currentStep = currentStep,
            totalSteps = onboardingSteps.size,
            onDismiss = onDismiss,
            onNext = onNext,
            onPrevious = onPrevious,
            modifier = Modifier.align(Alignment.Center)
        )
    }
}

@Composable
fun TutorialPopup(
    step: TutorialStep,
    currentStep: Int,
    totalSteps: Int,
    onDismiss: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .padding(32.dp)
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null
            ) { /* Don't dismiss on background click */ },
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp
    ) {
        Box(
            modifier = Modifier.padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.foundation.layout.Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp)
            ) {
                // Step indicator
                Text(
                    text = "Step ${currentStep + 1} of $totalSteps",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )

                // Title
                Text(
                    text = step.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Description
                Text(
                    text = step.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Navigation buttons
                androidx.compose.foundation.layout.Row(
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    if (currentStep > 0) {
                        androidx.compose.material3.OutlinedButton(onClick = onPrevious) {
                            Text("Previous")
                        }
                    }

                    androidx.compose.material3.Button(
                        onClick = if (currentStep < totalSteps - 1) onNext else onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (currentStep < totalSteps - 1) "Next" else "Got it!")
                    }

                    // Skip button (only show if not last step)
                    if (currentStep < totalSteps - 1) {
                        androidx.compose.material3.TextButton(
                            onClick = onDismiss,
                            modifier = Modifier.padding(start = 8.dp)
                        ) {
                            Text("Skip")
                        }
                    }
                }
            }
        }
    }
}

/**
 * Simple tutorial dialog (alternative to full overlay)
 */
@Composable
fun TutorialDialog(
    showTutorial: Boolean,
    currentStep: Int,
    onDismiss: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit
) {
    if (!showTutorial) return

    val step = onboardingSteps.getOrNull(currentStep) ?: return

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = step.title,
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = "Step ${currentStep + 1} of ${onboardingSteps.size}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        text = {
            Text(
                text = step.description,
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (currentStep > 0) {
                    OutlinedButton(onClick = onPrevious) {
                        Text("Previous")
                    }
                }

                Button(
                    onClick = if (currentStep < onboardingSteps.size - 1) onNext else onDismiss
                ) {
                    Text(if (currentStep < onboardingSteps.size - 1) "Next" else "Got it!")
                }

                if (currentStep < onboardingSteps.size - 1) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Text("Skip")
                    }
                }
            }
        }
    )
}
