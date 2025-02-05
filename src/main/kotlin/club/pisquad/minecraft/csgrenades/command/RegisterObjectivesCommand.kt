package club.pisquad.minecraft.csgrenades.command

import club.pisquad.minecraft.csgrenades.COMMAND_REGISTER_OBJECTIVE_KILLCOUNT_HEGRENADE
import club.pisquad.minecraft.csgrenades.COMMAND_REGISTER_OBJECTIVE_KILLCOUNT_INCENDIARY
import club.pisquad.minecraft.csgrenades.COMMAND_REGISTER_OBJECTIVE_KILLCOUNT_MOLOTOV
import com.mojang.brigadier.Command
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.context.CommandContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands

object RegisterObjectivesCommand {
    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal("csgrenades").then(
                Commands.literal("scoreboard").then(
                    Commands.literal("init")
                        .executes(RegisterObjectivesCommand::scoreboardInit)
                )
            )
        )
    }

    private fun scoreboardInit(context: CommandContext<CommandSourceStack>): Int {
        val source = context.source
        val server = context.source.server
        val dispatcher = server.commands.dispatcher

        dispatcher.execute(dispatcher.parse(COMMAND_REGISTER_OBJECTIVE_KILLCOUNT_HEGRENADE, source))
        dispatcher.execute(dispatcher.parse(COMMAND_REGISTER_OBJECTIVE_KILLCOUNT_INCENDIARY, source))
        dispatcher.execute(dispatcher.parse(COMMAND_REGISTER_OBJECTIVE_KILLCOUNT_MOLOTOV, source))
        return Command.SINGLE_SUCCESS
    }
}