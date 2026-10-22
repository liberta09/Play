package com.kaan.play.ui.web

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import com.kaan.play.data.web.WebCursorController

/**
 * TV / kumanda kipinde sanal fare imlecini çizen hafif View.
 * [WebCursorController] ile senkronize çalışır; konum ve soluklaşma değiştikçe
 * kendini yeniden çizer.
 */
class WebCursorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var controller: WebCursorController? = null

    private val density = resources.displayMetrics.density

    // Turkuaz dolgu (#00E5FF)
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E5FF")
        style = Paint.Style.FILL
    }

    // Koyu dış çizgi (açık ve koyu arka planlarda imlecin net görünmesi için)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#121212")
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
        strokeJoin = Paint.Join.ROUND
    }

    // Küçük, şık fare oku (ucu 0,0 koordinatında)
    private val cursorPath = Path().apply {
        moveTo(0f, 0f)
        lineTo(0f, 18f * density)
        lineTo(4.5f * density, 14f * density)
        lineTo(8.5f * density, 22f * density)
        lineTo(11.5f * density, 20.5f * density)
        lineTo(7.5f * density, 13f * density)
        lineTo(13.5f * density, 13f * density)
        close()
    }

    private val fadeRunnable = Runnable {
        val c = controller ?: return@Runnable
        if (c.updateInactivity()) {
            postInvalidateOnAnimation()
        }
    }

    init {
        // Dokunmaları engellememesi ve odak almaması için
        isFocusable = false
        isClickable = false
    }

    private fun scheduleFadeTimer() {
        removeCallbacks(fadeRunnable)
        val c = controller ?: return
        if (!c.isVisible || c.isFaded) return
        postDelayed(fadeRunnable, c.inactivityTimeoutMs)
    }

    fun bind(cursorController: WebCursorController) {
        controller?.onChange = null
        controller = cursorController
        cursorController.onChange = {
            scheduleFadeTimer()
            postInvalidateOnAnimation()
        }
        scheduleFadeTimer()
        postInvalidateOnAnimation()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        removeCallbacks(fadeRunnable)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            controller?.setViewport(w.toFloat(), h.toFloat())
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val c = controller ?: return
        if (!c.isVisible) return

        val alpha = if (c.isFaded) 0.6f else 1.0f
        fillPaint.alpha = (alpha * 255).toInt()
        strokePaint.alpha = (alpha * 255).toInt()

        canvas.save()
        canvas.translate(c.x, c.y)
        canvas.drawPath(cursorPath, fillPaint)
        canvas.drawPath(cursorPath, strokePaint)
        canvas.restore()
    }
}
