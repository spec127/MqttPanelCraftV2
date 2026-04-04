package com.example.mqttpanelcraft.ui.components

import com.example.mqttpanelcraft.ui.components.definitions.*

object ComponentDefinitionRegistry {

    private val definitions = mutableMapOf<String, IComponentDefinition>()

    init {
        register(ButtonDefinition)
        register(SwitchDefinition)
        register(TextDefinition)
        register(SliderDefinition)
        register(SelectorDefinition)
        register(ImageDefinition)
        register(LedDefinition)
        register(ScaleMeterDefinition)
        register(GaugeDefinition)
        register(ValueDisplayDefinition)
        register(TextDisplayDefinition)
        register(LineChartDefinition)
        register(JoystickDefinition)
        register(ColorPaletteDefinition)
        register(InputBoxDefinition)
    }

    fun register(def: IComponentDefinition) {
        definitions[def.type] = def
    }

    fun get(type: String): IComponentDefinition? {
        return definitions[type]
    }

    fun getAllTypes(): Set<String> = definitions.keys
}
