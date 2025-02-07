package club.pisquad.minecraft.csgrenades.entity

import club.pisquad.minecraft.csgrenades.*
import club.pisquad.minecraft.csgrenades.enums.GrenadeType
import club.pisquad.minecraft.csgrenades.helper.SmokeRenderHelper
import club.pisquad.minecraft.csgrenades.registery.ModItems
import club.pisquad.minecraft.csgrenades.registery.ModSoundEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.resources.sounds.EntityBoundSoundInstance
import net.minecraft.core.Direction
import net.minecraft.core.Vec3i
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.projectile.ThrowableItemProjectile
import net.minecraft.world.item.Item
import net.minecraft.world.level.Level
import net.minecraft.world.phys.BlockHitResult

class SmokeGrenadeEntity(pEntityType: EntityType<out ThrowableItemProjectile>, pLevel: Level) :
    CounterStrikeGrenadeEntity(pEntityType, pLevel, GrenadeType.FLASH_BANG) {

    private var lastPos: Vec3i = Vec3i(0, 0, 0)
    private var tickCount: Int = 0
    private var localIsExploded = false

    override fun getDefaultItem(): Item {
        return ModItems.SMOKE_GRENADE_ITEM.get()
    }

    override fun tick() {
        super.tick()


        if (this.entityData.get(isLandedAccessor)) {
            val currentPos = this.position().toVec3i()
            if (currentPos == this.lastPos) {
                this.tickCount++
            } else {
                tickCount = 0
                this.lastPos = currentPos
            }
            if (getTimeFromTickCount(this.tickCount.toDouble()) > SMOKE_FUSE_TIME_AFTER_LAND && !localIsExploded) {
                if (this.level() is ClientLevel) {
                    this.clientRenderEffect()
                    localIsExploded = true
                }
                this.entityData.set(isExplodedAccessor, true)
            }
        }
        if (this.level() is ServerLevel) {
            if (this.entityData.get(isLandedAccessor)) {
                if (getTimeFromTickCount(tickCount.toDouble()) > SMOKE_GRENADE_SMOKE_LIFETIME) {
                    this.kill()
                }
                extinguishNearbyFires()
            }
        }
    }

    override fun onAddedToWorld() {
        super.onAddedToWorld()
        lastPos = this.position().toVec3i()
    }

    override fun onHitBlock(result: BlockHitResult) {
        // If the smoke hit the ground with in any incendiary's range, it will emit right away
        if (result.direction == Direction.UP) {
            if (extinguishNearbyFires() > 0) {
                this.entityData.set(isLandedAccessor, true)
            }
        }
        super.onHitBlock(result)
    }

    private fun extinguishNearbyFires(): Int {
        val bb = getFireExtinguishRange(this.position())

        val extinguishedFires = this.level().getEntitiesOfClass(
            IncendiaryEntity::class.java,
            bb
        ) { this.position().distanceTo(it.position()) < FIRE_EXTINGUISH_RANGE && it.entityData.get(isExplodedAccessor) }

        if (this.level() is ServerLevel) {
            extinguishedFires.forEach { it.extinguish() }
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
        SmokeRenderHelper.render(Minecraft.getInstance().particleEngine, this.position())
    }
}

