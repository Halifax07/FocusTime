package com.example.delayme.service

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageStatsManager
import android.app.usage.UsageEvents
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.LinearInterpolator
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
import java.util.Random

import android.provider.Settings
import android.util.Log
import android.widget.Toast
import android.os.PowerManager
import android.net.Uri

class DanmakuService : Service() {

    companion object {
        private const val TAG = "DanmakuService"
    }

    private var windowManager: WindowManager? = null
    private var overlayContainer: FrameLayout? = null
    private val handler = Handler(Looper.getMainLooper())
    private var repository: UsageRepository? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private val random = Random()

    // State
    private var distractionStartTime: Long = 0
    private var isOverlayShown = false
    private var screenWidth = 0
    private var screenHeight = 0
    private var lastDebugInfo = "" // 调试信息
    
    // Config
    private var triggerDurationMs = 15 * 60 * 1000L // Default 15 Minutes
    private val messages = listOf(
        // 神秘低语系列
        "“它”在注视着你...",
        "深渊也在凝视着你",
        "你的时间正在流向虚空",
        "古老的低语在耳边回荡...",
        "现实与虚幻的边界模糊了",
        "你听到那个声音了吗？",
        // 幽默警示系列
        "Ph'nglui... 放下手机吧",
        "你的SAN值正在下降",
        "无名之物在兑唤你...",
        "这不是梦，但你该醒醉了",
        // 存在主义系列
        "在无尽的宇宙中，你在看手机",
        "你的灵魂渴望自由",
        "偶尔抬头看看真实的天空",
        "羊皮纸上的文字正在褚色...",
        "绝望是无尽的，就像这个视频",
        // 温和提醒系列
        "梦中之城在召唤你休息",
        "抬起头，凝视虚空",
        "古老的智慧：休息一下"
    )

    private val checkRunnable = object : Runnable {
        override fun run() {
            checkConditions()
            handler.postDelayed(this, 5000) // Check every 5 seconds
        }
    }

    private val spawnDanmakuRunnable = object : Runnable {
        override fun run() {
            if (isOverlayShown) {
                spawnDanmaku()
                // Calculate delay based on duration (density increases with time)
                val duration = System.currentTimeMillis() - distractionStartTime
                val baseDelay = 2000L
                val factor = (duration - triggerDurationMs) / (1000 * 60 * 5) // Every 5 mins extra
                val delay = (baseDelay - factor * 200).coerceAtLeast(300) // Min 300ms
                
                handler.postDelayed(this, delay)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "onCreate: DanmakuService starting...")
        
        // 显示 Toast 让用户知道服务已启动
        handler.post {
            Toast.makeText(applicationContext, "弹幕攻击服务已启动", Toast.LENGTH_SHORT).show()
        }
        
        // 检查电池优化设置
        checkBatteryOptimization()
        
        startForegroundService()
        
        val db = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java, "delayme-db"
        ).build()
        repository = UsageRepository(applicationContext, db.appDao())

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        
        // Get screen metrics using modern API
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val windowMetrics = windowManager?.currentWindowMetrics
            windowMetrics?.let {
                screenWidth = it.bounds.width()
                screenHeight = it.bounds.height()
            }
        } else {
            @Suppress("DEPRECATION")
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager?.defaultDisplay?.getMetrics(metrics)
            screenWidth = metrics.widthPixels
            screenHeight = metrics.heightPixels
        }
        
        Log.d(TAG, "Screen initialized: ${screenWidth}x${screenHeight}")
        handler.post(checkRunnable)
        Log.i(TAG, "onCreate: DanmakuService started successfully")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "onStartCommand: Service received start command")
        // 返回 START_STICKY 确保服务被系统杀死后会自动重启
        return START_STICKY
    }

    private fun startForegroundService() {
        Log.d(TAG, "startForegroundService: Creating notification channel...")
        val channelId = "DanmakuServiceChannel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Danmaku Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
            Log.d(TAG, "Notification channel created: $channelId")
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("弹幕攻击就绪")
            .setContentText("正在监测您的专注状态...")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .build()

        Log.d(TAG, "startForegroundService: Starting foreground with notification ID 3")
        startForeground(3, notification)
        Log.d(TAG, "startForegroundService: Foreground started successfully")
    }

    private fun updateNotification(text: String) {
        val notification = NotificationCompat.Builder(this, "DanmakuServiceChannel")
            .setContentTitle("弹幕攻击运行中")
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOnlyAlertOnce(true)
            .build()
        
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(3, notification)
    }

    private fun checkBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                // 应用未被加入电池优化白名单，提示用户
                handler.post {
                    Toast.makeText(
                        applicationContext,
                        "⚠️ 请将专注时光加入电池优化白名单，否则服务可能被系统杀死",
                        Toast.LENGTH_LONG
                    ).show()
                }
                
                // 尝试打开电池优化设置
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
        
        // 先更新通知表示正在检查
        handler.post { updateNotification("🔍 检查中...") }
        
        if (!hasUsageStatsPermission()) {
            handler.post { updateNotification("⚠️ 缺少权限：请允许访问使用记录") }
            Log.w(TAG, "Missing usage stats permission")
            return
        }
        
        if (!Settings.canDrawOverlays(this)) {
            handler.post { updateNotification("⚠️ 缺少权限：请允许悬浮窗权限") }
            Log.w(TAG, "Missing overlay permission")
            return
        }

        // Update config
        var triggerMinutes = 0
        repository?.let {
            triggerMinutes = it.getDanmakuTriggerDuration()
            triggerDurationMs = triggerMinutes * 60 * 1000L
            Log.d(TAG, "Trigger duration set to: ${triggerDurationMs}ms ($triggerMinutes minutes)")
        }

        serviceScope.launch {
            val currentPkg = getForegroundApp()
            Log.d(TAG, "Current foreground app: $currentPkg, debug: $lastDebugInfo")
            
            if (currentPkg == null) {
                distractionStartTime = 0
                handler.post { 
                    // 显示调试信息帮助诊断
                    updateNotification("❓ 无前台应用 | $lastDebugInfo") 
                    stopDanmaku()
                }
                return@launch
            }
            
            // 调试：尝试获取所有配置，看看数据库是否有数据
            try {
                val config = repository?.getAppConfig(currentPkg)
                Log.d(TAG, "getAppConfig($currentPkg) returned: $config")
                
                val category = TimeClassifier.classify(applicationContext, currentPkg, 10000, config)
                val appName = repository?.getAppName(currentPkg) ?: currentPkg.substringAfterLast(".")
                
                // 更详细的日志，显示用户配置状态
                val configType = config?.type?.name ?: "未配置"
                Log.d(TAG, "App: $appName, Package: $currentPkg, Category: ${category.name}, Config: $configType")
                
                if (category == TimeCategory.FRAGMENTED) {
                    if (distractionStartTime == 0L) {
                        distractionStartTime = System.currentTimeMillis()
                        Log.d(TAG, "Started tracking distraction time")
                    }
                    
                    val duration = System.currentTimeMillis() - distractionStartTime
                    val remaining = (triggerDurationMs - duration) / 1000
                    
                    Log.d(TAG, "Distraction duration: ${duration}ms, Trigger at: ${triggerDurationMs}ms, Remaining: ${remaining}s")
                    
                    if (duration >= triggerDurationMs) {
                        Log.i(TAG, "TRIGGERING DANMAKU for app: $appName")
                        handler.post { 
                            updateNotification("🎯 攻击中: $appName")
                            startDanmaku() 
                        }
                    } else {
                        handler.post { 
                            updateNotification("⏱ $appName (${configType}) | ${remaining}秒后攻击")
                        }
                    }
                } else {
                    if (distractionStartTime != 0L) {
                        Log.d(TAG, "Stopping distraction tracking - app is now safe")
                    }
                    distractionStartTime = 0
                    handler.post { 
                        // 显示详细状态：应用名 + 配置类型 + 分类结果
                        updateNotification("👀 $appName | 配置:$configType | 分类:${category.name}")
                        stopDanmaku() 
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting config: ${e.message}", e)
                handler.post { updateNotification("❌ 错误: ${e.message}") }
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
        
        // 查询最近10分钟的事件
        val queryStart = time - 1000 * 60 * 10
        val events = usm.queryEvents(queryStart, time)
        Log.d(TAG, "Querying events from ${queryStart} to ${time}")
        
        val event = UsageEvents.Event()
        
        var latestValidPkg: String? = null
        var latestValidTime = 0L
        var totalEventCount = 0
        var foregroundEventCount = 0
        val allForegroundApps = mutableListOf<String>()
        
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            totalEventCount++
            
            // 检查所有可能的前台事件类型
            val eventType = event.eventType
            val isForegroundEvent = eventType == UsageEvents.Event.ACTIVITY_RESUMED ||
                eventType == UsageEvents.Event.MOVE_TO_FOREGROUND ||
                eventType == 1 // MOVE_TO_FOREGROUND 的实际值
            
            if (isForegroundEvent) {
                foregroundEventCount++
                val pkg = event.packageName
                if (!allForegroundApps.contains(pkg)) {
                    allForegroundApps.add(pkg)
                }
                
                // 只过滤系统包名
                val isSystemPackage = systemPackages.contains(pkg) || 
                    pkg.startsWith("com.android.launcher")
                
                if (!isSystemPackage) {
                    if (event.timeStamp > latestValidTime) {
                        latestValidTime = event.timeStamp
                        latestValidPkg = pkg
                        Log.d(TAG, "Found foreground event: $pkg at ${event.timeStamp}, type: $eventType")
                    }
                }
            }
        }
        
        // 保存调试信息供通知使用
        lastDebugInfo = "事件:$totalEventCount 前台:$foregroundEventCount 应用:${allForegroundApps.takeLast(3).joinToString(",") { it.substringAfterLast(".") }}"
        Log.d(TAG, "Total events: $totalEventCount, foreground events: $foregroundEventCount, apps: $allForegroundApps")
        Log.d(TAG, "Latest valid: $latestValidPkg")
        
        if (latestValidPkg != null) {
            val age = time - latestValidTime
            Log.d(TAG, "getForegroundApp: Latest foreground app: $latestValidPkg (${age}ms ago)")
            return latestValidPkg
        }
        
        Log.d(TAG, "getForegroundApp: No ACTIVITY_RESUMED events found, trying UsageStats fallback...")
        
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
                    Log.d(TAG, "getForegroundApp: Found via UsageStats: $pkg (lastTimeUsed: ${stat.lastTimeUsed})")
                    return pkg
                }
            }
        }
        
        Log.w(TAG, "getForegroundApp: Could not determine foreground app")
        return null
    }

    private fun startDanmaku() {
        if (isOverlayShown) {
            Log.d(TAG, "startDanmaku: Already showing, skipping")
            return
        }
        
        Log.i(TAG, "startDanmaku: Starting danmaku overlay")
        
        // Update metrics using modern API
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val windowMetrics = windowManager?.currentWindowMetrics
            windowMetrics?.let {
                screenWidth = it.bounds.width()
                screenHeight = it.bounds.height()
            }
        } else {
            @Suppress("DEPRECATION")
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager?.defaultDisplay?.getMetrics(metrics)
            screenWidth = metrics.widthPixels
            screenHeight = metrics.heightPixels
        }
        
        Log.d(TAG, "Screen size: ${screenWidth}x${screenHeight}")

        val params = WindowManager.LayoutParams(
            screenWidth,
            screenHeight,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) 
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY 
            else 
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or 
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or 
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        
        overlayContainer = FrameLayout(this)
        
        try {
            windowManager?.addView(overlayContainer, params)
            isOverlayShown = true
            Log.i(TAG, "startDanmaku: Overlay added successfully")
            android.widget.Toast.makeText(this, "弹幕攻击已触发！", android.widget.Toast.LENGTH_SHORT).show()
            handler.post(spawnDanmakuRunnable)
        } catch (e: Exception) {
            Log.e(TAG, "startDanmaku: Failed to add overlay", e)
            android.widget.Toast.makeText(this, "弹幕启动失败: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    private fun stopDanmaku() {
        if (!isOverlayShown) return
        
        handler.removeCallbacks(spawnDanmakuRunnable)
        if (overlayContainer != null) {
            try {
                windowManager?.removeView(overlayContainer)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            overlayContainer = null
            isOverlayShown = false
        }
    }

    private fun spawnDanmaku() {
        val container = overlayContainer ?: return
        
        val textView = TextView(this)
        textView.text = messages[random.nextInt(messages.size)]
        textView.textSize = 24f + random.nextInt(8) // 24-32sp (更大)
        textView.setTextColor(getCthulhuColor())
        textView.setTypeface(textView.typeface, android.graphics.Typeface.BOLD) // 加粗
        textView.setShadowLayer(4f, 2f, 2f, Color.BLACK) // 更明显的阴影
        textView.paint.isFakeBoldText = true // 额外加粗
        
        // Random Y position
        val params = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        )
        params.topMargin = random.nextInt(screenHeight - 200) + 100 // Avoid extreme top/bottom
        textView.layoutParams = params
        
        container.addView(textView)
        
        // Measure to get width
        textView.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val textWidth = textView.measuredWidth
        
        // Animation
        val startX = screenWidth.toFloat()
        val endX = -textWidth.toFloat()
        
        val duration = 4000L + random.nextInt(4000) // 4-8 seconds
        
        val animator = ObjectAnimator.ofFloat(textView, "translationX", startX, endX)
        animator.duration = duration
        animator.interpolator = LinearInterpolator()
        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                container.removeView(textView)
            }
        })
        animator.start()
    }
    
    private fun getCthulhuColor(): Int {
        // 克苏鲁风格颜色：神秘的紫/绿/青色调
        val colors = listOf(
            Color.rgb(138, 43, 226),   // 紫罗兰色
            Color.rgb(75, 0, 130),     // 靖蓝色
            Color.rgb(0, 255, 127),    // 绿色荧光
            Color.rgb(64, 224, 208),   // 绿松石色
            Color.rgb(186, 85, 211),   // 中兰花紫
            Color.rgb(148, 0, 211),    // 深紫罗兰
            Color.rgb(0, 206, 209),    // 深天蓝
            Color.rgb(127, 255, 212),  // 碟绿色
            Color.rgb(199, 21, 133),   // 深粉色
            Color.WHITE                 // 白色（对比）
        )
        return colors[random.nextInt(colors.size)]
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(checkRunnable)
        handler.removeCallbacks(spawnDanmakuRunnable)
        stopDanmaku()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
