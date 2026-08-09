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
        register(ImageSensorDefinition) // 影像(圖片與串流)

        // --- DISPLAY 多媒體分類 ---
        register(LineChartDefinition)
        register(BroadcastDefinition) // 廣播(TTS文字轉語音)
        register(TextDefinition)
        register(CalendarClockDefinition)
        register(WebBoxDefinition)
        
        // 媒體組件
        register(GraphicDefinition) // 圖式元件
    }

    fun register(def: IComponentDefinition) {
        definitions[def.type] = def
    }

    fun get(type: String): IComponentDefinition? {
        return definitions[type]
    }

    fun getAllTypes(): Set<String> = definitions.keys
}
