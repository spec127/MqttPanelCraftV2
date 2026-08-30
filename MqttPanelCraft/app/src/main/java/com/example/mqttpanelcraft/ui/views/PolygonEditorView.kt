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
        color = Color.parseColor("#807B1FA2")
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#7B1FA2")
        strokeWidth = 4f
    }
    private val nodePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }
    private val nodeStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#7B1FA2")
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
        this.edges = edgeCount
        this.points.clear()
        
        if (!existingPointsStr.isNullOrEmpty()) {
            val parsed = parsePoints(existingPointsStr)
            if (parsed.size == edgeCount) {
                this.points.addAll(parsed)
            } else {
                generateRegularPolygon(edgeCount)
            }
        } else {
            generateRegularPolygon(edgeCount)
        }
        
        notifyPointsChanged()
        invalidate()
    }

    private fun generateRegularPolygon(edges: Int) {
        if (edges == 0) return // Circle, no points needed here for editing
        
        val centerX = 0.5f
        val centerY = 0.5f
        val radius = 0.48f // keep some margin for nodes
        
        // Start from top (-PI/2)
        var startAngle = -Math.PI / 2.0
        if (edges % 2 == 0) startAngle += Math.PI / edges
        val angleStep = 2.0 * Math.PI / edges

        var minY = Float.MAX_VALUE
        var maxY = Float.MIN_VALUE

        for (i in 0 until edges) {
            val angle = startAngle + i * angleStep
            val px = centerX + (radius * Math.cos(angle)).toFloat()
            val py = centerY + (radius * Math.sin(angle)).toFloat()
            points.add(PointF(px, py))
            if (py < minY) minY = py
            if (py > maxY) maxY = py
        }
        
        val boxCenterY = (minY + maxY) / 2f
        val offsetY = centerY - boxCenterY
        if (offsetY != 0f) {
            for (p in points) {
                p.y += offsetY
            }
        }
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
                var nearestIdx = -1
                var minDist = Float.MAX_VALUE
                val touchRadius = 40f * resources.displayMetrics.density

                points.forEachIndexed { index, p ->
                    val px = p.x * w
                    val py = p.y * h
                    val dist = Math.hypot((x - px).toDouble(), (y - py).toDouble()).toFloat()
                    if (dist < touchRadius && dist < minDist) {
                        minDist = dist
                        nearestIdx = index
                    }
                }

                if (nearestIdx != -1) {
                    draggingIndex = nearestIdx
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (draggingIndex != -1) {
                    var nx = x / w
                    var ny = y / h
                    nx = Math.max(0f, Math.min(1f, nx))
                    ny = Math.max(0f, Math.min(1f, ny))
                    points[draggingIndex].set(nx, ny)
                    invalidate()
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (draggingIndex != -1) {
                    draggingIndex = -1
                    notifyPointsChanged()
                    invalidate()
                    parent?.requestDisallowInterceptTouchEvent(false)
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }
}
