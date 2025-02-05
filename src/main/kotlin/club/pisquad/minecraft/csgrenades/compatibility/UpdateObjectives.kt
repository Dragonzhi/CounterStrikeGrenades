package club.pisquad.minecraft.csgrenades.compatibility

import club.pisquad.minecraft.csgrenades.*
import club.pisquad.minecraft.csgrenades.registery.ModDamageType
import net.minecraft.world.entity.player.Player
import net.minecraftforge.event.entity.living.LivingDeathEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod

@Mod.EventBusSubscriber(modid = CounterStrikeGrenades.ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
object UpdateObjectives {
    @SubscribeEvent
    fun onPlayerDied(event: LivingDeathEvent) {
        if (event.entity is Player) {
            val server = event.entity.server ?: return
            val commandSource = server.createCommandSourceStack()
            if (event.source.`is`(ModDamageType.HEGRENADE_EXPLOSION_DAMAGE) && server.scoreboard.getObjective(
                    OBJECTIVE_KILLCOUNT_HEGRENADE
                ) != null
            ) {

                server.commands.dispatcher.execute(
                    COMMAND_INCREASE_OBJECTIVE_KILLCOUNT_HEGRENADE.format(event.entity.name.string), commandSource
                )
            } else if (event.source.`is`(ModDamageType.INCENDIARY_FIRE_DAMAGE) && server.scoreboard.getObjective(
                    OBJECTIVE_KILLCOUNT_INCENDIARY
                ) != null
            ) {
                server.commands.dispatcher.execute(
                    COMMAND_INCREASE_OBJECTIVE_KILLCOUNT_INCENDIARY.format(event.entity.name.string), commandSource
                )
            } else if (event.source.`is`(ModDamageType.MOLOTOV_FIRE_DAMAGE) && server.scoreboard.getObjective(
                    OBJECTIVE_KILLCOUNT_MOLOTOV
                ) != null
            ) {
                server.commands.dispatcher.execute(
                    COMMAND_INCREASE_OBJECTIVE_KILLCOUNT_MOLOTOV.format(event.entity.name.string), commandSource
                )
            }
        }
    }
}