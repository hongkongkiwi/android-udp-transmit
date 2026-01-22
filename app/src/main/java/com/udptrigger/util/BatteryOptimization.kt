package com.udptrigger.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore

private val Context.batteryDataStore: DataStore<Preferences> by preferencesDataStore(name = "battery_settings")

/**
 * Battery optimization utilities for UDP Trigger.
 * Helps ensure reliable packet delivery by managing battery optimization settings.
 */
object BatteryOptimization {

    private object PreferencesKeys {
        val IGNORE_BATTERY_OPTIMIZATION = booleanPreferencesKey("ignore_battery_optimization")
        val SHOW_BATTERY_WARNING = booleanPreferencesKey("show_battery_warning")
        val LAST_BATTERY_CHECK = booleanPreferencesKey("last_battery_check")
    }

    /**
     * Check if the app is ignoring battery optimizations
     */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * Check if battery optimization is restricting the app
     */
    fun isBatteryOptimizationRestricting(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return !powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * Open battery optimization settings for this app
     */
    fun openBatteryOptimizationSettings(context: Context): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            // Fallback to general battery settings
            openGeneralBatterySettings(context)
        }
    }

    /**
     * Open general battery settings
     */
    fun openGeneralBatterySettings(context: Context): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Check if we should show battery optimization warning
     */
    fun shouldShowWarning(context: Context): Boolean {
        if (isIgnoringBatteryOptimizations(context)) {
            return false
        }

        // Check if we've already shown the warning recently
        return true // Always show warning if not optimized
    }

    /**
     * Mark that we've shown the battery warning
     */
    suspend fun markWarningShown(context: Context) {
        context.batteryDataStore.edit { preferences ->
            preferences[PreferencesKeys.SHOW_BATTERY_WARNING] = false
        }
    }

    /**
     * Get detailed battery status
     */
    fun getBatteryStatus(context: Context): BatteryStatus {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager

        return BatteryStatus(
            isIgnoringOptimizations = powerManager.isIgnoringBatteryOptimizations(context.packageName),
            isDeviceIdleMode = powerManager.isDeviceIdleMode,
            isInteractive = powerManager.isInteractive,
            batterySaverEnabled = powerManager.isPowerSaveMode,
            suggestedAction = getSuggestedAction(context)
        )
    }

    /**
     * Get suggested action based on battery status
     */
    private fun getSuggestedAction(context: Context): BatterySuggestion {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager

        return when {
            !powerManager.isIgnoringBatteryOptimizations(context.packageName) -> {
                BatterySuggestion.DISABLE_OPTIMIZATION
            }
            powerManager.isPowerSaveMode -> {
                BatterySuggestion.DISABLE_BATTERY_SAVER
            }
            powerManager.isDeviceIdleMode -> {
                BatterySuggestion.WAIT_FOR_ACTIVE
            }
            else -> {
                BatterySuggestion.NONE
            }
        }
    }

    /**
     * Check doze mode status
     */
    fun isDozeModeActive(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isDeviceIdleMode
    }

    /**
     * Check if app is in battery saver whitelist
     */
    fun isInBatterySaverWhitelist(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * Get battery optimization info for debugging
     */
    fun getBatteryInfo(context: Context): Map<String, Any> {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager

        return mapOf(
            "ignoring_optimizations" to powerManager.isIgnoringBatteryOptimizations(context.packageName),
            "device_idle_mode" to powerManager.isDeviceIdleMode,
            "is_interactive" to powerManager.isInteractive,
            "power_save_mode" to powerManager.isPowerSaveMode,
            "android_version" to Build.VERSION.RELEASE,
            "sdk_version" to Build.VERSION.SDK_INT
        )
    }

    /**
     * Check if Doze restrictions are applied
     */
    fun areDozeRestrictionsApplied(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isDeviceIdleMode
    }

    /**
     * Check if App Standby is restricting the app
     */
    fun isAppStandbyRestricting(context: Context): Boolean {
        // This requires UsageStats permission which is restricted
        // We can only check indirectly
        return !isIgnoringBatteryOptimizations(context)
    }
}

/**
 * Battery status information
 */
data class BatteryStatus(
    val isIgnoringOptimizations: Boolean,
    val isDeviceIdleMode: Boolean,
    val isInteractive: Boolean,
    val batterySaverEnabled: Boolean,
    val suggestedAction: BatterySuggestion
)

/**
 * Battery optimization suggestions
 */
enum class BatterySuggestion {
    NONE,
    DISABLE_OPTIMIZATION,
    DISABLE_BATTERY_SAVER,
    WAIT_FOR_ACTIVE
}

/**
 * Power profile utilities for understanding device power characteristics
 */
object PowerProfileUtils {

    /**
     * Get battery capacity in mAh (if available)
     */
    fun getBatteryCapacity(context: Context): Double? {
        return try {
            val powerProfile = Class.forName("com.android.internal.os.PowerProfile")
                .getConstructor(Context::class.java)
                .newInstance(context)

            val batteryCapacity = Class.forName("com.android.internal.os.PowerProfile")
                .getMethod("getBatteryCapacity")
                .invoke(powerProfile) as Double

            batteryCapacity
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Estimate battery life based on current drain
     */
    fun estimateBatteryLife(context: Context): BatteryLifeEstimate {
        val intent = context.registerReceiver(
            null,
            android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED)
        )

        val level = intent?.getIntExtra("level", -1) ?: -1
        val scale = intent?.getIntExtra("scale", 100) ?: 100
        val status = intent?.getIntExtra("status", -1) ?: -1
        val plugged = intent?.getIntExtra("plugged", 0) ?: 0

        val batteryPct = if (level >= 0 && scale > 0) (level * 100 / scale) else null
        val isCharging = status == 2 || status == 5 // BATTERY_STATUS_CHARGING=2, BATTERY_STATUS_FULL=5

        val chargeCounter = intent?.getIntExtra("level", -1) ?: -1

        return BatteryLifeEstimate(
            percentage = batteryPct,
            isCharging = isCharging,
            isPluggedIn = plugged > 0,
            chargeCountermAh = chargeCounter
        )
    }
}

/**
 * Battery life estimate
 */
data class BatteryLifeEstimate(
    val percentage: Int?,
    val isCharging: Boolean,
    val isPluggedIn: Boolean,
    val chargeCountermAh: Int
)
