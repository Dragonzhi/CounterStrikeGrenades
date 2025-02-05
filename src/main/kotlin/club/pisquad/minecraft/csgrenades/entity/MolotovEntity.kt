package club.pisquad.minecraft.csgrenades.entity

import club.pisquad.minecraft.csgrenades.enums.GrenadeType
import club.pisquad.minecraft.csgrenades.registery.ModDamageType
import club.pisquad.minecraft.csgrenades.registery.ModItems
import net.minecraft.core.registries.Registries
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.projectile.ThrowableItemProjectile
import net.minecraft.world.item.Item
import net.minecraft.world.level.Level

class MolotovEntity(pEntityType: EntityType<out ThrowableItemProjectile>, pLevel: Level) :
    AbstractFireGrenade(pEntityType, pLevel, GrenadeType.MOLOTOV) {
    override fun getDefaultItem(): Item {
        return ModItems.MOLOTOV_ITEM.get()
    }

    override fun getDamageSource(): DamageSource {
        val registryAccess = this.level().registryAccess()
        return DamageSource(
            registryAccess.lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(ModDamageType.MOLOTOV_FIRE_DAMAGE),
            this.owner
        )
    }
}