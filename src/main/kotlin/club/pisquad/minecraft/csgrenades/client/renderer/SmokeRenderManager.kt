package club.pisquad.minecraft.csgrenades.client.renderer

import club.pisquad.minecraft.csgrenades.CounterStrikeGrenades
import club.pisquad.minecraft.csgrenades.config.ModConfig
import club.pisquad.minecraft.csgrenades.entity.SmokeGrenadeEntity
import club.pisquad.minecraft.csgrenades.millToTick
import club.pisquad.minecraft.csgrenades.particle.SmokeGrenadeParticle
import club.pisquad.minecraft.csgrenades.registry.ModParticles
import club.pisquad.minecraft.csgrenades.toVec3
import club.pisquad.minecraft.csgrenades.toVec3i
import net.minecraft.client.particle.ParticleEngine
import net.minecraft.core.BlockPos
import net.minecraft.util.RandomSource
import net.minecraft.world.phys.Vec3
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.event.TickEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod

@Mod.EventBusSubscriber(modid = CounterStrikeGrenades.ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = [Dist.CLIENT])
object SmokeRenderManager {
    private var renderers: MutableList<SmokeRenderer> = mutableListOf()

    @SubscribeEvent
    fun tick(event: TickEvent.ClientTickEvent) {
        if (event.phase == TickEvent.Phase.END) return
        renderers.forEach {
            it.update()
        }
        val shouldRemove: MutableList<SmokeRenderer> = mutableListOf()
        renderers.forEach {
            if (it.done) {
                shouldRemove.add(it)
            }
        }
        shouldRemove.forEach {
            renderers.remove(it)
        }
    }

    fun render(
        particleEngine: ParticleEngine,
        position: Vec3,
        smokeEntity: SmokeGrenadeEntity,
    ) {
        renderers.add(
            SmokeRenderer(
                particleEngine,
                position,
                smokeEntity,
            ),
        )
    }
}

class SmokeRenderer(
    private val particleEngine: ParticleEngine,
    private val center: Vec3,
    private val smokeEntity: SmokeGrenadeEntity,
) {
    var done: Boolean = false
    private var tickCount = 0
    private val randomSource = RandomSource.createNewThreadLocalInstance()

    private val blockPerTickDistanceRatio = 0.5
    private val particlePerBlock = 5
    private var rendered = false

    //        SMOKE_GRENADE_PARTICLE_COUNT.div(SMOKE_GRENADE_TOTAL_GENERATION_TIME.times(20)).toInt()
    private var renderBlockPos: MutableList<BlockPos> = mutableListOf()

    fun update() {
        tickCount++
//        In case there is an error, we will force quite the renderer
        if (tickCount > ModConfig.SmokeGrenade.SMOKE_LIFETIME.get().millToTick()) {
            this.done = true
            return
        }
//        We will wait for the spreadBlock property is calculated and synced from the server

        if (!this.rendered) {
            this.renderBlockPos = this.smokeEntity.getSpreadBlocks().toMutableList()
            if (this.renderBlockPos.isNotEmpty()) {
                this.renderBlockPos.sortBy { it.distSqr(center.toVec3i()) }
                this.rendered = true
            } else {
                return
            }
        }
        // Sometime there will be an OutOfBound error, this is a safety treatment
        if (this.renderBlockPos.isEmpty()) {
            this.done = true
            return
        }
        // unify generation rate should be ok>?
        val blockPerTick =
            (this.renderBlockPos[0].distSqr(center.toVec3i()) * (1 + blockPerTickDistanceRatio)).toInt() + 1
        repeat(blockPerTick) { blockCount ->
            if (this.renderBlockPos.isEmpty()) {
                this.done = true
                return
            }
            val position = this.renderBlockPos[0].toVec3()
            repeat(particlePerBlock) { particleCount ->
                val shuffledPosition = position.offsetRandom(this.randomSource, 1f)
                val particle = particleEngine.createParticle(
                    ModParticles.SMOKE_PARTICLE.get(),
                    shuffledPosition.x,
                    shuffledPosition.y,
                    shuffledPosition.z,
                    0.0,
                    0.0,
                    0.0,
                )
                if (particle != null) {
                    this.smokeEntity.registerParticle(particle as SmokeGrenadeParticle)
                }
            }
            this.renderBlockPos.removeAt(0)
        }
    }
}
