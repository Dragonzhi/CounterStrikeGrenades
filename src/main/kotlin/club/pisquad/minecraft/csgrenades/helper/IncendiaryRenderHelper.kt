package club.pisquad.minecraft.csgrenades.helper

import club.pisquad.minecraft.csgrenades.*
import club.pisquad.minecraft.csgrenades.network.message.IncendiaryExplodedMessage
import club.pisquad.minecraft.csgrenades.registery.ModSoundEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.EntityBoundSoundInstance
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.client.resources.sounds.SoundInstance
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.sounds.SoundSource
import net.minecraft.util.RandomSource
import net.minecraft.world.phys.Vec2
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.event.TickEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

@Mod.EventBusSubscriber(modid = CounterStrikeGrenades.ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = [Dist.CLIENT])
object IncendiaryRenderHelper {
    private val renderers: MutableList<IncendiaryRenderer> = mutableListOf()
    private val lock = ReentrantLock()

    fun render(data: IncendiaryExplodedMessage) {
        lock.withLock { renderers.add(IncendiaryRenderer(data)) }
    }

    @SubscribeEvent
    fun tick(event: TickEvent.ClientTickEvent) {
        if (event.phase == TickEvent.Phase.END) return

        val shouldRemove = mutableListOf<IncendiaryRenderer>()
        renderers.forEach { if (it.update()) shouldRemove.add(it) }
        shouldRemove.forEach { renderers.remove(it) }
    }

}

private class IncendiaryRenderer(
    val data: IncendiaryExplodedMessage
) {
    val soundInstance: SoundInstance
    val randomSource: RandomSource = RandomSource.createNewThreadLocalInstance()
    var tickCount = 0

    init {
        val player = Minecraft.getInstance().player!!

        soundInstance = EntityBoundSoundInstance(
            ModSoundEvents.INCENDIARY_EXPLODE.get(),
            SoundSource.AMBIENT,
            SoundUtils.getVolumeFromDistance(player.position().distanceTo(data.position), SoundTypes.INCENDIARY_EXPLODE)
                .toFloat(),
            1f,
            Minecraft.getInstance().level?.getEntity(data.entityId) ?: player, // don't know why im doing this
            0
        )
        // Sounds
        when {
            this.data.isInAir -> playAirPoppedSound()
            this.data.extinguished -> playExtinguishSound()
            else -> playExplosionSound()
        }
    }

    fun update(): Boolean {
        if (data.isInAir) {
            drawAirPoppedParticles()
            return true
        } else {
            if (getTimeFromTickCount(tickCount.toDouble()) > INCENDIARY_LIFETIME) {
                return true
            }

            // Check if the fire is extinguished by smoke grenade
            // Works fine now, but should use a separated message to indicate that the fire is extinguished
            // rather than detect the entity
            if (Minecraft.getInstance().level?.getEntity(data.entityId) == null) {
                playExtinguishSound()
                return true
            }
            drawGroundFireParticles()
        }

        tickCount++

        return false
    }

    private fun drawGroundFireParticles() {
        val particleEngine = Minecraft.getInstance().particleEngine
        val particleCount = (INCENDIARY_RANGE * INCENDIARY_RANGE * INCENDIARY_PARTICLE_DENSITY).toInt()

        for (i in 0 until particleCount) {
            val pos = getRandomLocationFromCircle(
                Vec2(data.position.x.toFloat(), data.position.z.toFloat()),
                INCENDIARY_RANGE
            )
            if (isPositionInSmoke(
                    BlockPos(pos.x.toInt(), data.position.y.toInt(), pos.y.toInt()),
                    SMOKE_GRENADE_RADIUS.toDouble()
                )
            ) {
                continue
            }

            val distance = Vec2(
                data.position.x.minus(pos.x).toFloat(),
                data.position.z.minus(pos.y).toFloat()
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
                data.position.y,
                pos.y.toDouble(),
                0.0,
                0.1,
                0.0
            )?.lifetime = getLifetimeFromDistance(distance)
        }
    }

    private fun drawAirPoppedParticles() {
        val particleEngine = Minecraft.getInstance().particleEngine
        val particleCount = 500
        val randomSource = RandomSource.createNewThreadLocalInstance()

        repeat(particleCount) {
            particleEngine.createParticle(
                ParticleTypes.FLAME,
                data.position.x,
                data.position.y,
                data.position.z,
                randomSource.nextDouble() - 0.5,
                randomSource.nextDouble() - 0.5,
                randomSource.nextDouble() - 0.5,
            )?.lifetime = 10
        }
    }

    private fun playExplosionSound() {
        val distance = data.position.distanceTo(Minecraft.getInstance().player!!.position())
        val randomSource = RandomSource.createNewThreadLocalInstance()
        val extinguishSoundInstance = SimpleSoundInstance(
            ModSoundEvents.INCENDIARY_EXPLODE.get(),
            SoundSource.AMBIENT,
            SoundUtils.getVolumeFromDistance(distance, SoundTypes.INCENDIARY_EXPLODE).toFloat(),
            1f,
            randomSource,
            data.position.x,
            data.position.y,
            data.position.z
        )
        Minecraft.getInstance().soundManager.play(extinguishSoundInstance)
    }

    private fun playExtinguishSound() {
        val distance = data.position.distanceTo(Minecraft.getInstance().player!!.position())
        val randomSource = RandomSource.createNewThreadLocalInstance()
        val extinguishSoundInstance = SimpleSoundInstance(
            ModSoundEvents.INCENDIARY_POP.get(),
            SoundSource.AMBIENT,
            SoundUtils.getVolumeFromDistance(distance, SoundTypes.INCENDIARY_POP).toFloat(),
            0.8f,
            randomSource,
            data.position.x,
            data.position.y,
            data.position.z
        )
        Minecraft.getInstance().soundManager.play(extinguishSoundInstance)
    }

    private fun playAirPoppedSound() {
        val distance = data.position.distanceTo(Minecraft.getInstance().player!!.position())
        val randomSource = RandomSource.createNewThreadLocalInstance()
        val extinguishSoundInstance = SimpleSoundInstance(
            ModSoundEvents.INCENDIARY_EXPLODE_AIR.get(),
            SoundSource.AMBIENT,
            SoundUtils.getVolumeFromDistance(distance, SoundTypes.INCENDIARY_EXPLODE_AIR).toFloat(),
            1f,
            randomSource,
            data.position.x,
            data.position.y,
            data.position.z
        )
        Minecraft.getInstance().soundManager.play(extinguishSoundInstance)
    }
}

fun getLifetimeFromDistance(distance: Double): Int {
    val randomSource = RandomSource.createNewThreadLocalInstance()
    return (INCENDIARY_RANGE - distance).div(INCENDIARY_LIFETIME).times(INCENDIARY_PARTICLE_LIFETIME)
        .toInt() + randomSource.nextInt(0, 5)
}