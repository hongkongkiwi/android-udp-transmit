package com.udptrigger.widget

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.udptrigger.MainActivity
import com.udptrigger.MainActivity.Companion.EXTRA_AUTO_TRIGGER

/**
 * Invisible activity that receives widget trigger and forwards to the receiver.
 * This allows instant UDP triggering without keeping the main app open.
 * Uses a small delay to ensure broadcast receiver is registered before processing.
 */
class WidgetTriggerActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Get the configured host/port for this widget from DataStore
        // The widget configuration activity saved these values
        val extras = intent.extras
        val widgetId = extras?.getInt("appWidgetId", -1) ?: -1

        // Create the trigger broadcast intent with widget config if available
        val triggerIntent = Intent(WidgetTriggerReceiver.ACTION_TRIGGER_UDP).apply {
            setPackage(packageName)
            if (widgetId > 0) {
                putExtra("appWidgetId", widgetId)
            }
        }

        // Send ordered broadcast - this ensures the receiver processes first
        sendOrderedBroadcast(triggerIntent, null, object : android.content.BroadcastReceiver() {
            override fun onReceive(context: android.content.Context?, intent: Intent?) {
                // After the broadcast is processed, start MainActivity
                Handler(Looper.getMainLooper()).postDelayed({
                    val mainIntent = Intent(this@WidgetTriggerActivity, MainActivity::class.java).apply {
                        action = MainActivity.ACTION_TRIGGER_UDP
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        putExtra(MainActivity.EXTRA_AUTO_TRIGGER, true)
                        if (widgetId > 0) {
                            putExtra("appWidgetId", widgetId)
                        }
                    }
                    startActivity(mainIntent)
                    finish()
                }, 100) // Small delay to ensure MainActivity is ready
            }
        }, null, Activity.RESULT_OK, null, null)
    }

    companion object {
        const val ACTION_WIDGET_TRIGGER = "com.udptrigger.WIDGET_TRIGGER"
    }
}
