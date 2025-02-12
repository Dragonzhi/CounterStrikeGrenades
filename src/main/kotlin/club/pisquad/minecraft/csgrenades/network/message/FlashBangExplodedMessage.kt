package club.pisquad.minecraft.csgrenades.network.message

import club.pisquad.minecraft.csgrenades.client.renderer.FlashbangBlindEffectRenderer
import club.pisquad.minecraft.csgrenades.client.renderer.FlashbangParticleEffectRenderer
import club.pisquad.minecraft.csgrenades.network.serializer.UUIDSerializer
import club.pisquad.minecraft.csgrenades.network.serializer.Vec3Serializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.minecraft.client.Minecraft
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import net.minecraftforge.network.NetworkEvent
import java.util.*
import java.util.function.Supplier
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.max

@Serializable
data class FlashbangEffectData(
    @Serializable(with = Vec3Serializer::class) val position: Vec3,
    val effectAttack: Int,
    val effectSustain: Int,
    val effectDecay: Int,
    val effectAmount: Int
) {
    companion object {
        fun create(flashbangPos: Vec3, player: Player): FlashbangEffectData {
            val playerToFlashVec = flashbangPos.add(player.position().reverse())
            val distance = playerToFlashVec.length()
            val angle = acos(player.lookAngle.dot(playerToFlashVec.normalize())).times(180).times(1 / PI)

            val distanceFactor = getDistanceFactor(distance)
            val blockingFactor = getBlockingFactor(flashbangPos, player.eyePosition)

            val fullyBlindedTime = max(
                0.0, when (angle) {
                    in 0.0..53.0 -> 1.88
                    in 53.0..72.0 -> 0.45
                    in 72.0..101.0 -> 0.08
                    in 101.0..180.0 -> 0.08
                    else -> 0.0
                } * distanceFactor * blockingFactor
            )

            val totalEffectTime = max(
                0.0, when (angle) {
                    in 0.0..53.0 -> 4.0
                    in 53.0..72.0 -> 3.0
                    in 72.0..101.0 -> 1.5
                    in 101.0..180.0 -> 0.5
                    else -> 0.0
                } * distanceFactor * blockingFactor
            )

            return FlashbangEffectData(
                position = flashbangPos,
                effectAttack = 20,
                effectAmount = 50,
                effectSustain = (fullyBlindedTime * 1000).toInt(),
                effectDecay = ((totalEffectTime - fullyBlindedTime) * 1000).toInt()
            )
        }

        private fun getDistanceFactor(distance: Double): Double {
            return max(-0.015 * distance + 1, 0.0)
        }

        private fun getBlockingFactor(flashbangPos: Vec3, playerEyePos: Vec3): Double {
            val level = Minecraft.getInstance().level ?: return 1.0
            val context =
                ClipContext(playerEyePos, flashbangPos, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, null)
            val result = level.clip(context)
            return if (result.type.equals(HitResult.Type.MISS)) 1.0 else 0.0
        }
    }

}

@Serializable
data class AffectedPlayerInfo(
    @Serializable(with = UUIDSerializer::class) val uuid: UUID,
    val effectData: FlashbangEffectData
)


@Serializable
class FlashBangExplodedMessage(
    @Serializable(with = Vec3Serializer::class) val position: Vec3,
    val affectedPlayers: List<AffectedPlayerInfo>
) {
    companion object {

        fun encoder(msg: FlashBangExplodedMessage, buffer: FriendlyByteBuf) {
            buffer.writeUtf(Json.encodeToString(msg))
        }

        fun decoder(buffer: FriendlyByteBuf): FlashBangExplodedMessage {
            val text = buffer.readUtf()
            return Json.decodeFromString<FlashBangExplodedMessage>(text)
        }

        fun handler(msg: FlashBangExplodedMessage, ctx: Supplier<NetworkEvent.Context>) {
            val context = ctx.get()
            context.packetHandled = true
            if (context.direction.receptionSide.isClient) {
                val localPlayer = Minecraft.getInstance().player ?: return
                msg.affectedPlayers.forEach { playerInfo ->
                    if (playerInfo.uuid == localPlayer.uuid) {
                        FlashbangBlindEffectRenderer.render(playerInfo.effectData)
                    } else {
                        FlashbangParticleEffectRenderer.render(playerInfo)
                    }
                }

            }
        }
    }
}