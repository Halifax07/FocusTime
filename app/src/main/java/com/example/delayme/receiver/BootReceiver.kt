package com.example.delayme.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.delayme.service.DanmakuService
import com.example.delayme.service.DimmerService
import com.example.delayme.service.ZenModeService

/**
 * 开机自启动接收器
 * 在设备重启后自动恢复之前开启的服务
 */
class BootReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "BootReceiver"
        private const val PREFS_NAME = "labs_settings"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON" ||
            intent.action == "com.htc.intent.action.QUICKBOOT_POWERON") {
            
            Log.i(TAG, "Boot completed, checking services to restore...")
            
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            
            // 恢复弹幕攻击服务
            if (prefs.getBoolean("danmaku_enabled", false)) {
                Log.i(TAG, "Restoring DanmakuService...")
                startServiceSafely(context, DanmakuService::class.java)
            }
            
            // 恢复禅模式服务
            if (prefs.getBoolean("zen_mode_enabled", false)) {
                Log.i(TAG, "Restoring ZenModeService...")
                startServiceSafely(context, ZenModeService::class.java)
            }
            
            // 恢复助眠渐暗服务
            if (prefs.getBoolean("dimmer_enabled", false)) {
                Log.i(TAG, "Restoring DimmerService...")
                startServiceSafely(context, DimmerService::class.java)
            }
        }
    }
    
    private fun startServiceSafely(context: Context, serviceClass: Class<*>) {
        try {
            val intent = Intent(context, serviceClass)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start service: ${serviceClass.simpleName}", e)
        }
    }
}
