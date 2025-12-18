package club.pisquad.minecraft.csgrenades.command

import club.pisquad.minecraft.csgrenades.config.ModConfig
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component

object SetConfigCommand {
    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        val command: LiteralArgumentBuilder<CommandSourceStack> = Commands.literal("csgrenades")
            .requires { source -> source.hasPermission(2) }
            .then(
                Commands.argument("grenadeType", StringArgumentType.string())
                    .suggests { _, builder ->
                        builder.suggest("hegrenade")
                        builder.suggest("firegrenade")
                        builder.buildFuture()
                    }
                    .then(
                        Commands.literal("causeDamageToOwner")
                            .then(
                                Commands.argument("value", StringArgumentType.string())
                                    .suggests { _, builder ->
                                        ModConfig.SelfDamageSetting.entries.forEach { builder.suggest(it.name.lowercase()) }
                                        builder.buildFuture()
                                    }
                                    .executes { context ->
                                        execute(
                                            context,
                                            StringArgumentType.getString(context, "grenadeType"),
                                            StringArgumentType.getString(context, "value")
                                        )
                                    }
                            )
                    )
            )
        dispatcher.register(command)
    }

    private fun execute(context: CommandContext<CommandSourceStack>, grenadeType: String, value: String): Int {
        val source = context.source
        val configValue = try {
            ModConfig.SelfDamageSetting.valueOf(value.uppercase())
        } catch (e: IllegalArgumentException) {
            source.sendFailure(Component.literal("Invalid value '$value'. Must be one of: never, not_in_team, always."))
            return 0
        }

        val configToChange = when (grenadeType.lowercase()) {
            "hegrenade" -> ModConfig.HEGrenade.CAUSE_DAMAGE_TO_OWNER
            "firegrenade" -> ModConfig.FireGrenade.CAUSE_DAMAGE_TO_OWNER
            else -> {
                source.sendFailure(Component.literal("Invalid grenade type '$grenadeType'. Must be 'hegrenade' or 'firegrenade'."))
                return 0
            }
        }

        configToChange.set(configValue)
        ModConfig.SPEC.save()

        source.sendSuccess(
            { Component.literal("Set $grenadeType causeDamageToOwner to $value") },
            true
        )
        return 1
    }
}
