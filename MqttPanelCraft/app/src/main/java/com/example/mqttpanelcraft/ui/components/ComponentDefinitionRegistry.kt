package com.example.mqttpanelcraft.ui.components

import com.example.mqttpanelcraft.ui.components.definitions.*

object ComponentDefinitionRegistry {

    private val definitions = mutableMapOf<String, IComponentDefinition>()

    init {
        register(ButtonDefinition)
        register(SwitchDefinition)
        register(LedDefinition)
        register(TextDefinition)
        register(SliderDefinition)
        register(SelectorDefinition)
        register(ImageDefinition)
        register(ThermometerDefinition)
        register(LevelIndicatorDefinition)
        register(LineChartDefinition)
        register(JoystickDefinition)
        register(ColorPaletteDefinition)
    }

    fun register(def: IComponentDefinition) {
        definitions[def.type] = def
    }

    fun get(type: String): IComponentDefinition? {
        return definitions[type]
    }

    fun getAllTypes(): Set<String> = definitions.keys
}
