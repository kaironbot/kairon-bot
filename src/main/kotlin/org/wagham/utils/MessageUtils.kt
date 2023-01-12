package org.wagham.utils

import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.entity.channel.MessageChannel

suspend fun MessageChannel.sendTextMessage(message: String) =
    message.split("\n")
        .fold(listOf("")) { acc, it ->
            if((acc.last().length + it.length) < 2000) {
                acc.dropLast(1) + ("${acc.last()}\n$it")
            } else acc + it
        }.forEach {
            this.createMessage {
                content = it
            }
        }
