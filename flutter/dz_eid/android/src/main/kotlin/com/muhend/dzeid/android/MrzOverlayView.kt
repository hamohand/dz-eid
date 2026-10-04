package com.muhend.dzeid.android

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View

/**
 * Masque assombri avec une fenêtre au format carte bancaire (ID-1, 85,6 × 54 mm) :
 * guide l'utilisateur pour cadrer le dos de la carte. La zone MRZ (tiers inférieur) est repérée en pointillés.
 */
internal class MrzOverlayView(context: Context) : View(context) {

    private val density = resources.displayMetrics.density
    private val mask = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(160, 0, 0, 0) }
    private val frame = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3 * density
        color = Color.WHITE
    }
    private val mrzZone = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
        color = Color.argb(200, 255, 255, 255)
        pathEffect = DashPathEffect(floatArrayOf(8 * density, 6 * density), 0f)
    }
    private val rect = RectF()
    private val path = Path()

    /** Vert quand une MRZ valide vient d'être reconnue (en attente de confirmation). */
    var detected = false
        set(value) {
            if (field != value) {
                field = value
                frame.color = if (value) Color.rgb(46, 204, 113) else Color.WHITE
                invalidate()
            }
        }

    /** Fenêtre de cadrage, en coordonnées de la vue. */
    val frameRect: RectF get() = rect

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val width = w * 0.9f
        val height = width / CARD_RATIO
        val left = (w - width) / 2
        val top = (h - height) / 2
        rect.set(left, top, left + width, top + height)
        path.reset()
        path.fillType = Path.FillType.EVEN_ODD
        path.addRect(0f, 0f, w.toFloat(), h.toFloat(), Path.Direction.CW)
        path.addRoundRect(rect, RADIUS * density, RADIUS * density, Path.Direction.CW)
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawPath(path, mask)
        canvas.drawRoundRect(rect, RADIUS * density, RADIUS * density, frame)
        val zoneTop = rect.top + rect.height() * 0.62f
        val inset = 6 * density
        canvas.drawRect(rect.left + inset, zoneTop, rect.right - inset, rect.bottom - inset, mrzZone)
    }

    private companion object {
        const val CARD_RATIO = 85.6f / 54f
        const val RADIUS = 14f
    }
}
