package com.example.delayme.utils

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ShareUtils {

    fun shareFocusCard(context: Context, focusScore: Int, scoreChange: Int) {
        val bitmap = createFocusCardBitmap(context, focusScore, scoreChange)
        val uri = saveBitmapToCache(context, bitmap)
        
        if (uri != null) {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "分享今日专注"))
        }
    }

    private fun createFocusCardBitmap(context: Context, focusScore: Int, scoreChange: Int): Bitmap {
        val width = 1080
        val height = 1080 // Square image for sharing
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Colors
        val creamColor = 0xFFFDFBF7.toInt()
        val charcoalGrey = 0xFF333333.toInt()
        val matchaGreen = 0xFFA8D8B9.toInt()
        val babyBlue = 0xFFAECBEB.toInt()
        val mutedPink = 0xFFE8A0BF.toInt()

        // 1. Background
        canvas.drawColor(creamColor)

        // 2. Grid Pattern (Subtle)
        val gridPaint = Paint().apply {
            color = 0xFFE0E0E0.toInt()
            strokeWidth = 2f
            alpha = 100
        }
        val step = 60f
        for (i in 0..width step step.toInt()) {
            canvas.drawLine(i.toFloat(), 0f, i.toFloat(), height.toFloat(), gridPaint)
        }
        for (i in 0..height step step.toInt()) {
            canvas.drawLine(0f, i.toFloat(), width.toFloat(), i.toFloat(), gridPaint)
        }

        // 3. Main Card (Rounded Rectangle with Border)
        val cardPaint = Paint().apply {
            color = 0xFFFFFFFF.toInt()
            style = Paint.Style.FILL
            setShadowLayer(10f, 0f, 5f, 0xFFCCCCCC.toInt())
        }
        val borderPaint = Paint().apply {
            color = charcoalGrey
            style = Paint.Style.STROKE
            strokeWidth = 6f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        
        val cardRect = RectF(100f, 200f, 980f, 880f)
        canvas.drawRoundRect(cardRect, 60f, 60f, cardPaint)
        canvas.drawRoundRect(cardRect, 60f, 60f, borderPaint)

        // 4. Text: "今日专注"
        val textPaint = Paint().apply {
            color = charcoalGrey
            textSize = 80f
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("今日专注", width / 2f, 350f, textPaint)

        // 5. Score Circle
        val circlePaint = Paint().apply {
            color = matchaGreen
            style = Paint.Style.STROKE
            strokeWidth = 10f
        }
        canvas.drawCircle(width / 2f, 550f, 150f, circlePaint)
        
        // Score Text
        val scorePaint = Paint().apply {
            color = matchaGreen
            textSize = 180f
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
        }
        // Adjust Y to center vertically
        val fontMetrics = scorePaint.fontMetrics
        val yOffset = (fontMetrics.descent - fontMetrics.ascent) / 2 - fontMetrics.descent
        canvas.drawText("$focusScore", width / 2f, 550f + yOffset, scorePaint)

        // 6. Comparison Text
        val subTextPaint = Paint().apply {
            color = 0xFF888888.toInt()
            textSize = 50f
            textAlign = Paint.Align.CENTER
        }
        val changeText = if (scoreChange >= 0) "较昨日 +$scoreChange" else "较昨日 $scoreChange"
        canvas.drawText(changeText, width / 2f, 780f, subTextPaint)

        // 7. Mascot (Simple Cat Head) at bottom right of card
        // Draw relative to cardRect.right, cardRect.bottom
        val mascotX = 850f
        val mascotY = 800f
        val mascotRadius = 60f
        
        val mascotFill = Paint().apply { color = 0xFFFFF0E0.toInt() }
        val mascotStroke = Paint().apply { 
            color = charcoalGrey
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }
        
        canvas.drawCircle(mascotX, mascotY, mascotRadius, mascotFill)
        canvas.drawCircle(mascotX, mascotY, mascotRadius, mascotStroke)
        
        // Ears
        val path = android.graphics.Path()
        path.moveTo(mascotX - 30f, mascotY - 40f)
        path.lineTo(mascotX - 50f, mascotY - 80f)
        path.lineTo(mascotX - 10f, mascotY - 50f)
        path.close()
        
        path.moveTo(mascotX + 30f, mascotY - 40f)
        path.lineTo(mascotX + 50f, mascotY - 80f)
        path.lineTo(mascotX + 10f, mascotY - 50f)
        path.close()
        
        canvas.drawPath(path, mascotFill)
        canvas.drawPath(path, mascotStroke)
        
        // Face
        val eyePaint = Paint().apply { color = charcoalGrey; style = Paint.Style.FILL }
        canvas.drawCircle(mascotX - 20f, mascotY, 5f, eyePaint)
        canvas.drawCircle(mascotX + 20f, mascotY, 5f, eyePaint)
        
        // Date at bottom
        val datePaint = Paint().apply {
            color = charcoalGrey
            textSize = 40f
            textAlign = Paint.Align.CENTER
            alpha = 150
        }
        val dateStr = SimpleDateFormat("yyyy.MM.dd", Locale.getDefault()).format(Date())
        canvas.drawText(dateStr, width / 2f, 950f, datePaint)

        return bitmap
    }

    private fun saveBitmapToCache(context: Context, bitmap: Bitmap): Uri? {
        return try {
            val cachePath = File(context.cacheDir, "images")
            cachePath.mkdirs()
            val stream = FileOutputStream("$cachePath/focus_share.png")
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            stream.close()
            
            FileProvider.getUriForFile(context, "${context.packageName}.provider", File("$cachePath/focus_share.png"))
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
