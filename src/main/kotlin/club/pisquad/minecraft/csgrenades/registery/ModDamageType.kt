package club.pisquad.minecraft.csgrenades.registery

import club.pisquad.minecraft.csgrenades.CounterStrikeGrenades
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.damagesource.DamageType

object ModDamageType {
    val HEGRENADE_EXPLOSION_DAMAGE: ResourceKey<DamageType> =
        ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation(CounterStrikeGrenades.ID, "hegrenade_explosion"))
    val INCENDIARY_FIRE_DAMAGE: ResourceKey<DamageType> =
        ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation(CounterStrikeGrenades.ID, "incendiary_fire"))
    val MOLOTOV_FIRE_DAMAGE: ResourceKey<DamageType> =
        ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation(CounterStrikeGrenades.ID, "molotov_fire"))
}