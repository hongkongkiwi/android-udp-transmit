package com.udptrigger.ui

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

/**
 * Gesture-based trigger support for swipe and tap gestures.
 * Supports configurable sensitivity and multi-tap patterns.
 */
@Composable
fun GestureTriggerOverlay(
    enabled: Boolean = true,
    onSingleTap: () -> Unit = {},
    onDoubleTap: () -> Unit = {},
    onSwipeUp: () -> Unit = {},
    onSwipeDown: () -> Unit = {},
    onSwipeLeft: () -> Unit = {},
    onSwipeRight: () -> Unit = {},
    onLongPress: () -> Unit = {},
    onTripleTap: () -> Unit = {},
    swipeThreshold: Float = 100f,
    doubleTapInterval: Long = 300L,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit = {}
) {
    var tapCount by remember { mutableIntStateOf(0) }
    var lastTapTime by remember { mutableLongStateOf(0L) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput

                // Tap detection for multi-tap
                detectTapGestures(
                    onTap = {
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastTapTime < doubleTapInterval) {
                            tapCount++
                        } else {
                            tapCount = 1
                        }
                        lastTapTime = currentTime

                        // Handle multi-tap with delay
                        when (tapCount) {
                            1 -> {
                                // Wait for potential double/triple tap
                                scope.launch {
                                    delay(doubleTapInterval)
                                    when (tapCount) {
                                        1 -> onSingleTap()
                                        2 -> {
                                            tapCount = 0
                                            onDoubleTap()
                                        }
                                        3 -> {
                                            tapCount = 0
                                            onTripleTap()
                                        }
                                    }
                                }
                            }
                        }
                    },
                    onLongPress = { onLongPress() }
                )
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput

                detectHorizontalDragGestures { _, dragAmount ->
                    val drag = dragAmount.absoluteValue
                    if (drag > swipeThreshold) {
                        when {
                            dragAmount > 0 -> onSwipeRight()
                            else -> onSwipeLeft()
                        }
                    }
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput

                detectVerticalDragGestures { _, dragAmount ->
                    val drag = dragAmount.absoluteValue
                    if (drag > swipeThreshold) {
                        when {
                            dragAmount < 0 -> onSwipeUp()
                            else -> onSwipeDown()
                        }
                    }
                }
            }
    ) {
        content()
    }
}

/**
 * Swipe direction enumeration
 */
enum class SwipeDirection {
    UP, DOWN, LEFT, RIGHT, NONE
}

/**
 * Gesture configuration for triggers
 */
data class GestureConfig(
    val enabled: Boolean = true,
    val swipeEnabled: Boolean = true,
    val tapEnabled: Boolean = true,
    val longPressEnabled: Boolean = true,
    val multiTapEnabled: Boolean = true,
    val swipeThreshold: Float = 100f,
    val doubleTapInterval: Long = 300L,
    val tripleTapInterval: Long = 500L,
    val longPressDuration: Long = 500L,
    // Gesture actions
    val swipeUpAction: GestureAction = GestureAction.TRIGGER,
    val swipeDownAction: GestureAction = GestureAction.SECONDARY_TRIGGER,
    val swipeLeftAction: GestureAction = GestureAction.BURST_MODE,
    val swipeRightAction: GestureAction = GestureAction.SCHEDULED_TRIGGER,
    val singleTapAction: GestureAction = GestureAction.TRIGGER,
    val doubleTapAction: GestureAction = GestureAction.CONNECT,
    val tripleTapAction: GestureAction = GestureAction.DISCONNECT,
    val longPressAction: GestureAction = GestureAction.SHOW_SETTINGS
)

/**
 * Actions that can be triggered by gestures
 */
enum class GestureAction {
    TRIGGER,
    SECONDARY_TRIGGER,
    BURST_MODE,
    SCHEDULED_TRIGGER,
    CONNECT,
    DISCONNECT,
    SHOW_SETTINGS,
    TOGGLE_WAKE_LOCK,
    TOGGLE_FOREGROUND_SERVICE,
    CUSTOM
}

/**
 * Gesture trigger handler that maps gestures to actions
 */
class GestureTriggerHandler(
    private val onTrigger: () -> Unit = {},
    private val onSecondaryTrigger: () -> Unit = {},
    private val onBurstMode: () -> Unit = {},
    private val onScheduledTrigger: () -> Unit = {},
    private val onConnect: () -> Unit = {},
    private val onDisconnect: () -> Unit = {},
    private val onShowSettings: () -> Unit = {},
    private val onToggleWakeLock: () -> Unit = {},
    private val onToggleForegroundService: () -> Unit = {}
) {
    private var config = GestureConfig()

    /**
     * Update configuration
     */
    fun updateConfig(newConfig: GestureConfig) {
        config = newConfig
    }

    /**
     * Handle gesture action
     */
    fun handleAction(action: GestureAction) {
        when (action) {
            GestureAction.TRIGGER -> onTrigger()
            GestureAction.SECONDARY_TRIGGER -> onSecondaryTrigger()
            GestureAction.BURST_MODE -> onBurstMode()
            GestureAction.SCHEDULED_TRIGGER -> onScheduledTrigger()
            GestureAction.CONNECT -> onConnect()
            GestureAction.DISCONNECT -> onDisconnect()
            GestureAction.SHOW_SETTINGS -> onShowSettings()
            GestureAction.TOGGLE_WAKE_LOCK -> onToggleWakeLock()
            GestureAction.TOGGLE_FOREGROUND_SERVICE -> onToggleForegroundService()
            GestureAction.CUSTOM -> { /* Custom action handled elsewhere */ }
        }
    }

    /**
     * Get action for swipe up
     */
    fun getSwipeUpAction() = config.swipeUpAction

    /**
     * Get action for swipe down
     */
    fun getSwipeDownAction() = config.swipeDownAction

    /**
     * Get action for swipe left
     */
    fun getSwipeLeftAction() = config.swipeLeftAction

    /**
     * Get action for swipe right
     */
    fun getSwipeRightAction() = config.swipeRightAction

    /**
     * Get action for single tap
     */
    fun getSingleTapAction() = config.singleTapAction

    /**
     * Get action for double tap
     */
    fun getDoubleTapAction() = config.doubleTapAction

    /**
     * Get action for triple tap
     */
    fun getTripleTapAction() = config.tripleTapAction

    /**
     * Get action for long press
     */
    fun getLongPressAction() = config.longPressAction
}
