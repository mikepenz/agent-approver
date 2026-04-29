package com.mikepenz.agentbelay.protection.modules

import com.mikepenz.agentbelay.model.ProtectionMode
import kotlin.test.Test
import kotlin.test.assertEquals

class UncommittedFilesModuleTest {

    private val module = UncommittedFilesModule

    @Test
    fun moduleMetadata() {
        assertEquals("uncommitted_files", module.id)
        assertEquals("Uncommitted Files", module.name)
        assertEquals(false, module.corrective)
        assertEquals(ProtectionMode.ASK, module.defaultMode)
        assertEquals(setOf("Bash"), module.applicableTools)
        assertEquals(1, module.rules.size)
    }

    @Test
    fun ruleId() {
        assertEquals("destructive_on_dirty", module.rules.first().id)
    }
}
