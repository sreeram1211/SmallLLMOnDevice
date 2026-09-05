package com.pocketsloth.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlin.math.exp
import kotlin.random.Random
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Live training dashboard VM.
 * Wires StateFlows for loss curve, RAM, thermal, battery, ETA.
 *
 * TODO: collect from FineTuningService (com.pocketsloth.app.service.FineTuningService)
 *       and NativeLoRAEngine (com.pocketsloth.app.native.NativeLoRAEngine) metrics.
 */
class TrainingDashboardViewModel : ViewModel() {

    // TODO: inject FineTuningService (com.pocketsloth.app.service.FineTuningService)
    private val gateway: FineTuningGateway? = null


    private val _metrics = MutableStateFlow(UiTrainingMetrics(isRunning = true, statusMessage = "Training"))
    val metrics: StateFlow<UiTrainingMetrics> = _metrics.asStateFlow()

    private var simJob: Job? = null

    init {
        if (gateway != null) {
            viewModelScope.launch {
                gateway.metricsFlow().collect { m -> _metrics.value = m }
            }
        } else {
            startLocalSimulation()
        }
    }

    private fun startLocalSimulation() {
        simJob?.cancel()
        simJob = viewModelScope.launch {
            var step = 0
            var loss = 2.8f
            val history = mutableListOf<Float>()
            while (isActive) {
                val current = _metrics.value
                if (!current.isRunning || current.isPaused) {
                    delay(400)
                    continue
                }
                step++
                loss = (loss * 0.985f + Random.nextFloat() * 0.08f).coerceAtLeast(0.05f)
                // gentle decay envelope
                val envelope = 2.5f * exp(-step / 80.0).toFloat() + 0.12f
                val point = (envelope + Random.nextFloat() * 0.15f).coerceAtLeast(0.05f)
                history.add(point)
                if (history.size > 120) history.removeAt(0)

                val thermal = when {
                    step % 90 > 75 -> ThermalState.Critical
                    step % 90 > 55 -> ThermalState.Serious
                    step % 90 > 30 -> ThermalState.Fair
                    else -> ThermalState.Nominal
                }
                val ramUsed = 2_400L + (step % 40) * 12L
                val battery = (92 - step / 25).coerceIn(5, 100)
                val remaining = ((200 - step).coerceAtLeast(0) * 3.2).toLong()

                _metrics.update {
                    it.copy(
                        step = step,
                        epoch = step / 40 + 1,
                        loss = point,
                        lossHistory = history.toList(),
                        ramUsedMb = ramUsed,
                        ramTotalMb = 8_192L,
                        thermalState = thermal,
                        batteryPercent = battery,
                        etaSeconds = remaining,
                        isRunning = true,
                        statusMessage = if (thermal == ThermalState.Critical) {
                            "Throttling — severe thermal"
                        } else {
                            "Training · step $step"
                        },
                    )
                }
                delay(500)
            }
        }
    }

    fun pause() {
        viewModelScope.launch {
            gateway?.pause()
            _metrics.update {
                it.copy(isPaused = true, statusMessage = "Paused")
            }
        }
    }

    fun resume() {
        viewModelScope.launch {
            gateway?.resume()
            _metrics.update {
                it.copy(isPaused = false, statusMessage = "Training", isRunning = true)
            }
            if (gateway == null && simJob?.isActive != true) {
                startLocalSimulation()
            }
        }
    }

    fun stop() {
        viewModelScope.launch {
            gateway?.stop()
            simJob?.cancel()
            _metrics.update {
                it.copy(
                    isRunning = false,
                    isPaused = false,
                    statusMessage = "Stopped",
                    etaSeconds = 0,
                )
            }
        }
    }

    override fun onCleared() {
        simJob?.cancel()
        super.onCleared()
    }
}
