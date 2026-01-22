package com.udptrigger.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Build
import androidx.core.content.getSystemService
import com.udptrigger.MainActivity

/**
 * Broadcast receiver for handling app shortcuts.
 */
class ShortcutReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_TRIGGER = "com.udptrigger.TRIGGER"
        const val ACTION_CONNECT = "com.udptrigger.CONNECT"
        const val ACTION_DISCONNECT = "com.udptrigger.DISCONNECT"
        const val ACTION_PRESET = "com.udptrigger.PRESET"
        const val ACTION_SETTINGS = "com.udptrigger.SETTINGS"

        const val EXTRA_PRESET_NAME = "preset_name"

        // Shortcut IDs
        const val SHORTCUT_ID_TRIGGER = "trigger"
        const val SHORTCUT_ID_CONNECT = "connect"
        const val SHORTCUT_ID_DISCONNECT = "disconnect"
        const val SHORTCUT_ID_PRESET = "preset"
        const val SHORTCUT_ID_SETTINGS = "settings"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return

        // Forward to main activity with appropriate action
        val forwardIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            when (action) {
                ACTION_TRIGGER -> putExtra("shortcut_action", "trigger")
                ACTION_CONNECT -> putExtra("shortcut_action", "connect")
                ACTION_DISCONNECT -> putExtra("shortcut_action", "disconnect")
                ACTION_PRESET -> {
                    putExtra("shortcut_action", "preset")
                    putExtra(EXTRA_PRESET_NAME, intent.getStringExtra(EXTRA_PRESET_NAME))
                }
                ACTION_SETTINGS -> putExtra("shortcut_action", "settings")
            }
        }
        context.startActivity(forwardIntent)
    }
}

/**
 * Manager for dynamic shortcuts
 */
object ShortcutManager {

    /**
     * Report shortcut as used (for pinned shortcuts)
     */
    fun reportShortcutUsed(context: Context, shortcutId: String) {
        val shortcutManager = context.getSystemService<ShortcutManager>() ?: return
        shortcutManager.reportShortcutUsed(shortcutId)
    }

    /**
     * Update dynamic shortcuts
     */
    fun updateDynamicShortcuts(context: Context) {
        val shortcutManager = context.getSystemService<ShortcutManager>() ?: return

        val shortcuts = mutableListOf<ShortcutInfo>()

        // Trigger shortcut
        shortcuts.add(
            ShortcutInfo.Builder(context, ShortcutReceiver.SHORTCUT_ID_TRIGGER)
                .setShortLabel("Trigger")
                .setLongLabel("Send UDP Trigger")
                .setIcon(Icon.createWithResource(context, com.udptrigger.R.drawable.ic_shortcut_trigger))
                .setIntent(Intent(Intent.ACTION_MAIN).apply {
                    setPackage(context.packageName)
                    setClassName(context, MainActivity::class.java.name)
                    putExtra("shortcut_action", "trigger")
                })
                .build()
        )

        try {
            shortcutManager.setDynamicShortcuts(shortcuts)
        } catch (e: Exception) {
            // Shortcuts may not be fully supported on this API level
        }
    }

    /**
     * Create a pinned shortcut
     */
    fun requestPinShortcut(context: Context, shortcutId: String): Boolean {
        val shortcutManager = context.getSystemService<ShortcutManager>() ?: return false

        if (!shortcutManager.isRequestPinShortcutSupported) {
            return false
        }

        val shortcutInfo = when (shortcutId) {
            ShortcutReceiver.SHORTCUT_ID_TRIGGER -> createShortcutInfo(
                context, shortcutId, "Trigger", "Send UDP Trigger",
                com.udptrigger.R.drawable.ic_shortcut_trigger, "trigger"
            )
            ShortcutReceiver.SHORTCUT_ID_CONNECT -> createShortcutInfo(
                context, shortcutId, "Connect", "Connect to host",
                com.udptrigger.R.drawable.ic_shortcut_connect, "connect"
            )
            ShortcutReceiver.SHORTCUT_ID_DISCONNECT -> createShortcutInfo(
                context, shortcutId, "Disconnect", "Disconnect from host",
                com.udptrigger.R.drawable.ic_shortcut_disconnect, "disconnect"
            )
            else -> return false
        }

        return try {
            shortcutManager.requestPinShortcut(shortcutInfo, null)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun createShortcutInfo(
        context: Context,
        id: String,
        shortLabel: String,
        longLabel: String,
        iconRes: Int,
        action: String
    ): ShortcutInfo {
        return ShortcutInfo.Builder(context, id)
            .setShortLabel(shortLabel)
            .setLongLabel(longLabel)
            .setIcon(Icon.createWithResource(context, iconRes))
            .setIntent(Intent(Intent.ACTION_MAIN).apply {
                setPackage(context.packageName)
                setClassName(context, MainActivity::class.java.name)
                putExtra("shortcut_action", action)
            })
            .build()
    }

    /**
     * Get all available shortcut IDs
     */
    fun getAvailableShortcutIds(): List<String> {
        return listOf(
            ShortcutReceiver.SHORTCUT_ID_TRIGGER,
            ShortcutReceiver.SHORTCUT_ID_CONNECT,
            ShortcutReceiver.SHORTCUT_ID_DISCONNECT,
            ShortcutReceiver.SHORTCUT_ID_PRESET,
            ShortcutReceiver.SHORTCUT_ID_SETTINGS
        )
    }
}
