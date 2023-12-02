package org.wagham.utils

import dev.kord.common.entity.ButtonStyle
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.entity.channel.MessageChannel
import dev.kord.rest.builder.component.MessageComponentBuilder
import dev.kord.rest.builder.message.modify.InteractionResponseModifyBuilder
import dev.kord.rest.builder.message.modify.actionRow
import dev.kord.rest.builder.message.modify.embed
import org.wagham.config.Colors
import org.wagham.config.locale.CommonLocale
import org.wagham.db.exceptions.NoActiveCharacterException

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

fun createGenericEmbedError(
    message: String,
    actionRows: MutableList<MessageComponentBuilder> = mutableListOf()
): InteractionResponseModifyBuilder.() -> Unit = {
    embed {
        color = Colors.WARNING.value
        title = "Error"
        description = message
    }
    components = actionRows
}

fun createGenericEmbedSuccess(
    message: String,
    actionRows: MutableList<MessageComponentBuilder> = mutableListOf()
): InteractionResponseModifyBuilder.() -> Unit = {
    embed {
        color = Colors.DEFAULT.value
        title = "Ok"
        description = message
    }
    components = actionRows
}

fun alternativeOptionMessage(
    locale: String,
    notFound: String,
    probable: String?,
    buttonId: String
): InteractionResponseModifyBuilder.() -> Unit = {
        embed {
            title = CommonLocale.ERROR.locale(locale)
            description = buildString {
                append(CommonLocale.ELEMENT_NOT_FOUND.locale(locale))
                append(notFound)
                probable?.also {
                    append("\n")
                    append(CommonLocale.ALTERNATIVE.locale(locale))
                    append(it)
                }
            }
           color = Colors.DEFAULT.value
        }
        probable?.also {
            actionRow {
                interactionButton(ButtonStyle.Primary, buttonId) {
                    label = "${CommonLocale.ASSIGN_ALTERNATIVE.locale(locale)}$it"
                }
            }
        }
    }

suspend fun guaranteeActiveCharacters(locale: String, block: suspend (locale: String) -> InteractionResponseModifyBuilder.() -> Unit): InteractionResponseModifyBuilder.() -> Unit {
    return try {
        block(locale)
    } catch (e: NoActiveCharacterException) {
        createGenericEmbedError(
            "<@!${e.playerId}> ${CommonLocale.NO_ACTIVE_CHARACTER.locale(locale)}"
        )
    }
}