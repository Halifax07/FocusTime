package com.example.delayme.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.delayme.data.model.AppConfig
import com.example.delayme.data.model.AppType
import com.example.delayme.data.model.TimeCategory
import com.example.delayme.data.model.TimeSegment
import com.example.delayme.data.repository.UsageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AppUsageDisplay(
    val packageName: String,
    val appName: String,
    val duration: Long,
    val openCount: Int,
    val fragmentationIndex: Float,
    val category: TimeCategory = TimeCategory.FRAGMENTED
)

data class DailyAnalysis(
    val timeWasters: List<String> = emptyList(),
    val focusWins: List<String> = emptyList(),
    val advice: String = ""
)

data class HomeUiState(
    val segments: List<TimeSegment> = emptyList(),
    val totalNecessary: Long = 0, // Focus
    val totalFragmented: Long = 0, // Distraction
    val totalLife: Long = 0, // Life/Utility
    val totalRest: Long = 0,
    val focusScore: Int = 0,
    val focusScoreChange: Int = 0, // Difference from yesterday
    val topFragmentedApps: List<AppUsageDisplay> = emptyList(),
    val weeklyScores: List<Pair<String, Int>> = emptyList(),
    val dailyAnalysis: DailyAnalysis = DailyAnalysis()
)

class MainViewModel(private val repository: UsageRepository) : ViewModel() {

    private val _refreshTrigger = MutableStateFlow(System.currentTimeMillis())
    
    // Labs State
    private val _isDimmerEnabled = MutableStateFlow(repository.isDimmerEnabled())
    val isDimmerEnabled: StateFlow<Boolean> = _isDimmerEnabled

    fun setDimmerEnabled(enabled: Boolean) {
        _isDimmerEnabled.value = enabled
        repository.setDimmerEnabled(enabled)
    }

    private val _dimmerTriggerHour = MutableStateFlow(repository.getDimmerTriggerHour())
    val dimmerTriggerHour: StateFlow<Int> = _dimmerTriggerHour

    private val _dimmerTriggerMinute = MutableStateFlow(repository.getDimmerTriggerMinute())
    val dimmerTriggerMinute: StateFlow<Int> = _dimmerTriggerMinute

    fun setDimmerTriggerTime(hour: Int, minute: Int) {
        _dimmerTriggerHour.value = hour
        _dimmerTriggerMinute.value = minute
        repository.setDimmerTriggerHour(hour)
        repository.setDimmerTriggerMinute(minute)
    }
    
    private val _isDanmakuEnabled = MutableStateFlow(repository.isDanmakuEnabled())
    val isDanmakuEnabled: StateFlow<Boolean> = _isDanmakuEnabled

    fun setDanmakuEnabled(enabled: Boolean) {
        _isDanmakuEnabled.value = enabled
        repository.setDanmakuEnabled(enabled)
    }

    private val _danmakuTriggerDuration = MutableStateFlow(repository.getDanmakuTriggerDuration())
    val danmakuTriggerDuration: StateFlow<Int> = _danmakuTriggerDuration

    fun setDanmakuTriggerDuration(minutes: Int) {
        _danmakuTriggerDuration.value = minutes
        repository.setDanmakuTriggerDuration(minutes)
    }
    
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _allInstalledApps = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    
    val filteredApps = combine(_allInstalledApps, _searchQuery) { apps, query ->
        if (query.isBlank()) {
            apps
        } else {
            apps.filter { it.second.contains(query, ignoreCase = true) || it.first.contains(query, ignoreCase = true) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _weeklyHistory = MutableStateFlow<List<Pair<String, Int>>>(emptyList())

    init {
        viewModelScope.launch(Dispatchers.IO) {
            _allInstalledApps.value = repository.getInstalledApps()
        }
        
        // Pre-load past 6 days history in background to avoid UI lag
        // 加载过去6天的数据（今天-6 到 今天-1）
        viewModelScope.launch(Dispatchers.IO) {
            val history = mutableListOf<Pair<String, Int>>()
            val calendar = java.util.Calendar.getInstance()
            val chineseDayNames = listOf("周日", "周一", "周二", "周三", "周四", "周五", "周六")
            val now = System.currentTimeMillis()
            
            // Load data for: Today-6, Today-5, ... Today-1
            // 从左到右：最左边是6天前，最右边是昨天
            for (i in 6 downTo 1) {
                calendar.timeInMillis = now
                calendar.add(java.util.Calendar.DAY_OF_YEAR, -i)
                val date = calendar.timeInMillis
                
                // 获取星期几的中文名称
                val dayOfWeek = calendar.get(java.util.Calendar.DAY_OF_WEEK) - 1 // 0=周日
                val dayName = chineseDayNames[dayOfWeek]
                
                // Heavy IO - 获取那一天完整的数据 (0:00 - 24:00)
                val daySegments = repository.getDailySegments(date)
                val score = calculateScore(daySegments)
                history.add(dayName to score)
            }
            _weeklyHistory.value = history
        }
    }
    
    val appConfigs = repository.allConfigs.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val uiState: StateFlow<HomeUiState> = combine(_refreshTrigger, appConfigs, _weeklyHistory) { _, configs, history ->
        // Re-calculate when trigger or config changes
        val today = System.currentTimeMillis()
        
        // 1. Get Today's Data (Realtime)
        // Pass configs explicitly to ensure we use the very latest settings for classification
        val segments = repository.getDailySegments(today, configs)
        val preciseUsageMap = repository.getPreciseTodayAppUsage()
        
        // 2. Get Weekly Data (Combine History + Today)
        // history 包含过去6天的数据，今日显示为"今日"
        val todayScore = calculateScore(segments)
        // weeklyScores: [6天前, 5天前, 4天前, 3天前, 2天前, 昨天, 今日]
        val weeklyScores = history + ("今日" to todayScore)
        
        // 3. Get Yesterday's Score (for comparison)
        // Optimization: Try to get from history (last item is yesterday)
        val yesterdayScore = if (history.isNotEmpty()) {
            history.last().second
        } else {
             // Fallback if history not loaded yet (avoid heavy call if possible, or just accept it once)
             val yesterday = today - 24 * 60 * 60 * 1000
             val yesterdaySegments = repository.getDailySegments(yesterday)
             calculateScore(yesterdaySegments)
        }
        
        calculateStats(segments, preciseUsageMap, yesterdayScore, weeklyScores)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        HomeUiState()
    )

    private fun calculateScore(segments: List<TimeSegment>): Int {
        var focusMinutes = 0L
        var distractionMinutes = 0L
        var lifeMinutes = 0L

        segments.forEach { segment ->
            val duration = segment.durationMinutes
            
            when (segment.category) {
                TimeCategory.NECESSARY -> focusMinutes += duration
                TimeCategory.FRAGMENTED -> distractionMinutes += duration
                TimeCategory.LIFE -> lifeMinutes += duration
                else -> { /* Rest or others ignored */ }
            }
        }
        
        // Minute-Based Logic
        // Base Score: 100
        // Distraction: Cost 0.075/min
        // Life: Cost 0.01/min (Capped at 15 points max deduction)
        // Focus: Bonus 0.025/min
        
        val baseScore = 100.0
        val distractionCost = distractionMinutes * 0.075
        // New Logic: Cap & Cut Model for Life category
        // We take the smaller of (minutes * 0.01) or 15.0
        val rawLifeCost = lifeMinutes * 0.01
        val lifeCost = if (rawLifeCost > 15.0) 15.0 else rawLifeCost
        
        val focusBonus = focusMinutes * 0.025
        
        val finalScore = (baseScore - distractionCost - lifeCost + focusBonus)
            .coerceIn(0.0, 100.0)
        
        return kotlin.math.round(finalScore).toInt()
    }

    private fun calculateStats(
        segments: List<TimeSegment>, 
        preciseUsageMap: Map<String, Long>,
        yesterdayScore: Int,
        weeklyScores: List<Pair<String, Int>>
    ): HomeUiState {
        var necessary = 0L
        var fragmented = 0L
        var life = 0L
        var rest = 0L
        
        data class AppStats(
            var duration: Long, 
            var openCount: Int,
            var necessaryDuration: Long = 0,
            var lifeDuration: Long = 0,
            var fragmentedDuration: Long = 0
        )
        val appStatsMap = mutableMapOf<String, AppStats>()

        segments.forEach { segment ->
            when (segment.category) {
                TimeCategory.NECESSARY -> necessary += segment.durationMinutes
                TimeCategory.LIFE -> life += segment.durationMinutes
                TimeCategory.FRAGMENTED -> fragmented += segment.durationMinutes
                TimeCategory.REST -> rest += segment.durationMinutes
            }
            
            segment.packageName?.let { pkg ->
                val stats = appStatsMap.getOrPut(pkg) { AppStats(0, 0) }
                stats.duration += segment.durationMinutes
                stats.openCount += 1
                
                when(segment.category) {
                    TimeCategory.NECESSARY -> stats.necessaryDuration += segment.durationMinutes
                    TimeCategory.LIFE -> stats.lifeDuration += segment.durationMinutes
                    TimeCategory.FRAGMENTED -> stats.fragmentedDuration += segment.durationMinutes
                    else -> {}
                }
            }
        }

        val topApps = preciseUsageMap.entries
            .filter { it.value > 0 }
            .sortedByDescending { it.value }
            .take(10)
            .map { (pkg, totalTimeMillis) ->
                val durationMinutes = totalTimeMillis / 1000 / 60
                val name = repository.getAppName(pkg)
                
                val stats = appStatsMap[pkg]
                val openCount = stats?.openCount ?: 0
                val index = if (openCount > 0) durationMinutes.toFloat() / openCount else 0f
                
                val dominantCategory = if (stats != null) {
                    when {
                        stats.fragmentedDuration > 0 && stats.fragmentedDuration >= stats.lifeDuration && stats.fragmentedDuration >= stats.necessaryDuration -> TimeCategory.FRAGMENTED
                        stats.lifeDuration >= stats.necessaryDuration -> TimeCategory.LIFE
                        else -> TimeCategory.NECESSARY
                    }
                } else {
                    TimeCategory.FRAGMENTED
                }
                
                AppUsageDisplay(pkg, name, durationMinutes, openCount, index, dominantCategory)
            }

        val currentScore = calculateScore(segments)

        // Analysis Logic
        val timeWasters = appStatsMap.entries
            .filter { it.value.fragmentedDuration > 30 } // > 30 mins distraction
            .sortedByDescending { it.value.fragmentedDuration }
            .take(3)
            .map { repository.getAppName(it.key) }
            
        val focusWins = appStatsMap.entries
            .filter { it.value.necessaryDuration > 30 } // > 30 mins focus
            .sortedByDescending { it.value.necessaryDuration }
            .take(3)
            .map { repository.getAppName(it.key) }
            
        val advice = if (timeWasters.isNotEmpty()) {
            "今天在 ${timeWasters.first()} 上花了不少时间呢。明天试着减少30分钟吧！"
        } else if (focusWins.isNotEmpty()) {
            "太棒了！在 ${focusWins.first()} 上保持了很好的专注。继续加油！"
        } else {
            "今天是平衡的一天。明天又是新的开始！"
        }

        return HomeUiState(
            segments = segments,
            totalNecessary = necessary,
            totalFragmented = fragmented,
            totalLife = life,
            totalRest = rest,
            focusScore = currentScore,
            focusScoreChange = currentScore - yesterdayScore,
            topFragmentedApps = topApps,
            weeklyScores = weeklyScores,
            dailyAnalysis = DailyAnalysis(timeWasters, focusWins, advice)
        )
    }

    fun updateConfig(packageName: String, appName: String, type: AppType) {
        viewModelScope.launch {
            repository.updateAppConfig(AppConfig(packageName, appName, type))
            _refreshTrigger.value = System.currentTimeMillis() // Force refresh
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }
    
    fun refreshData() {
        _refreshTrigger.value = System.currentTimeMillis()
    }

    // Zen Mode State
    private val _zenModeEnabled = MutableStateFlow(repository.isZenModeEnabled())
    val zenModeEnabled: StateFlow<Boolean> = _zenModeEnabled

    private val _zenModeTriggerDuration = MutableStateFlow(repository.getZenModeTriggerDuration())
    val zenModeTriggerDuration: StateFlow<Int> = _zenModeTriggerDuration

    fun toggleZenMode(enabled: Boolean) {
        _zenModeEnabled.value = enabled
        repository.setZenModeEnabled(enabled)
    }

    fun setZenModeTriggerDuration(minutes: Int) {
        _zenModeTriggerDuration.value = minutes
        repository.setZenModeTriggerDuration(minutes)
    }

    fun checkZenModeCondition(segments: List<TimeSegment>): Boolean {
        if (!_zenModeEnabled.value || segments.isEmpty()) return false
        
        val lastDistraction = segments.lastOrNull { it.category == TimeCategory.FRAGMENTED }
        
        if (lastDistraction != null) {
            val now = System.currentTimeMillis()
            val fiveMinsAgo = now - 5 * 60 * 1000
            
            if (lastDistraction.endTime > fiveMinsAgo && lastDistraction.durationMinutes >= _zenModeTriggerDuration.value) {
                return true
            }
        }
        return false
    }

    fun exportData(context: android.content.Context) {
        val segments = uiState.value.segments
        val sb = StringBuilder()
        sb.append("StartTime,EndTime,Package,Duration(m),Category\n")
        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
        
        segments.forEach { 
            sb.append("${dateFormat.format(java.util.Date(it.startTime))},")
            sb.append("${dateFormat.format(java.util.Date(it.endTime))},")
            sb.append("${it.packageName},")
            sb.append("${it.durationMinutes},")
            sb.append("${it.category}\n")
        }
        
        try {
            val fileName = "delayme_export_${System.currentTimeMillis()}.csv"
            val file = java.io.File(context.cacheDir, fileName)
            file.writeText(sb.toString())
            
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )
            
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(android.content.Intent.createChooser(intent, "Export Data"))
        } catch (e: Exception) {
            e.printStackTrace()
            android.widget.Toast.makeText(context, "Export failed: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    fun shareInsight(context: android.content.Context) {
        val state = uiState.value
        val score = state.focusScore
        val beatPercent = if (score > 80) 90 else if (score > 60) 60 else 30
        
        val text = """
            📅 每日心流图 (${java.text.SimpleDateFormat("MM-dd", java.util.Locale.getDefault()).format(java.util.Date())})
            
            🎯 专注分: $score
            🏆 击败了全国 $beatPercent% 的摸鱼人
            
            📊 专注: ${state.totalNecessary/60}h ${state.totalNecessary%60}m
            ☕ 生活: ${state.totalLife/60}h ${state.totalLife%60}m
            😈 干扰: ${state.totalFragmented/60}h ${state.totalFragmented%60}m
            
            #DelayMe #DigitalWellbeing
        """.trimIndent()
        
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_TEXT, text)
        }
        context.startActivity(android.content.Intent.createChooser(intent, "Share Insight"))
    }
}

class MainViewModelFactory(private val repository: UsageRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
