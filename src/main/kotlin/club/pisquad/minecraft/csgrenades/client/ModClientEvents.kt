package club.pisquad.minecraft.csgrenades.client

import club.pisquad.minecraft.csgrenades.CounterStrikeGrenades
import club.pisquad.minecraft.csgrenades.registry.ModItems
import net.minecraft.resources.ResourceLocation
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.client.event.ModelEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod

@Mod.EventBusSubscriber(modid = CounterStrikeGrenades.ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = [Dist.CLIENT])
object ModClientEvents {

    @SubscribeEvent
    fun onModelsRegistered(event: ModelEvent.RegisterAdditional) {
        val grenadeItems = listOf(
            ModItems.HEGRENADE_ITEM,
            ModItems.FLASH_BANG_ITEM,
            ModItems.SMOKE_GRENADE_ITEM,
            ModItems.INCENDIARY_ITEM,
            ModItems.MOLOTOV_ITEM,
            ModItems.DECOY_GRENADE_ITEM,
        )

        grenadeItems.forEach { item ->
            val itemName = item.id.path
            val thrownModelLocation = ResourceLocation(CounterStrikeGrenades.ID, "item/${itemName}_t")
            event.register(thrownModelLocation)
        }
    }
}
