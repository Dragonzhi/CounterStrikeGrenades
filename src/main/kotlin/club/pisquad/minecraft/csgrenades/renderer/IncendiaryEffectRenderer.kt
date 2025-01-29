package club.pisquad.minecraft.csgrenades.renderer

import club.pisquad.minecraft.csgrenades.*
import club.pisquad.minecraft.csgrenades.entity.CounterStrikeGrenadeEntity.Companion.isExplodedAccessor
import club.pisquad.minecraft.csgrenades.entity.IncendiaryEntity
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.util.RandomSource
import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.api.distmarker.OnlyIn
import net.minecraftforge.event.TickEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod

@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = CounterStrikeGrenades.ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = [Dist.CLIENT])
object IncendiaryEffectRenderer {
    val randomSource = RandomSource.createNewThreadLocalInstance()
    val particlePerTick = (INCENDIARY_RANGE * INCENDIARY_RANGE * INCENDIARY_PARTICLE_DENSITY).toInt()

    @SubscribeEvent
    fun tick(event: TickEvent.RenderTickEvent) {
        if (event.phase == TickEvent.Phase.END) {
            // This may have some performance issues, but it depends on how Minecraft lookup entities
            val minecraft = Minecraft.getInstance()
            val player = minecraft.player ?: return
            val renderDistance = minecraft.options.renderDistance().get()
            val incendiaries = player.level()
                .getEntitiesOfClass(
                    IncendiaryEntity::class.java,
                    player.boundingBox.inflate(renderDistance.toDouble() * 16)
                ) { it.entityData.get(isExplodedAccessor) }
            for (incendiary in incendiaries) {
                renderOneIncendiary(incendiary.position())
            }
        }
    }

    private fun renderOneIncendiary(position: Vec3) {
        val particleEngine = Minecraft.getInstance().particleEngine

        for (i in 0 until particlePerTick) {
            val pos = getRandomLocationFromCircle(
                Vec2(position.x.toFloat(), position.z.toFloat()),
                INCENDIARY_RANGE
            )
            if (isPositionInSmoke(
                    BlockPos(pos.x.toInt(), position.y.toInt(), pos.y.toInt()),
                    SMOKE_GRENADE_RADIUS.toDouble()
                )
            ) {
                continue
            }

            val distance = Vec2(
                position.x.minus(pos.x).toFloat(),
                position.z.minus(pos.y).toFloat()
            ).length().toDouble()

            val random = randomSource.nextDouble()
            val particleType = when {
                random < 0.2 -> ParticleTypes.SMOKE
                random < 0.5 -> ParticleTypes.FLAME
                else -> ParticleTypes.SMALL_FLAME
            }


            particleEngine.createParticle(
                particleType,
                pos.x.toDouble(),
                position.y,
                pos.y.toDouble(),
                0.0,
                0.1,
                0.0
            )?.lifetime = getLifetimeFromDistance(distance)
        }

    }
}

fun getLifetimeFromDistance(distance: Double): Int {
    val randomSource = RandomSource.createNewThreadLocalInstance()
    return (INCENDIARY_RANGE - distance).div(INCENDIARY_LIFETIME).times(INCENDIARY_PARTICLE_LIFETIME)
        .toInt() + randomSource.nextInt(0, 5)
}