package club.pisquad.minecraft.csgrenades.entity

import club.pisquad.minecraft.csgrenades.client.SoundListener
import club.pisquad.minecraft.csgrenades.enums.GrenadeType
import club.pisquad.minecraft.csgrenades.registery.ModItems
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.projectile.ThrowableItemProjectile
import net.minecraft.world.item.Item
import net.minecraft.world.level.Level
import kotlin.random.Random

class DecoyGrenadeEntity(pEntityType: EntityType<out ThrowableItemProjectile>, pLevel: Level) :
    CounterStrikeGrenadeEntity(pEntityType, pLevel, GrenadeType.DECOY_GRENADE) {

    // --- State Variables ---
    private var activationTick: Int? = null
    private var nextSoundTick: Int = 0

    companion object {
        private const val TOTAL_DURATION_TICKS = 15 * 20 // 15 seconds
        private const val SOUND_INTERVAL_BASE_TICKS = 1 * 20   // 1 second
        private const val SOUND_INTERVAL_RANDOM_TICKS = 2 * 20  // up to 2 extra seconds
    }

    override fun getDefaultItem(): Item {
        return ModItems.DECOY_GRENADE_ITEM.get()
    }

    override fun tick() {
        super.tick()
        if (level().isClientSide) return

        if (activationTick == null) {
            // Check for landing and activation
            if (this.entityData.get(isLandedAccessor) && this.velocity.lengthSqr() < 0.01) {
                activationTick = this.tickCount
                scheduleNextSound()
            }
        } else {
            // Logic for when the decoy is active
            val currentActivationTick = tickCount - activationTick!!

            // Check if it's time to play a sound
            if (tickCount >= nextSoundTick) {
                playSoundLogic()
                scheduleNextSound()
            }

            // Check if it's time to end its life
            if (currentActivationTick > TOTAL_DURATION_TICKS) {
                endOfLifeExplosion()
            }
        }
    }

    private fun scheduleNextSound() {
        nextSoundTick = tickCount + SOUND_INTERVAL_BASE_TICKS + Random.nextInt(SOUND_INTERVAL_RANDOM_TICKS)
    }

    private fun playSoundLogic() {
        val soundToPlay = SoundListener.lastPlayerGunSound
        if (soundToPlay != null) {
            val soundEvent = SoundEvent.createVariableRangeEvent(soundToPlay)
            level().playSound(null, this.x, this.y, this.z, soundEvent, SoundSource.PLAYERS, 1.0f, 1.0f)
        }
        // If soundToPlay is null, it remains silent, as per our design.
    }

    private fun endOfLifeExplosion() {
        if (!level().isClientSide) {
            // Creates a very small explosion with low damage and no fire/block destruction.
            this.level().explode(this, this.x, this.y, this.z, 1.0f, false, Level.ExplosionInteraction.NONE)
        }
        this.discard()
    }
}
