package com.pocketsloth.app.service

import android.content.Context
import android.os.Build
import android.os.PowerManager
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import java.util.concurrent.Executor

/**
 * Thin wrapper around [PowerManager]'s Thermal Status API (API 29+).
 *
 * Emits the current [ThermalStatus] immediately, then on every change.
 * On older platforms, emits [ThermalStatus.NONE] once and does not register
 * a platform listener.
 */
class ThermalMonitor(
    context: Context,
    private val mainExecutor: Executor = ContextCompat.getMainExecutor(context),
) {
    private val appContext = context.applicationContext
    private val powerManager: PowerManager? =
        appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager

    /** Snapshot of the current thermal status. */
    fun currentStatus(): ThermalStatus {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || powerManager == null) {
            return ThermalStatus.NONE
        }
        return ThermalStatus.fromPlatform(powerManager.currentThermalStatus)
    }

    /** True when the device reports [ThermalStatus.SEVERE] or hotter. */
    fun isSevereOrWorse(): Boolean = currentStatus().isSevereOrWorse

    /**
     * Cold flow of thermal status updates. Collectors should cancel when done;
     * the platform listener is unregistered on close (API 29+).
     */
    fun observeStatus(): Flow<ThermalStatus> = callbackFlow {
        val pm = powerManager
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || pm == null) {
            trySend(ThermalStatus.NONE)
            awaitClose { }
            return@callbackFlow
        }

        trySend(ThermalStatus.fromPlatform(pm.currentThermalStatus))

        val listener = PowerManager.OnThermalStatusChangedListener { status ->
            trySend(ThermalStatus.fromPlatform(status))
        }
        registerListener(pm, listener)
        awaitClose { unregisterListener(pm, listener) }
    }.distinctUntilChanged()

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun registerListener(
        pm: PowerManager,
        listener: PowerManager.OnThermalStatusChangedListener,
    ) {
        pm.addThermalStatusListener(mainExecutor, listener)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun unregisterListener(
        pm: PowerManager,
        listener: PowerManager.OnThermalStatusChangedListener,
    ) {
        pm.removeThermalStatusListener(listener)
    }
}

/**
 * Mirrors [PowerManager] thermal status constants with a friendlier API.
 */
enum class ThermalStatus(val platformValue: Int) {
    NONE(PowerManager.THERMAL_STATUS_NONE),
    LIGHT(PowerManager.THERMAL_STATUS_LIGHT),
    MODERATE(PowerManager.THERMAL_STATUS_MODERATE),
    SEVERE(PowerManager.THERMAL_STATUS_SEVERE),
    CRITICAL(PowerManager.THERMAL_STATUS_CRITICAL),
    EMERGENCY(PowerManager.THERMAL_STATUS_EMERGENCY),
    SHUTDOWN(PowerManager.THERMAL_STATUS_SHUTDOWN),
    ;

    val isSevereOrWorse: Boolean
        get() = platformValue >= PowerManager.THERMAL_STATUS_SEVERE

    companion object {
        fun fromPlatform(value: Int): ThermalStatus =
            entries.firstOrNull { it.platformValue == value } ?: when {
                value >= PowerManager.THERMAL_STATUS_SHUTDOWN -> SHUTDOWN
                value >= PowerManager.THERMAL_STATUS_EMERGENCY -> EMERGENCY
                value >= PowerManager.THERMAL_STATUS_CRITICAL -> CRITICAL
                value >= PowerManager.THERMAL_STATUS_SEVERE -> SEVERE
                value >= PowerManager.THERMAL_STATUS_MODERATE -> MODERATE
                value >= PowerManager.THERMAL_STATUS_LIGHT -> LIGHT
                else -> NONE
            }
    }
}
