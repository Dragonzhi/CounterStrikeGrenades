package club.pisquad.minecraft.csgrenades.entity

import club.pisquad.minecraft.csgrenades.*
import club.pisquad.minecraft.csgrenades.client.renderer.SmokeRenderManager
import club.pisquad.minecraft.csgrenades.enums.GrenadeType
import club.pisquad.minecraft.csgrenades.particle.SmokeGrenadeParticle
import club.pisquad.minecraft.csgrenades.registery.ModDamageType
import club.pisquad.minecraft.csgrenades.registery.ModItems
import club.pisquad.minecraft.csgrenades.registery.ModSerializers
import club.pisquad.minecraft.csgrenades.registery.ModSoundEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.resources.sounds.EntityBoundSoundInstance
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.Vec3i
import net.minecraft.core.registries.Registries
import net.minecraft.network.syncher.EntityDataAccessor
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundSource
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.projectile.ThrowableItemProjectile
import net.minecraft.world.item.Item
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.Level
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import java.time.Duration
import java.time.Instant

class SmokeGrenadeEntity(pEntityType: EntityType<out ThrowableItemProjectile>, pLevel: Level) :
    CounterStrikeGrenadeEntity(pEntityType, pLevel, GrenadeType.FLASH_BANG) {

    private var lastPos: Vec3i = Vec3i(0, 0, 0)
    private val particles = mutableMapOf<Vec3i, List<SmokeGrenadeParticle>>()
    private var explosionTime: Instant? = null
    private val spreadBlocksCache: MutableList<BlockPos> = mutableListOf()

    override fun getDefaultItem(): Item {
        return ModItems.SMOKE_GRENADE_ITEM.get()
    }

    companion object {
        val spreadBlocksAccessor: EntityDataAccessor<List<BlockPos>> = SynchedEntityData.defineId(
            SmokeGrenadeEntity::class.java,
            ModSerializers.blockPosListEntityDataSerializer
        )
    }

    override fun defineSynchedData() {
        super.defineSynchedData()
        this.entityData.define(SmokeGrenadeEntity.spreadBlocksAccessor, listOf())
    }

    fun registerParticle(particle: SmokeGrenadeParticle) {
        val pos = particle.pos.toVec3i()
        if (this.particles.containsKey(pos)) {
            this.particles[pos] = this.particles[pos]!!.plus(particle)
        } else {
            this.particles[pos] = listOf(particle)
        }
    }

    fun clearSmokeWithinRange(position: Vec3, range: Double) {
        val bb = AABB(BlockPos(position.toVec3i())).inflate(range)
        this.particles.filter { bb.contains(it.key.toVec3()) }.forEach {
            it.value.forEach { particle ->
                if (particle.pos.distanceTo(position) < range) {
                    particle.opacityTime = getRegenerationTime(particle.pos.distanceTo(position), range)
                }
            }
        }
    }

    private fun getRegenerationTime(distance: Double, radius: Double): Int {
        return linearInterpolate(
            0.0,
            SMOKE_GRENADE_TIME_BEFORE_REGENERATE * 20,
            distance / radius
        ).toInt() + linearInterpolate(
            SMOKE_GRENADE_REGENERATE_TIME * 20,
            0.0,
            distance / radius
        ).toInt()
    }

    override fun tick() {
        super.tick()
        if (this.entityData.get(isLandedAccessor)) {
            if (this.position() == Vec3(this.xOld, this.yOld, this.zOld)) {
                this.tickCount++
            } else {
                tickCount = 0
            }
            if (getTimeFromTickCount(this.tickCount.toDouble()) > SMOKE_FUSE_TIME_AFTER_LAND && this.explosionTime == null) {
                if (this.level() is ClientLevel) {
                    this.clientRenderEffect()
                } else {
                    this.entityData.set(spreadBlocksAccessor, calculateSpreadBlocks())
                }
                this.entityData.set(isExplodedAccessor, true)
                this.explosionTime = Instant.now()
            }
        }
        if (this.level() is ServerLevel) {
            if (this.entityData.get(isExplodedAccessor)) {
                if (this.explosionTime != null && Duration.between(
                        this.explosionTime,
                        Instant.now()
                    ) > Duration.ofSeconds(
                        SMOKE_GRENADE_SMOKE_LIFETIME.toLong()
                    )
                ) {
                    this.kill()
                }
                extinguishNearbyFires()
            }
        }
    }

    override fun onAddedToWorld() {
        super.onAddedToWorld()
        lastPos = this.position().toVec3i()
    }

    override fun onHitBlock(result: BlockHitResult) {
        // If the smoke hit the ground with in any incendiary's range, it will emit right away
        super.onHitBlock(result)
        if (result.direction == Direction.UP) {
            if (this.extinguishNearbyFires() > 0) {
                this.entityData.set(isLandedAccessor, true)
                if (this.level() is ServerLevel && result.isInside) {
                    this.setPos(Vec3(this.position().x, result.blockPos.y + 1.0, this.position().z))
                }
            }
        }
    }

    private fun extinguishNearbyFires(): Int {
        var extinguishedFires: List<AbstractFireGrenade> = listOf()
        if (this.entityData.get(isExplodedAccessor)) {
            val bb = AABB(this.blockPosition()).inflate(
                SMOKE_GRENADE_RADIUS.toDouble(),
                SMOKE_GRENADE_FALLDOWN_HEIGHT.toDouble(),
                SMOKE_GRENADE_RADIUS.toDouble()
            )

            extinguishedFires = this.level().getEntitiesOfClass(
                AbstractFireGrenade::class.java,
                bb
            ) {
                it.entityData.get(isExplodedAccessor) && canDistinguishFire(it.position())
            }
        } else {
            val bb = AABB(this.blockPosition()).inflate(FIREGRENADE_RANGE.toDouble())
            extinguishedFires = this.level().getEntitiesOfClass(
                AbstractFireGrenade::class.java,
                bb
            ) {
                it.entityData.get(isExplodedAccessor) && it.getSpreadBlocks()
                    .any { pos -> pos.center.distanceToSqr(this.position()) < 1 }
            }
        }

        if (this.level() is ServerLevel) {
            extinguishedFires.forEach {
                it.extinguish()
            }
        }
        return extinguishedFires.size
    }

    private fun clientRenderEffect() {
        val player = Minecraft.getInstance().player ?: return
        val distance = this.position().subtract(player.position()).length()


        // Sounds
        val soundManager = Minecraft.getInstance().soundManager
        val soundEvent =
            if (distance > 30) ModSoundEvents.SMOKE_EXPLODE_DISTANT.get() else ModSoundEvents.SMOKE_EMIT.get()
        val soundType =
            if (distance > 30) SoundTypes.SMOKE_GRENADE_EXPLODE_DISTANT else SoundTypes.SMOKE_GRENADE_EMIT

        val soundInstance = EntityBoundSoundInstance(
            soundEvent,
            SoundSource.AMBIENT,
            SoundUtils.getVolumeFromDistance(distance, soundType).toFloat(),
            1f,
            this,
            0
        )
        soundManager.play(soundInstance)

        // Particles
        SmokeRenderManager.render(
            Minecraft.getInstance().particleEngine,
            this.position(),
            this,
        )
    }

    private fun calculateSpreadBlocks(): List<BlockPos> {
        val initialSmoke = getBlocksAround3D(
            this.position(),
            SMOKE_GRENADE_RADIUS, SMOKE_GRENADE_RADIUS - 2, SMOKE_GRENADE_RADIUS
        ).filter { it.center.distanceToSqr(this.position()) < (SMOKE_GRENADE_RADIUS * SMOKE_GRENADE_RADIUS) + 1 }
            .filter { pos ->
                val context = ClipContext(
                    this.position(),
                    pos.center,
                    ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE,
                    null
                ); this.level().clip(context).type == HitResult.Type.MISS
            }
        val fallDownSmoke = mutableListOf<BlockPos>()
        initialSmoke.forEach {
            fallDownSmoke.addAll(
                getSpaceBelow(it)
            )
        }
        return (initialSmoke + fallDownSmoke).distinct()

    }

    private fun getSpaceBelow(position: BlockPos): List<BlockPos> {
        // Temporarily set the maximum fall down height to 10
        val result = mutableListOf<BlockPos>()
        var currentPos = position
        repeat(SMOKE_GRENADE_FALLDOWN_HEIGHT) {
            if (this.level().getBlockState(currentPos.below()).isAir
            ) {
                result.add(currentPos)
                currentPos = currentPos.below()
            } else {
                return result
            }
        }
        return result
    }

    override fun getHitDamageSource(): DamageSource {
        val registryAccess = this.level().registryAccess()
        return DamageSource(
            registryAccess.lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(ModDamageType.SMOKEGRENADE_HIT), this
        )
    }

    fun getSpreadBlocks(): List<BlockPos> {
        if (this.spreadBlocksCache.isEmpty()) {
            this.spreadBlocksCache.addAll(this.entityData.get(spreadBlocksAccessor))
        }
        return this.spreadBlocksCache
    }

    fun canDistinguishFire(position: Vec3): Boolean {
        return this.getSpreadBlocks().any { it.center.distanceToSqr(position) < 2.0 }
    }
}