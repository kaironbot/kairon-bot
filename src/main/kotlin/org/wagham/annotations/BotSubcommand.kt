package org.wagham.annotations

import org.wagham.commands.SlashCommand
import kotlin.reflect.KClass

annotation class BotSubcommand(val profile: String, val baseCommand: KClass<out SlashCommand>)
