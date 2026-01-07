package com.example.delayme.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.room.Room
import com.example.delayme.R
import com.example.delayme.data.local.AppDatabase
import com.example.delayme.data.model.TimeCategory
import com.example.delayme.data.repository.UsageRepository
import com.example.delayme.domain.logic.TimeClassifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

import android.provider.Settings
import android.widget.Toast
import android.os.PowerManager
import android.net.Uri

class ZenModeService : Service() {

    companion object {
        private const val TAG = "ZenModeService"
    }

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private val handler = Handler(Looper.getMainLooper())
    private var repository: UsageRepository? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())

    // State
    private var distractionStartTime: Long = 0
    private var isOverlayShown = false
    
    // Config
    private var triggerDurationMs = 20 * 60 * 1000L // Default 20 Minutes

    private val checkRunnable = object : Runnable {
        override fun run() {
            checkConditions()
            handler.postDelayed(this, 5000) // Check every 5 seconds
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "onCreate: ZenModeService starting...")
        
        handler.post {
            Toast.makeText(applicationContext, "禅模式服务已启动", Toast.LENGTH_SHORT).show()
        }
        
        checkBatteryOptimization()
        startForegroundService()
        
        val db = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java, "delayme-db"
        ).build()
        repository = UsageRepository(applicationContext, db.appDao())

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        
        handler.post(checkRunnable)
        Log.i(TAG, "onCreate: ZenModeService started successfully")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "onStartCommand: Service received start command")
        return START_STICKY
    }

    private fun startForegroundService() {
        val channelId = "ZenModeServiceChannel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Zen Mode Service",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("禅模式运行中")
            .setContentText("正在监测您的专注状态...")
            .setSmallIcon(R.mipmap.ic_launcher)
            .build()

        startForeground(4, notification)
    }

    private fun updateNotification(text: String) {
        val notification = NotificationCompat.Builder(this, "ZenModeServiceChannel")
            .setContentTitle("禅模式")
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOnlyAlertOnce(true)
            .build()
        
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(4, notification)
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

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                packageName
            )
        } else {
            appOps.checkOpNoThrow(
                android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                packageName
            )
        }
        return mode == android.app.AppOpsManager.MODE_ALLOWED
    }

    private fun checkConditions() {
        Log.d(TAG, "checkConditions: Starting check...")
        
        if (!hasUsageStatsPermission()) {
            updateNotification("⚠️ 缺少权限：请允许访问使用记录")
            Log.w(TAG, "Missing usage stats permission")
            return
        }
        
        if (!Settings.canDrawOverlays(this)) {
            updateNotification("⚠️ 缺少权限：请允许悬浮窗权限")
            Log.w(TAG, "Missing overlay permission")
            return
        }

        // Update config
        repository?.let {
            triggerDurationMs = it.getZenModeTriggerDuration() * 60 * 1000L
            Log.d(TAG, "Trigger duration set to: ${triggerDurationMs}ms (${it.getZenModeTriggerDuration()} minutes)")
        }

        serviceScope.launch {
            val currentPkg = getForegroundApp()
            Log.d(TAG, "Current foreground app: $currentPkg")
            
            if (currentPkg != null) {
                val config = repository?.getAppConfig(currentPkg)
                val category = TimeClassifier.classify(applicationContext, currentPkg, 10000, config)
                val appName = repository?.getAppName(currentPkg) ?: currentPkg
                
                val configStatus = if (config != null) "已配置为${config.type}" else "未配置"
                Log.d(TAG, "App: $appName, Package: $currentPkg, Category: ${category.name}, Config: $configStatus")
                
                if (category == TimeCategory.FRAGMENTED) {
                    if (distractionStartTime == 0L) {
                        distractionStartTime = System.currentTimeMillis()
                        Log.d(TAG, "Started tracking distraction time")
                    }
                    
                    val duration = System.currentTimeMillis() - distractionStartTime
                    val remaining = (triggerDurationMs - duration) / 1000
                    
                    Log.d(TAG, "Distraction duration: ${duration}ms, Trigger at: ${triggerDurationMs}ms, Remaining: ${remaining}s")
                    
                    if (duration >= triggerDurationMs) {
                        Log.i(TAG, "TRIGGERING ZEN MODE for app: $appName")
                        updateNotification("🧘 提醒喝水: $appName")
                        handler.post { showOverlay() }
                    } else {
                        updateNotification("⏱ $appName | 剩余: ${remaining}s")
                    }
                } else {
                    if (distractionStartTime != 0L) {
                        Log.d(TAG, "Stopping distraction tracking - app is now safe")
                    }
                    distractionStartTime = 0
                    val hint = if (config == null) "（未设为干扰）" else ""
                    updateNotification("✓ $appName $hint")
                    handler.post { hideOverlay() }
                }
            } else {
                if (distractionStartTime != 0L) {
                    Log.d(TAG, "Stopping distraction tracking - no foreground app")
                }
                distractionStartTime = 0
                updateNotification("待机中...")
                handler.post { hideOverlay() }
            }
        }
    }

    private fun getForegroundApp(): String? {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val time = System.currentTimeMillis()
        
        Log.d(TAG, "getForegroundApp: Querying usage events at $time")
        
        // 需要过滤的系统包名（精确匹配）
        val systemPackages = setOf(
            "com.android.systemui",
            "android",
            "com.android.settings",
            "com.miui.home",
            "com.huawei.android.launcher",
            "com.oppo.launcher",
            "com.vivo.launcher",
            "com.sec.android.app.launcher",
            "com.google.android.apps.nexuslauncher",
            "com.android.launcher",
            "com.android.launcher2",
            "com.android.launcher3",
            "com.miui.securitycenter",
            "com.huawei.systemmanager",
            "com.coloros.safecenter",
            "com.vivo.permissionmanager"
        )
        
        // 查询最近5分钟的事件
        val queryStart = time - 1000 * 60 * 5
        val events = usm.queryEvents(queryStart, time) 
        val event = UsageEvents.Event()
        
        var latestValidPkg: String? = null
        var latestValidTime = 0L
        var eventCount = 0
        
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            eventCount++
            
            // Check for both MOVE_TO_FOREGROUND (old) and ACTIVITY_RESUMED (new)
            val isForegroundEvent = when {
                android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q -> {
                    event.eventType == UsageEvents.Event.ACTIVITY_RESUMED
                }
                else -> {
                    event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND
                }
            }
            
            if (isForegroundEvent) {
                val pkg = event.packageName
                // 只过滤系统包名（精确匹配），不再使用模糊匹配
                val isSystemPackage = systemPackages.contains(pkg) || 
                    pkg.startsWith("com.android.launcher")
                
                if (!isSystemPackage) {
                    if (event.timeStamp > latestValidTime) {
                        latestValidTime = event.timeStamp
                        latestValidPkg = pkg
                        Log.d(TAG, "Found foreground event: $pkg at ${event.timeStamp}")
                    }
                }
            }
        }
        
        Log.d(TAG, "Total events scanned: $eventCount, latest: $latestValidPkg")
        
        if (latestValidPkg != null) {
            val age = time - latestValidTime
            Log.d(TAG, "getForegroundApp: Latest foreground app: $latestValidPkg (${age}ms ago)")
            return latestValidPkg
        }
        
        Log.d(TAG, "getForegroundApp: No recent events, trying UsageStats fallback...")
        
        // Fallback to UsageStats - get most recently used app
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, time - 1000 * 60 * 60 * 24, time)
        Log.d(TAG, "UsageStats count: ${stats?.size ?: 0}")
        if (stats != null && stats.isNotEmpty()) {
            val sorted = stats.sortedByDescending { it.lastTimeUsed }
            for (stat in sorted) {
                val pkg = stat.packageName
                val isSystemPackage = systemPackages.contains(pkg) || 
                    pkg.startsWith("com.android.launcher")
                    
                if (!isSystemPackage) {
                    Log.d(TAG, "getForegroundApp: Found via UsageStats: $pkg")
                    return pkg
                }
            }
        }
        
        Log.w(TAG, "getForegroundApp: Could not determine foreground app")
        return null
    }

    private fun showOverlay() {
        if (isOverlayShown) return
        
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or 
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.CENTER

        val layout = FrameLayout(this)
        layout.setBackgroundColor(Color.parseColor("#CC000000")) // Semi-transparent black
        layout.setPadding(50, 50, 50, 50)
        
        val textView = TextView(this)
        textView.text = "🧘\n\n已经看了很久了\n喝口水休息一下吧"
        textView.textSize = 24f
        textView.setTextColor(Color.WHITE)
        textView.gravity = Gravity.CENTER
        
        layout.addView(textView)
        
        overlayView = layout
        windowManager?.addView(overlayView, params)
        isOverlayShown = true
    }

    private fun hideOverlay() {
        if (!isOverlayShown) return
        
        if (overlayView != null) {
            windowManager?.removeView(overlayView)
            overlayView = null
        }
        isOverlayShown = false
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }
    
    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(checkRunnable)
        hideOverlay()
    }
}
