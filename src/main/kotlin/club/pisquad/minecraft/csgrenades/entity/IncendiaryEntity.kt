package club.pisquad.minecraft.csgrenades.entity

import club.pisquad.minecraft.csgrenades.*
import club.pisquad.minecraft.csgrenades.enums.GrenadeType
import club.pisquad.minecraft.csgrenades.network.CsGrenadePacketHandler
import club.pisquad.minecraft.csgrenades.network.message.IncendiaryExplodedMessage
import club.pisquad.minecraft.csgrenades.registery.ModItems
import club.pisquad.minecraft.csgrenades.registery.ModSoundEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.ai.targeting.TargetingConditions
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.projectile.ThrowableItemProjectile
import net.minecraft.world.item.Item
import net.minecraft.world.level.Level
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.network.PacketDistributor

class IncendiaryEntity(pEntityType: EntityType<out ThrowableItemProjectile>, pLevel: Level) :
    CounterStrikeGrenadeEntity(pEntityType, pLevel, GrenadeType.INCENDIARY) {

    var explosionTick = 0
    var extinguished = false
    var poppedInAir = false

    init {
        hitBlockSound = ModSoundEvents.HEGRENADE_BOUNCE.get()
        throwSound = ModSoundEvents.INCENDIARY_THROW.get()
    }

    override fun getDefaultItem(): Item {
        return ModItems.INCENDIARY_ITEM.get()
    }

    override fun tick() {
        super.tick()
        if (this.level() is ClientLevel) return
        if (this.entityData.get(isExplodedAccessor)) {
            // Damage players within range
            val level = this.level() as ServerLevel
            for (player in level.players()) {
                val distance = player.distanceTo(this).toDouble()
                if (distance < INCENDIARY_RANGE && !isPositionInSmoke(
                        BlockPos(player.position().toVec3i()),
                        SMOKE_GRENADE_RADIUS.toDouble()
                    )
                ) {
                    player.hurt(player.damageSources().generic(), 2.5f)
                }
            }

            if (getTimeFromTickCount((this.tickCount - this.explosionTick).toDouble()) > INCENDIARY_LIFETIME) {
                this.kill()
                return
            }
        }
        if (!this.entityData.get(isExplodedAccessor) && getTimeFromTickCount(this.tickCount.toDouble()) > INCENDIARY_FUSE_TIME) {
            this.entityData.set(isExplodedAccessor, true)
            this.poppedInAir = true
            sendExplodedMessage()
            this.kill()
        }
    }

    override fun onHitBlock(result: BlockHitResult) {
        // Incendiary Grenade Explodes when hit a walkable surface that is 30 degree or smaller from horizon.
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
                val size = this.level()
                    .getEntitiesOfClass(SmokeGrenadeEntity::class.java, getFireExtinguishRange(this.position())) {
                        this.position().distanceTo(it.position()) < FIRE_EXTINGUISH_RANGE
                    }.size
                if (size > 0) {
                    this.extinguished = true
                }
                sendExplodedMessage()
                return
            }
        }
        super.onHitBlock(result)


    }

    fun sendExplodedMessage() {
        CsGrenadePacketHandler.INSTANCE.send(
            PacketDistributor.ALL.noArg(),
            IncendiaryExplodedMessage(this.id, this.extinguished, poppedInAir, this.position())
        )
    }

    fun extinguish() {
        this.extinguished = true
        this.kill()
    }
}