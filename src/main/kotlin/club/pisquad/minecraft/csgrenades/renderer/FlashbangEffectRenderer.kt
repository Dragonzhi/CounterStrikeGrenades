package club.pisquad.minecraft.csgrenades.renderer

import club.pisquad.minecraft.csgrenades.CounterStrikeGrenades
import club.pisquad.minecraft.csgrenades.SoundTypes
import club.pisquad.minecraft.csgrenades.SoundUtils
import club.pisquad.minecraft.csgrenades.registery.ModSoundEvents
import club.pisquad.minecraft.csgrenades.sound.FlashbangRingSound
import club.pisquad.minecraft.csgrenades.toVec3i
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.core.BlockPos
import net.minecraft.sounds.SoundSource
import net.minecraft.util.FastColor
import net.minecraft.util.RandomSource
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.Vec3
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.api.distmarker.OnlyIn
import net.minecraftforge.client.event.RenderGuiOverlayEvent
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod
import java.time.Duration
import java.time.Instant
import kotlin.math.*


@OnlyIn(Dist.CLIENT)
data class FlashbangEffectData(
    val position: Vec3, val effectAttack: Int, val effectSustain: Int, val effectDecay: Int, val effectAmount: Int
) {
    companion object {
        fun create(flashbangPos: Vec3): FlashbangEffectData {
            val player = Minecraft.getInstance().player!!
            val playerToFlashVec = flashbangPos.add(player.position().reverse())
            val distance = playerToFlashVec.length()
            val angle = acos(player.lookAngle.dot(playerToFlashVec.normalize())).times(180).times(1 / PI)

            val distanceFactor = getDistanceFactor(distance)
            val blockingFactor = getBlockingFactor(flashbangPos, player.position())

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
                effectAmount = 30,
                effectSustain = (fullyBlindedTime * 1000).toInt(),
                effectDecay = ((totalEffectTime - fullyBlindedTime) * 1000).toInt()
            )
        }

        private fun getDistanceFactor(distance: Double): Double {
            return max(-0.015 * distance + 1, 0.0)
        }

        private fun getBlockingFactor(flashbangPos: Vec3, playerEyePos: Vec3): Double {

            var blockingFactor = 1.0
            val playerToFlashBangVec = flashbangPos.add(playerEyePos.reverse())
            val direction = playerToFlashBangVec.normalize()
            val level = Minecraft.getInstance().level ?: return blockingFactor

            for (i in 1..playerToFlashBangVec.length().toInt()) {
                val blockState =
                    level.getBlockState(BlockPos(playerEyePos.add(direction.scale(i.toDouble())).toVec3i()))
                blockingFactor -= getBlockingFactorDelta(blockState)
                if (blockingFactor <= 0.0) {
                    return 0.0
                }

            }
            return blockingFactor
        }

        private fun getBlockingFactorDelta(blockState: BlockState): Double {
            if (blockState.isAir) {
                return 0.0
            }
            if (blockState.canOcclude()) {
                return 1.0
            }
            return 0.2
        }
    }

}

private enum class RenderState {
    IDLE,
    AttackStage,
    SustainStage,
    DecayStage,
}

@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = CounterStrikeGrenades.ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = [Dist.CLIENT])
object FlashbangEffectRenderer {
    private var effectAttack: Int = 0
    private var effectSustain: Int = 0
    private var effectDecay: Int = 0
    private var effectAmount: Int = 0
    private var renderStartTime: Instant = Instant.now()
    private var renderState: RenderState = RenderState.IDLE

    fun render(effectData: FlashbangEffectData) {
        when (renderState) {
            RenderState.IDLE -> {
                renderStartTime = Instant.now()

                effectAttack = effectData.effectAttack
                effectSustain = effectData.effectSustain
                effectDecay = effectData.effectDecay
                effectAmount = effectData.effectAmount

                MinecraftForge.EVENT_BUS.register(FlashbangEffectRenderer::eventHandler)
            }

            RenderState.AttackStage, RenderState.SustainStage -> {
                effectAttack = max(effectAttack, effectData.effectAttack)
                effectSustain = max(effectSustain, effectData.effectSustain)
                effectDecay = max(effectDecay, effectData.effectDecay)
                effectAmount = max(effectAmount, effectData.effectAmount)
            }

            RenderState.DecayStage -> {
                renderStartTime = Instant.now() + Duration.ofMillis(effectData.effectDecay.toLong())
                effectAttack = effectData.effectAttack
                effectSustain = effectData.effectSustain
                effectDecay = effectData.effectDecay
            }
        }
        playExplosionSound(effectData)
        playRingSound(effectData)
    }

    @SubscribeEvent
    fun eventHandler(event: RenderGuiOverlayEvent.Post) {
        val currentTime = Instant.now()
        // Converting type to double for precise calculation
        val timeDelta = Duration.between(renderStartTime, currentTime).toMillis().toDouble()

        if (timeDelta < effectAttack) {
            val opacity = (timeDelta / effectAttack * effectAmount).toInt()
            drawOverlay(event.guiGraphics, opacity)
            renderState = RenderState.AttackStage
        } else if (timeDelta < effectSustain + effectAttack) {
            val opacity = (effectAmount)
            drawOverlay(event.guiGraphics, opacity)
            renderState = RenderState.SustainStage
        } else if (timeDelta < effectDecay + effectSustain + effectAttack) {
            val opacity =
                effectAmount - ((timeDelta - effectAttack - effectSustain) / effectDecay * effectAmount).toInt()
            drawOverlay(event.guiGraphics, opacity)
            renderState = RenderState.DecayStage
        } else {
            clean()
        }
    }

    private fun drawOverlay(gui: GuiGraphics, opacity: Int) {
        gui.fill(
            0,
            0,
            gui.guiWidth(),
            gui.guiHeight(),
            FastColor.ABGR32.color(
                opacity, 255, 255, 255
            ),
        )
    }

    private fun clean() {
        effectAttack = 0
        effectDecay = 0
        effectSustain = 0
        effectAmount = 0
        renderState = RenderState.IDLE
        MinecraftForge.EVENT_BUS.unregister(FlashbangEffectRenderer::eventHandler)
    }

    private fun playRingSound(effectData: FlashbangEffectData) {
        val distance = sqrt(Minecraft.getInstance().player!!.distanceToSqr(effectData.position))

        Minecraft.getInstance().soundManager.play(
            FlashbangRingSound(
                attack = 0,
                sustain = effectSustain,
                decay = effectDecay + 300,
                targetVolume = SoundUtils.getVolumeFromDistance(distance, SoundTypes.FLASHBANG_RING).toFloat()
            )
        )
    }

    private fun playExplosionSound(effectData: FlashbangEffectData) {
        val distance = sqrt(Minecraft.getInstance().player!!.distanceToSqr(effectData.position))
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
                soundEvent, SoundSource.AMBIENT, SoundUtils.getVolumeFromDistance(distance, soundType).toFloat(), 1f,
                RandomSource.createNewThreadLocalInstance(),
                effectData.position.x, effectData.position.y, effectData.position.z
            )
        )

    }
}