package club.pisquad.minecraft.csgrenades.command

import club.pisquad.minecraft.csgrenades.CsGrenadeConfigManager
import com.mojang.brigadier.Command
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.BoolArgumentType
import com.mojang.brigadier.context.CommandContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands

object ChangeSettingCommand {
    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal("csgrenades").then(
                Commands.literal("config").then(
                    Commands.literal("ignoreBarrierBlock")
                        .then(
                            Commands.argument("value", BoolArgumentType.bool())
                                .executes(ChangeSettingCommand::toggleIgnoreBarrierBlock)
                        )
                )
            )
        )
    }

    private fun toggleIgnoreBarrierBlock(context: CommandContext<CommandSourceStack>): Int {
        val config = CsGrenadeConfigManager.config
        config.ignoreBarrierBlock = BoolArgumentType.getBool(context, "value")
        CsGrenadeConfigManager.update(config)
        return Command.SINGLE_SUCCESS
    }
}