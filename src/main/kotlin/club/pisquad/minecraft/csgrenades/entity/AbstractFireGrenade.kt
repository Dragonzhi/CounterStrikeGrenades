package club.pisquad.minecraft.csgrenades.entity

import club.pisquad.minecraft.csgrenades.*
import club.pisquad.minecraft.csgrenades.enums.GrenadeType
import club.pisquad.minecraft.csgrenades.network.CsGrenadePacketHandler
import club.pisquad.minecraft.csgrenades.network.message.FireGrenadeMessage
import club.pisquad.minecraft.csgrenades.registery.ModSoundEvents
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.projectile.ThrowableItemProjectile
import net.minecraft.world.level.Level
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import net.minecraftforge.network.PacketDistributor

abstract class AbstractFireGrenade(
    pEntityType: EntityType<out ThrowableItemProjectile>,
    pLevel: Level,
    grenadeType: GrenadeType,
) :
    CounterStrikeGrenadeEntity(pEntityType, pLevel, grenadeType) {

    var explosionTick = 0
    var extinguished = false
    var poppedInAir = false

    init {
        hitBlockSound = ModSoundEvents.INCENDIARY_BOUNCE.get()
        throwSound = ModSoundEvents.INCENDIARY_THROW.get()
    }

    override fun tick() {
        super.tick()
        if (this.level() is ClientLevel) return
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
                val size = this.level()
                    .getEntitiesOfClass(SmokeGrenadeEntity::class.java, getFireExtinguishRange(this.position())) {
                        this.position().distanceTo(it.position()) < FIRE_EXTINGUISH_RANGE
                    }.size
                if (size > 0) {
                    this.extinguished = true
                }
                CsGrenadePacketHandler.INSTANCE.send(
                    PacketDistributor.ALL.noArg(),
                    FireGrenadeMessage(
                        FireGrenadeMessage.MessageType.GroundExploded, this.position()
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
        for (player in level.players()) {
            val distance = player.distanceTo(this).toDouble()
            if (distance < FIREGRENADE_RANGE && !isPositionInSmoke(
                    player.position(),
                    SMOKE_GRENADE_RADIUS.toDouble()
                )
            ) {
                val playerMovement = player.deltaMovement
                player.hurt(damageSource, 3f)
                player.deltaMovement = playerMovement
            }
        }
    }
}