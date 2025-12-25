package club.pisquad.minecraft.csgrenades.network.message

import club.pisquad.minecraft.csgrenades.SoundTypes
import club.pisquad.minecraft.csgrenades.SoundUtils
import club.pisquad.minecraft.csgrenades.client.renderer.FlashbangBlindEffectRenderer
import club.pisquad.minecraft.csgrenades.client.renderer.FlashbangParticleEffectRenderer
import club.pisquad.minecraft.csgrenades.config.ModConfig
import club.pisquad.minecraft.csgrenades.linearInterpolate
import club.pisquad.minecraft.csgrenades.network.serializer.UUIDSerializer
import club.pisquad.minecraft.csgrenades.network.serializer.Vec3Serializer
import club.pisquad.minecraft.csgrenades.registery.ModSoundEvents
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.sounds.SoundSource
import net.minecraft.util.RandomSource
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.Level
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import net.minecraftforge.network.NetworkEvent
import java.util.*
import java.util.function.Supplier
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

@Serializable
data class FlashbangEffectData(
    @Serializable(with = Vec3Serializer::class) val position: Vec3,
    val effectAttack: Int,
    val effectSustain: Int,
    val effectDecay: Int,
    val effectAmount: Int
) {
    companion object {
        fun create(level: Level, flashbangPos: Vec3, player: Player): FlashbangEffectData {
            val playerToFlashVec = flashbangPos.add(player.position().reverse())
            val distance = playerToFlashVec.length()
            val angle = acos(player.lookAngle.dot(playerToFlashVec.normalize())).times(180).times(1 / PI)

            val distanceFactor = getDistanceFactor(distance)
            val blockingFactor = getBlockingFactor(flashbangPos, player)

            val fullyBlindedTime = max(
                0.0, when (angle) {
                    in 0.0..53.0 -> 2.5 // Increased from 1.88
                    in 53.0..72.0 -> 0.6 // Increased from 0.45
                    in 72.0..101.0 -> 0.1 // Increased from 0.08
                    in 101.0..180.0 -> 0.02 // Changed from 0.0
                    else -> 0.0
                } * distanceFactor * blockingFactor
            )

            val totalEffectTime = max(
                0.0, when (angle) {
                    in 0.0..53.0 -> 5.0 // Increased from 4.0
                    in 53.0..72.0 -> 3.75 // Increased from 3.0
                    in 72.0..101.0 -> 2.0 // Increased from 1.5
                    in 101.0..180.0 -> 0.25 // Decreased from 0.5
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
            val range = ModConfig.Flashbang.EFFECTIVE_RANGE.get()
            val ratio = (distance / range).coerceIn(0.0, 1.0)
            // Use a power curve (1 - ratio^2) to make the effect stronger at a distance.
            return max(1.0 - ratio.pow(2.0), 0.0)
        }

        private fun getBlockingFactor(flashbangPos: Vec3, player: Player): Double {
            val context =
                ClipContext(player.eyePosition, flashbangPos, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, player)
            val result = player.level().clip(context)
            return if (result.type.equals(HitResult.Type.MISS)) 1.0 else 0.0
        }
    }

}

@Serializable
data class AffectedPlayerInfo(
    @Serializable(with = UUIDSerializer::class) val uuid: UUID, val effectData: FlashbangEffectData
)


@Serializable
class FlashBangExplodedMessage(
    @Serializable(with = Vec3Serializer::class) val position: Vec3, val affectedPlayers: List<AffectedPlayerInfo>
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
                playExplosionSound(msg.position)

                // Add particle spawning
                val level = Minecraft.getInstance().level
                val random = RandomSource.createNewThreadLocalInstance()
                for (i in 0 until 20) { // Spawn 20 particles
                    val speedX = random.nextGaussian().toFloat() * 0.02f
                    val speedY = random.nextGaussian().toFloat() * 0.02f
                    val speedZ = random.nextGaussian().toFloat() * 0.02f
                    if (level != null) {
                        level.addParticle(
                            net.minecraft.core.particles.ParticleTypes.FLASH, // The FLASH particle type
                            msg.position.x,
                            msg.position.y,
                            msg.position.z,
                            speedX.toDouble(), speedY.toDouble(), speedZ.toDouble()
                        )
                    }
                }
            }
        }
    }
}

private fun playExplosionSound(position: Vec3) {
    val distance = sqrt(Minecraft.getInstance().player!!.distanceToSqr(position))
    val soundEvent = when {
        distance <= 15 -> ModSoundEvents.FLASHBANG_EXPLODE.get()
        else -> ModSoundEvents.FLASHBANG_EXPLODE_DISTANT.get()
    }
    val soundType = when {
        distance <= 15 -> SoundTypes.FLASHBANG_EXPLODE
        else -> SoundTypes.FLASHBANG_EXPLODE_DISTANT
    }
    Minecraft.getInstance().soundManager.play(
        SimpleSoundInstance(
            soundEvent,
            SoundSource.AMBIENT,
            SoundUtils.getVolumeFromDistance(distance, soundType).toFloat(),
            1f,
            RandomSource.createNewThreadLocalInstance(),
            position.x,
            position.y,
            position.z
        )
    )

}