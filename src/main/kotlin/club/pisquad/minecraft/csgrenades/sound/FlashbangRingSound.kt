package club.pisquad.minecraft.csgrenades.sound

import club.pisquad.minecraft.csgrenades.registry.ModSoundEvents
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance
import net.minecraft.client.resources.sounds.SoundInstance
import net.minecraft.sounds.SoundSource
import net.minecraft.util.RandomSource
import java.time.Duration
import java.time.Instant

class FlashbangRingSound(
    val attack: Int,
    val sustain: Int,
    val decay: Int,
    val targetVolume: Float,
    private val startTime: Instant = Instant.now(),
) : AbstractTickableSoundInstance(
    ModSoundEvents.FLASHBANG_EXPLOSION_RING.get(),
    SoundSource.MASTER,
    RandomSource.createNewThreadLocalInstance(),
) {
    init {
        this.looping = true
        this.pitch = 1.0f
        this.attenuation = SoundInstance.Attenuation.NONE
        this.volume = targetVolume
    }

    override fun tick() {
        val timeNow = Instant.now()
        val timeDelta = Duration.between(this.startTime, timeNow).toMillis().toDouble()
        if (timeDelta < attack) {
            this.volume = (timeDelta / attack).toFloat() * targetVolume
        } else if (timeDelta < attack + sustain) {
            this.volume = targetVolume
        } else if (timeDelta < attack + sustain + decay) {
            this.volume = targetVolume * (1 - (timeDelta - attack - sustain) / decay).toFloat()
        } else {
            this.stop()
        }
    }
}
