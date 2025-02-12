package club.pisquad.minecraft.csgrenades.client.renderer

import club.pisquad.minecraft.csgrenades.CounterStrikeGrenades
import club.pisquad.minecraft.csgrenades.SoundTypes
import club.pisquad.minecraft.csgrenades.SoundUtils
import club.pisquad.minecraft.csgrenades.network.message.FlashbangEffectData
import club.pisquad.minecraft.csgrenades.registery.ModSoundEvents
import club.pisquad.minecraft.csgrenades.sound.FlashbangRingSound
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.sounds.SoundSource
import net.minecraft.util.FastColor
import net.minecraft.util.RandomSource
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.api.distmarker.OnlyIn
import net.minecraftforge.client.event.RenderGuiOverlayEvent
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod
import java.time.Duration
import java.time.Instant
import kotlin.math.max
import kotlin.math.sqrt


private enum class RenderState {
    IDLE,
    AttackStage,
    SustainStage,
    DecayStage,
}

@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = CounterStrikeGrenades.ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = [Dist.CLIENT])
object FlashbangBlindEffectRenderer {
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

                MinecraftForge.EVENT_BUS.register(FlashbangBlindEffectRenderer::eventHandler)
            }

            RenderState.AttackStage, RenderState.SustainStage -> {
                effectAttack = max(effectAttack, effectData.effectAttack)
                effectSustain = max(effectSustain, effectData.effectSustain)
                effectDecay = max(effectDecay, effectData.effectDecay)
                effectAmount = max(effectAmount, effectData.effectAmount)
            }

            RenderState.DecayStage -> {
                renderStartTime = Instant.now() - Duration.ofMillis(effectData.effectAttack.toLong())
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
        val player = Minecraft.getInstance().player ?: return
        if (player.isSpectator) return

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
        MinecraftForge.EVENT_BUS.unregister(FlashbangBlindEffectRenderer::eventHandler)
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