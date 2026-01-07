package com.example.delayme.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageStatsManager
import android.app.usage.UsageEvents
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.example.delayme.R
import com.example.delayme.data.model.AppType
import com.example.delayme.data.model.TimeCategory
import com.example.delayme.domain.logic.TimeClassifier
import com.example.delayme.data.local.AppDatabase
import com.example.delayme.data.repository.UsageRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.Calendar
import androidx.room.Room
import android.widget.Toast
import android.os.PowerManager
import android.net.Uri
import android.provider.Settings

class DimmerService : Service() {

    companion object {
        private const val TAG = "DimmerService"
    }

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var isDimmed = false
    private var currentAlpha = 0f
    private val handler = Handler(Looper.getMainLooper())
    private var repository: UsageRepository? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())

    // Config
    private var triggerHour = 23
    private var triggerMinute = 0
    private var dimDurationMinutes = 10

    private val checkRunnable = object : Runnable {
        override fun run() {
            checkConditions()
            handler.postDelayed(this, 5000) // Check every 5 seconds
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "onCreate: DimmerService starting...")
        
        handler.post {
            Toast.makeText(applicationContext, "助眠渐暗服务已启动", Toast.LENGTH_SHORT).show()
        }
        
        checkBatteryOptimization()
        startForegroundService()
        
        // Init DB/Repo manually since we don't have Hilt yet
        val db = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java, "delayme-db"
        ).build()
        repository = UsageRepository(applicationContext, db.appDao())

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        
        handler.post(checkRunnable)
        Log.i(TAG, "onCreate: DimmerService started successfully")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "onStartCommand: Service received start command")
        return START_STICKY
    }

    private fun startForegroundService() {
        val channelId = "DimmerServiceChannel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Sleep Dimmer Service",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("助眠模式运行中")
            .setContentText("正在监测您的睡眠时间...")
            .setSmallIcon(R.mipmap.ic_launcher)
            .build()

        startForeground(1, notification)
    }

    private fun updateNotification(text: String) {
        val notification = NotificationCompat.Builder(this, "DimmerServiceChannel")
            .setContentTitle("助眠模式")
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOnlyAlertOnce(true)
            .build()
        
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(1, notification)
    }

    private fun checkBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                handler.post {
                    Toast.makeText(
                        applicationContext,
                        "⚠️ 请将专注时光加入电池优化白名单",
                        Toast.LENGTH_LONG
                    ).show()
                }
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to open battery optimization settings: ${e.message}")
                }
            }
        }
    }

    private fun checkConditions() {
        Log.d(TAG, "checkConditions: Starting check...")
        
        // Update config
        repository?.let {
            triggerHour = it.getDimmerTriggerHour()
            triggerMinute = it.getDimmerTriggerMinute()
        }

        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)
        
        Log.d(TAG, "Current time: $hour:$minute, Trigger time: $triggerHour:$triggerMinute")
        
        // 判断是否在夜间时段（触发时间到次日6点）
        val isNightTime = if (triggerHour >= 12) {
            // 触发时间在下午/晚上（如22:00, 23:00）
            // 生效时段：triggerHour:triggerMinute ~ 次日06:00
            (hour > triggerHour) || (hour == triggerHour && minute >= triggerMinute) || (hour < 6)
        } else {
            // 触发时间在凌晨（如01:00）
            // 生效时段：triggerHour:triggerMinute ~ 06:00
            (hour > triggerHour || (hour == triggerHour && minute >= triggerMinute)) && hour < 6
        }
        
        Log.d(TAG, "Is night time: $isNightTime")
        
        if (!isNightTime) {
            updateNotification("等待触发时间 ($triggerHour:${String.format("%02d", triggerMinute)})")
            handler.post { removeOverlay() }
            return
        }

        // 2. Check Foreground App
        serviceScope.launch {
            val currentPkg = getForegroundApp()
            Log.d(TAG, "Current foreground app: $currentPkg")
            
            if (currentPkg != null) {
                val config = repository?.getAppConfig(currentPkg)
                val category = TimeClassifier.classify(applicationContext, currentPkg, 10000, config)
                val appName = repository?.getAppName(currentPkg) ?: currentPkg
                
                val configStatus = if (config != null) "已配置为${config.type}" else "未配置"
                Log.d(TAG, "App: $appName, Category: ${category.name}, Config: $configStatus")
                
                // If Distraction (Fragmented) - 只有用户设置为干扰的应用才会触发
                if (category == TimeCategory.FRAGMENTED) {
                    Log.i(TAG, "Dimming for distraction app: $appName")
                    updateNotification("🌙 变暗中: $appName")
                    handler.post { updateDimmer(true) }
                } else {
                    val hint = if (config == null) "（未设为干扰）" else ""
                    updateNotification("✓ $appName $hint")
                    handler.post { updateDimmer(false) }
                }
            } else {
                updateNotification("夜间模式就绪")
                handler.post { updateDimmer(false) }
            }
        }
    }

    private fun getForegroundApp(): String? {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val time = System.currentTimeMillis()
        
        // 扩大查询范围到1小时，确保能获取到前台应用
        val events = usm.queryEvents(time - 1000 * 60 * 60, time)
        val event = UsageEvents.Event()
        
        var lastEventTime = 0L
        var currentPkg: String? = null
        
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                // 过滤系统应用
                if (event.packageName != "com.android.systemui" && 
                    event.packageName != "android" &&
                    event.packageName != packageName) {
                    if (event.timeStamp > lastEventTime) {
                        lastEventTime = event.timeStamp
                        currentPkg = event.packageName
                    }
                }
            }
        }
        return currentPkg
    }

    private fun updateDimmer(shouldDim: Boolean) {
        if (shouldDim) {
            if (overlayView == null) {
                addOverlay()
            }
            
            // Increase alpha
            if (currentAlpha < 0.85f) {
                currentAlpha += 0.005f // Slow increase
                updateOverlayAlpha(currentAlpha)
            }
        } else {
            // Decrease alpha or remove
            if (currentAlpha > 0) {
                currentAlpha -= 0.02f // Fast decrease
                if (currentAlpha <= 0) {
                    currentAlpha = 0f
                    removeOverlay()
                } else {
                    updateOverlayAlpha(currentAlpha)
                }
            }
        }
    }

    private fun addOverlay() {
        if (overlayView != null) return

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) 
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY 
            else 
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or 
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or 
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        
        overlayView = View(this).apply {
            setBackgroundColor(0xFF000000.toInt()) // Black
            alpha = 0f
        }
        
        try {
            windowManager?.addView(overlayView, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateOverlayAlpha(alpha: Float) {
        overlayView?.let {
            it.alpha = alpha
            // windowManager?.updateViewLayout(it, (it.layoutParams as WindowManager.LayoutParams))
        }
    }

    private fun removeOverlay() {
        if (overlayView != null) {
            try {
                windowManager?.removeView(overlayView)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            overlayView = null
            currentAlpha = 0f
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(checkRunnable)
        removeOverlay()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
