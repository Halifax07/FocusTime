package com.example.delayme.data.repository

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import com.example.delayme.data.local.AppDao
import com.example.delayme.data.model.AppConfig
import com.example.delayme.data.model.AppType
import com.example.delayme.data.model.TimeCategory
import com.example.delayme.data.model.TimeSegment
import com.example.delayme.domain.logic.TimeClassifier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.util.Calendar

class UsageRepository(
    private val context: Context,
    private val appDao: AppDao
) {

    val allConfigs: Flow<List<AppConfig>> = appDao.getAllConfigs()
    
    private val prefs = context.getSharedPreferences("delayme_prefs", Context.MODE_PRIVATE)

    fun isDimmerEnabled(): Boolean = prefs.getBoolean("dimmer_enabled", false)
    fun setDimmerEnabled(enabled: Boolean) = prefs.edit().putBoolean("dimmer_enabled", enabled).apply()

    fun getDimmerTriggerHour(): Int = prefs.getInt("dimmer_trigger_hour", 23)
    fun setDimmerTriggerHour(hour: Int) = prefs.edit().putInt("dimmer_trigger_hour", hour).apply()

    fun getDimmerTriggerMinute(): Int = prefs.getInt("dimmer_trigger_minute", 0)
    fun setDimmerTriggerMinute(minute: Int) = prefs.edit().putInt("dimmer_trigger_minute", minute).apply()

    fun isZenModeEnabled(): Boolean = prefs.getBoolean("zen_mode_enabled", false)
    fun setZenModeEnabled(enabled: Boolean) = prefs.edit().putBoolean("zen_mode_enabled", enabled).apply()

    fun getZenModeTriggerDuration(): Int = prefs.getInt("zen_mode_trigger_duration", 20)
    fun setZenModeTriggerDuration(minutes: Int) = prefs.edit().putInt("zen_mode_trigger_duration", minutes).apply()

    fun isDanmakuEnabled(): Boolean = prefs.getBoolean("danmaku_enabled", false)
    fun setDanmakuEnabled(enabled: Boolean) = prefs.edit().putBoolean("danmaku_enabled", enabled).apply()

    fun getDanmakuTriggerDuration(): Int = prefs.getInt("danmaku_trigger_duration", 15)
    fun setDanmakuTriggerDuration(minutes: Int) = prefs.edit().putInt("danmaku_trigger_duration", minutes).apply()

    suspend fun updateAppConfig(config: AppConfig) {
        appDao.insertConfig(config)
    }
    
    suspend fun getAppConfig(packageName: String): AppConfig? {
        return appDao.getConfig(packageName)
    }

    suspend fun getDailySegments(date: Long, explicitConfigs: List<AppConfig>? = null): List<TimeSegment> {
        val configs = explicitConfigs?.associateBy { it.packageName }
            ?: allConfigs.first().associateBy { it.packageName }
        return getRealUsageSegments(date, configs)
    }

    fun getTodayUsageStats(): List<android.app.usage.UsageStats> {
        // Deprecated: Use getPreciseTodayAppUsage instead for accurate foreground time
        return emptyList()
    }

    suspend fun getPreciseTodayAppUsage(): Map<String, Long> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val calendar = Calendar.getInstance()
        
        // Target Range: Today 00:00 to Now
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val dayStart = calendar.timeInMillis
        val now = System.currentTimeMillis()
        
        // Query Range: Start 2 hours earlier to catch apps open across midnight
        val queryStart = dayStart - 2 * 60 * 60 * 1000 
        val events = usageStatsManager.queryEvents(queryStart, now)
        
        val appUsageMap = mutableMapOf<String, Long>()
        var currentPackage: String? = null
        var lastEventTime = queryStart
        
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val eventTime = event.timeStamp
            
            // Calculate duration for the previous segment
            if (currentPackage != null) {
                // Intersection Logic:
                // Segment: [lastEventTime, eventTime]
                // Target: [dayStart, now]
                val start = maxOf(lastEventTime, dayStart)
                val end = minOf(eventTime, now)
                
                if (end > start) {
                    val duration = end - start
                    appUsageMap[currentPackage] = (appUsageMap[currentPackage] ?: 0L) + duration
                }
            }
            
            // Update State
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                currentPackage = event.packageName
            } else if (event.eventType == UsageEvents.Event.MOVE_TO_BACKGROUND ||
                       event.eventType == UsageEvents.Event.SCREEN_NON_INTERACTIVE) {
                currentPackage = null
            }
            
            lastEventTime = eventTime
        }
        
        // Handle currently open app (from last event until now)
        if (currentPackage != null) {
            val start = maxOf(lastEventTime, dayStart)
            val end = now
            
            if (end > start) {
                val duration = end - start
                appUsageMap[currentPackage] = (appUsageMap[currentPackage] ?: 0L) + duration
            }
        }
        
        appUsageMap
    }

    private suspend fun getRealUsageSegments(date: Long, configs: Map<String, AppConfig>): List<TimeSegment> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val calendar = Calendar.getInstance().apply { timeInMillis = date }
        
        // Start of day (00:00:00)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val dayStart = calendar.timeInMillis

        // End of day (23:59:59) or Now if it's today
        val now = System.currentTimeMillis()
        calendar.add(Calendar.DAY_OF_MONTH, 1) // Next day 00:00
        val endOfDay = calendar.timeInMillis
        val dayEnd = if (endOfDay > now) now else endOfDay

        // Query Range: Start 2 hours earlier to catch apps open across midnight
        val queryStart = dayStart - 2 * 60 * 60 * 1000 
        val events = usageStatsManager.queryEvents(queryStart, dayEnd)
        
        val segments = mutableListOf<TimeSegment>()
        
        var lastEventTime = queryStart
        var currentPackage: String? = null

        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val eventTime = event.timeStamp
            
            // Logic: Process the interval [lastEventTime, eventTime]
            // We only care about the part of this interval that overlaps with [dayStart, dayEnd]
            
            val start = maxOf(lastEventTime, dayStart)
            val end = minOf(eventTime, dayEnd)
            
            if (end > start) {
                val duration = (end - start) / 1000 / 60
                
                // If duration is valid (even 0 minutes if >0ms, but here we work with minutes for segments)
                // Filter out tiny gaps? No, sum accurately.
                
                if (duration >= 0) { // Keep even small segments for timeline continuity? Or > 0?
                    // Let's stick to >0 minutes for visual segments
                     if (duration > 0 || (end - start) > 1000) { // at least 1 sec
                         val segmentDuration = if (duration == 0L) 1L else duration
                         
                         if (currentPackage == null) {
                             // This was a gap -> REST
                             segments.add(TimeSegment(
                                startTime = start,
                                endTime = end,
                                packageName = null,
                                category = TimeCategory.REST,
                                durationMinutes = segmentDuration
                             ))
                         } else {
                             // This was an app session
                             // SAFETY CAP: If a single segment is > 3 hours (180 mins), assume it's a ghost session
                             if (segmentDuration > 180) {
                                 // Split it? Or just mark as REST?
                                 // Let's keep the first 30 mins as valid, rest as REST? 
                                 // Simplest: Mark as REST to avoid punishing the user for bugs.
                                 segments.add(TimeSegment(
                                    startTime = start,
                                    endTime = end,
                                    packageName = currentPackage,
                                    category = TimeCategory.REST, // Downgrade to REST
                                    durationMinutes = segmentDuration
                                 ))
                             } else {
                                 val config = configs[currentPackage]
                                 val category = TimeClassifier.classify(context, currentPackage!!, (end - start), config)
                                 segments.add(TimeSegment(
                                    startTime = start,
                                    endTime = end,
                                    packageName = currentPackage,
                                    category = category,
                                    durationMinutes = segmentDuration
                                 ))
                             }
                         }
                     }
                }
            }

            // Update State
            // 1: MOVE_TO_FOREGROUND
            // 2: MOVE_TO_BACKGROUND
            // 12: KEYGUARD_HIDDEN (Unlock)
            // 16: SCREEN_NON_INTERACTIVE (Screen Off) - API 29+
            // 17: KEYGUARD_SHOWN (Lock Screen) - API 29+
            // 23: ACTIVITY_STOPPED
            // 26: DEVICE_SHUTDOWN

            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                currentPackage = event.packageName
            } else if (event.eventType == UsageEvents.Event.MOVE_TO_BACKGROUND ||
                       event.eventType == 16 || // SCREEN_NON_INTERACTIVE
                       event.eventType == 17 || // KEYGUARD_SHOWN
                       event.eventType == 26) { // DEVICE_SHUTDOWN
                currentPackage = null 
            }
            
            lastEventTime = eventTime
        }
        
        // Handle Tail: [lastEventTime, dayEnd]
        if (dayEnd > lastEventTime) {
            val start = maxOf(lastEventTime, dayStart)
            val end = dayEnd
            
            if (end > start) {
                val duration = (end - start) / 1000 / 60
                val segmentDuration = if (duration == 0L) 1L else duration
                
                if (currentPackage != null) {
                     val config = configs[currentPackage]
                     val category = TimeClassifier.classify(context, currentPackage!!, (end - start), config)
                     segments.add(TimeSegment(
                        startTime = start,
                        endTime = end,
                        packageName = currentPackage,
                        category = category,
                        durationMinutes = segmentDuration
                     ))
                } else {
                     segments.add(TimeSegment(
                        startTime = start,
                        endTime = end,
                        packageName = null,
                        category = TimeCategory.REST,
                        durationMinutes = segmentDuration
                     ))
                }
            }
        }

        segments
    }
    
    fun getAppName(packageName: String): String {
        return try {
            val pm = context.packageManager
            val info = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(info).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            // App is uninstalled, return package name with a suffix
            if (packageName.contains(".")) {
                "${packageName.substringAfterLast('.')} (已卸载)"
            } else {
                "$packageName (已卸载)"
            }
        }
    }

    fun getInstalledApps(): List<Pair<String, String>> {
        val pm = context.packageManager
        val intent = android.content.Intent(android.content.Intent.ACTION_MAIN, null)
        intent.addCategory(android.content.Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(intent, 0)
        
        return apps.map { resolveInfo ->
            val pkg = resolveInfo.activityInfo.packageName
            val name = resolveInfo.loadLabel(pm).toString()
            pkg to name
        }.distinctBy { it.first }.sortedBy { it.second }
    }
}
