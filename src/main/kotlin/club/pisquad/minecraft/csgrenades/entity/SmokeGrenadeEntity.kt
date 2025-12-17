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
import net.minecraftforge.fml.ModList
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

    fun clearSmokeWithinRange(position: Vec3, range: Double, printHeader: Boolean) {
        if (printHeader) {
            println("CS-GRENADES DEBUG: Clearing smoke at pos: $position with range: $range. Checking ALL particles.")
            val totalParticles = this.particles.values.sumOf { it.size }
            println("CS-GRENADES DEBUG: Total particles in map: $totalParticles")
            if (totalParticles == 0) {
                println("CS-GRENADES DEBUG: Cleared 0 particles because the map is empty.")
                return // Nothing to do
            }
        }

        var clearedCount = 0
        var checkedCount = 0 // To avoid spamming logs

        // Iterate over all lists of particles in the map
        this.particles.values.forEach { particleList ->
            // Iterate over each particle in the list
            particleList.forEach { particle ->
                // New Debugging: Print first few particle positions
                if (printHeader && checkedCount < 5) {
                    val dist = particle.pos.distanceTo(position)
                    println("CS-GRENADES DEBUG:   - Checking particle at ${particle.pos}, distance to bullet: $dist")
                }
                checkedCount++

                // Directly check the distance between the bullet and the particle
                if (particle.pos.distanceToSqr(position) < range * range) { // Use distanceToSqr for performance
                    particle.opacityTime = getRegenerationTime(particle.pos.distanceTo(position), range)
                    clearedCount++
                }
            }
        }

        if (clearedCount > 0) {
            println("CS-GRENADES DEBUG: Cleared $clearedCount particles at interpolated position.")
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
        if (this.level().isClientSide && this.entityData.get(isExplodedAccessor)) {
            this.disperseSmokeByProjectiles()
        }
    }

    private fun disperseSmokeByProjectiles() {
        val spreadBlocks = this.getSpreadBlocks()
        if (spreadBlocks.isEmpty()) {
            return
        }

        // --- Vanilla Arrow Logic ---
        var smokeBB = AABB(this.blockPosition()).inflate(10.0) // Use a larger BB to find arrows near the cloud
        val nearbyArrows = this.level().getEntitiesOfClass(
            net.minecraft.world.entity.projectile.AbstractArrow::class.java,
            smokeBB
        ) { arrow -> arrow.deltaMovement.lengthSqr() > 0.01 } // Only consider moving arrows

        nearbyArrows.forEach { arrow ->
            // Interpolate position to prevent tunneling
            val posNow = arrow.position()
            val delta = arrow.deltaMovement
            val posOld = posNow.subtract(delta)
            if (delta.lengthSqr() < 0.001) {
                this.clearSmokeWithinRange(posNow, ModConfig.SmokeGrenade.ARROW_CLEAR_RANGE.get(), true)
            } else {
                val steps = (delta.length() / 0.5).toInt().coerceAtLeast(1).coerceAtMost(10) // Check every 50cm
                for (i in 0..steps) {
                    val interpolatedPos = posOld.lerp(posNow, i.toDouble() / steps)
                    this.clearSmokeWithinRange(interpolatedPos, ModConfig.SmokeGrenade.ARROW_CLEAR_RANGE.get(), i == 0)
                }
            }
        }

        // --- TACZ BULLET COMPATIBILITY ---
        if (this.level().isClientSide && ModList.get().isLoaded("tacz")) {
            val clientLevel = this.level() as? net.minecraft.client.multiplayer.ClientLevel ?: return
            val allRenderEntities = clientLevel.entitiesForRendering()

            if (allRenderEntities.none()) {
                return
            }

            val smokeCenter = this.position()
            allRenderEntities.forEach { entity ->
                if (entity.position().distanceToSqr(smokeCenter) < 225) { // Pre-filter to check entities within 15 blocks
                    println("CS-GRENADES DEBUG: Nearby entity in radius: ${entity::class.java.name}") // Re-added for debugging new gun packs
                    if (entity::class.java.name == "com.tacz.guns.entity.EntityKineticBullet") {
                        println("CS-GRENADES DEBUG:     ^ FINAL MATCH! This is the bullet. Clearing smoke.")

                        val posNow = entity.position()

                        // --- Reflection for Dynamic Range ---
                        var finalClearRange = ModConfig.SmokeGrenade.BULLET_CLEAR_RANGE.get() // Default/fallback value
                        try {
                            // 1. Get the getDamage(Vec3) method from the bullet entity
                            val getDamageMethod = entity::class.java.getMethod("getDamage", Vec3::class.java)
                            // 2. Invoke it to get the damage value
                            val damage = getDamageMethod.invoke(entity, posNow) as Float
                            // 3. Calculate a dynamic range based on damage
                            // Formula: base_range + (damage/damage_divisor), clamped to a min/max
                            val calculatedRange = (1.0 + (damage / 15.0f)).coerceIn(0.5, 5.0)
                            finalClearRange = calculatedRange
                            println("CS-GRENADES DEBUG: Bullet Damage: $damage -> Calculated Range: $finalClearRange")
                        } catch (e: Exception) {
                            println("CS-GRENADES DEBUG: Could not get dynamic damage via reflection, falling back to config value. Reason: ${e.message}")
                        }
                        // --- End Reflection ---

                        // Interpolate position to prevent tunneling
                        val delta = entity.deltaMovement
                        val posOld = posNow.subtract(delta)

                        if (delta.lengthSqr() < 0.001) {
                            this.clearSmokeWithinRange(posNow, finalClearRange, true)
                        } else {
                            val steps = (delta.length() / 0.5).toInt().coerceAtLeast(1).coerceAtMost(10) // Check every 50cm
                            for (i in 0..steps) {
                                val interpolatedPos = posOld.lerp(posNow, i.toDouble() / steps)
                                this.clearSmokeWithinRange(interpolatedPos, finalClearRange, i == 0)
                            }
                        }
                    }
                }
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
        // Optimization: Use a Set to efficiently combine and find distinct blocks.
        return initialSmoke.union(fallDownSmoke).toList()
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

    // Optimization: Use Sets instead of Lists to avoid duplicates from the start.
    fun calculate(level: Level): Set<BlockPos> {
        val blocks = mutableSetOf<BlockPos>()
        var generatedInLastCycle = mutableSetOf<BlockPos>(origin)
        var generatedInCurrentCycle = mutableSetOf<BlockPos>()
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
            generatedInCurrentCycle = mutableSetOf()
        }
        return blocks
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