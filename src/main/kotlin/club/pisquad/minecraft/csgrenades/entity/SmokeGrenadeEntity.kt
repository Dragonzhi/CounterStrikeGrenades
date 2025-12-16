package club.pisquad.minecraft.csgrenades.entity

import club.pisquad.minecraft.csgrenades.*
import club.pisquad.minecraft.csgrenades.client.renderer.SmokeRenderManager
import club.pisquad.minecraft.csgrenades.config.ModConfig
import club.pisquad.minecraft.csgrenades.enums.GrenadeType
import club.pisquad.minecraft.csgrenades.particle.SmokeGrenadeParticle
import club.pisquad.minecraft.csgrenades.registery.ModDamageType
import club.pisquad.minecraft.csgrenades.registery.ModItems
import club.pisquad.minecraft.csgrenades.registery.ModSerializers
import club.pisquad.minecraft.csgrenades.registery.ModSoundEvents
import kotlinx.serialization.Serializable
import net.minecraft.client.Minecraft
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
import net.minecraft.world.level.Level
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import java.time.Duration
import java.time.Instant
import kotlin.random.Random

class SmokeGrenadeEntity(pEntityType: EntityType<out ThrowableItemProjectile>, pLevel: Level) :
    CounterStrikeGrenadeEntity(pEntityType, pLevel, GrenadeType.FLASH_BANG) {

    private var lastPos: Vec3i = Vec3i(0, 0, 0)
    private val particles = mutableMapOf<Vec3i, List<SmokeGrenadeParticle>>()
    private var explosionTime: Instant? = null
    private val spreadBlocksCache: MutableList<@Serializable BlockPos> = mutableListOf()

    override fun getDefaultItem(): Item {
        return ModItems.SMOKE_GRENADE_ITEM.get()
    }

    companion object {
        val spreadBlocksAccessor: EntityDataAccessor<List<@Serializable BlockPos>> = SynchedEntityData.defineId(
            SmokeGrenadeEntity::class.java,
            ModSerializers.blockPosListEntityDataSerializer
        )
    }

    override fun defineSynchedData() {
        super.defineSynchedData()
        this.entityData.define(spreadBlocksAccessor, listOf())
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
        return ModConfig.SmokeGrenade.TIME_BEFORE_REGENERATE.get().millToTick().toInt() + linearInterpolate(
            ModConfig.SmokeGrenade.REGENERATION_TIME.get().millToTick().toDouble(),
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
            if (this.tickCount > ModConfig.SmokeGrenade.FUSE_TIME_AFTER_LANDING.get().millToTick()
                && this.explosionTime == null
            ) {
                if (this.level().isClientSide) {
                    this.clientRenderEffect()
                } else {
                    this.entityData.set(spreadBlocksAccessor, calculateSpreadBlocks())
                    this.setItem(net.minecraft.world.item.ItemStack.EMPTY)
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
                    ) > Duration.ofMillis(
                        ModConfig.SmokeGrenade.SMOKE_LIFETIME.get().toLong()
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
        val extinguishedFires: List<AbstractFireGrenade>
        val smokeRadius = ModConfig.SmokeGrenade.SMOKE_RADIUS.get()
        val smokeFallingHeight = ModConfig.SmokeGrenade.SMOKE_MAX_FALLING_HEIGHT.get()
        if (this.entityData.get(isExplodedAccessor)) {
            val bb = AABB(this.blockPosition()).inflate(
                smokeRadius.toDouble(),
                smokeFallingHeight.toDouble(),
                smokeRadius.toDouble()
            )

            extinguishedFires = this.level().getEntitiesOfClass(
                AbstractFireGrenade::class.java,
                bb
            ) {
                it.entityData.get(isExplodedAccessor) && canDistinguishFire(it.position())
            }
        } else {
            val bb = AABB(this.blockPosition()).inflate(ModConfig.FireGrenade.FIRE_RANGE.get().toDouble())
            extinguishedFires = this.level().getEntitiesOfClass(
                AbstractFireGrenade::class.java,
                bb
            ) {
                it.entityData.get(isExplodedAccessor) && it.getSpreadBlocks()
                    .any { pos -> pos.above().center.distanceToSqr(this.position()) < 2 }
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
        val initialSmoke = SmokeGrenadeSpreadBlockCalculator(
            5, 1500, 2, this.blockPosition()
        ).calculate(this.level())
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
        repeat(ModConfig.SmokeGrenade.SMOKE_MAX_FALLING_HEIGHT.get()) {
            if (!this.level().getBlockState(currentPos.below()).canOcclude()
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

private class SmokeGrenadeSpreadBlockCalculator(
    val generateCycle: Int,
    val blockPerCycle: Int,
    val stepPerMove: Int,
    val origin: BlockPos,
) {

    fun calculate(level: Level): List<BlockPos> {
        val blocks = mutableListOf<BlockPos>()
        var generatedInLastCycle = mutableListOf<BlockPos>(origin)
        var generatedInCurrentCycle = mutableListOf<BlockPos>()
        repeat(generateCycle) {
            repeat(blockPerCycle) {
                generatedInLastCycle.random().let {
                    var currentLocation = it
                    repeat(stepPerMove) {
                        currentLocation = randomMoveOnce(level, currentLocation)
                    }
                    generatedInCurrentCycle.add(currentLocation)
                }
            }
            blocks.addAll(generatedInLastCycle)
            generatedInLastCycle = generatedInCurrentCycle
            generatedInCurrentCycle = mutableListOf()
        }
        return blocks.distinct()
    }

    private fun randomMoveOnce(level: Level, blockPos: BlockPos): BlockPos {
        val newLocation = when (randomDirection()) {
            Direction.UP -> blockPos.below()
            Direction.DOWN -> blockPos.above()
            Direction.NORTH -> blockPos.north()
            Direction.SOUTH -> blockPos.south()
            Direction.WEST -> blockPos.west()
            Direction.EAST -> blockPos.east()
        }
        val blockState = level.getBlockState(newLocation)
        if (!blockState.canOcclude() && !blockState.isCollisionShapeFullBlock(level, newLocation)) {
            return newLocation
        }

        return blockPos
    }

    private fun randomDirection(): Direction {
        return when ((Random.nextInt(10))) {
            0 -> Direction.UP
            1, 5 -> Direction.DOWN
            2, 6 -> Direction.NORTH
            3, 7 -> Direction.SOUTH
            4, 8 -> Direction.WEST
            else -> Direction.EAST

        }
    }
}