package club.pisquad.minecraft.csgrenades.entity

import club.pisquad.minecraft.csgrenades.SoundTypes
import club.pisquad.minecraft.csgrenades.SoundUtils
import club.pisquad.minecraft.csgrenades.config.ModConfig
import club.pisquad.minecraft.csgrenades.enums.GrenadeType
import club.pisquad.minecraft.csgrenades.registery.ModSoundEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.EntityBoundSoundInstance
import net.minecraft.core.Direction
import net.minecraft.network.syncher.EntityDataAccessor
import net.minecraft.network.syncher.EntityDataSerializers
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.sounds.SoundSource
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.projectile.ThrowableItemProjectile
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.BarrierBlock
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.EntityHitResult
import net.minecraft.world.phys.Vec3

abstract class CounterStrikeGrenadeEntity(
    pEntityType: EntityType<out ThrowableItemProjectile>,
    pLevel: Level,
    val grenadeType: GrenadeType
) :
    ThrowableItemProjectile(pEntityType, pLevel) {


    var hitBlockSound = ModSoundEvents.GRENADE_HIT.get()
    var throwSound = ModSoundEvents.GRENADE_THROW.get()

    companion object {
        val speedAccessor: EntityDataAccessor<Float> =
            SynchedEntityData.defineId(CounterStrikeGrenadeEntity::class.java, EntityDataSerializers.FLOAT)
        val isLandedAccessor: EntityDataAccessor<Boolean> =
            SynchedEntityData.defineId(CounterStrikeGrenadeEntity::class.java, EntityDataSerializers.BOOLEAN)
        val isExplodedAccessor: EntityDataAccessor<Boolean> =
            SynchedEntityData.defineId(CounterStrikeGrenadeEntity::class.java, EntityDataSerializers.BOOLEAN)
    }

    override fun defineSynchedData() {
        super.defineSynchedData()
        this.entityData.define(speedAccessor, 0f)
        this.entityData.define(isLandedAccessor, false)
        this.entityData.define(isExplodedAccessor, false)
    }


    override fun onHitEntity(result: EntityHitResult) {
        result.entity.hurt(this.getHitDamageSource(), 1f)
    }

    override fun tick() {
        super.tick()
        if (this.entityData.get(isLandedAccessor) || this.entityData.get(isExplodedAccessor)) {
            this.deltaMovement = Vec3.ZERO
            this.isNoGravity = true
        }
    }

    /**
     * Called when the entity is added to the world.
     *
     * This function plays the sound effect of a grenade being thrown when the entity is added to the world.
     *
     * @return None
     */
    override fun onAddedToWorld() {
        this.playSound(this.throwSound, 0.2f, 1f)
    }

    /**
     * Handles the bounce logic for custom grenades when they hit a block.
     *
     * This function updates the entity's movement and position based on the block hit result,
     * simulating a bouncing effect. It also plays a sound effect when the entity hits a block on the client side.
     *
     * @param result The block hit result.
     */
    override fun onHitBlock(result: BlockHitResult) {
        if (ModConfig.IGNORE_BARRIER_BLOCK.get() && this.level()
                .getBlockState(result.blockPos).block is BarrierBlock
        ) {
            return
        }

        this.setPos(this.xOld, this.yOld, this.zOld)

        // entity.playSound seems to have a relatively small hearable ange
        // This function seems to be work fine when calling from server and client side?
        // So I just make a test here
        // (In integrated server, haven't tested on other configurations yet)
        if (this.level().isClientSide && !this.entityData.get(isExplodedAccessor) && !this.entityData.get(
                isLandedAccessor
            )
        ) {
            val player = Minecraft.getInstance().player!!
            val distance = this.position().add(player.position().reverse()).length()
            val soundInstance = EntityBoundSoundInstance(
                hitBlockSound,
                SoundSource.AMBIENT,
                SoundUtils.getVolumeFromDistance(
                    distance,
                    SoundTypes.GRENADE_HIT // unify volume for all grenades hit sounds
                ).toFloat(),
                1f,
                this,
                0
            )
            Minecraft.getInstance().soundManager.play(soundInstance)
        }

        // Calculate the movement of the entity
        if (this.entityData.get(isLandedAccessor) || this.entityData.get(isExplodedAccessor)) {
            return

        } else {
            this.deltaMovement = when (result.direction) {
                Direction.UP, Direction.DOWN -> Vec3(deltaMovement.x, -deltaMovement.y, deltaMovement.z)

                Direction.WEST, Direction.EAST ->
                    Vec3(-deltaMovement.x, deltaMovement.y, deltaMovement.z)

                Direction.NORTH, Direction.SOUTH ->
                    Vec3(deltaMovement.x, deltaMovement.y, -deltaMovement.z)

                null -> deltaMovement

            }
//                if (result.isInside) {
            this.setPos(this.xOld, this.yOld, this.zOld)
//                }

            this.deltaMovement = this.deltaMovement.scale(0.5)
        }
        // fix: the entity will keep bouncing on the ground
        if (result.direction == Direction.UP && this.deltaMovement.length() < 0.05) {
            this.setPos(this.x, result.blockPos.y.toDouble() + 1, this.z)
            this.deltaMovement = Vec3.ZERO
            this.entityData.set(isLandedAccessor, true)
        }
    }

    override fun isOnFire(): Boolean {
        return false
    }

    override fun shouldBeSaved(): Boolean {
        return false
    }

    abstract fun getHitDamageSource(): DamageSource

}