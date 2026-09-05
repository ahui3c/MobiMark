package tw.ahuimark.battery.core

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.SystemClock
import tw.ahuimark.battery.model.BatterySample

class BatteryMonitor(private val context: Context) {
    private val batteryManager = context.getSystemService(BatteryManager::class.java)

    fun read(): BatterySample {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
            ?: BatteryManager.BATTERY_STATUS_UNKNOWN
        val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val temperature = (intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f
        val voltage = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0

        return BatterySample(
            elapsedRealtimeMs = SystemClock.elapsedRealtime(),
            wallTimeMs = System.currentTimeMillis(),
            levelPercent = if (scale > 0) ((level * 100f) / scale).toInt() else level,
            isCharging = plugged != 0 || status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL,
            temperatureCelsius = temperature,
            voltageMv = voltage,
            currentMicroAmps = batteryManager.intPropertyOrNull(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW),
            chargeCounterMicroAh = batteryManager.intPropertyOrNull(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        )
    }

    private fun BatteryManager.intPropertyOrNull(property: Int): Int? {
        val value = getIntProperty(property)
        return value.takeUnless { it == Int.MIN_VALUE || it == 0 }
    }
}

