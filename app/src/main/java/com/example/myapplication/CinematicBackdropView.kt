package com.example.myapplication

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View

/** Lightweight, locally drawn cinematic poster wall used behind the entry screens. */
class CinematicBackdropView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bounds = RectF()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        paint.shader = LinearGradient(0f, 0f, w, h,
            Color.rgb(7, 8, 13), Color.rgb(17, 7, 13), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = RadialGradient(w * .5f, h * .53f, w * .52f,
            intArrayOf(Color.rgb(94, 10, 24), Color.rgb(43, 8, 17), Color.rgb(7, 8, 13)),
            floatArrayOf(0f, .47f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null

        val posters = listOf(
            Poster(.025f, .10f, .145f, .34f, 0), Poster(.17f, .32f, .16f, .34f, 1),
            Poster(.035f, .66f, .19f, .27f, 2), Poster(.25f, .055f, .13f, .21f, 3),
            Poster(.83f, .08f, .145f, .34f, 2), Poster(.72f, .37f, .16f, .34f, 3),
            Poster(.82f, .72f, .15f, .22f, 1), Poster(.56f, .77f, .14f, .17f, 0)
        )
        posters.forEach { drawPoster(canvas, w, h, it) }

        // A dark veil keeps the poster art atmospheric and leaves the central UI legible.
        paint.color = 0x9904070D.toInt()
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = RadialGradient(w * .5f, h * .52f, w * .48f,
            intArrayOf(0x08000000, 0x5504070D, 0xAA04070D.toInt()),
            floatArrayOf(0f, .62f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null
    }

    private fun drawPoster(canvas: Canvas, w: Float, h: Float, poster: Poster) {
        bounds.set(w * poster.x, h * poster.y, w * (poster.x + poster.width), h * (poster.y + poster.height))
        val radius = h * .018f
        val palettes = arrayOf(
            intArrayOf(0xFF1B3451.toInt(), 0xFF11131E.toInt()),
            intArrayOf(0xFF7C2731.toInt(), 0xFF22111B.toInt()),
            intArrayOf(0xFFB05C28.toInt(), 0xFF20202A.toInt()),
            intArrayOf(0xFF244860.toInt(), 0xFF171421.toInt())
        )
        val colors = palettes[poster.theme]
        paint.shader = LinearGradient(bounds.left, bounds.top, bounds.right, bounds.bottom,
            colors[0], colors[1], Shader.TileMode.CLAMP)
        canvas.drawRoundRect(bounds, radius, radius, paint)
        paint.shader = null

        val cx = bounds.centerX()
        val cy = bounds.top + bounds.height() * .39f
        paint.color = when (poster.theme) {
            0 -> 0xFF7AC6D8.toInt()
            1 -> 0xFFFF9C61.toInt()
            2 -> 0xFFFFD28A.toInt()
            else -> 0xFF90B8EE.toInt()
        }
        canvas.drawCircle(bounds.left + bounds.width() * .70f, bounds.top + bounds.height() * .24f,
            bounds.width() * .10f, paint)

        val mountain = Path().apply {
            moveTo(bounds.left, bounds.top + bounds.height() * .68f)
            lineTo(bounds.left + bounds.width() * .42f, bounds.top + bounds.height() * .38f)
            lineTo(bounds.left + bounds.width() * .72f, bounds.top + bounds.height() * .64f)
            lineTo(bounds.right, bounds.top + bounds.height() * .42f)
            lineTo(bounds.right, bounds.bottom)
            lineTo(bounds.left, bounds.bottom)
            close()
        }
        paint.color = when (poster.theme) {
            0 -> 0xFF111A2B.toInt()
            1 -> 0xFF2A111D.toInt()
            2 -> 0xFF29212A.toInt()
            else -> 0xFF101A2A.toInt()
        }
        canvas.drawPath(mountain, paint)

        // Small abstract silhouette suggests poster artwork without using third-party images.
        paint.color = 0xD9080A10.toInt()
        val figureX = bounds.left + bounds.width() * (.34f + (poster.theme % 2) * .24f)
        val figureY = bounds.top + bounds.height() * .57f
        canvas.drawCircle(figureX, figureY, bounds.width() * .07f, paint)
        val figure = Path().apply {
            moveTo(figureX - bounds.width() * .10f, bounds.bottom)
            lineTo(figureX - bounds.width() * .045f, figureY + bounds.width() * .04f)
            lineTo(figureX + bounds.width() * .045f, figureY + bounds.width() * .04f)
            lineTo(figureX + bounds.width() * .12f, bounds.bottom)
            close()
        }
        canvas.drawPath(figure, paint)

        paint.color = 0x88ED495B.toInt()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = (resources.displayMetrics.density * 1.2f)
        canvas.drawRoundRect(bounds, radius, radius, paint)
        paint.style = Paint.Style.FILL
    }

    private data class Poster(val x: Float, val y: Float, val width: Float, val height: Float, val theme: Int)
}
