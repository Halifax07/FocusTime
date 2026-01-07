package com.example.delayme.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.delayme.ui.components.RiverOfTime
import com.example.delayme.ui.components.StickerCard
import com.example.delayme.ui.components.HandDrawnCard
import com.example.delayme.ui.theme.*
import com.example.delayme.ui.viewmodel.MainViewModel
import com.example.delayme.ui.viewmodel.AppUsageDisplay
import com.example.delayme.utils.ShareUtils
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.ui.draw.shadow
import androidx.compose.material.icons.filled.Share
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import com.example.delayme.R

@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onNavigateToDetails: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val showZenOverlay = viewModel.checkZenModeCondition(uiState.segments)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CreamBackground)
    ) {
        // Background Grid Texture
        DetailGridBackground()

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 13.dp),
            contentPadding = PaddingValues(top = 4.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp) // Tighter spacing
        ) {
            // 1. Illustrated Header with Data
            item {
                DashboardHeader(
                    focusScore = uiState.focusScore,
                    scoreChange = uiState.focusScoreChange,
                    onShare = { ShareUtils.shareFocusCard(context, uiState.focusScore, uiState.focusScoreChange) }
                )
            }

            // 2. Daily Summary Card (Replaces Focus Score Card)
            item {
                DailySummaryCard(
                    focusScore = uiState.focusScore,
                    scoreChange = uiState.focusScoreChange,
                    onDetailsClick = onNavigateToDetails
                )
            }

            // 3. Top 5 Distractions (Sticker Collection)
            item {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 7.dp) // Match SettingsScreen width (20dp total)
                ) {
                    Text(
                        text = "耗时应用排行",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = Color(0xFF5D5D5D),
                        modifier = Modifier.padding(bottom = 4.dp)
                    )

                    uiState.topFragmentedApps.forEach { item ->
                        StickerAppRow(item = item)
                    }
                }
            }
            
            item {
                Spacer(modifier = Modifier.height(80.dp)) // Bottom padding
            }
        }
        
        // Zen Mode Overlay
        if (showZenOverlay) {
            ZenOverlay()
        }
    }
}

@Composable
fun DashboardHeader(
    focusScore: Int,
    scoreChange: Int,
    onShare: () -> Unit
) {
    // 获取与猫咪匹配的背景色
    val (catImageRes, catBgColor) = rememberCatWithBackground(focusScore)
    
    // 使用猫咪的背景色作为整体背景色（稍微调淡一点作为大面积背景）
    val backgroundColor = catBgColor.copy(alpha = 0.6f)

    // A large "Scene" header without the card border
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp) // Reduced height
            .clip(RoundedCornerShape(bottomStart = 40.dp, bottomEnd = 40.dp))
            .background(backgroundColor)
    ) {
        // Background Decor (Clouds/Hearts) - Subtle
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            
            // Draw some soft circles/blobs for atmosphere
            drawCircle(
                color = Color.White.copy(alpha = 0.4f),
                radius = canvasWidth * 0.4f,
                center = Offset(canvasWidth * 0.5f, canvasHeight * 0.4f)
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.3f),
                radius = canvasWidth * 0.2f,
                center = Offset(canvasWidth * 0.8f, canvasHeight * 0.2f)
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.3f),
                radius = canvasWidth * 0.25f,
                center = Offset(canvasWidth * 0.2f, canvasHeight * 0.6f)
            )
        }

        // Top Bar: Share Button (Right)
        IconButton(
            onClick = onShare,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(20.dp) // Keep consistent with previous padding
                .background(Color.White.copy(alpha = 0.6f), CircleShape)
        ) {
            Icon(Icons.Default.Share, contentDescription = "Share", tint = MutedPink)
        }

        // Center: The Cat Scene
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .align(Alignment.Center)
                // Offset removed to center vertically
                .size(180.dp) // Larger size for the whole composition
        ) {
            // 1. The Outer Ring / Halo (Translucent White)
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.5f),
                    radius = size.minDimension / 2
                )
            }

            // 2. The Cat Image with matching background color (使用已获取的猫咪资源)
            CatMascotWithRes(
                imageRes = catImageRes,
                bgColor = catBgColor,
                modifier = Modifier
                    .fillMaxSize(0.85f)
                    .clip(CircleShape)
            )
        }
    }
}

@Composable
fun DailySummaryCard(
    focusScore: Int,
    scoreChange: Int,
    onDetailsClick: () -> Unit
) {
    // A "Sticky Note" style card that feels more integrated
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(16.dp))
            .background(Color(0xFFFFF9E6), RoundedCornerShape(16.dp)) // Light yellow sticky note color
            .border(1.dp, Color(0xFFE0E0E0), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        // Details Button (Top Right)
        Text(
            text = "详情 >",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF8D8D8D),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .clickable { onDetailsClick() }
                .padding(4.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Score Circle (Smaller)
            Box(
                modifier = Modifier
                    .size(70.dp)
                    .background(Color.White, CircleShape)
                    .border(2.dp, MatchaGreen.copy(alpha = 0.3f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "$focusScore",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = MatchaGreen,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Cursive
                    )
                    Text(
                        text = "分",
                        fontSize = 10.sp,
                        color = Color.Gray
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Right: Daily Comment & Change
            Column(modifier = Modifier.weight(1f)) {
                // Daily Comment
                Text(
                    text = when {
                        focusScore > 80 -> "状态绝佳！继续保持这份专注力。"
                        focusScore >= 60 -> "表现不错，今天也很有收获。"
                        else -> "别灰心，休息一下找回状态吧。"
                    },
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color(0xFF4A4A4A)
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Change Indicator (Subtle)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (scoreChange >= 0) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                        contentDescription = null,
                        tint = if (scoreChange >= 0) MutedPink else Color.Gray,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "较昨日 ${if (scoreChange > 0) "+" else ""}$scoreChange",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            }
        }
    }
}

@Composable
fun CatMascot(
    score: Int,
    modifier: Modifier = Modifier
) {
    val (imageRes, bgColor) = rememberCatWithBackground(score)
    CatMascotWithRes(imageRes = imageRes, bgColor = bgColor, modifier = modifier)
}

/**
 * 直接使用指定的图片资源和背景色显示猫咪
 */
@Composable
fun CatMascotWithRes(
    imageRes: Int,
    bgColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(bgColor),
        contentAlignment = Alignment.Center
    ) {
        // 先绘制背景装饰（在图片下层）
        Canvas(modifier = Modifier.fillMaxSize()) {
            when (imageRes) {
                // 🎀 cat_neutral - 彩带装饰
                R.drawable.cat_neutral -> {
                    val ribbonColors = listOf(
                        Color(0xFFFF6B6B), Color(0xFF4ECDC4), Color(0xFFFFE66D),
                        Color(0xFF95E1D3), Color(0xFFF38181), Color(0xFFAA96DA)
                    )
                    val ribbonWidth = 10.dp.toPx()
                    for (i in 0..8) {
                        val offset = i * 30.dp.toPx()
                        drawLine(
                            color = ribbonColors[i % ribbonColors.size].copy(alpha = 0.5f),
                            start = Offset(-offset, -20.dp.toPx()),
                            end = Offset(size.width - offset + 60.dp.toPx(), size.height + 20.dp.toPx()),
                            strokeWidth = ribbonWidth,
                            cap = StrokeCap.Round
                        )
                    }
                    for (i in 0..6) {
                        val offset = i * 35.dp.toPx()
                        drawLine(
                            color = ribbonColors[(i + 3) % ribbonColors.size].copy(alpha = 0.4f),
                            start = Offset(size.width + offset, -20.dp.toPx()),
                            end = Offset(-offset - 60.dp.toPx(), size.height + 20.dp.toPx()),
                            strokeWidth = ribbonWidth * 0.8f,
                            cap = StrokeCap.Round
                        )
                    }
                    // 小圆点装饰
                    val dotColors = listOf(Color(0xFFFF6B6B), Color(0xFF4ECDC4), Color(0xFFFFE66D), Color(0xFFAA96DA))
                    val random = kotlin.random.Random(42)
                    for (i in 0..20) {
                        drawCircle(
                            color = dotColors[i % dotColors.size].copy(alpha = 0.6f),
                            radius = (4 + random.nextFloat() * 6).dp.toPx(),
                            center = Offset(random.nextFloat() * size.width, random.nextFloat() * size.height)
                        )
                    }
                }
                
                // 💖 cat_happy - 爱心和星星装饰
                R.drawable.cat_happy -> {
                    val random = kotlin.random.Random(123)
                    val heartColor = Color(0xFFFF69B4).copy(alpha = 0.4f)
                    for (i in 0..12) {
                        val cx = random.nextFloat() * size.width
                        val cy = random.nextFloat() * size.height
                        val heartSize = (10 + random.nextFloat() * 12).dp.toPx()
                        drawCircle(color = heartColor, radius = heartSize * 0.5f, center = Offset(cx - heartSize * 0.3f, cy))
                        drawCircle(color = heartColor, radius = heartSize * 0.5f, center = Offset(cx + heartSize * 0.3f, cy))
                        drawPath(
                            path = androidx.compose.ui.graphics.Path().apply {
                                moveTo(cx - heartSize * 0.6f, cy)
                                lineTo(cx, cy + heartSize * 0.8f)
                                lineTo(cx + heartSize * 0.6f, cy)
                                close()
                            },
                            color = heartColor
                        )
                    }
                    // 闪烁星星
                    for (i in 0..10) {
                        drawCircle(
                            color = Color.White.copy(alpha = 0.7f),
                            radius = (2 + random.nextFloat() * 4).dp.toPx(),
                            center = Offset(random.nextFloat() * size.width, random.nextFloat() * size.height)
                        )
                    }
                }
                
                // 🍂 cat_autumn - 飘落树叶装饰
                R.drawable.cat_autumn -> {
                    val leafColors = listOf(Color(0xFFFF8C00), Color(0xFFCD853F), Color(0xFFDAA520), Color(0xFFB8860B), Color(0xFFFF6347))
                    val random = kotlin.random.Random(456)
                    for (i in 0..15) {
                        val cx = random.nextFloat() * size.width
                        val cy = random.nextFloat() * size.height
                        val leafSize = (8 + random.nextFloat() * 10).dp.toPx()
                        drawOval(
                            color = leafColors[i % leafColors.size].copy(alpha = 0.5f),
                            topLeft = Offset(cx - leafSize, cy - leafSize * 0.5f),
                            size = Size(leafSize * 2, leafSize)
                        )
                    }
                    // 金色光点
                    for (i in 0..10) {
                        drawCircle(
                            color = Color(0xFFFFD700).copy(alpha = 0.5f),
                            radius = (3 + random.nextFloat() * 4).dp.toPx(),
                            center = Offset(random.nextFloat() * size.width, random.nextFloat() * size.height)
                        )
                    }
                }
                
                // 👑 cat_emperor - 金色光芒装饰
                R.drawable.cat_emperor -> {
                    val goldColor = Color(0xFFFFD700)
                    val centerX = size.width / 2
                    val centerY = size.height / 2
                    // 放射光芒
                    for (i in 0..17) {
                        val angle = (i * 20f) * (Math.PI / 180f).toFloat()
                        val startRadius = size.minDimension * 0.2f
                        val endRadius = size.minDimension * 0.55f
                        drawLine(
                            color = goldColor.copy(alpha = 0.35f),
                            start = Offset(centerX + kotlin.math.cos(angle) * startRadius, centerY + kotlin.math.sin(angle) * startRadius),
                            end = Offset(centerX + kotlin.math.cos(angle) * endRadius, centerY + kotlin.math.sin(angle) * endRadius),
                            strokeWidth = 4.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                    // 金色小星星
                    val random = kotlin.random.Random(789)
                    for (i in 0..12) {
                        drawCircle(
                            color = goldColor.copy(alpha = 0.6f),
                            radius = (3 + random.nextFloat() * 4).dp.toPx(),
                            center = Offset(random.nextFloat() * size.width, random.nextFloat() * size.height)
                        )
                    }
                }
                
                // 🍉 cat_watermelon - 西瓜条纹和籽装饰
                R.drawable.cat_watermelon -> {
                    val random = kotlin.random.Random(111)
                    // 深绿色条纹
                    val stripeColor = Color(0xFF228B22).copy(alpha = 0.25f)
                    for (i in 0..7) {
                        val offset = i * 22.dp.toPx()
                        drawLine(
                            color = stripeColor,
                            start = Offset(offset, 0f),
                            end = Offset(offset + size.width * 0.4f, size.height),
                            strokeWidth = 14.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                    // 西瓜籽
                    val seedColor = Color(0xFF1C1C1C).copy(alpha = 0.5f)
                    for (i in 0..15) {
                        val cx = random.nextFloat() * size.width
                        val cy = random.nextFloat() * size.height
                        drawOval(
                            color = seedColor,
                            topLeft = Offset(cx - 4.dp.toPx(), cy - 6.dp.toPx()),
                            size = Size(8.dp.toPx(), 12.dp.toPx())
                        )
                    }
                }
                
                // ☕ cat_coffee - 咖啡蒸汽和咖啡豆装饰
                R.drawable.cat_coffee -> {
                    val random = kotlin.random.Random(222)
                    // 蒸汽云朵
                    val steamColor = Color(0xFF8B4513).copy(alpha = 0.12f)
                    for (i in 0..20) {
                        drawCircle(
                            color = steamColor,
                            radius = (12 + random.nextFloat() * 15).dp.toPx(),
                            center = Offset(random.nextFloat() * size.width, random.nextFloat() * size.height)
                        )
                    }
                    // 咖啡豆
                    val beanColor = Color(0xFF3E2723).copy(alpha = 0.35f)
                    for (i in 0..10) {
                        drawOval(
                            color = beanColor,
                            topLeft = Offset(random.nextFloat() * size.width - 5.dp.toPx(), random.nextFloat() * size.height - 7.dp.toPx()),
                            size = Size(10.dp.toPx(), 14.dp.toPx())
                        )
                    }
                }
                
                // 😢 cat_sad - 雨滴装饰
                R.drawable.cat_sad -> {
                    val rainColor = Color(0xFF87CEEB).copy(alpha = 0.4f)
                    val random = kotlin.random.Random(333)
                    // 雨滴
                    for (i in 0..25) {
                        val x = random.nextFloat() * size.width
                        val y = random.nextFloat() * size.height
                        val dropLength = (12 + random.nextFloat() * 15).dp.toPx()
                        drawLine(
                            color = rainColor,
                            start = Offset(x, y),
                            end = Offset(x - 4.dp.toPx(), y + dropLength),
                            strokeWidth = 3.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                    // 小水珠
                    for (i in 0..12) {
                        drawCircle(
                            color = rainColor.copy(alpha = 0.3f),
                            radius = (3 + random.nextFloat() * 4).dp.toPx(),
                            center = Offset(random.nextFloat() * size.width, random.nextFloat() * size.height)
                        )
                    }
                }
                
                // 😴 cat_tired - 灰色云朵和ZZZ装饰
                R.drawable.cat_tired -> {
                    val cloudColor = Color(0xFF9E9E9E).copy(alpha = 0.25f)
                    val random = kotlin.random.Random(444)
                    // 云朵
                    for (i in 0..8) {
                        val cx = random.nextFloat() * size.width
                        val cy = random.nextFloat() * size.height
                        val cloudSize = (18 + random.nextFloat() * 15).dp.toPx()
                        drawCircle(color = cloudColor, radius = cloudSize, center = Offset(cx, cy))
                        drawCircle(color = cloudColor, radius = cloudSize * 0.7f, center = Offset(cx - cloudSize * 0.5f, cy + cloudSize * 0.2f))
                        drawCircle(color = cloudColor, radius = cloudSize * 0.8f, center = Offset(cx + cloudSize * 0.4f, cy + cloudSize * 0.25f))
                    }
                    // ZZZ 睡眠符号
                    val zColor = Color(0xFF607D8B).copy(alpha = 0.4f)
                    drawCircle(color = zColor, radius = 5.dp.toPx(), center = Offset(size.width * 0.75f, size.height * 0.15f))
                    drawCircle(color = zColor, radius = 7.dp.toPx(), center = Offset(size.width * 0.82f, size.height * 0.25f))
                    drawCircle(color = zColor, radius = 9.dp.toPx(), center = Offset(size.width * 0.88f, size.height * 0.38f))
                }
            }
        }
        
        // 猫咪图片在最上层，会覆盖背景装饰
        Image(
            painter = painterResource(id = imageRes),
            contentDescription = "Cat Mascot",
            modifier = Modifier.fillMaxSize()
        )
    }
}

/**
 * 根据分数返回对应的猫咪图片资源和背景色
 * 使用 remember 保持在同一分数区间内选择稳定
 */
@Composable
fun rememberCatWithBackground(score: Int): Pair<Int, Color> {
    // 图片和对应背景色的配对 - 每只猫配专属背景色（增加不透明度）
    val highScoreOptions = listOf(
        R.drawable.cat_happy to Color(0xFFFFC1CC),      // 更饱和的粉色 - 配合开心猫的温馨感
        R.drawable.cat_autumn to Color(0xFFFFCC80),     // 更饱和的秋叶橙色 - 配合秋天主题
        R.drawable.cat_emperor to Color(0xFFFFF8DC)     // 更饱和的象牙白黄 - 配合皇帝的高贵感
    )
    val midScoreOptions = listOf(
        R.drawable.cat_neutral to Color(0xFFFFEE58),    // 更饱和的明亮黄色 - 活泼欢快
        R.drawable.cat_watermelon to Color(0xFFA5D6A7), // 更饱和的薄荷绿 - 配合西瓜的绿皮
        R.drawable.cat_coffee to Color(0xFFFFE082)      // 更饱和的焦糖奶油色 - 配合咖啡
    )
    val lowScoreOptions = listOf(
        R.drawable.cat_sad to Color(0xFF90CAF9),        // 更饱和的忧郁淡蓝 - 配合sad表情
        R.drawable.cat_tired to Color(0xFFB0BEC5)       // 更饱和的疲惫灰蓝 - 配合机甲灰色
    )
    
    // 根据分数确定当前区间
    val scoreLevel = when {
        score > 80 -> 2  // 高分
        score >= 60 -> 1 // 中分
        else -> 0        // 低分
    }
    
    // 使用 remember(scoreLevel) 当分数区间变化时重新随机选择
    return remember(scoreLevel) {
        when (scoreLevel) {
            2 -> highScoreOptions.random()
            1 -> midScoreOptions.random()
            else -> lowScoreOptions.random()
        }
    }
}

@Composable
fun StickerAppRow(item: AppUsageDisplay) {
    val context = androidx.compose.ui.platform.LocalContext.current
    StickerCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = { /* Maybe show details */ }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // App Icon
            androidx.compose.ui.viewinterop.AndroidView(
                factory = { ctx ->
                    android.widget.ImageView(ctx).apply {
                        scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                    }
                },
                update = { imageView ->
                    try {
                        val icon = context.packageManager.getApplicationIcon(item.packageName)
                        imageView.setImageDrawable(icon)
                    } catch (e: Exception) {
                        imageView.setImageResource(android.R.drawable.sym_def_app_icon)
                    }
                },
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = item.appName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF4A4A4A)
                )
                // Cute doodle next to name?
            }
        }
        
        // Time Badge
        Box(
            modifier = Modifier
                .background(Color(0xFFF5F5F5), RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            val durationText = if (item.duration >= 60) {
                "${item.duration / 60}时${item.duration % 60}分"
            } else {
                "${item.duration}分"
            }
            Text(
                text = durationText,
                color = Color(0xFF8D8D8D),
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun ZenOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.8f))
            .clickable { /* Absorb clicks */ },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "",
                fontSize = 64.sp
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Take a break...",
                style = MaterialTheme.typography.headlineLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Cursive
            )
            Text(
                text = "The world is beautiful, no rush.",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.8f)
            )
        }
    }
}
