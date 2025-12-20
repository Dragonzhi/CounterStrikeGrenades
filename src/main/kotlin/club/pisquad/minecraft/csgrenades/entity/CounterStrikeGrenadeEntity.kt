package club.pisquad.minecraft.csgrenades.entity

import club.pisquad.minecraft.csgrenades.SoundTypes
import club.pisquad.minecraft.csgrenades.SoundUtils
import club.pisquad.minecraft.csgrenades.config.ModConfig
import club.pisquad.minecraft.csgrenades.enums.GrenadeType
import club.pisquad.minecraft.csgrenades.registery.ModSoundEvents
import club.pisquad.minecraft.csgrenades.snapToAxis
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.EntityBoundSoundInstance
import net.minecraft.core.Direction
import net.minecraft.network.syncher.EntityDataAccessor
import net.minecraft.network.syncher.EntityDataSerializers
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.sounds.SoundSource
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.boss.enderdragon.EndCrystal
import net.minecraft.world.entity.projectile.ThrowableItemProjectile
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.BarrierBlock
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.EntityHitResult
import net.minecraft.world.phys.Vec3
import java.util.*

abstract class CounterStrikeGrenadeEntity(
    pEntityType: EntityType<out ThrowableItemProjectile>,
    pLevel: Level,
    val grenadeType: GrenadeType
) :
    ThrowableItemProjectile(pEntityType, pLevel), GrenadeEntityInterface {


    var hitBlockSound = ModSoundEvents.GRENADE_HIT.get()
    var throwSound = ModSoundEvents.GRENADE_THROW.get()
    var lastHitEntity: UUID? = null

    // For client side rotation
    private var xRotSpeed: Float = 0f
    private var yRotSpeed: Float = 0f
    private var zRotSpeed: Float = 0f
    var zRot: Float = 0f
    var zRotO: Float = 0f


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

    override fun shoot(x: Double, y: Double, z: Double, pVelocity: Float, pInaccuracy: Float) {
        super.shoot(x, y, z, pVelocity, pInaccuracy)
        if (level().isClientSide) {
            randomizeRotation()
        }
    }

    override fun onHitEntity(result: EntityHitResult) {
        if (level().isClientSide) {
            randomizeRotation()
        }

        if (result.entity is EndCrystal) {
            result.entity.hurt(result.entity.damageSources().generic(), 1f)
        }


        if (result.entity is LivingEntity) {
            val entity = result.entity as LivingEntity

            val originalKnockBackResistance =
                entity.getAttribute(Attributes.KNOCKBACK_RESISTANCE)?.baseValue ?: 0.0

            entity.getAttribute(Attributes.KNOCKBACK_RESISTANCE)?.baseValue = 1.0
            entity.hurt(this.getHitDamageSource(entity), 1f)

            entity.getAttribute(Attributes.KNOCKBACK_RESISTANCE)?.baseValue = originalKnockBackResistance
        }
        if (this.lastHitEntity == null || this.lastHitEntity != result.entity.uuid) {
            val direction: Direction = this.deltaMovement.snapToAxis().opposite
            this.bounce(direction, 0.1f, 0.08f)
            this.lastHitEntity = result.entity.uuid
        }
    }

    override fun tick() {
        super.tick()
        if (this.entityData.get(isLandedAccessor) || this.entityData.get(isExplodedAccessor)) {
            this.deltaMovement = Vec3.ZERO
            this.isNoGravity = true
        }

        // Client-side rotation logic
        if (this.level().isClientSide) {
            val isLanded = this.entityData.get(isLandedAccessor)
            if (!isLanded && !this.entityData.get(isExplodedAccessor)) {
                // In air: keep rotating
                this.xRotO = this.xRot
                this.yRotO = this.yRot
                this.zRotO = this.zRot

                this.xRot = (this.xRot + xRotSpeed) % 360
                this.yRot = (this.yRot + yRotSpeed) % 360
                this.zRot = (this.zRot + zRotSpeed) % 360

                // Air resistance
                this.xRotSpeed *= 0.99f
                this.yRotSpeed *= 0.99f
                this.zRotSpeed *= 0.99f
            } else {
                // On ground: stop rotation
                this.xRotSpeed = 0f
                this.yRotSpeed = 0f
                this.zRotSpeed = 0f
            }
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
        if (level().isClientSide) {
            randomizeRotation()
        }

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
            this.bounce(
                result.direction,
                speedCoefficient = 0.7f,
                frictionFactor = 0.9f
            )
            this.setPos(this.xOld, this.yOld, this.zOld)

            this.deltaMovement = this.deltaMovement.scale(0.5)
        }
        // fix: the entity will keep bouncing on the ground
        if (result.direction == Direction.UP && this.deltaMovement.length() < 0.05) {
            this.setPos(this.x, result.blockPos.y.toDouble() + 1, this.z)
            this.deltaMovement = Vec3.ZERO
            this.entityData.set(isLandedAccessor, true)
        }
    }

    private fun randomizeRotation() {
        this.xRotSpeed = (random.nextFloat() - 0.5f) * 40
        this.yRotSpeed = (random.nextFloat() - 0.5f) * 40
        this.zRotSpeed = (random.nextFloat() - 0.5f) * 40
    }

    private fun bounce(direction: Direction, speedCoefficient: Float, frictionFactor: Float) {
        this.deltaMovement = when (direction) {
            Direction.UP, Direction.DOWN ->
                Vec3(
                    deltaMovement.x * frictionFactor,
                    -deltaMovement.y * speedCoefficient,
                    deltaMovement.z * frictionFactor
                )

            Direction.WEST, Direction.EAST ->
                Vec3(
                    -deltaMovement.x * speedCoefficient,
                    deltaMovement.y * frictionFactor,
                    deltaMovement.z * frictionFactor
                )

            Direction.NORTH, Direction.SOUTH ->
                Vec3(
                    deltaMovement.x * frictionFactor,
                    deltaMovement.y * frictionFactor,
                    -deltaMovement.z * speedCoefficient
                )
        }
    }

    override fun isOnFire(): Boolean {
        return false
    }

    override fun shouldBeSaved(): Boolean {
        return false
    }

    abstract fun getHitDamageSource(hitEntity: LivingEntity): DamageSource

}