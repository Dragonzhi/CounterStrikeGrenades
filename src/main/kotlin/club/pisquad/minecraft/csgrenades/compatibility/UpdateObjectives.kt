package club.pisquad.minecraft.csgrenades.compatibility

import club.pisquad.minecraft.csgrenades.CounterStrikeGrenades
import club.pisquad.minecraft.csgrenades.entity.*
import com.mojang.brigadier.CommandDispatcher
import net.minecraft.client.Minecraft
import net.minecraft.commands.CommandSourceStack
import net.minecraft.server.MinecraftServer
import net.minecraft.world.entity.player.Player
import net.minecraft.world.scores.Scoreboard
import net.minecraftforge.event.entity.living.LivingDeathEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod

enum class ModObjectives(val objectiveName: String) {
    KILLCOUNT_HEGRENADE("csgrenade_killcount_hegrenade"),
    KILLCOUNT_INCENDIARY("csgrenade_killcount_incendiary"),
    KILLCOUNT_MOLOTOV("csgrenade_killcount_molotov"),
    KILLCOUNT_FLASHBANG("csgrenade_killcount_flashbang"),
    KILLCOUNT_SMOKEGRENADE("csgrenade_killcount_smokegrenade"),
}

object ObjectiveManager {
    fun initAllObjectives(
        scoreboard: Scoreboard,
        dispatcher: CommandDispatcher<CommandSourceStack>,
        commandSource: CommandSourceStack
    ) {
        for (objective in ModObjectives.entries) {
            if (scoreboard.getObjective(objective.objectiveName) != null) {
                removeObjective(objective, dispatcher, commandSource)
            }
            dispatcher.execute("scoreboard objectives add ${objective.objectiveName} dummy", commandSource)
        }
    }

    private fun removeObjective(
        objective: ModObjectives,
        dispatcher: CommandDispatcher<CommandSourceStack>,
        commandSource: CommandSourceStack
    ) {
        dispatcher.execute("scoreboard objectives remove ${objective.objectiveName}", commandSource)
    }

    fun increaseObjective(
        player: String,
        scoreboard: Scoreboard,
        objective: ModObjectives,
        dispatcher: CommandDispatcher<CommandSourceStack>,
        commandSource: CommandSourceStack
    ) {
        if (scoreboard.getObjective(objective.objectiveName) != null) {
            dispatcher.execute("scoreboard players add $player ${objective.objectiveName} 1", commandSource)
        }
    }
}


@Mod.EventBusSubscriber(modid = CounterStrikeGrenades.ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
object UpdateObjectives {
    @SubscribeEvent
    fun onPlayerDied(event: LivingDeathEvent) {
        if (event.entity is Player && event.source.entity is CounterStrikeGrenadeEntity) {
            val server = event.entity.server ?: return
            val attackerName = (event.source.entity as CounterStrikeGrenadeEntity).owner?.name?.string ?: return
            val commandSource = server.createCommandSourceStack()

            val objective = when (event.source.entity) {
                is FlashBangEntity -> ModObjectives.KILLCOUNT_FLASHBANG
                is SmokeGrenadeEntity -> ModObjectives.KILLCOUNT_SMOKEGRENADE
                is HEGrenadeEntity -> ModObjectives.KILLCOUNT_HEGRENADE
                is IncendiaryEntity -> ModObjectives.KILLCOUNT_INCENDIARY
                is MolotovEntity -> ModObjectives.KILLCOUNT_MOLOTOV
                else -> {
                    null
                }
            }
            if (objective != null) {
                ObjectiveManager.increaseObjective(
                    attackerName,
                    server.scoreboard,
                    objective,
                    server.commands.dispatcher,
                    commandSource
                )
            }

        }
    }
}