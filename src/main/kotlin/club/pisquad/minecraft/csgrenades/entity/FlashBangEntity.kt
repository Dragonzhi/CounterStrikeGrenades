package club.pisquad.minecraft.csgrenades.entity

import club.pisquad.minecraft.csgrenades.enums.GrenadeType
import club.pisquad.minecraft.csgrenades.network.CsGrenadePacketHandler
import club.pisquad.minecraft.csgrenades.network.message.AffectedPlayerInfo
import club.pisquad.minecraft.csgrenades.network.message.FlashBangExplodedMessage
import club.pisquad.minecraft.csgrenades.network.message.FlashbangEffectData
import club.pisquad.minecraft.csgrenades.registery.ModDamageType
import club.pisquad.minecraft.csgrenades.registery.ModItems
import net.minecraft.core.registries.Registries
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.projectile.ThrowableItemProjectile
import net.minecraft.world.item.Item
import net.minecraft.world.level.Level
import net.minecraftforge.network.PacketDistributor

class FlashBangEntity(pEntityType: EntityType<out ThrowableItemProjectile>, pLevel: Level) :
    CounterStrikeGrenadeEntity(pEntityType, pLevel, GrenadeType.FLASH_BANG) {
//    private val logger: Logger = LogManager.getLogger(CounterStrikeGrenades.ID + ":flashbang_entity")

    override fun getDefaultItem(): Item {
        return ModItems.FLASH_BANG_ITEM.get()
    }

    override fun tick() {
        super.tick()

        // FlashBang exploded after 1.6 second in CSGO, this logic only for server side
        if (!this.level().isClientSide) {
            if (this.tickCount > 1.6 * 20) {
                CsGrenadePacketHandler.INSTANCE.send(
                    PacketDistributor.ALL.noArg(),
                    FlashBangExplodedMessage(this.position(), calculateAffectedPlayers())
                )
                this.entityData.set(isExplodedAccessor, true)
                this.kill()
            }
        }
    }

    override fun getHitDamageSource(): DamageSource {
        val registryAccess = this.level().registryAccess()
        return DamageSource(
            registryAccess.lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(ModDamageType.FLASHBANG_HIT),
            this
        )
    }

    private fun calculateAffectedPlayers(): List<AffectedPlayerInfo> {
        val level = this.level() as ServerLevel
        return level.getPlayers { it.distanceToSqr(this.position()) < 640 }.map {
            AffectedPlayerInfo(it.uuid, FlashbangEffectData.create(this.position(), it))
        }.filter { it.effectData.effectSustain > 0 }

    }
}