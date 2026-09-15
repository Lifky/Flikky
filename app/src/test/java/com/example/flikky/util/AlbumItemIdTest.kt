package com.example.flikky.util

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlbumItemIdTest {
    @Test fun `image ids round trip`() { val id=AlbumItemId(AlbumMediaKind.IMAGE,1234); assertEquals("img:1234",id.format()); assertEquals(id,AlbumItemId.parse(id.format())) }
    @Test fun `video ids round trip`() { val id=AlbumItemId(AlbumMediaKind.VIDEO,5678); assertEquals("vid:5678",id.format()); assertEquals(id,AlbumItemId.parse(id.format())) }
    @Test fun `large media store ids survive`() { val id=AlbumItemId(AlbumMediaKind.IMAGE,9_007_199_254_740_991); assertEquals(id,AlbumItemId.parse(id.format())) }
    @Test fun `other content authorities are rejected`() { listOf("content://media/external/images/media/1","content://sms/1","content://com.android.contacts/data/1","file:///sdcard/a.jpg").forEach { assertNull(it,AlbumItemId.parse(it)) } }
    @Test fun `path traversal shapes are rejected`() { listOf("img:../2","img:1/../2","img:1/2","../img:1","img:%2e%2e").forEach { assertNull(it,AlbumItemId.parse(it)) } }
    @Test fun `unknown prefixes are rejected`() { listOf("doc:1","aud:1","any:1",":1","1","img1","imgvid:1").forEach { assertNull(it,AlbumItemId.parse(it)) } }
    @Test fun `non numeric ids are rejected`() { listOf("img:abc","img:1e3","img:0x1","img:1.0","img:1,2","img:","img:1:2","img:１２３").forEach { assertNull(it,AlbumItemId.parse(it)) } }
    @Test fun `signed and zero ids are rejected`() { listOf("img:-1","img:+1","img:0","vid:-0").forEach { assertNull(it,AlbumItemId.parse(it)) } }
    @Test fun `overflowing ids are rejected instead of throwing`() { assertNull(AlbumItemId.parse("img:99999999999999999999")) }
    @Test fun `case variants and whitespace are rejected`() { listOf("IMG:1","Img:1","VID:1"," img:1","img:1 ","img: 1","img :1","").forEach { assertNull(it,AlbumItemId.parse(it)) } }
    @Test fun `leading zeros are rejected`() { assertNull(AlbumItemId.parse("img:01")); assertNull(AlbumItemId.parse("img:0001")) }
    @Test fun `additional separators are rejected before numeric conversion`() {
        val file = File("src/main/java/com/example/flikky/util/AlbumItemId.kt").takeIf { it.isFile }
            ?: File("app/src/main/java/com/example/flikky/util/AlbumItemId.kt")
        assertTrue(file.readText().contains("raw.indexOf(':', cut + 1) >= 0"))
    }
}
