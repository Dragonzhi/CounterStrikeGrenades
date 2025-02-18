package club.pisquad.minecraft.csgrenades.client.renderer

import club.pisquad.minecraft.csgrenades.*
import club.pisquad.minecraft.csgrenades.entity.AbstractFireGrenade
import net.minecraft.client.Minecraft
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.util.RandomSource
import net.minecraft.world.phys.Vec2
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.api.distmarker.OnlyIn


fun getLifetimeFromDistance(distance: Double): Int {
    val randomSource = RandomSource.createNewThreadLocalInstance()
    return (FIREGRENADE_RANGE - distance).div(FIREGRENADE_LIFETIME).times(INCENDIARY_PARTICLE_LIFETIME)
        .toInt() + randomSource.nextInt(0, 5)
}

@OnlyIn(Dist.CLIENT)
object FireGrenadeRenderer {
    private val randomSource = RandomSource.createNewThreadLocalInstance()
    fun renderOne(grenade: AbstractFireGrenade) {
        val particleEngine = Minecraft.getInstance().particleEngine
        val spreadBlocks = grenade.getSpreadBlocks()
        val particlePerTick =
            (FIREGRENADE_RANGE * FIREGRENADE_RANGE * INCENDIARY_PARTICLE_DENSITY) * spreadBlocks.size / (FIREGRENADE_RANGE * FIREGRENADE_RANGE)

        for (i in 0 until particlePerTick) {
            val position = grenade.position()


            val pos = getRandomLocationFromBlockSurface(spreadBlocks.random().below())
            if (isPositionInSmoke(
                    grenade.level(),
                    position.add(0.0, 1.0, 0.0)
                )
            ) {
                continue
            }

            val distance = Vec2(
                position.x.minus(pos.x).toFloat(),
                position.z.minus(pos.z).toFloat()
            ).length().toDouble()

            val random = randomSource.nextDouble()
            val particleType = when {
                random < 0.2 -> ParticleTypes.SMOKE
                random < 0.5 -> ParticleTypes.FLAME
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