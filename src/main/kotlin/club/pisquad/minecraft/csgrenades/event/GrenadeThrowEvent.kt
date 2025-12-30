package club.pisquad.minecraft.csgrenades.event

import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraftforge.event.entity.player.PlayerEvent
import net.minecraftforge.eventbus.api.Cancelable

/**
 * 当玩家尝试投掷一个CS手榴弹时触发的事件。
 * 这个事件是可取消的。如果事件被取消，手榴弹将不会被投掷。
 */
@Cancelable
class GrenadeThrowEvent(
    player: Player,
    val itemStack: ItemStack
) : PlayerEvent(player)
