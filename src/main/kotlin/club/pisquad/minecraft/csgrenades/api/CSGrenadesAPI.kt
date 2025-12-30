package club.pisquad.minecraft.csgrenades.api

import net.minecraft.world.entity.player.Player
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * 提供了与CS手榴弹模组交互的公共API。
 */
object CSGrenadesAPI {

    // 存储被闪光弹致盲的玩家UUID及其剩余的致盲时间（tick）
    private val flashedPlayers: ConcurrentHashMap<UUID, Int> = ConcurrentHashMap()

    /**
     * 检查给定的玩家是否当前被CS手榴弹模组的闪光弹致盲。
     * @param player 要检查的玩家。
     * @return 如果玩家被致盲，则返回 true；否则返回 false。
     */
    @JvmStatic
    fun isPlayerFlashed(player: Player): Boolean {
        return flashedPlayers.containsKey(player.uuid) && (flashedPlayers[player.uuid] ?: 0) > 0
    }

    /**
     * 内部方法：设置玩家的闪光弹致盲状态和持续时间。
     * 仅供CS手榴弹模组内部使用。
     * @param player 被致盲的玩家。
     * @param durationTicks 致盲持续时间，单位是游戏刻 (tick)。
     */
    internal fun setPlayerFlashed(player: Player, durationTicks: Int) {
        if (durationTicks > 0) {
            flashedPlayers[player.uuid] = durationTicks
        } else {
            flashedPlayers.remove(player.uuid)
        }
    }

    /**
     * 内部方法：在每个服务器tick更新玩家的致盲状态。
     * 仅供CS手榴弹模组内部使用。
     */
    internal fun onServerTick() {
        flashedPlayers.forEach { (uuid, duration) ->
            if (duration <= 1) { // 如果持续时间小于等于1，则移除
                flashedPlayers.remove(uuid)
            } else {
                flashedPlayers[uuid] = duration - 1
            }
        }
    }
}
