package com.pocketsloth.app.presentation.viewmodel

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.BatteryManager
import android.os.IBinder
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pocketsloth.app.domain.model.TrainingMetrics
import com.pocketsloth.app.service.FineTuningService
import com.pocketsloth.app.service.ThermalMonitor
import com.pocketsloth.app.service.ThermalStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Live training dashboard — binds [FineTuningService] for metrics SharedFlow,
 * pause/stop intents, and thermal status from [ThermalMonitor].
 */
class TrainingDashboardViewModel(
    private val appContext: Context,
) : ViewModel() {

    private val thermalMonitor = ThermalMonitor(appContext)

    private val _metrics = MutableStateFlow(
        UiTrainingMetrics(statusMessage = "Waiting for FineTuningService…"),
    )
    val metrics: StateFlow<UiTrainingMetrics> = _metrics.asStateFlow()

    private var service: FineTuningService? = null
    private var metricsJob: Job? = null
    private var runStateJob: Job? = null
    private var thermalJob: Job? = null
    private var deviceStatsJob: Job? = null

    private val lossHistory = ArrayDeque<Float>(128)

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val svc = (binder as? FineTuningService.LocalBinder)?.getService() ?: return
            service = svc
            metricsJob?.cancel()
            metricsJob = viewModelScope.launch {
                svc.latestMetrics.value?.let { applyDomainMetrics(it, svc.runState.value) }
                svc.metrics.collect { m -> applyDomainMetrics(m, svc.runState.value) }
            }
            runStateJob?.cancel()
            runStateJob = viewModelScope.launch {
                svc.runState.collect { state ->
                    applyRunState(state)
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            _metrics.update {
                it.copy(isRunning = false, statusMessage = "Service disconnected")
            }
        }
    }

    init {
        bindService()
        observeThermal()
        pollDeviceStats()
    }

    private fun bindService() {
        val intent = Intent(appContext, FineTuningService::class.java)
        runCatching {
            appContext.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
    }

    private fun observeThermal() {
        thermalJob = viewModelScope.launch {
            thermalMonitor.observeStatus().collect { status ->
                _metrics.update {
                    it.copy(thermalState = status.toUiThermal())
                }
            }
        }
    }

    private fun pollDeviceStats() {
        deviceStatsJob = viewModelScope.launch {
            while (isActive) {
                val (used, total) = readRamMb()
                val battery = readBatteryPercent()
                _metrics.update {
                    it.copy(
                        ramUsedMb = used,
                        ramTotalMb = total,
                        batteryPercent = battery,
                    )
                }
                delay(2_000)
            }
        }
    }

    private fun applyDomainMetrics(m: TrainingMetrics, runState: FineTuningService.RunState) {
        if (lossHistory.isEmpty() || lossHistory.last() != m.loss) {
            lossHistory.addLast(m.loss)
            while (lossHistory.size > 120) lossHistory.removeFirst()
        }
        val etaSec = if (m.etaMs >= 0) m.etaMs / 1000L else -1L
        _metrics.update {
            it.copy(
                step = m.step,
                epoch = m.epoch,
                loss = m.loss,
                lossHistory = lossHistory.toList(),
                etaSeconds = etaSec,
                isRunning = runState == FineTuningService.RunState.RUNNING ||
                    runState == FineTuningService.RunState.STARTING,
                isPaused = runState == FineTuningService.RunState.PAUSED,
                statusMessage = statusFor(runState, m),
            )
        }
    }

    private fun applyRunState(state: FineTuningService.RunState) {
        _metrics.update {
            it.copy(
                isRunning = state == FineTuningService.RunState.RUNNING ||
                    state == FineTuningService.RunState.STARTING,
                isPaused = state == FineTuningService.RunState.PAUSED,
                statusMessage = when (state) {
                    FineTuningService.RunState.IDLE -> "Idle"
                    FineTuningService.RunState.STARTING -> "Starting…"
                    FineTuningService.RunState.RUNNING ->
                        if (it.step > 0) "Training · step ${it.step}" else "Training…"
                    FineTuningService.RunState.PAUSED -> "Paused"
                    FineTuningService.RunState.COMPLETED -> "Completed"
                    FineTuningService.RunState.STOPPED -> "Stopped"
                    FineTuningService.RunState.FAILED -> "Failed"
                },
                etaSeconds = if (state == FineTuningService.RunState.COMPLETED ||
                    state == FineTuningService.RunState.STOPPED
                ) {
                    0L
                } else {
                    it.etaSeconds
                },
            )
        }
    }

    private fun statusFor(state: FineTuningService.RunState, m: TrainingMetrics): String =
        when (state) {
            FineTuningService.RunState.RUNNING ->
                "Training · step ${m.step} · loss ${"%.4f".format(m.loss)}"
            FineTuningService.RunState.STARTING -> "Starting…"
            FineTuningService.RunState.PAUSED -> "Paused"
            FineTuningService.RunState.COMPLETED -> "Completed"
            FineTuningService.RunState.STOPPED -> "Stopped"
            FineTuningService.RunState.FAILED -> "Failed"
            FineTuningService.RunState.IDLE -> "Idle"
        }

    fun pause() {
        appContext.startService(FineTuningService.pauseIntent(appContext))
        _metrics.update { it.copy(isPaused = true, statusMessage = "Paused") }
    }

    fun resume() {
        appContext.startService(FineTuningService.resumeIntent(appContext))
        _metrics.update {
            it.copy(isPaused = false, isRunning = true, statusMessage = "Training")
        }
    }

    fun stop() {
        appContext.startService(FineTuningService.stopIntent(appContext))
        _metrics.update {
            it.copy(
                isRunning = false,
                isPaused = false,
                statusMessage = "Stopped",
                etaSeconds = 0,
            )
        }
    }

    private fun readRamMb(): Pair<Long, Long> {
        return try {
            val am = appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val info = ActivityManager.MemoryInfo()
            am.getMemoryInfo(info)
            val used = ((info.totalMem - info.availMem) / (1024L * 1024L))
            val total = info.totalMem / (1024L * 1024L)
            used to total
        } catch (_: Throwable) {
            0L to 0L
        }
    }

    private fun readBatteryPercent(): Int {
        return try {
            val bm = appContext.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100)
        } catch (_: Throwable) {
            100
        }
    }

    override fun onCleared() {
        metricsJob?.cancel()
        runStateJob?.cancel()
        thermalJob?.cancel()
        deviceStatsJob?.cancel()
        runCatching { appContext.unbindService(connection) }
        super.onCleared()
    }
}

private fun ThermalStatus.toUiThermal(): ThermalState = when (this) {
    ThermalStatus.NONE -> ThermalState.Nominal
    ThermalStatus.LIGHT -> ThermalState.Fair
    ThermalStatus.MODERATE -> ThermalState.Fair
    ThermalStatus.SEVERE -> ThermalState.Serious
    ThermalStatus.CRITICAL,
    ThermalStatus.EMERGENCY,
    ThermalStatus.SHUTDOWN,
    -> ThermalState.Critical
}
