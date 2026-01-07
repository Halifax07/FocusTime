package com.example.delayme.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import com.example.delayme.data.model.TimeCategory
import com.example.delayme.data.model.TimeSegment
import com.example.delayme.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

// --- Reusable "Hand-Drawn" Components ---

@Composable
fun HandDrawnCard(
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    content: @Composable ColumnScope.() -> Unit
) {
    // A card that looks like a piece of paper with a wobbly border
    Box(
        modifier = modifier
            .shadow(2.dp, RoundedCornerShape(20.dp))
            .background(backgroundColor, RoundedCornerShape(20.dp))
            .border(
                width = 1.5.dp,
                color = Color(0xFFE0E0E0), // Soft pencil grey
                shape = RoundedCornerShape(20.dp)
            )
            .padding(16.dp)
    ) {
        Column(content = content)
    }
}

@Composable
fun StickerCard(
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    content: @Composable RowScope.() -> Unit
) {
    // A floating "sticker" or "chip" style card
    Surface(
        onClick = onClick,
        modifier = modifier
            .shadow(4.dp, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF0F0F0))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            content = content
        )
    }
}

// --- Specific Dashboard Components ---

@Composable
fun TimeSummaryCard(
    necessary: Long,
    fragmented: Long,
    life: Long,
    rest: Long,
    focusScore: Int,
    scoreChange: Int
) {
    val total = necessary + fragmented + life + rest
    
    HandDrawnCard(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = Color.White
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "今日专注",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF8D8D8D)
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "$focusScore",
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Cursive
                        ),
                        color = if (focusScore >= 80) NecessaryColor else if (focusScore >= 60) LifeColor else FragmentedColor
                    )
                    Text(
                        text = "分",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(bottom = 8.dp, start = 4.dp),
                        color = Color(0xFF8D8D8D)
                    )
                }
                if (scoreChange != 0) {
                    Text(
                        text = "${if (scoreChange > 0) "" else ""} 比昨日 ${if (scoreChange > 0) "+" else ""}$scoreChange",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (scoreChange > 0) NecessaryColor else FragmentedColor
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            StatItem("专注", necessary, NecessaryColor)
            StatItem("生活", life, LifeColor)
            StatItem("干扰", fragmented, FragmentedColor)
            StatItem("休息", rest, RestColor)
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Crayon Stroke Bar Visualization
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(18.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(Color(0xFFF5F5F5))
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                if (total > 0) {
                    if (necessary > 0) Spacer(
                        modifier = Modifier
                            .weight(necessary.toFloat())
                            .fillMaxHeight()
                            .background(NecessaryColor)
                    )
                    if (life > 0) Spacer(
                        modifier = Modifier
                            .weight(life.toFloat())
                            .fillMaxHeight()
                            .background(LifeColor)
                    )
                    if (fragmented > 0) Spacer(
                        modifier = Modifier
                            .weight(fragmented.toFloat())
                            .fillMaxHeight()
                            .background(FragmentedColor)
                    )
                    if (rest > 0) Spacer(
                        modifier = Modifier
                            .weight(rest.toFloat())
                            .fillMaxHeight()
                            .background(RestColor)
                    )
                }
            }
            // Add a "shine" or texture overlay if desired
        }
    }
}

@Composable
fun StatItem(label: String, minutes: Long, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "${minutes / 60}h ${minutes % 60}m",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = color
        )
        Text(
            text = label, 
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF8D8D8D)
        )
    }
}

@Composable
fun RiverOfTime(
    segments: List<TimeSegment>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    Column(modifier = modifier) {
        Canvas(modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFF9F9F9)) // Very light grey paper
            .pointerInput(segments) {
                detectTapGestures { offset ->
                    val width = size.width
                    val msPerDay = 24 * 60 * 60 * 1000f
                    val clickTimeRatio = offset.x / width
                    val clickTimeMs = (clickTimeRatio * msPerDay).toLong()
                    
                    // Simple toast for now
                    val hour = clickTimeMs / (60 * 60 * 1000)
                    val minute = (clickTimeMs % (60 * 60 * 1000)) / (60 * 1000)
                    Toast.makeText(context, String.format("%02d:%02d", hour, minute), Toast.LENGTH_SHORT).show()
                }
            }
        ) {
            val width = size.width
            val height = size.height
            val msPerDay = 24 * 60 * 60 * 1000f

            segments.forEach { segment ->
                // Calculate position
                val calendar = Calendar.getInstance().apply { timeInMillis = segment.startTime }
                val startOfDay = Calendar.getInstance().apply {
                    timeInMillis = segment.startTime
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis

                val startOffset = segment.startTime - startOfDay
                val endOffset = segment.endTime - startOfDay
                
                val xStart = (startOffset / msPerDay) * width
                val xEnd = (endOffset / msPerDay) * width
                val barWidth = (xEnd - xStart).toFloat().coerceAtLeast(2f) // Min width for visibility

                val color = when (segment.category) {
                    TimeCategory.NECESSARY -> NecessaryColor
                    TimeCategory.FRAGMENTED -> FragmentedColor
                    TimeCategory.LIFE -> LifeColor
                    TimeCategory.REST -> RestColor
                    else -> Color.Gray
                }

                // Draw "Watercolor" stroke
                // We draw a rect but with slightly rounded corners and lower alpha to look like marker
                drawRoundRect(
                    color = color.copy(alpha = 0.5f), // Softer alpha
                    topLeft = Offset(xStart.toFloat(), 5f), // Slight padding
                    size = Size(barWidth, height - 10f), // Slightly smaller than full height
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f) // Softer corners
                )
            }
            
            // Draw time markers (00:00, 06:00, etc.)
            val textPaint = android.graphics.Paint().apply {
                setColor(android.graphics.Color.LTGRAY)
                textSize = 24f
                textAlign = android.graphics.Paint.Align.CENTER
            }
            
            // We can't easily draw text in Canvas without nativeCanvas, skipping for simplicity or using basic lines
            // Draw grid lines
            for (i in 0..24 step 6) {
                val x = (i / 24f) * width
                drawLine(
                    color = Color.LightGray.copy(alpha = 0.5f),
                    start = Offset(x, 0f),
                    end = Offset(x, height),
                    strokeWidth = 1f,
                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                )
            }
        }
        
        // Time Labels below
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("00:00", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            Text("06:00", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            Text("12:00", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            Text("18:00", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            Text("24:00", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        }
    }
}

@Composable
fun WashiTapeButton(
    text: String,
    onClick: () -> Unit,
    color: Color
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .rotate(-1f) // Slight rotation for hand-drawn feel
            .shadow(2.dp, WashiTapeShape())
            .clip(WashiTapeShape())
            .background(color)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        // Texture overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White.copy(alpha = 0.15f))
        )
        
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            ),
            color = Color.White
        )
    }
}

class WashiTapeShape : androidx.compose.ui.graphics.Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
        density: androidx.compose.ui.unit.Density
    ): androidx.compose.ui.graphics.Outline {
        val path = Path().apply {
            val zigzagWidth = 15f
            val zigzagHeight = 10f
            
            // Top Left
            moveTo(0f, 0f)
            // Top edge
            lineTo(size.width, 0f)
            
            // Right edge (Zigzag)
            var currentY = 0f
            while (currentY < size.height) {
                lineTo(size.width - zigzagWidth, currentY + zigzagHeight / 2)
                lineTo(size.width, currentY + zigzagHeight)
                currentY += zigzagHeight
            }
            
            // Bottom edge
            lineTo(0f, size.height)
            
            // Left edge (Zigzag)
            currentY = size.height
            while (currentY > 0) {
                lineTo(zigzagWidth, currentY - zigzagHeight / 2)
                lineTo(0f, currentY - zigzagHeight)
                currentY -= zigzagHeight
            }
            
            close()
        }
        return androidx.compose.ui.graphics.Outline.Generic(path)
    }
}
