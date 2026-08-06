package com.example.flikky.ui.serving

import com.example.flikky.data.settings.FlikkySettings
import com.example.flikky.session.Message
import com.example.flikky.session.Origin
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServingMessageRecallPolicyTest {

    private fun text(origin: Origin, id: Long = 1L) =
        Message.Text(id, origin, 0L, "message")

    @Test
    fun `phone can recall own messages when recall is enabled`() {
        assertTrue(canShowServingRecallAction(FlikkySettings(recallBetaEnabled = true), text(Origin.PHONE)))
    }

    @Test
    fun `phone cannot recall browser messages until peer recall is enabled`() {
        val ownOnly = FlikkySettings(recallBetaEnabled = true, allowPeerRecall = false)
        val bothSides = ownOnly.copy(allowPeerRecall = true)

        assertFalse(canShowServingRecallAction(ownOnly, text(Origin.BROWSER)))
        assertTrue(canShowServingRecallAction(bothSides, text(Origin.BROWSER)))
    }

    @Test
    fun `failed file messages never expose recall`() {
        val failed = Message.File(
            id = 1L,
            origin = Origin.BROWSER,
            timestamp = 0L,
            fileId = "f",
            name = "f.bin",
            sizeBytes = 1L,
            mime = "application/octet-stream",
            status = Message.File.Status.FAILED,
        )
        assertFalse(
            canShowServingRecallAction(
                FlikkySettings(recallBetaEnabled = true, allowPeerRecall = true),
                failed,
            ),
        )
    }
}
