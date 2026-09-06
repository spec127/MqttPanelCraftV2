package com.example.mqttpanelcraft.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ComponentRegistryTest {
    @Test
    fun registryContainsExactlyTheSupportedComponents() {
        val definitions =
                ComponentDefinitionRegistry.getAllTypes().mapNotNull {
                    ComponentDefinitionRegistry.get(it)
                }

        assertEquals(22, definitions.size)
        assertEquals(22, definitions.map { it.type }.toSet().size)
        definitions.forEach { definition ->
            assertTrue(definition.displayNameResId != 0)
            assertTrue(definition.iconResId != 0)
            assertTrue(definition.propertiesLayoutId != 0)
            assertTrue(definition.getDefaultProps().keys.none(String::isBlank))
        }
    }
}
