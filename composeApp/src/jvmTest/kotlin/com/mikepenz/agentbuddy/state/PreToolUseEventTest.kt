package com.mikepenz.agentbuddy.state

import com.mikepenz.agentbuddy.model.ProtectionHit
import com.mikepenz.agentbuddy.model.ProtectionMode
import kotlin.test.Test
import kotlin.test.assertEquals

class PreToolUseEventTest {

    private fun hit(mode: ProtectionMode) = ProtectionHit(
        moduleId = "test_module",
        ruleId = "test_rule",
        message = "test",
        mode = mode,
    )

    @Test
    fun emptyHitsReturnPass() {
        assertEquals(ProtectionLogConclusion.PASS, conclusionFromHits(emptyList()))
    }

    @Test
    fun autoBlockReturnsAutoBlocked() {
        assertEquals(
            ProtectionLogConclusion.AUTO_BLOCKED,
            conclusionFromHits(listOf(hit(ProtectionMode.AUTO_BLOCK))),
        )
    }

    @Test
    fun askAutoBlockReturnsAsk() {
        assertEquals(
            ProtectionLogConclusion.ASK,
            conclusionFromHits(listOf(hit(ProtectionMode.ASK_AUTO_BLOCK))),
        )
    }

    @Test
    fun askReturnsAsk() {
        assertEquals(
            ProtectionLogConclusion.ASK,
            conclusionFromHits(listOf(hit(ProtectionMode.ASK))),
        )
    }

    @Test
    fun logOnlyReturnsLogged() {
        assertEquals(
            ProtectionLogConclusion.LOGGED,
            conclusionFromHits(listOf(hit(ProtectionMode.LOG_ONLY))),
        )
    }

    @Test
    fun disabledReturnsPass() {
        assertEquals(
            ProtectionLogConclusion.PASS,
            conclusionFromHits(listOf(hit(ProtectionMode.DISABLED))),
        )
    }

    @Test
    fun highestSeverityWins() {
        // AUTO_BLOCK has lowest ordinal (highest severity)
        val hits = listOf(
            hit(ProtectionMode.LOG_ONLY),
            hit(ProtectionMode.AUTO_BLOCK),
            hit(ProtectionMode.ASK),
        )
        assertEquals(ProtectionLogConclusion.AUTO_BLOCKED, conclusionFromHits(hits))
    }

    @Test
    fun askAutoBlockBeatsAsk() {
        val hits = listOf(
            hit(ProtectionMode.ASK),
            hit(ProtectionMode.ASK_AUTO_BLOCK),
        )
        assertEquals(ProtectionLogConclusion.ASK, conclusionFromHits(hits))
    }

    @Test
    fun askBeatsLogOnly() {
        val hits = listOf(
            hit(ProtectionMode.LOG_ONLY),
            hit(ProtectionMode.ASK),
        )
        assertEquals(ProtectionLogConclusion.ASK, conclusionFromHits(hits))
    }
}
