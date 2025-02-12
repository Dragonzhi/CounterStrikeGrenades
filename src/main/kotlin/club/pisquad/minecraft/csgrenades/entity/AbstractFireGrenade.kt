package club.pisquad.minecraft.csgrenades.entity

import club.pisquad.minecraft.csgrenades.*
import club.pisquad.minecraft.csgrenades.client.renderer.FireGrenadeRenderer
import club.pisquad.minecraft.csgrenades.enums.GrenadeType
import club.pisquad.minecraft.csgrenades.network.CsGrenadePacketHandler
import club.pisquad.minecraft.csgrenades.network.message.FireGrenadeMessage
import club.pisquad.minecraft.csgrenades.registery.ModSerializers
import club.pisquad.minecraft.csgrenades.registery.ModSoundEvents
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.network.syncher.EntityDataAccessor
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.projectile.ThrowableItemProjectile
import net.minecraft.world.level.Level
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import net.minecraftforge.network.PacketDistributor

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

    init {
        hitBlockSound = ModSoundEvents.INCENDIARY_BOUNCE.get()
        throwSound = ModSoundEvents.INCENDIARY_THROW.get()
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
        if (this.level() is ClientLevel) {
            if (!this.poppedInAir && this.entityData.get(isExplodedAccessor)) {
                FireGrenadeRenderer.renderOne(this)
            }
        } else {
            if (this.entityData.get(isExplodedAccessor)) {
                // Damage players within range
                this.doDamage()

                if (getTimeFromTickCount((this.tickCount - this.explosionTick).toDouble()) > FIREGRENADE_LIFETIME) {
                    this.kill()
                    return
                }
            }
            if (!this.entityData.get(isExplodedAccessor) && getTimeFromTickCount(this.tickCount.toDouble()) > FIREGRENADE_FUSE_TIME) {
                this.entityData.set(isExplodedAccessor, true)
                this.poppedInAir = true
                CsGrenadePacketHandler.INSTANCE.send(
                    PacketDistributor.ALL.noArg(),
                    FireGrenadeMessage(FireGrenadeMessage.MessageType.AirExploded, this.position())
                )
                this.kill()
            }
        }
    }

    override fun onHitBlock(result: BlockHitResult) {
        // fire type grenade Explodes when hit a walkable surface that is 30 degree or smaller from horizon.
        // But in MC, all grounds are flat and horizontal
        // we only want the server to handle this logic
        if (this.extinguished) return
        if (this.level() !is ClientLevel) {
            if (this.entityData.get(isExplodedAccessor) || this.entityData.get(isLandedAccessor)) return
            if (result.direction == Direction.UP) {
                this.entityData.set(isExplodedAccessor, true)
                this.entityData.set(isLandedAccessor, true)
                this.deltaMovement = Vec3.ZERO
                this.explosionTick = this.tickCount
                this.isNoGravity = true

                // Test if any smoke nearby that extinguish this fire
                val bb = AABB(this.blockPosition()).inflate(
                    SMOKE_GRENADE_RADIUS.toDouble(), SMOKE_GRENADE_FALLDOWN_HEIGHT.toDouble(),
                    SMOKE_GRENADE_RADIUS.toDouble()
                )
                if (this.level()
                        .getEntitiesOfClass(SmokeGrenadeEntity::class.java, bb) {
                            this.position().distanceTo(it.position()) < FIRE_EXTINGUISH_RANGE
                        }.any {
                            it.canDistinguishFire(this.position())
                        }
                ) {
                    this.extinguished = true
                }
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
                return
            }
        }
        super.onHitBlock(result)
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
        for (player in level.players()) {
            spreadBlocks.any {
                if (it == player.blockPosition().offset(0, -1, 0) && !isPositionInSmoke(
                        player.position(),
                    )
                ) {
                    val playerMovement = player.deltaMovement
                    player.hurt(damageSource, 3f)
                    player.deltaMovement = playerMovement
                    return@any true
                }
                return@any false

            }
        }
    }

    private fun calculateSpreadBlocks(level: Level, center: Vec3): List<BlockPos> {
        val blocksAround = getBlockPosAround2D(center.add(0.0, 1.0, 0.0), FIREGRENADE_RANGE)
        return blocksAround.mapNotNull { getGroundBelow(level, it) }
    }

    private fun getGroundBelow(level: Level, position: BlockPos): BlockPos? {
        var currentPos = position
        var emptySpaceAbove = false
        repeat(FIRE_MAX_SPREAD_DOWNWARD) {
            if (!level.getBlockState(currentPos).isAir) {
                return if (emptySpaceAbove) {
                    currentPos
                } else {
                    null
                }
            } else {
                emptySpaceAbove = true
            }
            currentPos = currentPos.below()
        }
        return null
    }

    fun getSpreadBlocks(): List<BlockPos> {
        if (spreadBlocks.isEmpty()) {
            this.spreadBlocks.addAll(this.entityData.get(spreadBlocksAccessor))
        }
        return this.spreadBlocks
    }
}