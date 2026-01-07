package com.example.delayme.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.delayme.ui.theme.CreamBackground
import com.example.delayme.ui.theme.MatchaGreen
import com.example.delayme.ui.viewmodel.MainViewModel

@Composable
fun WeeklyFocusDetailScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CreamBackground)
    ) {
        // 1. Background Grid (Notebook paper style)
        DetailGridBackground()

        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // 2. Hand-drawn Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, start = 16.dp, end = 16.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color.White, RoundedCornerShape(12.dp))
                        .border(1.5.dp, Color(0xFF5D5D5D), RoundedCornerShape(12.dp))
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "返回",
                        tint = Color(0xFF5D5D5D),
                        modifier = Modifier.size(20.dp)
                    )
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Text(
                    text = "专注周报", // Changed title to be more "Journal" like
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontFamily = FontFamily.Cursive,
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color(0xFF5D5D5D)
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(32.dp)
            ) {
                // Section A: Weekly Focus Bar Chart container
                CozyCard(
                    title = "本周专注趋势"
                ) {
                    WeeklyBarChart(data = uiState.weeklyScores)
                }

                // Section B: Today's Focus Analysis (Diary Style)
                Column {
                    Text(
                        text = "今日洞察",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Cursive // Hand-drawn font
                        ),
                        color = Color(0xFF5D5D5D),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    
                    StickyNoteAnalysis(
                        timeWasters = uiState.dailyAnalysis.timeWasters,
                        focusWins = uiState.dailyAnalysis.focusWins,
                        advice = uiState.dailyAnalysis.advice
                    )
                }
                
                Spacer(modifier = Modifier.height(30.dp))
            }
        }
    }
}

// --- Components ---

@Composable
fun CozyCard(
    title: String,
    content: @Composable () -> Unit
) {
    // A container that looks like a framed picture or a neat notebook section
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 0.dp, 
                shape = RoundedCornerShape(16.dp)
            ) // No default shadow, we handle borders
            .background(Color.White, RoundedCornerShape(16.dp))
            .border(
                width = 2.dp, 
                color = Color(0xFF5D5D5D), // Thick dark border
                shape = RoundedCornerShape(16.dp)
            )
            .padding(24.dp)
    ) {
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = FontFamily.Cursive,
                    fontSize = 20.sp
                ),
                color = Color(0xFF8D6E63) // Warm brown title
            )
            Spacer(modifier = Modifier.height(20.dp))
            content()
        }
    }
}

@Composable
fun WeeklyBarChart(data: List<Pair<String, Int>>) {
    // Pastel Palette
    val barColor = Color(0xFFC5E1A5) // Soft Green
    val barWidth = 28.dp
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        data.forEachIndexed { index, (day, score) ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
                modifier = Modifier.fillMaxHeight()
            ) {
                // Score Bubble
                if (score > 0) {
                    Text(
                        text = "$score",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF5D5D5D),
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                
                // Bar (Rounded)
                val heightPercent = (score / 100f).coerceIn(0f, 1f)
                val barHeight = (150 * heightPercent).dp.coerceAtLeast(4.dp)
                
                Box(
                    modifier = Modifier
                        .width(barWidth)
                        .height(barHeight)
                        .border(1.5.dp, Color(0xFF5D5D5D), RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                        .background(barColor, RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Day Label
                Text(
                    text = day,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Cursive,
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color(0xFF5D5D5D)
                )
            }
        }
    }
}

@Composable
fun StickyNoteAnalysis(
    timeWasters: List<String>,
    focusWins: List<String>,
    advice: String
) {
    // Washi Tape + Sticky Note
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.TopCenter
    ) {
        // The Paper
        Box(
            modifier = Modifier
                .padding(top = 12.dp) // Space for tape
                .fillMaxWidth()
                .rotate(-1f) // Slight tilt for natural feel
                .shadow(2.dp, RoundedCornerShape(2.dp))
                .background(Color(0xFFFFF9C4)) // Yellow Sticky Note
                .border(1.dp, Color(0xFFE0E0E0)) // Subtle border
                .padding(24.dp)
        ) {
            Column {
                Text(
                    text = "Dear Diary,",
                    style = MaterialTheme.typography.bodyLarge,
                    fontFamily = FontFamily.Cursive,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF5D4037)
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                if (timeWasters.isNotEmpty()) {
                    Text(
                        text = "今天有点分心在: ${timeWasters.joinToString(", ")}...",
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Cursive),
                        color = Color(0xFF5D4037)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                
                if (focusWins.isNotEmpty()) {
                    Text(
                        text = "但是! 在 ${focusWins.joinToString(", ")} 上很专注!",
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Cursive),
                        color = Color(0xFF5D4037)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
                
                // Divider line
                Box(modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color(0xFFD7CCC8)))
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Text(
                    text = advice,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontFamily = FontFamily.Cursive,
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color(0xFFE64A19) // Deep Orange advice
                )
            }
        }
        
        // The Tape (Top Center)
        Box(
            modifier = Modifier
                .width(100.dp)
                .height(24.dp)
                .rotate(-2f)
                .background(Color(0xFFFFCC80).copy(alpha = 0.6f)) // Semi-transparent Orange tape
        )
    }
}

@Composable
fun DetailGridBackground() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val gridSize = 30.dp.toPx()
        val gridColor = Color(0xFFE0E0E0).copy(alpha = 0.5f)
        
        // Vertical lines
        for (x in 0..size.width.toInt() step gridSize.toInt()) {
            drawLine(
                color = gridColor,
                start = Offset(x.toFloat(), 0f),
                end = Offset(x.toFloat(), size.height),
                strokeWidth = 1.dp.toPx()
            )
        }
        
        // Horizontal lines
        for (y in 0..size.height.toInt() step gridSize.toInt()) {
            drawLine(
                color = gridColor,
                start = Offset(0f, y.toFloat()),
                end = Offset(size.width, y.toFloat()),
                strokeWidth = 1.dp.toPx()
            )
        }
    }
}