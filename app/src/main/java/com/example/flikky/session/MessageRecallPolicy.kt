package com.example.flikky.session

fun canRecallMessage(
    requesterOrigin: Origin,
    messageOrigin: Origin,
    recallEnabled: Boolean,
    allowPeerRecall: Boolean,
): Boolean = recallEnabled && (requesterOrigin == messageOrigin || allowPeerRecall)
