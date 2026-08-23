import os
import re

# layout_prop_graphic.xml
f = 'app/src/main/res/layout/layout_prop_graphic.xml'
with open(f, 'r', encoding='utf-8') as file:
    content = file.read()

# Make sure tgImageCropMode exists
if 'tgImageCropMode' not in content:
    # Insert it before imageCropper
    to_insert = '''
        <com.google.android.material.button.MaterialButtonToggleGroup
            android:id="@+id/tgImageCropMode"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginBottom="12dp"
            app:singleSelection="true"
            app:selectionRequired="true">
            <com.google.android.material.button.MaterialButton
                android:id="@+id/btnCropFill"
                style="@style/Widget.MaterialComponents.Button.OutlinedButton"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                app:cornerRadius="8dp"
                android:text="填滿"/>
            <com.google.android.material.button.MaterialButton
                android:id="@+id/btnCropFit"
                style="@style/Widget.MaterialComponents.Button.OutlinedButton"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                app:cornerRadius="8dp"
                android:text="留白"/>
            <com.google.android.material.button.MaterialButton
                android:id="@+id/btnCropCustom"
                style="@style/Widget.MaterialComponents.Button.OutlinedButton"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                app:cornerRadius="8dp"
                android:text="自訂"/>
        </com.google.android.material.button.MaterialButtonToggleGroup>
'''
    content = content.replace('<com.example.mqttpanelcraft.ui.views.ImageCropperView', to_insert + '\n        <com.example.mqttpanelcraft.ui.views.ImageCropperView')

# Add cornerRadius="8dp" to all MaterialButtons in toggle groups
content = re.sub(r'(<com\.google\.android\.material\.button\.MaterialButton[^>]+android:layout_weight="1")([^>]*?)(\s*(?:app:cornerRadius="\d+dp")?\s*)(android:text="[^"]+"/>)', r'\1 \n            app:cornerRadius="8dp"\n            \4', content)

with open(f, 'w', encoding='utf-8') as file:
    file.write(content)

# GraphicDefinition.kt
f2 = 'app/src/main/java/com/example/mqttpanelcraft/ui/components/definitions/GraphicDefinition.kt'
with open(f2, 'r', encoding='utf-8') as file:
    kt = file.read()

kt = kt.replace('override val defaultSize: Pair<Int, Int> = Pair(200, 200)', 'override val defaultSize: Pair<Int, Int> = Pair(100, 100)')
kt = kt.replace('val line_thickness = props["line_thickness"]?.toFloatOrNull() ?: 5f', 'val line_thickness = props["line_thickness"]?.toFloatOrNull() ?: 3f')
kt = kt.replace('val radius = 20f * resources.displayMetrics.density', 'val radius = 10f * resources.displayMetrics.density')

if 'clipRect' not in kt:
    # Add clipRect for image drawing
    canvas_draw = '''                    if (enableCorner) {
                        val path = Path()
                        path.addRoundRect(RectF(0f, 0f, usableW, usableH), 10f * resources.displayMetrics.density, 10f * resources.displayMetrics.density, Path.Direction.CW)
                        canvas.clipPath(path)
                    } else {
                        canvas.clipRect(0f, 0f, usableW, usableH)
                    }'''
    kt = re.sub(r'(canvas\.save\(\)\n\s+canvas\.translate\(sw/2f,\s*sw/2f\))(.*?)(\s*if\s*\(bitmap\s*!=\s*null\))', r'\1\n' + canvas_draw + r'\3', kt, flags=re.DOTALL)

with open(f2, 'w', encoding='utf-8') as file:
    file.write(kt)

print("Done")
