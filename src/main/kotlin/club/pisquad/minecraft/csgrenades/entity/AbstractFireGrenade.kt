package club.pisquad.minecraft.csgrenades.entity

import club.pisquad.minecraft.csgrenades.*
import club.pisquad.minecraft.csgrenades.client.renderer.FireGrenadeRenderer
import club.pisquad.minecraft.csgrenades.config.ModConfig
import club.pisquad.minecraft.csgrenades.enums.GrenadeType
import club.pisquad.minecraft.csgrenades.network.CsGrenadePacketHandler
import club.pisquad.minecraft.csgrenades.network.message.FireGrenadeMessage
import club.pisquad.minecraft.csgrenades.registery.ModSerializers
import club.pisquad.minecraft.csgrenades.registery.ModSoundEvents
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.network.syncher.EntityDataAccessor
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.projectile.ThrowableItemProjectile
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.Level
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import net.minecraftforge.network.PacketDistributor
import java.time.Instant
import java.util.*
import kotlin.math.min

abstract class AbstractFireGrenade(
    pEntityType: EntityType<out ThrowableItemProjectile>,
    pLevel: Level,
    grenadeType: GrenadeType,
) :

    CounterStrikeGrenadeEntity(pEntityType, pLevel, grenadeType) {

    private var explosionTick = 0
    private var extinguished = false
    private var poppedInAir = false
    private var spreadBlocks: MutableList<BlockPos> = mutableListOf()
    private var entitiesLastInRange: MutableMap<UUID, Long> = mutableMapOf()
    private var lastInWater: Boolean = false

    init {
        hitBlockSound = ModSoundEvents.INCENDIARY_BOUNCE.get()
        throwSound = ModSoundEvents.INCENDIARY_THROW.get()
        this.lastInWater = isCurrentInWater()
    }

    companion object {
        val spreadBlocksAccessor: EntityDataAccessor<List<BlockPos>> = SynchedEntityData.defineId(
            AbstractFireGrenade::class.java,
            ModSerializers.blockPosListEntityDataSerializer
        )
    }

    override fun defineSynchedData() {
        super.defineSynchedData()
        this.entityData.define(spreadBlocksAccessor, listOf())
    }

    override fun tick() {
        super.tick()
        if (this.level().isClientSide) {
            if (!this.poppedInAir && this.entityData.get(isExplodedAccessor)) {
                FireGrenadeRenderer.renderOne(this)
            }
        } else {
            if (this.entityData.get(isExplodedAccessor)) {
                // [注释] 燃烧弹/瓶爆炸后，实体必须继续存在，以处理持续伤害(doDamage)并作为火焰粒子效果的锚点。
                // 它会在其生命周期(LIFETIME)结束后，由下面的逻辑移除。
                // 注意：这也导致了其实体模型会在火焰中心持续可见。所有常规的隐形方法(isInvisible, setItem)均尝试失败，
                // 这意味着该实体可能使用了非常规的渲染管线？
                // Damage players within range
                this.doDamage()

                if ((this.tickCount - this.explosionTick) > ModConfig.FireGrenade.LIFETIME.get().millToTick()
                ) {
                    this.kill()
                    return
                }
            }
            if (!this.entityData.get(isExplodedAccessor) && this.tickCount > ModConfig.FireGrenade.FUSE_TIME.get()
                    .div(50)
            ) {
                this.entityData.set(isExplodedAccessor, true)
                this.poppedInAir = true
                CsGrenadePacketHandler.INSTANCE.send(
                    PacketDistributor.ALL.noArg(),
                    FireGrenadeMessage(FireGrenadeMessage.MessageType.AirExploded, this.position())
                )
                this.kill()
            }
        }
        if (!this.lastInWater && this.deltaMovement.snapToAxis() == Direction.DOWN) {
            val nextPosition = this.position().add(this.deltaMovement)
            val nextBlockPos = BlockPos.containing(nextPosition)
            val isNextBlockPosInWater = !this.level().getBlockState(nextBlockPos).fluidState.isEmpty
            if (isNextBlockPosInWater) {
                this.onHitBlock(BlockHitResult(nextPosition, Direction.UP, nextBlockPos, false))
                this.deltaMovement = Vec3.ZERO
            }
        }
        this.lastInWater = this.isCurrentInWater()
    }

    override fun onHitBlock(result: BlockHitResult) {
        // fire type grenade Explodes when hit a walkable surface that is 30 degree or smaller from horizon.
        // But in MC, all grounds are flat and horizontal
        // we only want the server to handle this logic

        if (this.entityData.get(isExplodedAccessor) || this.entityData.get(isLandedAccessor)) return
        if (this.extinguished) return
        if (result.direction == Direction.UP) {
            this.deltaMovement = Vec3.ZERO
            this.explosionTick = this.tickCount
            this.isNoGravity = true
            if (!this.level().isClientSide) {
                this.entityData.set(isExplodedAccessor, true)
                this.entityData.set(isLandedAccessor, true)
                // Test if any smoke nearby that extinguish this fire
                val smokeRadius = ModConfig.SmokeGrenade.SMOKE_RADIUS.get().toDouble()
                val bb = AABB(this.blockPosition()).inflate(
                    smokeRadius, ModConfig.SmokeGrenade.SMOKE_MAX_FALLING_HEIGHT.get().toDouble(),
                    smokeRadius
                )
                val fireExtinguishRange = ModConfig.FireGrenade.FIRE_EXTINGUISH_RANGE.get().toDouble()
                if (this.level()
                        .getEntitiesOfClass(SmokeGrenadeEntity::class.java, bb) {
                            this.position().distanceTo(it.position()) < fireExtinguishRange
                        }.any {
                            it.canDistinguishFire(this.position())
                        }
                ) {
                    this.extinguished = true
                } else {
                    // Prevent fire grenade clipping inside a block
                    this.setPos(result.blockPos.center.add(Vec3(0.0, 0.5 + GRENADE_ENTITY_SIZE, 0.0)))

                    this.entityData.set(
                        spreadBlocksAccessor,
                        calculateSpreadBlocks(this.level(), this.position())
                    )
                    CsGrenadePacketHandler.INSTANCE.send(
                        PacketDistributor.ALL.noArg(),
                        FireGrenadeMessage(
                            FireGrenadeMessage.MessageType.GroundExploded, this.position(),
                        )
                    )
                }
            }
            return
        }
        super.onHitBlock(result)
    }

    private fun isCurrentInWater(): Boolean {
        return !this.level().getBlockState(this.blockPosition()).fluidState.isEmpty
    }

    fun extinguish() {
        this.extinguished = true
        CsGrenadePacketHandler.INSTANCE.send(
            PacketDistributor.ALL.noArg(),
            FireGrenadeMessage(FireGrenadeMessage.MessageType.ExtinguishedBySmoke, this.position())
        )
        this.kill()
    }

    abstract fun getDamageSource(): DamageSource


    private fun doDamage() {
        //Should only be run on the server
        val level = this.level() as ServerLevel
        val damageSource = this.getDamageSource()
        val spreadBlocks = this.entityData.get(spreadBlocksAccessor) ?: return

        val entities =
            level.getEntitiesOfClass(
                if (ModConfig.DAMAGE_NON_PLAYER_ENTITY.get()) LivingEntity::class.java else Player::class.java,
                AABB(this.blockPosition()).inflate(ModConfig.HEGrenade.DAMAGE_RANGE.get())
            )
        val entitiesInRange = entities.filter { entity ->
            spreadBlocks.any { blockPos ->
                blockPos.above().center.horizontalDistanceTo(entity.position()) < 1 &&
                        (entity.y < blockPos.y + 2.8 && entity.y > blockPos.y - 2.8)
                        && !isPositionInSmoke(
                    this.level(), entity.position(),
                )
            }
        }

        this.entitiesLastInRange =
            this.entitiesLastInRange.filter { dataPair -> entitiesInRange.any { entity -> dataPair.key == entity.uuid } }
                .toMutableMap()

        val timeNow = Instant.now().toEpochMilli()
        entitiesInRange.forEach { entity ->

            if (entity.invulnerableTime > 0) {
                return
            }

            var damage = ModConfig.FireGrenade.DAMAGE.get().toFloat()
            if (entity.uuid in this.entitiesLastInRange.keys) {
                damage = min(
                    damage,
                    linearInterpolate(
                        0.0, damage.toDouble(), (timeNow - this.entitiesLastInRange[entity.uuid]!!).div(
                            ModConfig.FireGrenade.DAMAGE_INCREASE_TIME.get().toDouble()
                        )
                    ).toFloat()
                )
            } else {
                damage = 0.1f
                this.entitiesLastInRange[entity.uuid] = timeNow
            }

            val originalKnockBackResistance =
                entity.getAttribute(Attributes.KNOCKBACK_RESISTANCE)?.baseValue ?: 0.0
            entity.getAttribute(Attributes.KNOCKBACK_RESISTANCE)?.baseValue = 1.0

            entity.hurt(damageSource, damage)
            entity.invulnerableTime = 10

            entity.getAttribute(Attributes.KNOCKBACK_RESISTANCE)?.baseValue = originalKnockBackResistance
        }
    }

    private fun calculateSpreadBlocks(level: Level, center: Vec3): List<BlockPos> {
//        val blocksAround = getBlockPosAround2D(center.add(0.0, 1.0, 0.0), ModConfig.FireGrenade.FIRE_RANGE.get())
//        return blocksAround.mapNotNull { getGroundBelow(level, it) }.filter { canPositionBeSpread(center, it) }
        return FireSpreadCalculator.calculate(level, BlockPos.containing(center).below())
    }

    fun getSpreadBlocks(): List<BlockPos> {
        if (spreadBlocks.isEmpty()) {
            this.spreadBlocks.addAll(this.entityData.get(spreadBlocksAccessor))
        }
        return this.spreadBlocks
    }
}

class SpreadPathData(
    val visited: MutableList<BlockPos>,
    private val center: BlockPos,
    private var currentPos: BlockPos
) {
    private var jumpCount: Int = 0
    private var lastMoveDirection: Direction? = null

    constructor(origin: BlockPos) : this(mutableListOf(origin), origin, origin)

    companion object {
        val directions = listOf(
            Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST
        )

        private fun getGroundBelow(level: Level, origin: BlockPos): Pair<BlockPos, Int>? {
            ClipContext(
                origin.center,
                origin.offset(0, -ModConfig.FireGrenade.FIRE_MAX_SPREAD_DOWNWARD.get(), 0).center,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.ANY,
                null
            ).let {
                val clipResult = level.clip(it)
                return if (clipResult.type == HitResult.Type.MISS) {
                    null
                } else {
                    Pair(clipResult.blockPos, origin.y - clipResult.blockPos.y)
                }
            }
        }
    }

    private fun getRandomDirection(): Direction {
        return if (this.lastMoveDirection != null) {
            directions.minus(lastMoveDirection).random()!!
        } else {
            directions.random()
        }
    }

    private fun tryMoveToDirection(level: Level, direction: Direction): Boolean {
        val horizontalShifted = this.currentPos.relative(direction)
        if (horizontalShifted.horizontalDistanceTo(center) > ModConfig.FireGrenade.FIRE_RANGE.get()) {
            return false
        }
        val groundCalculateResult = getGroundBelow(level, horizontalShifted.above()) ?: return false

        if (groundCalculateResult.second == 0) {
            if (level.getBlockState(
                    horizontalShifted.above().above()
                ).isAir && level.getBlockState(this.currentPos.above()).isAir && this.jumpCount < 3
            ) {
                jumpCount++
            } else {
                return false
            }
        }
        if (groundCalculateResult.first in this.visited) {
            return false
        }
        this.visited.add(groundCalculateResult.first)
        this.currentPos = groundCalculateResult.first
        return true
    }

    fun randomMoveOnce(level: Level) {
        for (i in 0..4) {
            val direction = this.getRandomDirection()
            if (!this.tryMoveToDirection(level, direction)) {
                continue
            } else {
                this.lastMoveDirection = direction
            }
        }
    }
}

private object FireSpreadCalculator {

    fun calculate(level: Level, origin: BlockPos): List<BlockPos> {
        val originBlockState = level.getBlockState(origin.above())
        if (!originBlockState.fluidState.isEmpty) {
            return listOf()
        }
        val result = mutableListOf<BlockPos>(origin)
        repeat(ModConfig.FireGrenade.FIRE_RANGE.get() * 3) {
            val pathData = SpreadPathData(origin)
            repeat(ModConfig.FireGrenade.FIRE_RANGE.get()) {
                pathData.randomMoveOnce(level)
            }
            result.addAll(pathData.visited)
        }
        return result.distinct().filter { it.horizontalDistanceTo(origin) < ModConfig.FireGrenade.FIRE_RANGE.get() }
    }
}

