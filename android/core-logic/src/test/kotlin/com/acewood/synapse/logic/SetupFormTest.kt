package com.acewood.synapse.logic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SetupFormTest {
    private val token = "a".repeat(40)
    private fun input(url: String = "http://ha.local:8123/", tok: String = token, pin: String = "1234", confirm: String = "1234",
                      owner: String = "Mason", id: String = " Living Panel 01 ") =
        SetupInput(owner, id, "", url, tok, pin, confirm)

    @Test fun normalizesAndBuildsConfig() {
        val c = SetupForm.toConfig(input())
        assertEquals("living-panel-01", c.nodeId); assertEquals("livingroom", c.room)
        assertEquals("http://ha.local:8123", c.haUrl); assertEquals("1234", c.pin)
    }

    @Test fun reportsFirstProblem() {
        assertNull(SetupForm.problem(input()))
        assertEquals("Enter the name to show on the remote.", SetupForm.problem(input(owner = " ")))
        assertTrue(SetupForm.problem(input(url = "ha.local:8123"))!!.contains("http://"))
        assertTrue(SetupForm.problem(input(tok = "short"))!!.contains("token"))
        assertTrue(SetupForm.problem(input(pin = "12a4", confirm = "12a4"))!!.contains("4 digits"))
        assertEquals("The PIN entries don't match.", SetupForm.problem(input(confirm = "9999")))
    }

    @Test fun saveRequiresATestOfTheSameUrlAndToken() {
        val i = input()
        assertNotNull(SetupForm.saveBlocker(i, null))
        val fp = SetupForm.fingerprint(i.haUrl, i.haToken)
        assertNull(SetupForm.saveBlocker(i, fp))
        assertNull(SetupForm.saveBlocker(input(url = "http://ha.local:8123"), fp))   // trailing slash is the same URL
        assertTrue(SetupForm.saveBlocker(input(tok = "b".repeat(40)), fp)!!.contains("changed"))
    }

    @Test fun testMessages() {
        assertTrue(SetupForm.testMessage(200).first)
        assertTrue(SetupForm.testMessage(401).second.contains("token"))
        assertTrue(SetupForm.testMessage(null).second.contains("reach"))
        assertTrue(!SetupForm.testMessage(500).first)
    }
}
