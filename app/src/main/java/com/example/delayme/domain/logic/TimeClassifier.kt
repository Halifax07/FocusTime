package com.example.delayme.domain.logic

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.example.delayme.data.model.AppConfig
import com.example.delayme.data.model.AppType
import com.example.delayme.data.model.TimeCategory

object TimeClassifier {

    private val UTILITY_PACKAGES = setOf(
        "com.android.settings",
        "com.android.dialer",
        "com.android.mms",
        "com.google.android.calendar",
        "com.android.deskclock",
        "com.android.calculator2",
        "com.eg.android.AlipayGphone", // Alipay
        "com.autonavi.minimap", // Amap
        "com.android.systemui",
        "android",
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller",
        "com.google.android.packageinstaller",
        "com.android.packageinstaller",
        "com.miui.home",
        "com.huawei.android.launcher",
        "com.oppo.launcher",
        "com.bbk.launcher2",
        "com.sec.android.app.launcher",
        "com.google.android.apps.nexuslauncher"
    )

    private val DISTRACTION_PACKAGES = setOf(
        // 短视频
        "com.ss.android.ugc.aweme", // 抖音
        "com.ss.android.ugc.aweme.lite", // 抖音极速版
        "com.zhiliaoapp.musically", // TikTok
        "com.smile.gifmaker", // 快手
        "com.kuaishou.nebula", // 快手极速版
        "tv.danmaku.bili", // B站
        "tv.danmaku.bilibilihd", // B站HD
        "com.bilibili.app.in", // B站国际版
        
        // 社交媒体
        "com.sina.weibo", // 微博
        "com.sina.weibog3", // 微博国际版
        "com.xingin.xhs", // 小红书
        "com.zhihu.android", // 知乎
        "com.douban.frodo", // 豆瓣
        "com.hupu.games", // 虎扑
        "com.baidu.tieba", // 贴吧
        "com.max.xiaoheihe", // 小黑盒
        
        // 国际社交
        "com.google.android.youtube", // YouTube
        "com.instagram.android", // Instagram
        "com.facebook.katana", // Facebook
        "com.facebook.lite", // Facebook Lite
        "com.twitter.android", // Twitter/X
        "com.reddit.frontpage", // Reddit
        "com.snapchat.android", // Snapchat
        "com.pinterest", // Pinterest
        "com.tumblr", // Tumblr
        "com.discord", // Discord
        
        // 视频/直播
        "com.youku.phone", // 优酷
        "com.qiyi.video", // 爱奇艺
        "com.tencent.qqlive", // 腾讯视频
        "com.hunantv.imgo.activity", // 芒果TV
        "com.letv.android.client", // 乐视视频
        "com.sohu.sohuvideo", // 搜狐视频
        "air.tv.douyu.android", // 斗鱼
        "com.duowan.kiwi", // 虎牙
        "cn.bilibili.live", // B站直播
        "com.huajiao", // 花椒直播
        "com.immomo.momo", // 陌陌
        "com.yy.hiyo", // YY
        "com.netflix.mediaclient", // Netflix
        "com.hulu.plus", // Hulu
        "tv.twitch.android.app", // Twitch
        
        // 游戏相关
        "com.tencent.tmgp.sgame", // 王者荣耀
        "com.tencent.ig", // 和平精英
        "com.miHoYo.Yuanshen", // 原神
        "com.miHoYo.hkrpg", // 崩坏：星穹铁道
        "com.netease.onmyoji", // 阴阳师
        "com.netease.dwrg", // 第五人格
        "com.taptap", // TapTap
        
        // 新闻资讯（容易沉迷刷新闻）
        "com.ss.android.article.news", // 今日头条
        "com.ss.android.article.lite", // 今日头条极速版
        "com.netease.newsreader.activity", // 网易新闻
        "com.tencent.news", // 腾讯新闻
        "com.sohu.newsclient", // 搜狐新闻
        "com.ifeng.news2", // 凤凰新闻
        
        // 小说/漫画
        "com.qidian.QDReader", // 起点读书
        "com.jingdong.app.reader", // 京东读书
        "com.ireader.ui.activitys", // 掌阅iReader
        "com.kuaikan.comic", // 快看漫画
        "com.dmzj.manhua", // 动漫之家
        
        // 购物（容易沉迷浏览）
        "com.taobao.taobao", // 淘宝
        "com.jingdong.app.mall", // 京东
        "com.xunmeng.pinduoduo", // 拼多多
        "com.xingin.xhs" // 小红书（也是购物）
    )

    fun classify(
        context: Context,
        packageName: String?,
        durationMillis: Long,
        config: AppConfig?
    ): TimeCategory {
        if (packageName == null) return TimeCategory.REST // Screen off

        // 用户配置优先级最高
        // 只有用户明确设置为 BLACK_LIST（干扰）的应用才会返回 FRAGMENTED
        // 这确保了"弹幕攻击"等功能只对用户主动标记的干扰应用生效
        if (config != null) {
            return when (config.type) {
                AppType.WHITE_LIST -> TimeCategory.NECESSARY
                AppType.BLACK_LIST -> TimeCategory.FRAGMENTED
                AppType.UNLISTED -> TimeCategory.LIFE
            }
        }

        // 未配置的应用默认返回 LIFE，不会触发弹幕攻击
        // 用户需要在应用中主动将某个应用标记为"干扰"才能触发相关功能
        return TimeCategory.LIFE
    }

    private fun classifySmart(context: Context, packageName: String, durationMillis: Long): TimeCategory {
        val minutes = durationMillis / 1000 / 60
        
        // Special Case: WeChat (Mixed Mode)
        if (packageName == "com.tencent.mm") {
             // < 10m -> Life (Communication/Payment), >= 10m -> Distraction (Moments/Articles)
             return if (minutes < 10) TimeCategory.LIFE else TimeCategory.FRAGMENTED
        }
        
        // Special Case: QQ (Similar to WeChat)
        if (packageName == "com.tencent.mobileqq" || packageName == "com.tencent.tim") {
            return if (minutes < 10) TimeCategory.LIFE else TimeCategory.FRAGMENTED
        }

        // A. Utility Check (Always Life/Necessary)
        if (isUtilityApp(context, packageName)) {
            return TimeCategory.LIFE
        }
        
        // B. Direct Distraction Package Check (Highest Priority for known distractions)
        if (DISTRACTION_PACKAGES.contains(packageName)) {
            return TimeCategory.FRAGMENTED // 已知的干扰应用，直接返回 FRAGMENTED
        }

        // C. Category Check
        val category = getAppCategory(context, packageName)
        val isEntertainment = category == ApplicationInfo.CATEGORY_GAME || 
                              category == ApplicationInfo.CATEGORY_VIDEO ||
                              category == ApplicationInfo.CATEGORY_SOCIAL ||
                              category == ApplicationInfo.CATEGORY_NEWS // 新闻类也容易沉迷
        
        // D. Package name heuristics for unknown apps
        val isLikelyDistraction = packageName.contains("video") ||
                                   packageName.contains("game") ||
                                   packageName.contains("live") ||
                                   packageName.contains("news") ||
                                   packageName.contains("comic") ||
                                   packageName.contains("novel") ||
                                   packageName.contains("music") ||
                                   packageName.contains("short") ||
                                   packageName.contains("social")

        // E. Duration Logic
        return if (isEntertainment || isLikelyDistraction) {
            // 娱乐类应用，无论时长都是干扰
            TimeCategory.FRAGMENTED
        } else if (minutes >= 10) {
            // 长时间使用非娱乐应用 -> 专注
            TimeCategory.NECESSARY
        } else {
            // 短时间使用非娱乐应用 -> 生活
            TimeCategory.LIFE
        }
    }

    private fun isUtilityApp(context: Context, packageName: String): Boolean {
        if (UTILITY_PACKAGES.contains(packageName) || packageName.contains("pay") || packageName.contains("bank")) {
            return true
        }
        return isLauncher(context, packageName)
    }

    private fun isLauncher(context: Context, packageName: String): Boolean {
        val intent = android.content.Intent(android.content.Intent.ACTION_MAIN)
        intent.addCategory(android.content.Intent.CATEGORY_HOME)
        val res = context.packageManager.queryIntentActivities(intent, 0)
        return res.any { it.activityInfo.packageName == packageName }
    }

    private fun getAppCategory(context: Context, packageName: String): Int {
        return try {
            val pm = context.packageManager
            val info = pm.getApplicationInfo(packageName, 0)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                info.category
            } else {
                -1
            }
        } catch (e: Exception) {
            -1
        }
    }
}
