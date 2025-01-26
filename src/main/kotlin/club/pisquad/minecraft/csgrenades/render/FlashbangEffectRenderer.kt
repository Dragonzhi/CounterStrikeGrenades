package club.pisquad.minecraft.csgrenades.render

import club.pisquad.minecraft.csgrenades.CounterStrikeGrenades
import club.pisquad.minecraft.csgrenades.toVec3i
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.core.BlockPos
import net.minecraft.util.FastColor
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
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.max


@OnlyIn(Dist.CLIENT)
data class FlashbangEffectData(
    val effectAttack: Int, val effectSustain: Int, val effectDecay: Int, val effectAmount: Int
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
                    in 0.0..53.0 -> 4.87
                    in 53.0..72.0 -> 3.4
                    in 72.0..101.0 -> 1.95
                    in 101.0..180.0 -> 0.95
                    else -> 0.0
                } * distanceFactor * blockingFactor
            )

            return FlashbangEffectData(
                effectAttack = 20,
                effectAmount = 150,
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

@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = CounterStrikeGrenades.ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = [Dist.CLIENT])
object FlashbangEffectRenderer {
    private var effectAttack: Int = 0
    private var effectSustain: Int = 0
    private var effectDecay: Int = 0
    private var effectAmount: Int = 0
    private var renderStartTime: Instant = Instant.now()
    private var rendererActive: Boolean = false

    fun render(effectData: FlashbangEffectData) {
        this.renderStartTime = Instant.now()
        this.rendererActive = true

        this.effectAttack = effectData.effectAttack
        this.effectSustain = effectData.effectSustain
        this.effectDecay = effectData.effectDecay
        this.effectAmount = effectData.effectAmount

        println("Rendering${effectData}")
        MinecraftForge.EVENT_BUS.register(FlashbangEffectRenderer::eventHandler);
    }

    @SubscribeEvent
    fun eventHandler(event: RenderGuiOverlayEvent.Post) {
        if (!this.rendererActive) {
            return
        }
        val currentTime = Instant.now()
        // Converting type to double for precise calculation
        val timeDelta = Duration.between(this.renderStartTime, currentTime).toMillis().toDouble()

        if (timeDelta < this.effectAttack) {
            val opacity = (timeDelta / this.effectAttack * this.effectAmount).toInt()
            println("Attack Phase opacity $opacity")
            this.drawOverlay(event.guiGraphics, opacity)

        } else if (timeDelta < this.effectSustain + this.effectAttack) {
            val opacity = (this.effectAmount)
            println("Sustain Phase opacity $opacity")
            this.drawOverlay(event.guiGraphics, opacity)

        } else if (timeDelta < this.effectDecay + this.effectSustain + this.effectAttack) {
            val opacity =
                this.effectAmount - ((timeDelta - this.effectAttack - this.effectSustain) / this.effectDecay * this.effectAmount).toInt()
            println("Decay Phase opacity $opacity")
            this.drawOverlay(event.guiGraphics, opacity)
        } else {
            this.clean()
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
        this.effectAttack = 0
        this.effectDecay = 0
        this.effectSustain = 0
        this.effectAmount = 0
        this.rendererActive = false
        MinecraftForge.EVENT_BUS.unregister(FlashbangEffectRenderer::eventHandler);
    }
}