package com.example.mqttpanelcraft.ui.views

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

class PolygonEditorView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Output string format: "x,y;x,y;x,y" where x,y are 0.0~1.0
    var onPointsChanged: ((String) -> Unit)? = null

    private var edges: Int = 4
    private val points = mutableListOf<PointF>()
    private var isCircle = false

    // Paints
    private val pathPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#802196F3") // Semi-transparent blue
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#2196F3")
        strokeWidth = 4f
    }
    private val nodePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }
    private val nodeStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.BLACK
        strokeWidth = 2f
    }

    private var draggingIndex: Int = -1
    private val nodeRadius = 20f
    private val touchSlop = 40f

    fun setShape(edgeCount: Int, existingPointsStr: String? = null) {
        if (edgeCount == 0) {
            isCircle = true
            points.clear()
            invalidate()
            return
        }
        isCircle = false
        edges = edgeCount
        
        // Try parsing existing points if they match the edge count
        if (!existingPointsStr.isNullOrEmpty()) {
            val parsed = parsePoints(existingPointsStr)
            if (parsed.size == edges) {
                points.clear()
                points.addAll(parsed)
                invalidate()
                return
            }
        }

        // Generate regular polygon points as default
        generateRegularPolygon()
    }

    private fun generateRegularPolygon() {
        points.clear()
        val centerX = 0.5f
        val centerY = 0.5f
        val radius = 0.4f // keep some margin for nodes
        
        // Start from top (-PI/2)
        val startAngle = -Math.PI / 2.0
        val angleStep = 2.0 * Math.PI / edges

        for (i in 0 until edges) {
            val angle = startAngle + i * angleStep
            val px = centerX + (radius * cos(angle)).toFloat()
            val py = centerY + (radius * sin(angle)).toFloat()
            points.add(PointF(px, py))
        }
        notifyPointsChanged()
        invalidate()
    }

    private fun notifyPointsChanged() {
        val str = points.joinToString(";") { "${it.x},${it.y}" }
        onPointsChanged?.invoke(str)
    }

    private fun parsePoints(str: String): List<PointF> {
        val res = mutableListOf<PointF>()
        try {
            val pairs = str.split(";")
            for (p in pairs) {
                if (p.isEmpty()) continue
                val coords = p.split(",")
                if (coords.size == 2) {
                    res.add(PointF(coords[0].toFloat(), coords[1].toFloat()))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return res
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()

        if (isCircle) {
            canvas.drawCircle(w / 2f, h / 2f, Math.min(w, h) * 0.4f, pathPaint)
            canvas.drawCircle(w / 2f, h / 2f, Math.min(w, h) * 0.4f, strokePaint)
            return
        }

        if (points.isEmpty()) return

        val path = Path()
        for (i in points.indices) {
            val px = points[i].x * w
            val py = points[i].y * h
            if (i == 0) {
                path.moveTo(px, py)
            } else {
                path.lineTo(px, py)
            }
        }
        path.close()
        canvas.drawPath(path, pathPaint)
        canvas.drawPath(path, strokePaint)

        // Draw nodes
        for (p in points) {
            val px = p.x * w
            val py = p.y * h
            canvas.drawCircle(px, py, nodeRadius, nodePaint)
            canvas.drawCircle(px, py, nodeRadius, nodeStrokePaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isCircle || points.isEmpty()) return false

        val x = event.x
        val y = event.y
        val w = width.toFloat()
        val h = height.toFloat()

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                // Find nearest node
                var nearestDist = Float.MAX_VALUE
                var nearestIdx = -1
                for (i in points.indices) {
                    val px = points[i].x * w
                    val py = points[i].y * h
                    val dx = x - px
                    val dy = y - py
                    val dist = Math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
                    if (dist < touchSlop && dist < nearestDist) {
                        nearestDist = dist
                        nearestIdx = i
                    }
                }
                if (nearestIdx != -1) {
                    draggingIndex = nearestIdx
                    return true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (draggingIndex != -1) {
                    val newX = (x / w).coerceIn(0f, 1f)
                    val newY = (y / h).coerceIn(0f, 1f)
                    points[draggingIndex] = PointF(newX, newY)
                    invalidate()
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (draggingIndex != -1) {
                    draggingIndex = -1
                    notifyPointsChanged()
                    invalidate()
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }
}
