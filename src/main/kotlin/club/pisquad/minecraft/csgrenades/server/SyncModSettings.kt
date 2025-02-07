package club.pisquad.minecraft.csgrenades.server

import club.pisquad.minecraft.csgrenades.CounterStrikeGrenades
import club.pisquad.minecraft.csgrenades.CsGrenadeConfigManager
import club.pisquad.minecraft.csgrenades.network.CsGrenadePacketHandler
import club.pisquad.minecraft.csgrenades.network.message.SettingsChangeMessage
import net.minecraftforge.event.entity.player.PlayerEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.network.PacketDistributor

@Mod.EventBusSubscriber(
    modid = CounterStrikeGrenades.ID,
    bus = Mod.EventBusSubscriber.Bus.FORGE,
)
object SyncModSettings {
    @SubscribeEvent
    fun onPlayerJoin(event: PlayerEvent.PlayerLoggedInEvent) {
        CsGrenadePacketHandler.INSTANCE.send(
            PacketDistributor.PLAYER.noArg(),
            SettingsChangeMessage(CsGrenadeConfigManager.config)
        )
    }
}