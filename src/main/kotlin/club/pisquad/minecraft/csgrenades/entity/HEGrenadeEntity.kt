package club.pisquad.minecraft.csgrenades.entity

import club.pisquad.minecraft.csgrenades.client.renderer.HEGrenadeExplosionData
import club.pisquad.minecraft.csgrenades.client.renderer.HEGrenadeRenderManager
import club.pisquad.minecraft.csgrenades.config.ModConfig
import club.pisquad.minecraft.csgrenades.enums.GrenadeType
import club.pisquad.minecraft.csgrenades.getTimeFromTickCount
import club.pisquad.minecraft.csgrenades.registery.ModDamageType
import club.pisquad.minecraft.csgrenades.registery.ModItems
import club.pisquad.minecraft.csgrenades.registery.ModSoundEvents
import net.minecraft.core.registries.Registries
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.projectile.ThrowableItemProjectile
import net.minecraft.world.item.Item
import net.minecraft.world.level.Level
import net.minecraft.world.phys.AABB

class HEGrenadeEntity(pEntityType: EntityType<out ThrowableItemProjectile>, pLevel: Level) :
    CounterStrikeGrenadeEntity(pEntityType, pLevel, GrenadeType.FLASH_BANG) {

    init {
        this.hitBlockSound = ModSoundEvents.HEGRENADE_BOUNCE.get()
    }

    override fun getDefaultItem(): Item {
        return ModItems.HEGRENADE_ITEM.get()
    }

    override fun tick() {
        super.tick()

        if (getTimeFromTickCount(this.tickCount.toDouble()) > 2.5 && !this.entityData.get(isExplodedAccessor)) {
            if (this.level() is ServerLevel) {
                this.doDamage()
            } else {
                HEGrenadeRenderManager.render(HEGrenadeExplosionData(this.position()))
                this.blowUpNearbySmokeGrenade()
            }
            this.entityData.set(isExplodedAccessor, true)
        }
        if (getTimeFromTickCount(this.tickCount.toDouble()) > 5) {
            if (this.level() is ServerLevel) {
                this.kill()
            }
        }
    }

    private fun doDamage() {
        val level = this.level() as ServerLevel
        val registryAccess = this.level().registryAccess()
        val damageRange = ModConfig.HEGrenade.DAMAGE_RANGE.get()
        val damageSource = DamageSource(
            registryAccess.lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(ModDamageType.HEGRENADE_EXPLOSION),
            this.owner
        )
        val entities =
            level.getEntitiesOfClass(
                if (ModConfig.DAMAGE_NON_PLAYER_ENTITY.get()) LivingEntity::class.java else Player::class.java,
                AABB(this.blockPosition()).inflate(ModConfig.HEGrenade.DAMAGE_RANGE.get())
            )
        for (entity in entities) {
            val distance = entity.distanceTo(this).toDouble()

            if (distance < damageRange) {
                val playerMovement = entity.deltaMovement
                entity.hurt(damageSource, calculateHEGrenadeDamage(distance, 0.0).toFloat())
                entity.deltaMovement = playerMovement
            }
        }
    }

    override fun getHitDamageSource(): DamageSource {
        val registryAccess = this.level().registryAccess()
        return DamageSource(
            registryAccess.lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(ModDamageType.HEGRENADE_HIT),
            this
        )
    }

    private fun blowUpNearbySmokeGrenade() {
        val smokeRadius = ModConfig.SmokeGrenade.SMOKE_RADIUS.get()
        val heDamageRange = ModConfig.HEGrenade.DAMAGE_RANGE.get()
        val smokeFallingHeight = ModConfig.SmokeGrenade.SMOKE_MAX_FALLING_HEIGHT.get()
        this.level().getEntitiesOfClass(
            SmokeGrenadeEntity::class.java,
            this.boundingBox.inflate(heDamageRange + smokeRadius)
                .inflate(0.0, smokeFallingHeight.toDouble(), 0.0)
        ).forEach {
            it.clearSmokeWithinRange(this.position(), heDamageRange + 2.5)
        }
    }
}

private fun calculateHEGrenadeDamage(distance: Double, armorReduction: Double): Double {
    val baseDamage = ModConfig.HEGrenade.BASE_DAMAGE.get()
    val damageRange = ModConfig.HEGrenade.DAMAGE_RANGE.get()
    return baseDamage.times(1.0.minus(distance.div(damageRange)))
        .times(1.0.minus(armorReduction))
}