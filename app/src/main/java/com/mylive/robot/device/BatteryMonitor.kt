package com.mylive.robot.device

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Battery percentage 0..100 and whether a charger is connected (plugged in, whether or not it is still drawing). */
data class BatteryInfo(val percent: Int, val charging: Boolean) {
    companion object {
        /** Used until the first reading arrives: full and unplugged, so nothing reacts. */
        val Unknown = BatteryInfo(100, false)

        /**
         * Parses the fields of an ACTION_BATTERY_CHANGED broadcast. Null if the level is unusable.
         * "Charging" means a charger is plugged in (EXTRA_PLUGGED != 0, any source), not the status
         * field: that can lag or contradict the plug state, and the spec says "device plugged in".
         */
        fun from(level: Int, scale: Int, plugged: Int): BatteryInfo? {
            if (level < 0 || scale <= 0) return null
            val percent = (level * 100 / scale).coerceIn(0, 100)
            return BatteryInfo(percent, plugged != 0)
        }
    }
}

/**
 * Thin adapter over the sticky ACTION_BATTERY_CHANGED broadcast (spec 02): no permission needed.
 * Call [start] when the screen is shown and [stop] when it goes away.
 */
class BatteryMonitor(private val context: Context) {
    private val state = MutableStateFlow(BatteryInfo.Unknown)
    val info: StateFlow<BatteryInfo> = state

    private var registered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = publish(intent)
    }

    fun start() {
        if (registered) return
        val sticky = ContextCompat.registerReceiver(
            context, receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        registered = true
        sticky?.let(::publish)
    }

    fun stop() {
        if (!registered) return
        context.unregisterReceiver(receiver)
        registered = false
    }

    private fun publish(intent: Intent) {
        val parsed = BatteryInfo.from(
            level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1),
            scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1),
            plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0),
        )
        if (parsed != null) state.value = parsed
    }
}
