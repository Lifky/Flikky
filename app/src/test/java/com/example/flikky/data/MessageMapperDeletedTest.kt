package com.example.flikky.data

import com.example.flikky.data.db.entities.MessageEntity
import com.example.flikky.session.Message
import org.junit.Assert.assertEquals
import org.junit.Test

class MessageMapperDeletedTest {
    @Test
    fun `DELETED fileStatus maps to domain and back`() {
        val entity = MessageEntity(
            id = 1L,
            sessionId = 2L,
            origin = "BROWSER",
            timestamp = 3L,
            kind = "FILE",
            fileId = "f1",
            fileName = "a.png",
            fileSize = 10L,
            fileMime = "image/png",
            fileStatus = "DELETED",
        )

        val domain = entity.toMessage() as Message.File

        assertEquals(Message.File.Status.DELETED, domain.status)
        assertEquals("DELETED", domain.toEntity(2L).fileStatus)
    }
}
