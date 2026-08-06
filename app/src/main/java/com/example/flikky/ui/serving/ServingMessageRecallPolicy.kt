package com.example.flikky.ui.serving

import com.example.flikky.data.settings.FlikkySettings
import com.example.flikky.session.Message
import com.example.flikky.session.Origin
import com.example.flikky.session.canRecallMessage

internal fun canShowServingRecallAction(settings: FlikkySettings, message: Message): Boolean {
    if (message is Message.File && message.status == Message.File.Status.FAILED) return false
    return canRecallMessage(
        requesterOrigin = Origin.PHONE,
        messageOrigin = message.origin,
        recallEnabled = settings.recallBetaEnabled,
        allowPeerRecall = settings.allowPeerRecall,
    )
}
