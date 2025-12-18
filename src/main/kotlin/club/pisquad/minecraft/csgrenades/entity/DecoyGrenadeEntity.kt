package club.pisquad.minecraft.csgrenades.entity

import club.pisquad.minecraft.csgrenades.enums.GrenadeType
import club.pisquad.minecraft.csgrenades.registery.ModDamageType
import club.pisquad.minecraft.csgrenades.registery.ModItems
import net.minecraft.core.registries.Registries
import net.minecraft.network.syncher.EntityDataAccessor
import net.minecraft.network.syncher.EntityDataSerializers
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LivingEntity
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
        private const val TOTAL_DURATION_TICKS = 15 * 20
        private const val SOUND_INTERVAL_BASE_TICKS = 1 * 20
        private const val SOUND_INTERVAL_RANDOM_TICKS = 2 * 20

        // Synched data for custom sound
        val CUSTOM_SOUND_ACCESSOR: EntityDataAccessor<String> = SynchedEntityData.defineId(
            DecoyGrenadeEntity::class.java,
            EntityDataSerializers.STRING
        )
    }

    override fun defineSynchedData() {
        super.defineSynchedData()
        this.entityData.define(CUSTOM_SOUND_ACCESSOR, "") // Default to empty string
    }

    override fun getDefaultItem(): Item {
        return ModItems.DECOY_GRENADE_ITEM.get()
    }

    override fun tick() {
        super.tick()
        if (level().isClientSide) return

        if (activationTick == null) {
            // Check for landing and activation
            if (this.entityData.get(isLandedAccessor) && this.getDeltaMovement().lengthSqr() < 0.01) {
                activationTick = this.tickCount
                scheduleNextSound()
            }
        } else {
            // Logic for when the decoy is active
            val currentActivationTick = tickCount - activationTick!!
            if (tickCount >= nextSoundTick) {
                playSoundLogic()
                scheduleNextSound()
            }
            if (currentActivationTick > TOTAL_DURATION_TICKS) {
                endOfLifeExplosion()
            }
        }
    }

    override fun getHitDamageSource(hitEntity: LivingEntity): DamageSource {
        val registryAccess = this.level().registryAccess()
        val damageTypeHolder = registryAccess.lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(ModDamageType.DECOY_GRENADE_HIT)
        return if (hitEntity == this.owner) {
            DamageSource(damageTypeHolder, this)
        } else {
            DamageSource(damageTypeHolder, this, this.owner)
        }
    }

    // This method is called from the server-side network handler
    fun setCustomSound(sound: String) {
        this.entityData.set(CUSTOM_SOUND_ACCESSOR, sound)
    }

    private fun scheduleNextSound() {
        nextSoundTick = tickCount + SOUND_INTERVAL_BASE_TICKS + Random.nextInt(SOUND_INTERVAL_RANDOM_TICKS)
    }

    private fun playSoundLogic() {
        val customSound = this.entityData.get(CUSTOM_SOUND_ACCESSOR)

        if (customSound.isNotBlank()) {
            // Play the custom sound from NBT
            try {
                val soundEvent = SoundEvent.createVariableRangeEvent(ResourceLocation(customSound))
                level().playSound(null, this.x, this.y, this.z, soundEvent, SoundSource.PLAYERS, 4.0f, 1.0f)
            } catch (e: Exception) {
                // If the custom sound name is invalid, fall back to default
                playDefaultSound()
            }
        } else {
            // Fallback to default random footstep sounds
            playDefaultSound()
        }
    }

    private fun playDefaultSound() {
        val footstepSounds = arrayOf(
            SoundEvents.CREEPER_HURT,
            SoundEvents.CREEPER_DEATH,
            SoundEvents.CREEPER_PRIMED,
//            SoundEvents.FIREWORK_ROCKET_BLAST,
            SoundEvents.CHICKEN_HURT,
            SoundEvents.CHICKEN_AMBIENT
        )
        val randomSoundHolder = footstepSounds[Random.nextInt(footstepSounds.size)]
        level().playSound(null, this.blockPosition(), randomSoundHolder, SoundSource.PLAYERS, 1.0f, 1.0f)
    }

    private fun endOfLifeExplosion() {
        if (!level().isClientSide) {
            this.level().explode(this, this.x, this.y, this.z, 1.0f, false, Level.ExplosionInteraction.NONE)
        }
        this.discard()
    }
}
