package com.example.mqttpanelcraft.ui.components.definitions

import android.content.Context
import android.graphics.*
import android.util.Base64
import android.util.Size
import android.view.View
import android.widget.*
import com.example.mqttpanelcraft.R
import com.example.mqttpanelcraft.model.ComponentData
import com.example.mqttpanelcraft.ui.components.ComponentContainer
import com.example.mqttpanelcraft.ui.components.IComponentDefinition
import com.example.mqttpanelcraft.ui.components.prop.CommonPropBinder
import com.google.android.material.textfield.TextInputEditText

/**
 * 圖式元件 (Graphic / Shape Component)
 *
 * Design Intent:
 * 用於儀表板美化、排版、視覺裝飾。無 MQTT 邏輯。
 * 支援：矩形(圓角)、圓形/橢圓、線條(實線/虛線/方向)、圖片(URL/Base64)。
 */
object GraphicDefinition : IComponentDefinition {

    override val type: String = "GRAPHIC"
    override val defaultSize: Size = Size(200, 100)
    override val labelPrefix: String = "graphic"
    override val iconResId: Int = android.R.drawable.ic_menu_gallery
    override val group: String = "DISPLAY"

    override val propertiesLayoutId: Int = R.layout.layout_prop_graphic

    override fun getDefaultProps(): Map<String, String> = mapOf(
        "graphic_type" to "RECT", // RECT, CIRCLE, LINE, IMAGE
        
        "fill_color" to "#E0E0E0",
        "stroke_color" to "#9E9E9E",
        "stroke_width" to "0",
        "corner_radius" to "0",
        
        "line_color" to "#000000",
        "line_direction" to "HORIZONTAL", // HORIZONTAL, VERTICAL
        "line_style" to "SOLID", // SOLID, DASHED, DOTTED
        "line_thickness" to "2",
        
        "image_src" to "",
        "scale_type" to "CENTER_CROP", // CENTER_CROP, FIT_CENTER
        "opacity" to "100"
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
        // No MQTT behavior for Graphic component
    }

    override fun onMqttMessage(
        view: View,
        data: ComponentData,
        payload: String,
        onUpdateProp: (key: String, value: String) -> Unit
    ) {
        // No MQTT behavior for Graphic component
    }

    override fun onUpdateView(view: View, data: ComponentData) {
        val compositeView = (if (view is GraphicCompositeView) view else view.findViewWithTag<GraphicCompositeView>("target_graphic")) ?: return
        
        compositeView.graphicType = data.props["graphic_type"] ?: "RECT"
        
        // Rect / Circle Props
        compositeView.fillColor = try { Color.parseColor(data.props["fill_color"] ?: "#E0E0E0") } catch (_: Exception) { Color.GRAY }
        compositeView.strokeColor = try { Color.parseColor(data.props["stroke_color"] ?: "#9E9E9E") } catch (_: Exception) { Color.DKGRAY }
        compositeView.strokeWidth = (data.props["stroke_width"] ?: "0").toFloatOrNull()?.coerceAtLeast(0f) ?: 0f
        compositeView.cornerRadius = (data.props["corner_radius"] ?: "0").toFloatOrNull()?.coerceAtLeast(0f) ?: 0f
        
        // Line Props
        compositeView.lineColor = try { Color.parseColor(data.props["line_color"] ?: "#000000") } catch (_: Exception) { Color.BLACK }
        compositeView.lineDirection = data.props["line_direction"] ?: "HORIZONTAL"
        compositeView.lineStyle = data.props["line_style"] ?: "SOLID"
        compositeView.lineThickness = (data.props["line_thickness"] ?: "2").toFloatOrNull()?.coerceAtLeast(1f) ?: 2f
        
        // Image Props
        compositeView.imageSrc = data.props["image_src"] ?: ""
        compositeView.scaleTypeStr = data.props["scale_type"] ?: "CENTER_CROP"
        compositeView.opacity = (data.props["opacity"] ?: "100").toIntOrNull()?.coerceIn(0, 100) ?: 100
        
        compositeView.applyUpdates()
    }

    override fun bindPropertiesPanel(
        panelView: View,
        data: ComponentData,
        onUpdate: (String, String) -> Unit
    ) {
        val context = panelView.context

        // UI References
        val sectionShape = panelView.findViewById<View>(R.id.sectionShape)
        val sectionLine = panelView.findViewById<View>(R.id.sectionLine)
        val sectionImage = panelView.findViewById<View>(R.id.sectionImage)
        val tilCornerRadius = panelView.findViewById<View>(R.id.tilCornerRadius)

        // Dropdown: Graphic Type
        val typeLabels = listOf(
            context.getString(R.string.val_graphic_type_rect),
            context.getString(R.string.val_graphic_type_circle),
            context.getString(R.string.val_graphic_type_line),
            context.getString(R.string.val_graphic_type_image)
        )
        val typeMap = mapOf(
            context.getString(R.string.val_graphic_type_rect) to "RECT",
            context.getString(R.string.val_graphic_type_circle) to "CIRCLE",
            context.getString(R.string.val_graphic_type_line) to "LINE",
            context.getString(R.string.val_graphic_type_image) to "IMAGE"
        )

        fun updateSections(type: String) {
            sectionShape?.visibility = if (type == "RECT" || type == "CIRCLE") View.VISIBLE else View.GONE
            sectionLine?.visibility = if (type == "LINE") View.VISIBLE else View.GONE
            sectionImage?.visibility = if (type == "IMAGE") View.VISIBLE else View.GONE
            tilCornerRadius?.visibility = if (type == "RECT") View.VISIBLE else View.GONE
        }
        val initialType = data.props["graphic_type"] ?: "RECT"
        updateSections(initialType)

        CommonPropBinder.bindDropdown(panelView, R.id.spGraphicType, "graphic_type", data, { k, v ->
            onUpdate(k, v)
            updateSections(v)
        }, typeLabels, typeMap)

        // Section: Shape
        CommonPropBinder.bindColorPalette(panelView, R.id.containerFillColor, "fill_color", data, onUpdate, context.getString(R.string.prop_graphic_fill_color), "#E0E0E0")
        CommonPropBinder.bindColorPalette(panelView, R.id.containerStrokeColor, "stroke_color", data, onUpdate, context.getString(R.string.prop_graphic_stroke_color), "#9E9E9E")
        CommonPropBinder.bindEditText(panelView, R.id.etStrokeWidth, "stroke_width", data, onUpdate, "0")
        CommonPropBinder.bindEditText(panelView, R.id.etCornerRadius, "corner_radius", data, onUpdate, "0")

        // Section: Line
        CommonPropBinder.bindColorPalette(panelView, R.id.containerLineColor, "line_color", data, onUpdate, context.getString(R.string.prop_graphic_fill_color), "#000000")
        
        val dirLabels = listOf(context.getString(R.string.val_line_horizontal), context.getString(R.string.val_line_vertical))
        val dirMap = mapOf(
            context.getString(R.string.val_line_horizontal) to "HORIZONTAL",
            context.getString(R.string.val_line_vertical) to "VERTICAL"
        )
        CommonPropBinder.bindDropdown(panelView, R.id.spLineDirection, "line_direction", data, onUpdate, dirLabels, dirMap)
        
        val styleLabels = listOf(context.getString(R.string.val_line_solid), context.getString(R.string.val_line_dashed), context.getString(R.string.val_line_dotted))
        val styleMap = mapOf(
            context.getString(R.string.val_line_solid) to "SOLID",
            context.getString(R.string.val_line_dashed) to "DASHED",
            context.getString(R.string.val_line_dotted) to "DOTTED"
        )
        CommonPropBinder.bindDropdown(panelView, R.id.spLineStyle, "line_style", data, onUpdate, styleLabels, styleMap)
        CommonPropBinder.bindEditText(panelView, R.id.etLineThickness, "line_thickness", data, onUpdate, "2")

        // Section: Image
        CommonPropBinder.bindEditText(panelView, R.id.etImageSource, "image_src", data, onUpdate, "")
        val btnUpload = panelView.findViewById<Button>(R.id.btnUploadImage)
        btnUpload?.setOnClickListener {
            Toast.makeText(context, "本地圖片上傳功能即將推出", Toast.LENGTH_SHORT).show()
        }
        
        val scaleLabels = listOf(context.getString(R.string.val_scale_center_crop), context.getString(R.string.val_scale_fit_center))
        val scaleMap = mapOf(
            context.getString(R.string.val_scale_center_crop) to "CENTER_CROP",
            context.getString(R.string.val_scale_fit_center) to "FIT_CENTER"
        )
        CommonPropBinder.bindDropdown(panelView, R.id.spScaleType, "scale_type", data, onUpdate, scaleLabels, scaleMap)
        CommonPropBinder.bindEditText(panelView, R.id.etOpacity, "opacity", data, onUpdate, "100")
    }

    private class GraphicCompositeView(context: Context) : FrameLayout(context) {
        // Render Properties
        var graphicType: String = "RECT"
        
        var fillColor: Int = Color.LTGRAY
        var strokeColor: Int = Color.DKGRAY
        var strokeWidth: Float = 0f
        var cornerRadius: Float = 0f
        
        var lineColor: Int = Color.BLACK
        var lineDirection: String = "HORIZONTAL"
        var lineStyle: String = "SOLID"
        var lineThickness: Float = 2f
        
        var imageSrc: String = ""
        var scaleTypeStr: String = "CENTER_CROP"
        var opacity: Int = 100

        // Tools
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        private val imageView = ImageView(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            visibility = View.GONE
        }
        
        private var loadedImageSrc = ""

        init {
            setWillNotDraw(false)
            addView(imageView)
        }

        fun applyUpdates() {
            if (graphicType == "IMAGE") {
                imageView.visibility = View.VISIBLE
                imageView.alpha = opacity / 100f
                imageView.scaleType = if (scaleTypeStr == "CENTER_CROP") ImageView.ScaleType.CENTER_CROP else ImageView.ScaleType.FIT_CENTER
                
                if (imageSrc != loadedImageSrc) {
                    loadedImageSrc = imageSrc
                    if (imageSrc.startsWith("http")) {
                        // Normally use Glide/Picasso here. Using simple placeholder or async task.
                        // We'll leave it empty or attempt naive load if required.
                        // Without Glide, we just set a broken image icon.
                        imageView.setImageResource(android.R.drawable.ic_menu_gallery)
                    } else if (imageSrc.startsWith("data:image") || imageSrc.length > 200) {
                        try {
                            val base64 = if (imageSrc.contains(",")) imageSrc.split(",")[1] else imageSrc
                            val decodedString = Base64.decode(base64, Base64.DEFAULT)
                            val bitmap = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.size)
                            imageView.setImageBitmap(bitmap)
                        } catch (e: Exception) {
                            imageView.setImageResource(android.R.drawable.ic_menu_report_image)
                        }
                    } else {
                        imageView.setImageDrawable(null)
                    }
                }
            } else {
                imageView.visibility = View.GONE
            }
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val density = resources.displayMetrics.density
            
            when (graphicType) {
                "RECT" -> {
                    val r = cornerRadius * density
                    paint.color = fillColor
                    paint.style = Paint.Style.FILL
                    canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), r, r, paint)
                    
                    if (strokeWidth > 0) {
                        strokePaint.color = strokeColor
                        strokePaint.strokeWidth = strokeWidth * density
                        // inset rect by half stroke width so it doesn't clip
                        val halfStroke = strokePaint.strokeWidth / 2f
                        canvas.drawRoundRect(halfStroke, halfStroke, width.toFloat() - halfStroke, height.toFloat() - halfStroke, r, r, strokePaint)
                    }
                }
                "CIRCLE" -> {
                    paint.color = fillColor
                    paint.style = Paint.Style.FILL
                    val oval = RectF(0f, 0f, width.toFloat(), height.toFloat())
                    canvas.drawOval(oval, paint)
                    
                    if (strokeWidth > 0) {
                        strokePaint.color = strokeColor
                        strokePaint.strokeWidth = strokeWidth * density
                        val halfStroke = strokePaint.strokeWidth / 2f
                        val strokeOval = RectF(halfStroke, halfStroke, width.toFloat() - halfStroke, height.toFloat() - halfStroke)
                        canvas.drawOval(strokeOval, strokePaint)
                    }
                }
                "LINE" -> {
                    strokePaint.color = lineColor
                    strokePaint.strokeWidth = lineThickness * density
                    
                    if (lineStyle == "DASHED") {
                        strokePaint.pathEffect = DashPathEffect(floatArrayOf(10f * density, 10f * density), 0f)
                    } else if (lineStyle == "DOTTED") {
                        strokePaint.strokeCap = Paint.Cap.ROUND
                        strokePaint.pathEffect = DashPathEffect(floatArrayOf(1f, lineThickness * density * 2), 0f)
                    } else {
                        strokePaint.pathEffect = null
                        strokePaint.strokeCap = Paint.Cap.BUTT
                    }
                    
                    if (lineDirection == "VERTICAL") {
                        val cx = width / 2f
                        canvas.drawLine(cx, 0f, cx, height.toFloat(), strokePaint)
                    } else {
                        val cy = height / 2f
                        canvas.drawLine(0f, cy, width.toFloat(), cy, strokePaint)
                    }
                }
            }
        }
    }
}
