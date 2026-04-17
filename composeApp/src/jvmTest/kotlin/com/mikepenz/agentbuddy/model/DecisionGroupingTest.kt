package com.mikepenz.agentbuddy.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DecisionGroupingTest {

    @Test
    fun manualApproveBucket() {
        assertEquals(DecisionGroup.MANUAL_APPROVE, Decision.APPROVED.group())
        assertEquals(DecisionGroup.MANUAL_APPROVE, Decision.ALWAYS_ALLOWED.group())
        assertEquals(DecisionGroup.MANUAL_APPROVE, Decision.PROTECTION_OVERRIDDEN.group())
    }

    @Test
    fun riskAutoBuckets() {
        assertEquals(DecisionGroup.RISK_APPROVE, Decision.AUTO_APPROVED.group())
        assertEquals(DecisionGroup.RISK_DENY, Decision.AUTO_DENIED.group())
    }

    @Test
    fun protectionBuckets() {
        assertEquals(DecisionGroup.PROTECTION_BLOCK, Decision.PROTECTION_BLOCKED.group())
        assertEquals(DecisionGroup.PROTECTION_LOG, Decision.PROTECTION_LOGGED.group())
    }

    @Test
    fun timeoutBucket() {
        assertEquals(DecisionGroup.TIMEOUT, Decision.TIMEOUT.group())
    }

    @Test
    fun externalBucket() {
        assertEquals(DecisionGroup.EXTERNAL, Decision.RESOLVED_EXTERNALLY.group())
    }

    @Test
    fun otherBucket() {
        assertEquals(DecisionGroup.OTHER, Decision.CANCELLED_BY_CLIENT.group())
    }

    @Test
    fun everyDecisionMaps() {
        // Guards against future Decision values that forget to extend the grouping helper.
        Decision.entries.forEach { decision ->
            // Just calling .group() is enough — exhaustive `when` would throw on a missing branch.
            decision.group()
        }
    }

    @Test
    fun meaningfulLatencyOnlyForDeliberatedGroups() {
        assertTrue(DecisionGroup.MANUAL_APPROVE.hasMeaningfulLatency)
        assertTrue(DecisionGroup.RISK_APPROVE.hasMeaningfulLatency)
        assertTrue(DecisionGroup.MANUAL_DENY.hasMeaningfulLatency)
        assertTrue(DecisionGroup.RISK_DENY.hasMeaningfulLatency)
        assertFalse(DecisionGroup.PROTECTION_BLOCK.hasMeaningfulLatency)
        assertFalse(DecisionGroup.TIMEOUT.hasMeaningfulLatency)
    }
}
