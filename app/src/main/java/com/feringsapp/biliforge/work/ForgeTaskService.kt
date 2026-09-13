package com.feringsapp.biliforge.work

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.feringsapp.biliforge.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * 长任务前台服务：能转码/合成期间保活 + 通知栏进度。
 * Android 14+ 显式声明类型（dataSync + mediaProcessing），Android 16 兼容。
 */
class ForgeTaskService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var collectJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startAsForeground("初始化…", -1)
        collectJob = scope.launch {
            ForgeCore.tasks.collectLatest(::onTasks)
        }
    }

    override fun onDestroy() {
        collectJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private fun onTasks(tasks: List<ForgeTask>) {
        val active = tasks.filter { it.isActive }
        if (active.isEmpty()) {
            stopSelf()
            return
        }
        val current = active.firstOrNull { it.status == ForgeTask.Status.RUNNING } ?: active.first()
        val pct = (current.progress * 100f).toInt().coerceIn(0, 100)
        val text = buildString {
            append(current.title)
            if (current.note.isNotBlank()) append(" · ").append(current.note)
            if (active.size > 1) append("（+${active.size - 1} 排队）")
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIF_ID, buildNotification(text, pct))
    }

    private fun startAsForeground(text: String, progress: Int) {
        val type = if (Build.VERSION.SDK_INT >= 34) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC or
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING
        } else {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        }
        ServiceCompat.startForeground(this, NOTIF_ID, buildNotification(text, progress), type)
    }

    private fun buildNotification(text: String, progress: Int): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_forge)
            .setContentTitle("BiliForge · 缓存熔炉")
            .setContentText(text)
            .setProgress(100, progress.coerceAtLeast(0), progress < 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "媒体任务",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "合成 / 转码 / 提取任务的进度"
            setShowBadge(false)
        }
        nm.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "forge_tasks"
        private const val NOTIF_ID = 0x8F01

        fun ensure(context: Context) {
            runCatching {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, ForgeTaskService::class.java),
                )
            }
        }
    }
}
