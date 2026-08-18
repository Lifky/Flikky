package com.example.flikky.service

import com.example.flikky.data.db.entities.FavoriteEntity
import com.example.flikky.data.db.entities.FavoriteGroupEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FavoriteDtoMapperTest {

    private fun textFav(id: Long, text: String, group: Long? = null, at: Long = 1000L) =
        FavoriteEntity(
            id = id,
            sourceSessionId = 7L,
            sourceMessageId = 70L,
            kind = "TEXT",
            textContent = text,
            groupId = group,
            createdAt = at,
        )

    private fun fileFav(id: Long, name: String, at: Long = 2000L) =
        FavoriteEntity(
            id = id,
            sourceSessionId = 7L,
            sourceMessageId = 71L,
            kind = "FILE",
            fileId = "depot-abc",
            fileName = name,
            fileSize = 1234L,
            fileMime = "application/pdf",
            createdAt = at,
        )

    @Test
    fun `text favorite maps text and leaves file fields null`() {
        val dto = toFavoritesResponseDto(listOf(textFav(1, "hello")), emptyList())
        val item = dto.items.single()
        assertEquals(1L, item.id)
        assertEquals("TEXT", item.kind)
        assertEquals("hello", item.text)
        assertNull(item.fileName)
        assertNull(item.fileSize)
        assertNull(item.mime)
    }

    @Test
    fun `file favorite maps name size mime and leaves text null`() {
        val dto = toFavoritesResponseDto(listOf(fileFav(2, "a.pdf")), emptyList())
        val item = dto.items.single()
        assertEquals("FILE", item.kind)
        assertNull(item.text)
        assertEquals("a.pdf", item.fileName)
        assertEquals(1234L, item.fileSize)
        assertEquals("application/pdf", item.mime)
    }

    @Test
    fun `depot fileId is never exposed to the browser`() {
        // 契约：浏览器按收藏行 id 取文件，不需要也不应该知道 depot 落盘 id。
        val dto = toFavoritesResponseDto(listOf(fileFav(2, "a.pdf")), emptyList())
        val rendered = dto.items.single().toString()
        assertEquals(false, rendered.contains("depot-abc"))
    }

    @Test
    fun `groups keep the phone's sortOrder`() {
        val groups = listOf(
            FavoriteGroupEntity(id = 5, name = "工作", sortOrder = 1, createdAt = 0L),
            FavoriteGroupEntity(id = 3, name = "常用片段", sortOrder = 0, createdAt = 0L),
        )
        val dto = toFavoritesResponseDto(emptyList(), groups)
        assertEquals(listOf(3L, 5L), dto.groups.map { it.id })
        assertEquals(listOf("常用片段", "工作"), dto.groups.map { it.name })
    }

    @Test
    fun `items are newest first`() {
        val dto = toFavoritesResponseDto(
            listOf(textFav(1, "old", at = 100L), textFav(2, "new", at = 900L)),
            emptyList(),
        )
        assertEquals(listOf(2L, 1L), dto.items.map { it.id })
    }

    @Test
    fun `ungrouped favorite keeps a null groupId`() {
        val dto = toFavoritesResponseDto(listOf(textFav(1, "x", group = null)), emptyList())
        assertNull(dto.items.single().groupId)
    }
}
