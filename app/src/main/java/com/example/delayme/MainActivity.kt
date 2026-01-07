package com.example.delayme

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModelProvider
import androidx.room.Room
import com.example.delayme.data.local.AppDatabase
import com.example.delayme.data.repository.UsageRepository
import com.example.delayme.ui.screens.HomeScreen
import com.example.delayme.ui.screens.SettingsScreen
import com.example.delayme.ui.screens.LabsScreen
import com.example.delayme.ui.theme.DelayMeTheme
import com.example.delayme.ui.viewmodel.MainViewModel
import com.example.delayme.ui.viewmodel.MainViewModelFactory
import com.example.delayme.service.DimmerService
import com.example.delayme.service.DanmakuService
import com.example.delayme.service.ZenModeService
import com.example.delayme.ui.theme.MatchaGreen
import com.example.delayme.ui.theme.MutedPink
import com.example.delayme.ui.theme.BabyBlue
import com.example.delayme.ui.theme.CharcoalGrey
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.example.delayme.ui.screens.WeeklyFocusDetailScreen
import com.example.delayme.ui.theme.CreamBackground

class MainActivity : ComponentActivity() {
    
    private lateinit var viewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Manual Dependency Injection
        val db = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java, "delayme-db"
        ).build()
        
        val repository = UsageRepository(applicationContext, db.appDao())
        val viewModelFactory = MainViewModelFactory(repository)
        viewModel = ViewModelProvider(this, viewModelFactory)[MainViewModel::class.java]

        // Restore Services if enabled
        if (viewModel.isDimmerEnabled.value) {
            val intent = Intent(this, DimmerService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        }
        if (viewModel.isDanmakuEnabled.value) {
            val intent = Intent(this, DanmakuService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        }
        if (viewModel.zenModeEnabled.value) {
            val intent = Intent(this, ZenModeService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        }

        setContent {
            DelayMeTheme {
                var currentScreen by remember { mutableStateOf(0) }
                var showPermissionDialog by remember { mutableStateOf(false) }

                // Handle System Back Press for Navigation
                // If on Detail screen (3), go back to Home (0). Otherwise system handles it (exit).
                BackHandler(enabled = currentScreen == 3) {
                    currentScreen = 0
                }

                LaunchedEffect(Unit) {
                    if (!hasUsageStatsPermission()) {
                        showPermissionDialog = true
                    }
                }
                
                // Refresh data when screen changes or app resumes (handled in onResume)
                LaunchedEffect(currentScreen) {
                    // Only refresh for Home (0) or Detail (3) screens which show usage stats
                    if (currentScreen == 0 || currentScreen == 3) {
                        viewModel.refreshData()
                    }
                }

                if (showPermissionDialog) {
                    AlertDialog(
                        onDismissRequest = { /* Do nothing, force user */ },
                        title = { Text("需要权限") },
                        text = { Text("为了统计应用使用时长，请在设置中授予使用情况访问权限。") },
                        confirmButton = {
                            Button(onClick = {
                                showPermissionDialog = false
                                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                            }) {
                                Text("去设置")
                            }
                        }
                    )
                }

                Scaffold(
                    bottomBar = {
                        if (currentScreen != 3) { // Hide bottom bar on detail screen
                            NavigationBar(
                                containerColor = CreamBackground,
                                contentColor = CharcoalGrey
                            ) {
                                NavigationBarItem(
                                    selected = currentScreen == 0,
                                    onClick = { currentScreen = 0 },
                                    icon = { Icon(Icons.Rounded.Home, contentDescription = "主页") },
                                    label = { 
                                        Text(
                                            "当下",
                                            fontFamily = FontFamily.Cursive,
                                            fontWeight = FontWeight.Bold
                                        ) 
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MatchaGreen,
                                        selectedTextColor = MatchaGreen,
                                        indicatorColor = MatchaGreen.copy(alpha = 0.2f),
                                        unselectedIconColor = CharcoalGrey.copy(alpha = 0.6f),
                                        unselectedTextColor = CharcoalGrey.copy(alpha = 0.6f)
                                    )
                                )
                                NavigationBarItem(
                                    selected = currentScreen == 1,
                                    onClick = { currentScreen = 1 },
                                    icon = { Icon(Icons.Rounded.Edit, contentDescription = "习惯") },
                                    label = { 
                                        Text(
                                            "习惯",
                                            fontFamily = FontFamily.Cursive,
                                            fontWeight = FontWeight.Bold
                                        ) 
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MutedPink, // Use Pink for Habits/Stickers
                                        selectedTextColor = MutedPink,
                                        indicatorColor = MutedPink.copy(alpha = 0.2f),
                                        unselectedIconColor = CharcoalGrey.copy(alpha = 0.6f),
                                        unselectedTextColor = CharcoalGrey.copy(alpha = 0.6f)
                                    )
                                )
                                NavigationBarItem(
                                    selected = currentScreen == 2,
                                    onClick = { currentScreen = 2 },
                                    icon = { Icon(Icons.Rounded.Build, contentDescription = "实验室") },
                                    label = { 
                                        Text(
                                            "实验室",
                                            fontFamily = FontFamily.Cursive,
                                            fontWeight = FontWeight.Bold
                                        ) 
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = BabyBlue, // Use Blue for Labs
                                        selectedTextColor = BabyBlue,
                                        indicatorColor = BabyBlue.copy(alpha = 0.2f),
                                        unselectedIconColor = CharcoalGrey.copy(alpha = 0.6f),
                                        unselectedTextColor = CharcoalGrey.copy(alpha = 0.6f)
                                    )
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        when (currentScreen) {
                            0 -> HomeScreen(
                                viewModel = viewModel,
                                onNavigateToDetails = { currentScreen = 3 }
                            )
                            1 -> SettingsScreen(viewModel)
                            2 -> LabsScreen(viewModel)
                            3 -> WeeklyFocusDetailScreen(
                                viewModel = viewModel,
                                onBack = { currentScreen = 0 }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }
    
    override fun onResume() {
        super.onResume()
        if (::viewModel.isInitialized) {
            viewModel.refreshData()
        }
    }
}
