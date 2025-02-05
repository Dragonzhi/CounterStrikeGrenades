package club.pisquad.minecraft.csgrenades.entity

import club.pisquad.minecraft.csgrenades.HEGRENADE_BASE_DAMAGE
import club.pisquad.minecraft.csgrenades.HEGRENADE_DAMAGE_RANGE
import club.pisquad.minecraft.csgrenades.enums.GrenadeType
import club.pisquad.minecraft.csgrenades.getTimeFromTickCount
import club.pisquad.minecraft.csgrenades.helper.HEGrenadeExplosionData
import club.pisquad.minecraft.csgrenades.helper.HEGrenadeRenderHelper
import club.pisquad.minecraft.csgrenades.registery.ModDamageType
import club.pisquad.minecraft.csgrenades.registery.ModItems
import club.pisquad.minecraft.csgrenades.registery.ModSoundEvents
import net.minecraft.core.registries.Registries
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.projectile.ThrowableItemProjectile
import net.minecraft.world.item.Item
import net.minecraft.world.level.Level

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
                HEGrenadeRenderHelper.render(HEGrenadeExplosionData(this.position()))
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
        val level = this.level()
        val registryAccess = this.level().registryAccess()
        val damageSource = DamageSource(
            registryAccess.lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(ModDamageType.HEGRENADE_EXPLOSION_DAMAGE),
            this.owner
        )
        for (player in level.players()) {
            val distance = player.distanceTo(this).toDouble()

            if (distance < HEGRENADE_DAMAGE_RANGE) {
                val playerMovement = player.deltaMovement
                player.hurt(damageSource, calculateHEGrenadeDamage(distance, 0.0).toFloat())
                player.deltaMovement = playerMovement
            }
        }
    }
}

private fun calculateHEGrenadeDamage(distance: Double, armorReduction: Double): Double {
    return HEGRENADE_BASE_DAMAGE.times(1.0.minus(distance.div(HEGRENADE_DAMAGE_RANGE)))
        .times(1.0.minus(armorReduction))
}