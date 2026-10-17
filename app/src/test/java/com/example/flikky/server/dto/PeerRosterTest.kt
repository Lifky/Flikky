package com.example.flikky.server.dto

import com.example.flikky.session.PeerDevice
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 「本场名册」：推给每台浏览器，让它认得会话里的其他设备（装机反馈：多台浏览器互相分不清）。
 * 只含名字、头像、在线与否 —— 凭据、UA 一律不出现。
 */
class PeerRosterTest {
    private fun dev(id: String, at: Long, connections: Int = 1, name: String = "", avatar: String = "icon:desktop_windows") =
        PeerDevice(deviceId = id, name = name, defaultLabel = "Chrome · Windows", avatarKey = avatar,
            connections = connections, connectedAt = at, visible = true)

    private fun entries(json: String) =
        Json.parseToJsonElement(json).jsonObject["devices"]!!.jsonArray.map { it.jsonObject }

    @Test fun `current devices win over what the session remembered`() {
        val known = listOf(dev("a", at = 1, name = "旧名"))
        val current = listOf(dev("a", at = 1, name = "新名", avatar = "emoji:🐱"))
        val a = entries(peerRosterJson(known, current)).single()
        assertEquals("新名", a["name"]!!.jsonPrimitive.content)
        assertEquals("emoji:🐱", a["avatarKey"]!!.jsonPrimitive.content)
        assertEquals(true, a["present"]!!.jsonPrimitive.boolean)
    }

    @Test fun `a disconnected device stays listed so its old messages keep their sender`() {
        val known = listOf(dev("a", at = 1), dev("b", at = 2))
        val current = listOf(dev("a", at = 1))
        val b = entries(peerRosterJson(known, current)).single { it["deviceId"]!!.jsonPrimitive.content == "b" }
        assertEquals(false, b["present"]!!.jsonPrimitive.boolean)
        assertEquals(false, b["online"]!!.jsonPrimitive.boolean)
    }

    @Test fun `entries are in join order and carry online state`() {
        val list = entries(peerRosterJson(emptyList(), listOf(dev("b", at = 5, connections = 0), dev("a", at = 1))))
        assertEquals(listOf("a", "b"), list.map { it["deviceId"]!!.jsonPrimitive.content })
        assertEquals(listOf(true, false), list.map { it["online"]!!.jsonPrimitive.boolean })
    }

    @Test fun `nothing secret or identifying leaks into the roster`() {
        val keys = entries(peerRosterJson(emptyList(), listOf(dev("a", at = 1)))).single().keys
        assertEquals(setOf("deviceId", "name", "defaultLabel", "defaultOrdinal", "avatarKey", "online", "present"), keys)
    }
}
