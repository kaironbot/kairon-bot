package org.wagham.utils

import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.entity.channel.MessageChannel
import dev.kord.rest.builder.component.MessageComponentBuilder
import dev.kord.rest.builder.message.modify.InteractionResponseModifyBuilder
import dev.kord.rest.builder.message.modify.embed
import org.wagham.config.Colors
import org.wagham.config.locale.CommonLocale

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

fun createGenericEmbedError(message: String, actionRows: MutableList<MessageComponentBuilder> = mutableListOf()): InteractionResponseModifyBuilder.() -> Unit =
    fun InteractionResponseModifyBuilder.() {
        embed {
            color = Colors.WARNING.value
            title = "Error"
            description = message
        }
        components = actionRows
    }

fun createGenericEmbedSuccess(message: String, actionRows: MutableList<MessageComponentBuilder> = mutableListOf()): InteractionResponseModifyBuilder.() -> Unit =
    fun InteractionResponseModifyBuilder.() {
        embed {
            color = Colors.DEFAULT.value
            title = "Success"
            description = message
        }
        components = actionRows
    }