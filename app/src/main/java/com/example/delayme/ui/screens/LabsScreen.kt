package com.example.delayme.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.delayme.service.DanmakuService
import com.example.delayme.service.DimmerService
import com.example.delayme.service.ZenModeService
import com.example.delayme.ui.theme.*
import com.example.delayme.ui.components.WashiTapeButton
import com.example.delayme.ui.viewmodel.MainViewModel
import kotlin.math.roundToInt

@Composable
fun LabsScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    var isDimmerExpanded by remember { mutableStateOf(false) }
    var isZenExpanded by remember { mutableStateOf(false) }
    var isDanmakuExpanded by remember { mutableStateOf(false) }
    
    val isDimmerEnabled by viewModel.isDimmerEnabled.collectAsState()
    val zenModeEnabled by viewModel.zenModeEnabled.collectAsState()
    val isDanmakuEnabled by viewModel.isDanmakuEnabled.collectAsState()
    val danmakuTriggerDuration by viewModel.danmakuTriggerDuration.collectAsState()
    val zenModeTriggerDuration by viewModel.zenModeTriggerDuration.collectAsState()
    
    val dimmerTriggerHour by viewModel.dimmerTriggerHour.collectAsState()
    val dimmerTriggerMinute by viewModel.dimmerTriggerMinute.collectAsState()
    
    // 记录等待启动的服务类型
    var pendingServiceType by remember { mutableStateOf<String?>(null) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (isGranted && pendingServiceType != null) {
                // 权限已授权，检查悬浮窗权限后启动服务
                if (!Settings.canDrawOverlays(context)) {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    )
                    context.startActivity(intent)
                } else {
                    // 根据等待的服务类型启动对应服务
                    when (pendingServiceType) {
                        "danmaku" -> {
                            viewModel.setDanmakuEnabled(true)
                            context.startForegroundService(Intent(context, DanmakuService::class.java))
                        }
                        "zen" -> {
                            viewModel.toggleZenMode(true)
                            context.startForegroundService(Intent(context, ZenModeService::class.java))
                        }
                        "dimmer" -> {
                            viewModel.setDimmerEnabled(true)
                            context.startForegroundService(Intent(context, DimmerService::class.java))
                        }
                    }
                }
                pendingServiceType = null
            }
        }
    )

    val scrollState = rememberScrollState()
    
    // 检查是否需要显示后台保活提示
    var showBackgroundSettingsCard by remember { mutableStateOf(true) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CreamBackground) // Global Rule: Cream Background
    ) {
        // Background Texture (Grid)
        DetailGridBackground()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(20.dp)
        ) {
            // Header Section
            LabsHeader()
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // 后台保活设置提示卡片
            if (showBackgroundSettingsCard) {
                BackgroundSettingsCard(
                    onDismiss = { showBackgroundSettingsCard = false }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            // Card 1: Sleep Dimmer (Magic Card Style)
            MagicFeatureCard(
                title = "助眠渐暗",
                description = "深夜使用干扰应用时屏幕变暗",
                icon = { MoonIcon() },
                accentColor = BabyBlue,
                isEnabled = isDimmerEnabled,
                isExpanded = isDimmerExpanded,
                onToggle = { enabled ->
                    if (enabled) {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                        ) {
                            pendingServiceType = "dimmer"
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else if (!Settings.canDrawOverlays(context)) {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            )
                            context.startActivity(intent)
                        } else {
                            viewModel.setDimmerEnabled(true)
                            context.startForegroundService(Intent(context, DimmerService::class.java))
                        }
                    } else {
                        viewModel.setDimmerEnabled(false)
                        context.stopService(Intent(context, DimmerService::class.java))
                    }
                },
                onClick = { isDimmerExpanded = !isDimmerExpanded }
            ) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    Text(
                        text = "详细设定",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = CharcoalGrey
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text("触发时间: ${String.format("%02d:%02d", dimmerTriggerHour, dimmerTriggerMinute)}", style = MaterialTheme.typography.bodyMedium, color = CharcoalGrey)
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("时", modifier = Modifier.width(24.dp), color = CharcoalGrey)
                        DoodleSlider(
                            value = dimmerTriggerHour.toFloat(),
                            onValueChange = { viewModel.setDimmerTriggerTime(it.roundToInt(), dimmerTriggerMinute) },
                            valueRange = 0f..23f,
                            color = MatchaGreen,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("分", modifier = Modifier.width(24.dp), color = CharcoalGrey)
                        DoodleSlider(
                            value = dimmerTriggerMinute.toFloat(),
                            onValueChange = { viewModel.setDimmerTriggerTime(dimmerTriggerHour, it.roundToInt()) },
                            valueRange = 0f..59f,
                            color = MatchaGreen,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("渐变时长", style = MaterialTheme.typography.bodyMedium, color = CharcoalGrey)
                        Text("10分钟", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = CharcoalGrey)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "原理：通过增加视觉负担，让人体自然产生疲惫感，从而主动放下手机睡觉。",
                        style = MaterialTheme.typography.bodySmall,
                        color = CharcoalGrey.copy(alpha = 0.6f),
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Card 2: Zen Mode
            MagicFeatureCard(
                title = "禅模式",
                description = "干扰时间过长时提醒喝水",
                icon = { ZenIcon() },
                accentColor = MatchaGreen,
                isEnabled = zenModeEnabled,
                isExpanded = isZenExpanded,
                onToggle = { enabled ->
                    if (enabled) {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                        ) {
                            pendingServiceType = "zen"
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else if (!Settings.canDrawOverlays(context)) {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            )
                            context.startActivity(intent)
                        } else {
                            viewModel.toggleZenMode(true)
                            context.startForegroundService(Intent(context, ZenModeService::class.java))
                        }
                    } else {
                        viewModel.toggleZenMode(false)
                        context.stopService(Intent(context, ZenModeService::class.java))
                    }
                },
                onClick = { isZenExpanded = !isZenExpanded }
            ) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    Text(
                        text = "详细设定",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = CharcoalGrey
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text("触发阈值: ${zenModeTriggerDuration}分钟", style = MaterialTheme.typography.bodyMedium, color = CharcoalGrey)
                    DoodleSlider(
                        value = zenModeTriggerDuration.toFloat(),
                        onValueChange = { viewModel.setZenModeTriggerDuration(it.roundToInt()) },
                        valueRange = 0f..60f,
                        color = MatchaGreen,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "原理：打断连续的沉浸状态，通过喝水这一物理动作，让大脑从多巴胺循环中脱离。",
                        style = MaterialTheme.typography.bodySmall,
                        color = CharcoalGrey.copy(alpha = 0.6f),
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(20.dp))

            // Card 4: Danmaku Attack
            MagicFeatureCard(
                title = "弹幕攻击",
                description = "沉浸干扰应用时屏幕飘过吐槽弹幕",
                icon = { DanmakuIcon() },
                accentColor = CreamyYellow,
                isEnabled = isDanmakuEnabled,
                isExpanded = isDanmakuExpanded,
                onToggle = { enabled ->
                    if (enabled) {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                        ) {
                            pendingServiceType = "danmaku"
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else if (!Settings.canDrawOverlays(context)) {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            )
                            context.startActivity(intent)
                        } else {
                            viewModel.setDanmakuEnabled(true)
                            context.startForegroundService(Intent(context, DanmakuService::class.java))
                        }
                    } else {
                        viewModel.setDanmakuEnabled(false)
                        context.stopService(Intent(context, DanmakuService::class.java))
                    }
                },
                onClick = { isDanmakuExpanded = !isDanmakuExpanded }
            ) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    Text(
                        text = "详细设定",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = CharcoalGrey
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text("触发阈值: ${danmakuTriggerDuration}分钟", style = MaterialTheme.typography.bodyMedium, color = CharcoalGrey)
                    DoodleSlider(
                        value = danmakuTriggerDuration.toFloat(),
                        onValueChange = { viewModel.setDanmakuTriggerDuration(it.roundToInt()) },
                        valueRange = 0f..60f,
                        color = NecessaryColor,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "原理：利用视觉遮挡和幽默打断，破坏沉浸式体验，让用户主动放下手机。",
                        style = MaterialTheme.typography.bodySmall,
                        color = CharcoalGrey.copy(alpha = 0.6f),
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(20.dp))

            // Data Export Tool
            WashiTapeButton(
                text = "导出数据 (CSV)",
                onClick = { viewModel.exportData(context) },
                color = MutedPink
            )
            
            Spacer(modifier = Modifier.height(80.dp)) // Bottom padding
        }
    }
}

@Composable
fun LabsHeader() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "实验室 (Labs)",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = CharcoalGrey
            )
            Text(
                text = "一些主动干预生活方式的小工具",
                style = MaterialTheme.typography.bodyMedium,
                color = CharcoalGrey.copy(alpha = 0.7f)
            )
        }
        // Cute Cat Character
        Box(
            modifier = Modifier.size(60.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                // Simple Cat Head
                drawCircle(color = Color(0xFFFFF0E0), radius = size.width / 2.2f)
                drawCircle(color = CharcoalGrey, radius = size.width / 2.2f, style = Stroke(width = 3f))
                
                // Ears
                val path = Path().apply {
                    moveTo(size.width * 0.2f, size.height * 0.3f)
                    lineTo(size.width * 0.1f, size.height * 0.1f)
                    lineTo(size.width * 0.35f, size.height * 0.2f)
                    close()
                    
                    moveTo(size.width * 0.8f, size.height * 0.3f)
                    lineTo(size.width * 0.9f, size.height * 0.1f)
                    lineTo(size.width * 0.65f, size.height * 0.2f)
                    close()
                }
                drawPath(path, color = Color(0xFFFFF0E0))
                drawPath(path, color = CharcoalGrey, style = Stroke(width = 3f, join = StrokeJoin.Round))
                
                // Face
                drawCircle(color = CharcoalGrey, radius = 2f, center = Offset(size.width * 0.35f, size.height * 0.5f))
                drawCircle(color = CharcoalGrey, radius = 2f, center = Offset(size.width * 0.65f, size.height * 0.5f))
                
                // Mouth
                drawArc(
                    color = CharcoalGrey,
                    startAngle = 0f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = Offset(size.width * 0.45f, size.height * 0.55f),
                    size = Size(size.width * 0.1f, size.height * 0.1f),
                    style = Stroke(width = 2f)
                )
            }
        }
    }
}

@Composable
fun MagicFeatureCard(
    title: String,
    description: String,
    icon: @Composable () -> Unit,
    accentColor: Color,
    isEnabled: Boolean,
    isExpanded: Boolean,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    // "Toy Block" / "Magic Card" Style
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 0.dp,
                shape = RoundedCornerShape(24.dp),
                clip = false
            ) // Custom shadow drawn below
    ) {
        // Hard Shadow (Block effect)
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = 4.dp, y = 4.dp)
                .background(Color.Black.copy(alpha = 0.1f), RoundedCornerShape(24.dp))
        )
        
        // Main Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White, RoundedCornerShape(24.dp))
                .border(2.dp, CharcoalGrey, RoundedCornerShape(24.dp))
                .clip(RoundedCornerShape(24.dp))
                .clickable { onClick() }
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    // Icon Container
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(accentColor.copy(alpha = 0.3f), RoundedCornerShape(18.dp))
                            .border(1.5.dp, CharcoalGrey, RoundedCornerShape(18.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        icon()
                    }
                    
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = CharcoalGrey
                        )
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodySmall,
                            color = CharcoalGrey.copy(alpha = 0.7f)
                        )
                    }
                }
                
                // Doodle Toggle
                DoodleToggle(checked = isEnabled, onCheckedChange = onToggle, accentColor = accentColor)
            }
            
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                content()
            }
        }
    }
}

@Composable
fun DoodleToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    accentColor: Color
) {
    val thumbOffset by animateDpAsState(if (checked) 22.dp else 2.dp)
    val trackColor by androidx.compose.animation.animateColorAsState(if (checked) accentColor else Color.LightGray.copy(alpha = 0.3f))
    
    Box(
        modifier = Modifier
            .width(48.dp)
            .height(28.dp)
            .background(trackColor, RoundedCornerShape(14.dp))
            .border(2.dp, CharcoalGrey, RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onCheckedChange(!checked) }
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset, y = 2.dp)
                .size(20.dp) // 24 - 4 border
                .background(Color.White, CircleShape)
                .border(2.dp, CharcoalGrey, CircleShape)
        )
    }
}

@Composable
fun DoodleSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    color: Color,
    modifier: Modifier = Modifier
) {
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        colors = SliderDefaults.colors(
            thumbColor = color,
            activeTrackColor = color,
            inactiveTrackColor = color.copy(alpha = 0.3f)
        ),
        modifier = modifier
    )
}

// --- Hand-Drawn Icons ---

@Composable
fun MoonIcon() {
    Canvas(modifier = Modifier.size(32.dp)) {
        val path = Path().apply {
            moveTo(size.width * 0.5f, 0f)
            cubicTo(
                size.width * 0.5f, 0f,
                size.width * 0.2f, size.height * 0.2f,
                size.width * 0.2f, size.height * 0.5f
            )
            cubicTo(
                size.width * 0.2f, size.height * 0.8f,
                size.width * 0.5f, size.height,
                size.width * 0.5f, size.height
            )
            cubicTo(
                size.width * 0.5f, size.height,
                size.width * 0.8f, size.height * 0.8f,
                size.width * 0.8f, size.height * 0.5f
            )
            cubicTo(
                size.width * 0.8f, size.height * 0.2f,
                size.width * 0.5f, 0f,
                size.width * 0.5f, 0f
            )
        }
        // Draw a crescent moon
        drawCircle(color = Color(0xFFFFD700), radius = size.width / 2.2f)
        drawCircle(color = Color.White.copy(alpha = 0.0f), radius = size.width / 2.2f) // Placeholder
        
        // Simple Crescent
        drawArc(
            color = CharcoalGrey,
            startAngle = 30f,
            sweepAngle = 300f,
            useCenter = false,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )
        // Fill
        drawCircle(color = Color(0xFFFFD700), radius = size.width/2.5f)
    }
}

@Composable
fun ZenIcon() {
    Canvas(modifier = Modifier.size(32.dp)) {
        // Head
        drawCircle(color = CharcoalGrey, radius = 4.dp.toPx(), center = Offset(size.width / 2, size.height * 0.2f), style = Stroke(2.dp.toPx()))
        // Body
        val path = Path().apply {
            moveTo(size.width / 2, size.height * 0.35f)
            lineTo(size.width * 0.2f, size.height * 0.8f)
            lineTo(size.width * 0.8f, size.height * 0.8f)
            close()
        }
        drawPath(path, color = CharcoalGrey, style = Stroke(2.dp.toPx(), join = StrokeJoin.Round))
    }
}

@Composable
fun DanmakuIcon() {
    Canvas(modifier = Modifier.size(32.dp)) {
        // Bubble
        val path = Path().apply {
            addRoundRect(
                androidx.compose.ui.geometry.RoundRect(
                    left = 0f, top = 0f, right = size.width, bottom = size.height * 0.8f,
                    cornerRadius = CornerRadius(8.dp.toPx())
                )
            )
            moveTo(size.width * 0.2f, size.height * 0.8f)
            lineTo(size.width * 0.1f, size.height)
            lineTo(size.width * 0.4f, size.height * 0.8f)
        }
        drawPath(path, color = Color.White)
        drawPath(path, color = CharcoalGrey, style = Stroke(2.dp.toPx(), join = StrokeJoin.Round))
        
        // Dots
        drawCircle(color = CharcoalGrey, radius = 2f, center = Offset(size.width * 0.3f, size.height * 0.4f))
        drawCircle(color = CharcoalGrey, radius = 2f, center = Offset(size.width * 0.5f, size.height * 0.4f))
        drawCircle(color = CharcoalGrey, radius = 2f, center = Offset(size.width * 0.7f, size.height * 0.4f))
    }
}

/**
 * 后台保活设置提示卡片
 */
@Composable
fun BackgroundSettingsCard(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val warningColor = MutedPink
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(2.dp, warningColor.copy(alpha = 0.5f), RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = warningColor.copy(alpha = 0.1f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "⚠️ 重要：后台保活设置",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = WarmBrown
                )
                Text(
                    text = "✕",
                    modifier = Modifier
                        .clickable { onDismiss() }
                        .padding(4.dp),
                    color = CharcoalGrey.copy(alpha = 0.6f),
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "为确保实验功能在后台正常运行，请完成以下设置：",
                style = MaterialTheme.typography.bodyMedium,
                color = CharcoalGrey
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // 设置项列表
            val settingsItems = listOf(
                "1. 电池优化 → 选择「无限制」",
                "2. 自启动 → 开启",
                "3. 后台运行 → 允许",
                "4. 省电策略 → 无限制"
            )
            
            settingsItems.forEach { item ->
                Text(
                    text = item,
                    style = MaterialTheme.typography.bodySmall,
                    color = CharcoalGrey.copy(alpha = 0.8f),
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 打开应用设置按钮
                Button(
                    onClick = {
                        try {
                            val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            // Fallback
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = warningColor),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("打开应用设置", fontSize = 12.sp)
                }
                
                // 打开电池设置按钮
                Button(
                    onClick = {
                        try {
                            val intent = Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            // Fallback
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CharcoalGrey),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("电池设置", fontSize = 12.sp)
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "提示：不同手机品牌设置位置可能不同，请在应用设置中寻找相关选项",
                style = MaterialTheme.typography.bodySmall,
                color = CharcoalGrey.copy(alpha = 0.5f),
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
            )
        }
    }
}
