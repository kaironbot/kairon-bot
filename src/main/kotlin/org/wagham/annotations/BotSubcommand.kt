package org.wagham.annotations

import dev.kord.rest.builder.RequestBuilder
import org.wagham.commands.SlashCommand
import kotlin.reflect.KClass

annotation class BotSubcommand(val profile: String, val baseCommand: KClass<out SlashCommand<RequestBuilder<*>>>)
