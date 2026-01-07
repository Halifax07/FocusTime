package com.example.delayme.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.delayme.data.model.AppType
import com.example.delayme.ui.theme.*
import com.example.delayme.ui.viewmodel.MainViewModel
import kotlin.random.Random

@Composable
fun SettingsScreen(viewModel: MainViewModel) {
    val configs by viewModel.appConfigs.collectAsState()
    val installedApps by viewModel.filteredApps.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val context = LocalContext.current

    // Helper State for Tab Filtering
    var selectedFilter by remember { mutableStateOf<AppType?>(null) }

    // Apply Filter locally (in addition to search)
    val displayedApps = remember(installedApps, configs, selectedFilter) {
        if (selectedFilter == null) installedApps
        else installedApps.filter { (pkg, _) ->
            val config = configs.find { it.packageName == pkg }
            val type = config?.type ?: AppType.UNLISTED
            type == selectedFilter
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CreamBackground)
    ) {
        // 1. Grid Paper Background
        GridBackground()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            // Header
            Text(
                text = "应用管理",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = CharcoalGrey,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                text = "管理你的应用权限",
                style = MaterialTheme.typography.bodyMedium,
                color = WarmBrown
            )

            // Move Search Bar Up
            Spacer(modifier = Modifier.height(16.dp))

            // 3. Hand-drawn Search Bar
            HandDrawnSearchBar(
                value = searchQuery,
                onValueChange = { viewModel.onSearchQueryChanged(it) }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // New: Filter Options (Hand-drawn Tabs)
            FilterOptionRow(
                selectedFilter = selectedFilter,
                onFilterSelected = { selectedFilter = it }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 4. App List (Sticky Notes)
            LazyColumn(
                contentPadding = PaddingValues(bottom = 80.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(displayedApps) { (pkg, name) ->
                    val config = configs.find { it.packageName == pkg }
                    val type = config?.type ?: AppType.UNLISTED
                    
                    AppStickerRow(
                        packageName = pkg,
                        name = name,
                        type = type,
                        onTypeChanged = { newType ->
                            viewModel.updateConfig(pkg, name, newType)
                        }
                    )
                }
            }
        }
    }
}

// --- Components ---

@Composable
fun GridBackground() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val gridSize = 30.dp.toPx()
        val gridColor = Color(0xFFE0E0E0).copy(alpha = 0.4f)
        
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



@Composable
fun HandDrawnSearchBar(
    value: String,
    onValueChange: (String) -> Unit
) {
    val handDrawnShape = RoundedCornerShape(
        topStart = 15.dp,
        topEnd = 12.dp,
        bottomEnd = 18.dp,
        bottomStart = 10.dp
    )

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = TextStyle(
            fontSize = 16.sp,
            color = CharcoalGrey,
            fontFamily = FontFamily.Cursive
        ),
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .shadow(2.dp, handDrawnShape)
                    .background(Color.White, handDrawnShape)
                    .border(
                        width = 1.5.dp,
                        color = CharcoalGrey,
                        shape = handDrawnShape
                    )
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = "Search",
                    tint = CharcoalGrey,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Box(modifier = Modifier.weight(1f)) {
                    if (value.isEmpty()) {
                        Text(
                            "搜索应用...",
                            color = Color.LightGray,
                            fontSize = 16.sp,
                            fontFamily = FontFamily.Cursive
                        )
                    }
                    innerTextField()
                }
            }
        }
    )
}

@Composable
fun AppStickerRow(
    packageName: String,
    name: String,
    type: AppType,
    onTypeChanged: (AppType) -> Unit
) {
    val rowShape = RoundedCornerShape(16.dp)
    val context = LocalContext.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(1.dp, rowShape)
            .background(Color.White, rowShape)
            .padding(vertical = 12.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: Icon and Name
        androidx.compose.ui.viewinterop.AndroidView(
            factory = { ctx ->
                android.widget.ImageView(ctx).apply {
                    scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                }
            },
            update = { imageView ->
                try {
                    val icon = context.packageManager.getApplicationIcon(packageName)
                    imageView.setImageDrawable(icon)
                } catch (e: Exception) {
                    imageView.setImageResource(android.R.drawable.sym_def_app_icon)
                }
            },
            modifier = Modifier.size(40.dp) // Adjusted to 40dp to match HomeScreen matches
        )
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Text(
            text = name,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold
            ),
            color = CharcoalGrey,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        Spacer(modifier = Modifier.width(8.dp))

        // Right: Compact Segmented Tags
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Distraction Tag
            TagButton(
                text = "干扰",
                isSelected = type == AppType.BLACK_LIST,
                color = MutedPink,
                onClick = { onTypeChanged(AppType.BLACK_LIST) }
            )
            
            // Normal Tag
            TagButton(
                text = "生活",
                isSelected = type == AppType.UNLISTED,
                color = Color.LightGray,
                onClick = { onTypeChanged(AppType.UNLISTED) }
            )
            
            // Focus Tag
            TagButton(
                text = "专注",
                isSelected = type == AppType.WHITE_LIST,
                color = MatchaGreen,
                onClick = { onTypeChanged(AppType.WHITE_LIST) }
            )
        }
    }
}

@Composable
fun TagButton(
    text: String,
    isSelected: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val backgroundColor = if (isSelected) color else Color.Transparent
    val contentColor = if (isSelected) Color.White else Color.Gray
    val borderColor = if (isSelected) color else Color.LightGray.copy(alpha = 0.5f)

    Box(
        modifier = modifier
            .height(30.dp) // Reduced height for compact row
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp), // Internal padding for width
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall.copy( // Smaller text
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            ),
            color = contentColor
        )
    }
}

@Composable
fun FilterOptionRow(
    selectedFilter: AppType?,
    onFilterSelected: (AppType?) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        FilterChip(
            text = "干扰",
            type = AppType.BLACK_LIST,
            isSelected = selectedFilter == AppType.BLACK_LIST,
            color = MutedPink,
            onSelect = onFilterSelected,
            modifier = Modifier.weight(1f)
        )
        FilterChip(
            text = "生活",
            type = AppType.UNLISTED,
            isSelected = selectedFilter == AppType.UNLISTED,
            color = Color.LightGray,
            onSelect = onFilterSelected,
            modifier = Modifier.weight(1f)
        )
        FilterChip(
            text = "专注",
            type = AppType.WHITE_LIST,
            isSelected = selectedFilter == AppType.WHITE_LIST,
            color = MatchaGreen,
            onSelect = onFilterSelected,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun FilterChip(
    text: String,
    type: AppType,
    isSelected: Boolean,
    color: Color,
    onSelect: (AppType?) -> Unit,
    modifier: Modifier = Modifier
) {
    // Hand-drawn shape simulation
    val shape = RoundedCornerShape(
        topStart = 12.dp,
        topEnd = 8.dp,
        bottomEnd = 12.dp,
        bottomStart = 8.dp
    )
    
    val backgroundColor = if (isSelected) color else Color.Transparent
    val borderColor = if (isSelected) color else Color.Gray.copy(alpha = 0.5f)
    val textColor = if (isSelected) Color.White else Color.Gray
    
    Box(
        modifier = modifier
            .height(40.dp)
            .border(1.5.dp, borderColor, shape)
            .background(backgroundColor, shape)
            .clip(shape)
            .clickable { 
                if (isSelected) onSelect(null) else onSelect(type)
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Cursive
            ),
            color = textColor
        )
    }
}
