package com.example.mqttpanelcraft.ui.components

import com.example.mqttpanelcraft.ui.components.definitions.*

object ComponentDefinitionRegistry {

    private val definitions = mutableMapOf<String, IComponentDefinition>()

    init {
        // --- CONTROL 控制器分類 (嚴格按照用戶指定順序：數值步進在選擇器後、方向鍵在搖桿後) ---
        register(ButtonDefinition)
        register(SwitchDefinition)
        register(SliderDefinition)
        register(SelectorDefinition)
        register(StepperDefinition) // 數值步進：插入在選擇器後
        register(JoystickDefinition)
        register(DpadDefinition)    // 方向鍵獨立：插入在搖桿之後
        register(ColorPaletteDefinition)
        register(InputBoxDefinition)

        // --- SENSOR 感測器分類 ---
        register(LedDefinition)
        register(ScaleMeterDefinition)
        register(GaugeMeterDefinition)
        register(SignalIndicatorDefinition)
        register(TextDisplayDefinition)
        register(ImageSensorDefinition) // 新增影像(圖片與串流)
        register(AudioSensorDefinition) // 新增聲音

        // --- DISPLAY 多媒體分類 (6 個元件：圖表、圖形、圖片、文字、日曆時鐘、內嵌方塊網頁) ---
        register(LineChartDefinition)
        register(ShapeDefinition)
        register(ImageDisplayDefinition)
        register(TextDefinition)
        register(CalendarClockDefinition)
        register(WebBoxDefinition)
    }

    fun register(def: IComponentDefinition) {
        definitions[def.type] = def
    }

    fun get(type: String): IComponentDefinition? {
        return definitions[type]
    }

    fun getAllTypes(): Set<String> = definitions.keys
}
