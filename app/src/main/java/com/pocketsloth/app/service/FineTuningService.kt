package com.pocketsloth.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.pocketsloth.app.PocketSlothApp
import com.pocketsloth.app.data.formatter.PromptFormatterFactory
import com.pocketsloth.app.data.local.db.AppDatabase
import com.pocketsloth.app.data.mapper.toDomain
import com.pocketsloth.app.data.mapper.toLoraParams
import com.pocketsloth.app.data.repository.DatasetRepositoryImpl
import com.pocketsloth.app.data.repository.TrainingRunRepositoryImpl
import com.pocketsloth.app.domain.model.TrainingMetrics
import com.pocketsloth.app.domain.model.TrainingRunStatus
import com.pocketsloth.app.domain.repository.DatasetRepository
import com.pocketsloth.app.domain.repository.TrainingRunRepository
import com.pocketsloth.app.native.NativeLoRAEngine
import com.pocketsloth.app.native.TrainingBridge
import com.pocketsloth.app.native.TrainingMetrics as NativeMetrics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

/**
 * Foreground service that drives on-device LoRA fine-tuning via [NativeLoRAEngine].
 *
 * - Persistent notification with determinate progress bar
 * - [PowerManager.PARTIAL_WAKE_LOCK] while a run is active
 * - Pause / Stop via notification actions or startService intents
 * - Auto-pause at [ThermalStatus.SEVERE]+ via [ThermalMonitor]
 * - Domain [TrainingMetrics] streamed on [metrics] ([SharedFlow])
 */
class FineTuningService : Service() {

    inner class LocalBinder : Binder() {
        fun getService(): FineTuningService = this@FineTuningService
    }

    private val binder = LocalBinder()
    private val serviceJob = SupervisorJob()
    private val scope = CoroutineScope(serviceJob + Dispatchers.Main.immediate)

    private lateinit var datasetRepository: DatasetRepository
    private lateinit var runRepository: TrainingRunRepository
    private lateinit var thermalMonitor: ThermalMonitor
    private lateinit var notificationManager: NotificationManager

    private var engine: NativeLoRAEngine? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var trainingJob: Job? = null
    private var thermalJob: Job? = null
    private var progressListener: TrainingBridge.ProgressListener? = null

    private val _metrics = MutableSharedFlow<TrainingMetrics>(
        replay = 1,
        extraBufferCapacity = 64,
    )
    val metrics: SharedFlow<TrainingMetrics> = _metrics.asSharedFlow()

    private val _latestMetrics = MutableStateFlow<TrainingMetrics?>(null)
    val latestMetrics: StateFlow<TrainingMetrics?> = _latestMetrics.asStateFlow()

    private val _runState = MutableStateFlow(RunState.IDLE)
    val runState: StateFlow<RunState> = _runState.asStateFlow()

    private var activeRunId: Long = -1L
    private var pausedByThermal: Boolean = false
    private var userPaused: Boolean = false
    private var trainStartedElapsed: Long = 0L
    private var trainDataFile: File? = null

    override fun onCreate() {
        super.onCreate()
        val app = applicationContext as? PocketSlothApp
        if (app != null) {
            datasetRepository = app.container.datasetRepository
            runRepository = app.container.trainingRunRepository
        } else {
            val db = AppDatabase.getInstance(this)
            datasetRepository = DatasetRepositoryImpl(db.datasetDao(), db.exampleDao())
            runRepository = TrainingRunRepositoryImpl(db.trainingRunDao())
        }
        thermalMonitor = ThermalMonitor(this)
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        ensureNotificationChannel()
        observeThermal()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val runId = intent.getLongExtra(EXTRA_RUN_ID, -1L)
                if (runId > 0L) startTraining(runId)
            }
            ACTION_PAUSE -> pauseTraining(fromUser = true)
            ACTION_RESUME -> resumeTraining()
            ACTION_STOP -> stopTraining(userRequested = true)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        thermalJob?.cancel()
        trainingJob?.cancel()
        detachProgressListener()
        engine?.destroySession()
        releaseWakeLock()
        trainDataFile?.delete()
        scope.cancel()
        super.onDestroy()
    }

    fun startTraining(runId: Long) {
        if (_runState.value == RunState.RUNNING || _runState.value == RunState.STARTING) {
            Log.w(TAG, "Training already in progress (run=$activeRunId)")
            return
        }
        activeRunId = runId
        userPaused = false
        pausedByThermal = false
        _runState.value = RunState.STARTING

        startAsForeground(progress = 0, indeterminate = true, statusText = "Starting…")
        acquireWakeLock()

        trainingJob?.cancel()
        trainingJob = scope.launch(Dispatchers.IO) {
            try {
                runTrainingPipeline(runId)
            } catch (t: Throwable) {
                Log.e(TAG, "Training failed", t)
                runRepository.markFinished(
                    id = runId,
                    status = TrainingRunStatus.FAILED,
                    errorMessage = t.message ?: t::class.java.simpleName,
                )
                _runState.value = RunState.FAILED
                withContext(Dispatchers.Main) {
                    updateNotification(0, false, "Failed")
                }
            } finally {
                // Keep session + wake lock alive across Pause so Resume can restart the epoch.
                if (_runState.value == RunState.PAUSED) {
                    return@launch
                }
                detachProgressListener()
                engine?.destroySession()
                releaseWakeLock()
                trainDataFile?.delete()
                trainDataFile = null
                if (_runState.value !in setOf(RunState.RUNNING, RunState.STARTING)) {
                    withContext(Dispatchers.Main) {
                        stopForegroundCompat()
                        stopSelf()
                    }
                }
            }
        }
    }

    fun pauseTraining(fromUser: Boolean = true) {
        if (_runState.value != RunState.RUNNING) return
        if (fromUser) userPaused = true
        engine?.cancelTraining()
        _runState.value = RunState.PAUSED
        scope.launch {
            val status = if (pausedByThermal && !fromUser) {
                TrainingRunStatus.THERMAL_PAUSED
            } else {
                TrainingRunStatus.PAUSED
            }
            runRepository.updateStatus(activeRunId, status)
        }
        val label = if (pausedByThermal && !fromUser) "Paused (thermal)" else "Paused"
        updateNotification(
            progress = _latestMetrics.value?.progressPercent ?: 0,
            indeterminate = false,
            statusText = label,
            showResume = true,
        )
    }

    fun resumeTraining() {
        if (_runState.value != RunState.PAUSED) return
        if (thermalMonitor.isSevereOrWorse()) {
            Log.w(TAG, "Refuse resume: thermal still severe+")
            pausedByThermal = true
            updateNotification(
                progress = _latestMetrics.value?.progressPercent ?: 0,
                indeterminate = false,
                statusText = "Paused (thermal)",
                showResume = true,
            )
            return
        }
        userPaused = false
        pausedByThermal = false
        val eng = engine
        val runId = activeRunId
        if (eng == null || !eng.hasActiveSession() || runId <= 0L) {
            _runState.value = RunState.IDLE
            if (runId > 0L) startTraining(runId)
            return
        }
        _runState.value = RunState.RUNNING
        scope.launch { runRepository.updateStatus(runId, TrainingRunStatus.RUNNING) }
        updateNotification(
            progress = _latestMetrics.value?.progressPercent ?: 0,
            indeterminate = false,
            statusText = "Training…",
            showResume = false,
        )
        // Re-await native completion after pause cancelled the previous suspend.
        trainingJob?.cancel()
        trainingJob = scope.launch(Dispatchers.IO) {
            try {
                acquireWakeLock()
                val terminal = awaitTrainingCompletion(eng, runId)
                when (terminal) {
                    NativeMetrics.Status.COMPLETED -> {
                        runRepository.markFinished(runId, TrainingRunStatus.COMPLETED)
                        _runState.value = RunState.COMPLETED
                        withContext(Dispatchers.Main) {
                            updateNotification(100, false, "Completed")
                        }
                    }
                    NativeMetrics.Status.CANCELLED -> {
                        if (_runState.value == RunState.PAUSED) return@launch
                        runRepository.markFinished(runId, TrainingRunStatus.STOPPED)
                        _runState.value = RunState.STOPPED
                    }
                    NativeMetrics.Status.ERROR -> {
                        runRepository.markFinished(
                            runId,
                            TrainingRunStatus.FAILED,
                            errorMessage = "Native training reported ERROR",
                        )
                        _runState.value = RunState.FAILED
                    }
                    else -> {
                        runRepository.markFinished(runId, TrainingRunStatus.STOPPED)
                        _runState.value = RunState.STOPPED
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Resume training failed", t)
                runRepository.markFinished(
                    runId,
                    TrainingRunStatus.FAILED,
                    errorMessage = t.message ?: t::class.java.simpleName,
                )
                _runState.value = RunState.FAILED
            } finally {
                if (_runState.value == RunState.PAUSED) return@launch
                detachProgressListener()
                engine?.destroySession()
                releaseWakeLock()
                trainDataFile?.delete()
                trainDataFile = null
                if (_runState.value !in setOf(RunState.RUNNING, RunState.STARTING)) {
                    withContext(Dispatchers.Main) {
                        stopForegroundCompat()
                        stopSelf()
                    }
                }
            }
        }
    }

    fun stopTraining(userRequested: Boolean = true) {
        engine?.cancelTraining()
        trainingJob?.cancel()
        trainingJob = null
        val status = if (userRequested) TrainingRunStatus.STOPPED else TrainingRunStatus.FAILED
        scope.launch {
            if (activeRunId > 0L) {
                runRepository.markFinished(activeRunId, status)
            }
        }
        _runState.value = RunState.STOPPED
        detachProgressListener()
        engine?.destroySession()
        releaseWakeLock()
        stopForegroundCompat()
        stopSelf()
    }

    private suspend fun runTrainingPipeline(runId: Long) {
        val run = runRepository.getRun(runId)
            ?: error("Training run $runId not found")

        val examples = datasetRepository.getExamples(run.datasetId)
        require(examples.isNotEmpty()) { "Dataset ${run.datasetId} has no examples" }

        val formatter = PromptFormatterFactory.create(run.promptFormat)
        val prompts = formatter.formatAll(examples)

        val dataFile = writeTrainDataFile(runId, prompts)
        trainDataFile = dataFile

        val eng = resolveEngine()
        engine = eng
        require(eng.isLibraryLoaded()) { "Native LoRA library not loaded" }

        val loraParams = run.hyperparams.toLoraParams(
            maxSteps = (examples.size * run.hyperparams.epochs).coerceAtLeast(1),
        )
        val inited = eng.initSession(
            modelPath = run.modelPath,
            trainDataPath = dataFile.absolutePath,
            params = loraParams,
        )
        require(inited) { "nativeInitTrainingSession returned 0" }

        runRepository.markStarted(runId)
        _runState.value = RunState.RUNNING
        trainStartedElapsed = SystemClock.elapsedRealtime()
        withContext(Dispatchers.Main) {
            updateNotification(0, false, "Training…")
        }

        val terminal = awaitTrainingCompletion(eng, runId)

        when (terminal) {
            NativeMetrics.Status.COMPLETED -> {
                runRepository.markFinished(runId, TrainingRunStatus.COMPLETED)
                _runState.value = RunState.COMPLETED
                withContext(Dispatchers.Main) {
                    updateNotification(100, false, "Completed")
                }
            }
            NativeMetrics.Status.CANCELLED -> {
                if (_runState.value == RunState.PAUSED) {
                    // Keep service alive for resume.
                    return
                }
                runRepository.markFinished(runId, TrainingRunStatus.STOPPED)
                _runState.value = RunState.STOPPED
            }
            NativeMetrics.Status.ERROR -> {
                runRepository.markFinished(
                    runId,
                    TrainingRunStatus.FAILED,
                    errorMessage = "Native training reported ERROR",
                )
                _runState.value = RunState.FAILED
            }
            else -> {
                runRepository.markFinished(runId, TrainingRunStatus.STOPPED)
                _runState.value = RunState.STOPPED
            }
        }
    }

    private suspend fun awaitTrainingCompletion(
        eng: NativeLoRAEngine,
        runId: Long,
    ): NativeMetrics.Status = suspendCancellableCoroutine { cont ->
        detachProgressListener()
        val listener = TrainingBridge.ProgressListener { native ->
            val elapsed = SystemClock.elapsedRealtime() - trainStartedElapsed
            val eta = if (native.step > 0 && native.totalSteps > native.step) {
                val perStep = elapsed.toFloat() / native.step
                ((native.totalSteps - native.step) * perStep).toLong()
            } else {
                -1L
            }
            val domain = native.toDomain(runId = runId, elapsedMs = elapsed, etaMs = eta)
            _latestMetrics.value = domain
            _metrics.tryEmit(domain)
            scope.launch { runRepository.updateProgress(runId, domain) }

            if (_runState.value == RunState.RUNNING) {
                updateNotification(
                    progress = domain.progressPercent,
                    indeterminate = false,
                    statusText = "Epoch ${domain.epoch} · loss ${"%.4f".format(domain.loss)}",
                )
            }

            when (native.status) {
                NativeMetrics.Status.COMPLETED,
                NativeMetrics.Status.CANCELLED,
                NativeMetrics.Status.ERROR,
                -> {
                    if (cont.isActive) cont.resume(native.status)
                }
                else -> Unit
            }
        }
        progressListener = listener
        eng.addProgressListener(listener)
        cont.invokeOnCancellation {
            eng.removeProgressListener(listener)
            eng.cancelTraining()
        }
        val started = eng.startEpoch()
        if (!started && cont.isActive) {
            cont.resume(NativeMetrics.Status.ERROR)
        }
    }

    private fun resolveEngine(): NativeLoRAEngine {
        return try {
            PocketSlothApp.get().loraEngine
        } catch (_: Throwable) {
            NativeLoRAEngine.loadLibrary()
            NativeLoRAEngine()
        }
    }

    private fun writeTrainDataFile(runId: Long, prompts: List<String>): File {
        val dir = File(cacheDir, "training").apply { mkdirs() }
        val file = File(dir, "run_${runId}.jsonl")
        file.bufferedWriter().use { writer ->
            prompts.forEach { prompt ->
                // Native STUB only needs a path; JSONL text keeps a real corpus for future llama.cpp.
                val escaped = prompt
                    .replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                writer.appendLine("""{"text":"$escaped"}""")
            }
        }
        return file
    }

    private fun detachProgressListener() {
        val listener = progressListener
        val eng = engine
        if (listener != null && eng != null) {
            eng.removeProgressListener(listener)
        }
        progressListener = null
    }

    private fun observeThermal() {
        thermalJob = thermalMonitor.observeStatus()
            .onEach { status ->
                if (status.isSevereOrWorse && _runState.value == RunState.RUNNING) {
                    Log.w(TAG, "Thermal $status — auto-pausing")
                    pausedByThermal = true
                    pauseTraining(fromUser = false)
                } else if (
                    pausedByThermal &&
                    !userPaused &&
                    !status.isSevereOrWorse &&
                    _runState.value == RunState.PAUSED
                ) {
                    Log.i(TAG, "Thermal recovered ($status) — auto-resuming")
                    resumeTraining()
                }
            }
            .launchIn(scope)
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "$packageName:FineTuningWakeLock",
        ).apply {
            setReferenceCounted(false)
            acquire(WAKE_LOCK_TIMEOUT_MS)
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let { if (it.isHeld) it.release() }
        } catch (_: RuntimeException) {
        }
        wakeLock = null
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Fine-tuning",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "On-device LoRA fine-tuning progress"
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun startAsForeground(progress: Int, indeterminate: Boolean, statusText: String) {
        val notification = buildNotification(progress, indeterminate, statusText, showResume = false)
        val fgsType = when {
            Build.VERSION.SDK_INT >= 34 -> ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            else -> 0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, fgsType)
    }

    private fun updateNotification(
        progress: Int,
        indeterminate: Boolean,
        statusText: String,
        showResume: Boolean = false,
    ) {
        notificationManager.notify(
            NOTIFICATION_ID,
            buildNotification(progress, indeterminate, statusText, showResume),
        )
    }

    private fun buildNotification(
        progress: Int,
        indeterminate: Boolean,
        statusText: String,
        showResume: Boolean,
    ): Notification {
        val stopPi = pendingServiceIntent(ACTION_STOP, 1)
        val togglePi = if (showResume) {
            pendingServiceIntent(ACTION_RESUME, 2)
        } else {
            pendingServiceIntent(ACTION_PAUSE, 3)
        }
        val contentIntent = packageManager.getLaunchIntentForPackage(packageName)?.let { launch ->
            PendingIntent.getActivity(
                this,
                0,
                launch,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("PocketSloth fine-tuning")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setProgress(100, progress.coerceIn(0, 100), indeterminate)
            .setContentIntent(contentIntent)
            .addAction(0, if (showResume) "Resume" else "Pause", togglePi)
            .addAction(0, "Stop", stopPi)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun pendingServiceIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, FineTuningService::class.java).setAction(action)
        return PendingIntent.getService(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun stopForegroundCompat() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
    }

    enum class RunState {
        IDLE,
        STARTING,
        RUNNING,
        PAUSED,
        COMPLETED,
        STOPPED,
        FAILED,
    }

    companion object {
        private const val TAG = "FineTuningService"
        const val CHANNEL_ID = "pocketsloth_finetune"
        const val NOTIFICATION_ID = 0x50F7
        private const val WAKE_LOCK_TIMEOUT_MS = 6 * 60 * 60 * 1000L

        const val ACTION_START = "com.pocketsloth.app.action.START_TRAINING"
        const val ACTION_PAUSE = "com.pocketsloth.app.action.PAUSE_TRAINING"
        const val ACTION_RESUME = "com.pocketsloth.app.action.RESUME_TRAINING"
        const val ACTION_STOP = "com.pocketsloth.app.action.STOP_TRAINING"
        const val EXTRA_RUN_ID = "extra_run_id"

        fun startIntent(context: Context, runId: Long): Intent =
            Intent(context, FineTuningService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_RUN_ID, runId)
            }

        fun pauseIntent(context: Context): Intent =
            Intent(context, FineTuningService::class.java).setAction(ACTION_PAUSE)

        fun resumeIntent(context: Context): Intent =
            Intent(context, FineTuningService::class.java).setAction(ACTION_RESUME)

        fun stopIntent(context: Context): Intent =
            Intent(context, FineTuningService::class.java).setAction(ACTION_STOP)
    }
}
