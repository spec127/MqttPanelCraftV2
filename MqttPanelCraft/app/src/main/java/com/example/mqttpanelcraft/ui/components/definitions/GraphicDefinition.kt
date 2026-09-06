package com.example.mqttpanelcraft.ui.components.definitions

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.*
import android.util.Base64
import android.util.Size
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import com.example.mqttpanelcraft.ProjectViewActivity
import com.example.mqttpanelcraft.R
import com.example.mqttpanelcraft.model.ComponentData
import com.example.mqttpanelcraft.ui.components.ComponentContainer
import com.example.mqttpanelcraft.ui.components.ComponentGroup
import com.example.mqttpanelcraft.ui.components.IComponentDefinition
import com.example.mqttpanelcraft.ui.components.prop.CommonPropBinder
import com.example.mqttpanelcraft.ui.views.ImageCropperView
import com.example.mqttpanelcraft.ui.views.PolygonEditorView
import com.google.android.material.button.MaterialButtonToggleGroup
import kotlin.math.cos
import kotlin.math.sin

internal fun createRegularPolygonPoints(edgeCount: Int): String {
    if (edgeCount < 3) return ""
    val radius = 0.48f
    var startAngle = -Math.PI / 2.0
    if (edgeCount % 2 == 0) startAngle += Math.PI / edgeCount
    return (0 until edgeCount).joinToString(";") { index ->
        val angle = startAngle + index * (2.0 * Math.PI / edgeCount)
        val x = 0.5f + radius * cos(angle).toFloat()
        val y = 0.5f + radius * sin(angle).toFloat()
        "$x,$y"
    }
}

internal fun swapGraphicDimensions(width: Int, height: Int): Pair<Int, Int> = height to width

/**
 * 圖形元件 (Graphic Component)
 */
object GraphicDefinition : IComponentDefinition {

    // Keep Graphic defaults aligned with TextDefinition's default text color.
    private const val DEFAULT_GRAPHIC_COLOR = "#7B1FA2"

    private fun getActivity(context: Context): Activity? {
        var current = context
        while (current is ContextWrapper) {
            if (current is Activity) return current
            current = current.baseContext
        }
        return null
    }

    override val type: String = "GRAPHIC"
    override val defaultSize: Size = Size(200, 100)
    override val labelPrefix: String = "graphic"
    override val displayNameResId: Int = R.string.component_label_graphic
    override val iconResId: Int = R.drawable.ic_palette
    override val group = ComponentGroup.DISPLAY

    override val propertiesLayoutId: Int = R.layout.layout_prop_graphic

    override fun getDefaultProps(): Map<String, String> = mapOf(
        "graphic_type" to "SHAPE", // SHAPE, LINE, IMAGE
        
        "polygon_edges" to "4", // 3~8 or 0 for Circle
        "polygon_points" to createRegularPolygonPoints(4),
        
        "line_style" to "SOLID", // SOLID, DASHED, DOTTED
        "line_thickness" to "2",
        
        "image_src" to "",
        "image_matrix" to "FIT;1.0,0.0,0.0", // mode;scale,transX_pct,transY_pct
        
        "direction" to "HORIZONTAL", // HORIZONTAL, VERTICAL
        "opacity" to "100",
        "stroke_width" to "0",
        "fill_color" to DEFAULT_GRAPHIC_COLOR,
        "stroke_color" to DEFAULT_GRAPHIC_COLOR,
        "enable_corner" to "false"
    )

    override fun createView(context: Context, isEditMode: Boolean): View {
        val container = ComponentContainer.createEndpoint(context, type, isEditMode, group)
        val view = GraphicCompositeView(context).apply {
            tag = "target_graphic"
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        container.addView(view, 0)
        return container
    }

    override fun attachBehavior(
        view: View,
        data: ComponentData,
        sendMqtt: (topic: String, payload: String) -> Unit,
        onUpdateProp: (key: String, value: String) -> Unit
    ) {
    }

    override fun onMqttMessage(
        view: View,
        data: ComponentData,
        payload: String,
        onUpdateProp: (key: String, value: String) -> Unit
    ) {
    }

    override fun onUpdateView(view: View, data: ComponentData) {
        val compositeView = (if (view is GraphicCompositeView) view else view.findViewWithTag<GraphicCompositeView>("target_graphic")) ?: return
        
        compositeView.graphicType = data.props["graphic_type"] ?: "SHAPE"
        compositeView.edges = (data.props["polygon_edges"] ?: "4").toIntOrNull() ?: 4
        compositeView.pointsStr =
            data.props["polygon_points"].takeUnless { it.isNullOrBlank() }
                ?: createRegularPolygonPoints(compositeView.edges)
        
        compositeView.lineStyle = data.props["line_style"] ?: "SOLID"
        compositeView.lineThickness = (data.props["line_thickness"] ?: "2").toFloatOrNull()?.coerceAtLeast(1f) ?: 2f
        
        compositeView.imageSrc = data.props["image_src"] ?: ""
        compositeView.imageMatrixStr = data.props["image_matrix"] ?: "FIT;1.0,0.0,0.0"
        
        compositeView.direction = data.props["direction"] ?: "HORIZONTAL"
        compositeView.opacity = (data.props["opacity"] ?: "100").toIntOrNull()?.coerceIn(0, 100) ?: 100
        compositeView.strokeWidth = (data.props["stroke_width"] ?: "0").toFloatOrNull()?.coerceAtLeast(0f) ?: 0f
        
        compositeView.fillColor = try { Color.parseColor(data.props["fill_color"] ?: DEFAULT_GRAPHIC_COLOR) } catch (_: Exception) { Color.parseColor(DEFAULT_GRAPHIC_COLOR) }
        compositeView.strokeColor = try { Color.parseColor(data.props["stroke_color"] ?: DEFAULT_GRAPHIC_COLOR) } catch (_: Exception) { Color.parseColor(DEFAULT_GRAPHIC_COLOR) }
        
        compositeView.enableCorner = (data.props["enable_corner"] == "true")
        
        compositeView.applyUpdates()
    }

    override fun bindPropertiesPanel(
        panelView: View,
        data: ComponentData,
        onUpdate: (String, String) -> Unit
    ) {
        val context = panelView.context

        // UI References
        val tgGraphicType = panelView.findViewById<MaterialButtonToggleGroup>(R.id.tgGraphicType)
        val sectionShape = panelView.findViewById<View>(R.id.sectionShape)
        val sectionLine = panelView.findViewById<View>(R.id.sectionLine)
        val sectionImage = panelView.findViewById<View>(R.id.sectionImage)
        
        val polygonEditor = panelView.findViewById<PolygonEditorView>(R.id.polygonEditor)
        val imageCropper = panelView.findViewById<ImageCropperView>(R.id.imageCropper)

        fun updatePreviewDirection(direction: String) {
            listOfNotNull<View>(polygonEditor, imageCropper).forEach { preview ->
                preview.post {
                    if (direction == "VERTICAL") {
                        val fitScale =
                            if (preview.width > 0 && preview.height > 0) {
                                minOf(
                                    preview.width.toFloat() / preview.height,
                                    preview.height.toFloat() / preview.width
                                )
                            } else 1f
                        preview.rotation = 90f
                        preview.scaleX = fitScale
                        preview.scaleY = fitScale
                    } else {
                        preview.rotation = 0f
                        preview.scaleX = 1f
                        preview.scaleY = 1f
                    }
                }
            }
        }

        // Type Setup
        fun updateSections(type: String) {
            sectionShape?.visibility = if (type == "SHAPE") View.VISIBLE else View.GONE
            sectionLine?.visibility = if (type == "LINE") View.VISIBLE else View.GONE
            sectionImage?.visibility = if (type == "IMAGE") View.VISIBLE else View.GONE
            
            val typeBtnId = when (type) {
                "LINE" -> R.id.btnTypeLine
                "IMAGE" -> R.id.btnTypeImage
                else -> R.id.btnTypeShape
            }
            if (tgGraphicType?.checkedButtonId != typeBtnId) {
                tgGraphicType?.check(typeBtnId)
            }
        }
        val initialType = data.props["graphic_type"] ?: "SHAPE"
        updateSections(initialType)

        tgGraphicType?.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val newType = when (checkedId) {
                    R.id.btnTypeLine -> "LINE"
                    R.id.btnTypeImage -> "IMAGE"
                    else -> "SHAPE"
                }
                onUpdate("graphic_type", newType)
                updateSections(newType)
            }
        }

        // Shape Section
        val edgeLabels = listOf("3", "4", "5", "6", "7", "8", "圓形")
        val edgeMap = mapOf(
            "3" to "3", "4" to "4", "5" to "5", "6" to "6", "7" to "7", "8" to "8", "圓形" to "0"
        )
        CommonPropBinder.bindDropdown(panelView, R.id.spPolygonEdges, "polygon_edges", data, { k, v ->
            onUpdate(k, v)
            polygonEditor?.setShape(v.toIntOrNull() ?: 4, null)
        }, edgeLabels, edgeMap)
        
        polygonEditor?.onPointsChanged = { pointsStr ->
            if (data.props["polygon_points"] != pointsStr) {
                onUpdate("polygon_points", pointsStr)
            }
        }
        polygonEditor?.setShape((data.props["polygon_edges"] ?: "4").toIntOrNull() ?: 4, data.props["polygon_points"])

        // Line Section
        val styleLabels = listOf(context.getString(R.string.val_line_solid), context.getString(R.string.val_line_dashed), context.getString(R.string.val_line_dotted))
        val styleMap = mapOf(
            context.getString(R.string.val_line_solid) to "SOLID",
            context.getString(R.string.val_line_dashed) to "DASHED",
            context.getString(R.string.val_line_dotted) to "DOTTED"
        )
        CommonPropBinder.bindDropdown(panelView, R.id.spLineStyle, "line_style", data, onUpdate, styleLabels, styleMap)
        CommonPropBinder.bindEditText(panelView, R.id.etLineThickness, "line_thickness", data, onUpdate, "2")

        // Image Section
        val btnUpload = panelView.findViewById<Button>(R.id.btnUploadImage)
        btnUpload?.setOnClickListener {
            val activity = getActivity(context) as? ProjectViewActivity
            if (activity == null) {
                Toast.makeText(context, "目前無法開啟圖片選擇器", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            activity.pickImage { imageData ->
                if (imageData != null) {
                    val resetMatrix = "FIT;1.0,0.0,0.0"
                    onUpdate("image_src", imageData)
                    onUpdate("image_matrix", resetMatrix)
                    imageCropper?.setImageSrc(imageData, resetMatrix)
                }
            }
        }
        imageCropper?.setImageSrc(data.props["image_src"], data.props["image_matrix"])
        imageCropper?.onMatrixChanged = { matStr ->
            onUpdate("image_matrix", matStr)
        }

        // Visual Appearance
        val tgDirection = panelView.findViewById<MaterialButtonToggleGroup>(R.id.tgDirection)
        val initialDir = data.props["direction"] ?: "HORIZONTAL"
        updatePreviewDirection(initialDir)
        val dirBtnId = if (initialDir == "VERTICAL") R.id.btnDirVertical else R.id.btnDirHorizontal
        tgDirection?.check(dirBtnId)
        tgDirection?.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val newDir = if (checkedId == R.id.btnDirVertical) "VERTICAL" else "HORIZONTAL"
                updatePreviewDirection(newDir)
                if (newDir != (data.props["direction"] ?: "HORIZONTAL")) {
                    val (newWidth, newHeight) = swapGraphicDimensions(data.width, data.height)
                    onUpdate("direction", newDir)
                    onUpdate("w", newWidth.toString())
                    onUpdate("h", newHeight.toString())
                }
            }
        }

        CommonPropBinder.bindEditText(panelView, R.id.etOpacity, "opacity", data, onUpdate, "100")
        CommonPropBinder.bindEditText(panelView, R.id.etStrokeWidth, "stroke_width", data, onUpdate, "0")
        CommonPropBinder.bindColorPalette(panelView, R.id.containerFillColor, "fill_color", data, onUpdate, context.getString(R.string.prop_graphic_fill_color), DEFAULT_GRAPHIC_COLOR)
        CommonPropBinder.bindColorPalette(panelView, R.id.containerStrokeColor, "stroke_color", data, onUpdate, context.getString(R.string.prop_graphic_stroke_color), DEFAULT_GRAPHIC_COLOR)

        val itemCornerRadius = panelView.findViewById<View>(R.id.itemCornerRadius)
        val cornerCheck = panelView.findViewById<ImageView>(R.id.vCornerRadiusEnabled)
        var cornerEnabled = data.props["enable_corner"] == "true"
        cornerCheck?.visibility = if (cornerEnabled) View.VISIBLE else View.INVISIBLE
        itemCornerRadius?.setOnClickListener {
            cornerEnabled = !cornerEnabled
            cornerCheck?.visibility = if (cornerEnabled) View.VISIBLE else View.INVISIBLE
            onUpdate("enable_corner", cornerEnabled.toString())
        }
    }

    private class GraphicCompositeView(context: Context) : FrameLayout(context) {
        var graphicType: String = "SHAPE"
        var edges: Int = 4
        var pointsStr: String = ""
        
        var lineStyle: String = "SOLID"
        var lineThickness: Float = 2f
        
        var imageSrc: String = ""
        var imageMatrixStr: String = "FIT;1.0,0.0,0.0"
        
        var direction: String = "HORIZONTAL"
        var opacity: Int = 100
        var strokeWidth: Float = 0f
        var fillColor: Int = Color.LTGRAY
        var strokeColor: Int = Color.DKGRAY
        var enableCorner: Boolean = false

        private val pathPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        private var bitmap: Bitmap? = null
        private val imageMatrix = Matrix()

        fun applyUpdates() {
            clipChildren = true
            clipToPadding = true
            alpha = opacity / 100f
            
            // Setup paints
            pathPaint.color = fillColor
            strokePaint.color = strokeColor
            strokePaint.strokeWidth = strokeWidth * resources.displayMetrics.density
            
            if (enableCorner) {
                val radius = 20f * resources.displayMetrics.density
                pathPaint.pathEffect = CornerPathEffect(radius)
                strokePaint.pathEffect = CornerPathEffect(radius)
            } else {
                pathPaint.pathEffect = null
                strokePaint.pathEffect = null
            }

            // Image decode
            if (graphicType == "IMAGE" && imageSrc.isNotEmpty()) {
                try {
                    val base64 = if (imageSrc.contains(",")) imageSrc.substringAfter(",") else imageSrc
                    val bytes = Base64.decode(base64, Base64.DEFAULT)
                    bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                } catch (e: Exception) {
                    bitmap = null
                }
            } else {
                bitmap = null
            }

            invalidate()
        }

        override fun dispatchDraw(canvas: Canvas) {
            super.dispatchDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            if (w == 0f || h == 0f) return

            canvas.save()
            canvas.clipRect(0f, 0f, w, h)
            var drawWidth = w
            var drawHeight = h
            if (direction == "VERTICAL") {
                canvas.translate(w, 0f)
                canvas.rotate(90f)
                drawWidth = h
                drawHeight = w
            }

            when (graphicType) {
                "SHAPE" -> {
                    if (edges == 0) {
                        // Circle
                        val r = Math.min(drawWidth, drawHeight) / 2f
                        canvas.drawCircle(drawWidth/2f, drawHeight/2f, r, pathPaint)
                        if (strokeWidth > 0) canvas.drawCircle(drawWidth/2f, drawHeight/2f, r - strokePaint.strokeWidth/2f, strokePaint)
                    } else {
                        // Custom Polygon
                        val pts = parsePoints(pointsStr)
                        if (pts.isNotEmpty()) {
                            val path = Path()
                            pts.forEachIndexed { index, p ->
                                val px = p.x * drawWidth
                                val py = p.y * drawHeight
                                if (index == 0) path.moveTo(px, py) else path.lineTo(px, py)
                            }
                            path.close()
                            canvas.drawPath(path, pathPaint)
                            if (strokeWidth > 0) canvas.drawPath(path, strokePaint)
                        }
                    }
                }
                "LINE" -> {
                    val density = resources.displayMetrics.density
                    strokePaint.strokeWidth = lineThickness * density
                    if (lineStyle == "DASHED") {
                        strokePaint.pathEffect = DashPathEffect(floatArrayOf(10f * density, 10f * density), 0f)
                    } else if (lineStyle == "DOTTED") {
                        strokePaint.strokeCap = Paint.Cap.ROUND
                        strokePaint.pathEffect = DashPathEffect(floatArrayOf(1f, lineThickness * density * 2), 0f)
                    } else {
                        strokePaint.strokeCap = Paint.Cap.BUTT
                        strokePaint.pathEffect = null
                    }
                    val cy = drawHeight / 2f
                    canvas.drawLine(0f, cy, drawWidth, cy, strokePaint)
                }
                "IMAGE" -> {
                    bitmap?.let { bmp ->
                        val hasMode = imageMatrixStr.contains(";")
                        val mode = if (hasMode) imageMatrixStr.substringBefore(";") else "CUSTOM"
                        val parts = imageMatrixStr.substringAfter(";", imageMatrixStr).split(",")
                        val scale = parts.getOrNull(0)?.toFloatOrNull() ?: 1f
                        val txPct = parts.getOrNull(1)?.toFloatOrNull() ?: 0f
                        val tyPct = parts.getOrNull(2)?.toFloatOrNull() ?: 0f
                        
                        imageMatrix.reset()
                        val bw = bmp.width.toFloat()
                        val bh = bmp.height.toFloat()
                        val initialScale = if (mode == "FILL") Math.max(drawWidth / bw, drawHeight / bh) else Math.min(drawWidth / bw, drawHeight / bh)
                        val dx = (drawWidth - bw * initialScale) / 2f
                        val dy = (drawHeight - bh * initialScale) / 2f
                        
                        imageMatrix.postScale(initialScale, initialScale)
                        imageMatrix.postTranslate(dx, dy)
                        if (mode == "CUSTOM") {
                            imageMatrix.postScale(scale, scale, drawWidth/2f, drawHeight/2f)
                            imageMatrix.postTranslate(txPct * drawWidth, tyPct * drawHeight)
                        }
                        
                        canvas.drawBitmap(bmp, imageMatrix, pathPaint) // pathPaint has alpha and cornereffect
                    }
                }
            }
            canvas.restore()
        }

        private fun parsePoints(str: String): List<PointF> {
            val res = mutableListOf<PointF>()
            if (str.isEmpty()) return res
            str.split(";").forEach { p ->
                if (p.isNotEmpty()) {
                    val coords = p.split(",")
                    if (coords.size == 2) {
                        res.add(PointF(coords[0].toFloat(), coords[1].toFloat()))
                    }
                }
            }
            return res
        }
    }
}
