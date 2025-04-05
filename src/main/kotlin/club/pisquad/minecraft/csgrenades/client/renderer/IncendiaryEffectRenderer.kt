package club.pisquad.minecraft.csgrenades.client.renderer

import club.pisquad.minecraft.csgrenades.*
import club.pisquad.minecraft.csgrenades.config.ModConfig
import club.pisquad.minecraft.csgrenades.entity.AbstractFireGrenade
import club.pisquad.minecraft.csgrenades.entity.IncendiaryEntity
import net.minecraft.client.Minecraft
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.util.RandomSource
import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.api.distmarker.OnlyIn


fun getLifetimeFromDistance(distance: Double): Int {
    return linearInterpolate(
        INCENDIARY_PARTICLE_LIFETIME.toDouble(),
        0.0,
        distance.div(ModConfig.FireGrenade.FIRE_RANGE.get())
    ).div(50).toInt()
}

@OnlyIn(Dist.CLIENT)
object FireGrenadeRenderer {
    private val randomSource = RandomSource.createNewThreadLocalInstance()

    fun renderOne(grenade: AbstractFireGrenade) {
        val particleEngine = Minecraft.getInstance().particleEngine
        val spreadBlocks = grenade.getSpreadBlocks()
        if (spreadBlocks.isEmpty()) {
            return
        }
        val fireRange = ModConfig.FireGrenade.FIRE_RANGE.get()
        val particlePerTick =
            (fireRange * fireRange * INCENDIARY_PARTICLE_DENSITY) * spreadBlocks.size / (fireRange * fireRange)

        for (i in 0 until particlePerTick) {
            val position = grenade.position()


            val pos = getRandomLocationFromBlockSurface(spreadBlocks.random())
            if (isPositionInSmoke(
                    grenade.level(),
                    pos.add(Vec3(0.0, 0.5, 0.0))
                )
            ) {
                continue
            }

            val distance = Vec2(
                position.x.minus(pos.x).toFloat(),
                position.z.minus(pos.z).toFloat()
            ).length().toDouble()

            val flameParticleType = when (grenade) {
                is IncendiaryEntity -> ParticleTypes.SOUL_FIRE_FLAME
                else -> ParticleTypes.FLAME
            }
            val random = randomSource.nextDouble()
            val particleType = when {
                random < 0.2 -> ParticleTypes.SMOKE
                random < 0.5 -> flameParticleType
                else -> ParticleTypes.SMALL_FLAME
            }
            particleEngine.createParticle(
                particleType,
                pos.x,
                pos.y,
                pos.z,
                0.0,
                0.1,
                0.0
            )?.lifetime = getLifetimeFromDistance(distance)
        }

    }
}