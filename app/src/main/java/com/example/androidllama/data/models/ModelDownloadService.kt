package com.example.androidllama.data.models

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.androidllama.ui.screens.models.models.HuggingFaceModelRepository
import com.example.androidllama.ui.screens.models.models.ModelInfo
import com.example.androidllama.ui.screens.models.models.ModelStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ModelDownloadService : Service() {
    private val serviceScope = MainScope()
    private val downloads = mutableMapOf<String, Job>()
    private lateinit var repository: HuggingFaceModelRepository

    override fun onCreate() {
        super.onCreate()
        repository = HuggingFaceModelRepository(applicationContext)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL_DOWNLOAD) {
            intent.getStringExtra(EXTRA_ID)?.let { modelId ->
                downloads[modelId]?.cancel()
            }
            return START_NOT_STICKY
        }
        val model = intent?.toModelInfo()
        if (model == null) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        startInForeground(model, null)
        if (downloads.containsKey(model.id)) return START_NOT_STICKY

        ModelStore.markDownloading(model.id)
        downloads[model.id] = serviceScope.launch {
            try {
                repository.download(model) { progress ->
                    withContext(Dispatchers.Main.immediate) {
                        ModelStore.updateProgress(model.id, progress)
                        updateNotification(model, progress)
                    }
                }
                ModelStore.markDownloaded(model.id)
                repository.cacheCatalog(ModelStore.models)
            } catch (cancelled: CancellationException) {
                ModelStore.markDownloadCancelled(model.id)
                throw cancelled
            } catch (error: Throwable) {
                ModelStore.markFailed(model.id, error.message ?: "Download failed")
            } finally {
                downloads.remove(model.id)
                if (downloads.isEmpty()) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                } else {
                    updateNotification(model, null)
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTimeout(startId: Int, fgsType: Int) {
        downloads.values.forEach(Job::cancel)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        downloads.values.forEach(Job::cancel)
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startInForeground(model: ModelInfo, progress: Int?) {
        val notification = notification(model, progress)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(model: ModelInfo, progress: Int?) {
        val canPost = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (canPost) {
            getSystemService(NotificationManager::class.java)
                .notify(NOTIFICATION_ID, notification(model, progress))
        }
    }

    private fun notification(model: ModelInfo, progress: Int?): Notification {
        val activeCount = downloads.size.coerceAtLeast(1)
        val title = if (activeCount == 1) {
            "Downloading ${model.name}"
        } else {
            "Downloading $activeCount models"
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(progress?.let { "$it% complete" } ?: "Preparing download…")
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(100, progress ?: 0, progress == null)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Model downloads",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Progress for GGUF model downloads"
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun Intent.toModelInfo(): ModelInfo? {
        val id = getStringExtra(EXTRA_ID) ?: return null
        val repositoryId = getStringExtra(EXTRA_REPOSITORY_ID) ?: return null
        val fileName = getStringExtra(EXTRA_FILE_NAME) ?: return null
        return ModelInfo(
            id = id,
            name = getStringExtra(EXTRA_NAME) ?: fileName.substringAfterLast('/'),
            size = getStringExtra(EXTRA_SIZE) ?: "Unknown size",
            quantization = getStringExtra(EXTRA_QUANTIZATION) ?: "GGUF",
            tags = getStringArrayListExtra(EXTRA_TAGS).orEmpty(),
            repositoryId = repositoryId,
            fileName = fileName,
            revision = getStringExtra(EXTRA_REVISION) ?: "main",
            sizeBytes = getLongExtra(EXTRA_SIZE_BYTES, -1L).takeIf { it >= 0 },
            parameterCount = getLongExtra(EXTRA_PARAMETER_COUNT, 0L)
        )
    }

    companion object {
        private const val CHANNEL_ID = "model_downloads"
        private const val NOTIFICATION_ID = 4101
        private const val ACTION_CANCEL_DOWNLOAD =
            "com.example.androidllama.action.CANCEL_MODEL_DOWNLOAD"
        private const val EXTRA_ID = "model_id"
        private const val EXTRA_NAME = "model_name"
        private const val EXTRA_SIZE = "model_size"
        private const val EXTRA_QUANTIZATION = "model_quantization"
        private const val EXTRA_TAGS = "model_tags"
        private const val EXTRA_REPOSITORY_ID = "model_repository_id"
        private const val EXTRA_FILE_NAME = "model_file_name"
        private const val EXTRA_REVISION = "model_revision"
        private const val EXTRA_SIZE_BYTES = "model_size_bytes"
        private const val EXTRA_PARAMETER_COUNT = "model_parameter_count"

        fun start(context: Context, model: ModelInfo) {
            val intent = Intent(context, ModelDownloadService::class.java).apply {
                putExtra(EXTRA_ID, model.id)
                putExtra(EXTRA_NAME, model.name)
                putExtra(EXTRA_SIZE, model.size)
                putExtra(EXTRA_QUANTIZATION, model.quantization)
                putStringArrayListExtra(EXTRA_TAGS, ArrayList(model.tags))
                putExtra(EXTRA_REPOSITORY_ID, model.repositoryId)
                putExtra(EXTRA_FILE_NAME, model.fileName)
                putExtra(EXTRA_REVISION, model.revision)
                putExtra(EXTRA_SIZE_BYTES, model.sizeBytes ?: -1L)
                putExtra(EXTRA_PARAMETER_COUNT, model.parameterCount)
            }
            ContextCompat.startForegroundService(context.applicationContext, intent)
        }

        fun cancel(context: Context, modelId: String) {
            val intent = Intent(context.applicationContext, ModelDownloadService::class.java).apply {
                action = ACTION_CANCEL_DOWNLOAD
                putExtra(EXTRA_ID, modelId)
            }
            context.applicationContext.startService(intent)
        }
    }
}
