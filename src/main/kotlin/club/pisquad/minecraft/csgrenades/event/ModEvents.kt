package club.pisquad.minecraft.csgrenades.event

import club.pisquad.minecraft.csgrenades.CounterStrikeGrenades
import club.pisquad.minecraft.csgrenades.api.CSGrenadesAPI
import net.minecraftforge.event.TickEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod

/**
 * 模组的Forge事件处理器。
 */
@Mod.EventBusSubscriber(modid = CounterStrikeGrenades.ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
object ModEvents {

    /**
     * 监听服务器Tick事件，用于更新闪光弹致盲状态。
     * @param event Tick事件。
     */
    @SubscribeEvent
    fun onServerTick(event: TickEvent.ServerTickEvent) {
        if (event.phase == TickEvent.Phase.END) {
            CSGrenadesAPI.onServerTick()
        }
    }
}
