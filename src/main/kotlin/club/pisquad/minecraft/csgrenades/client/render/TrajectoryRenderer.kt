package club.pisquad.minecraft.csgrenades.client.render

import club.pisquad.minecraft.csgrenades.CounterStrikeGrenades
import club.pisquad.minecraft.csgrenades.client.input.ThrowActionHandler
import club.pisquad.minecraft.csgrenades.config.ModConfig
import club.pisquad.minecraft.csgrenades.item.CounterStrikeGrenadeItem
import club.pisquad.minecraft.csgrenades.linearInterpolate
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.GameRenderer
import net.minecraft.client.renderer.RenderType
import net.minecraft.core.Direction
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.client.event.RenderLevelStageEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod
import java.time.Duration
import java.time.Instant

@Mod.EventBusSubscriber(modid = CounterStrikeGrenades.ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = [Dist.CLIENT])
object TrajectoryRenderer {

    private const val SIMULATION_TICKS = 150
    private const val GRAVITY = 0.03f
    private const val REST_THRESHOLD_SQR = 0.0025 // (0.05)^2

    private const val BOUNCE_SPEED_COEFFICIENT = 0.7f
    private const val BOUNCE_FRICTION_FACTOR = 0.9f

    private fun bounce(velocity: Vec3, direction: Direction): Vec3 = when (direction) {
        Direction.UP, Direction.DOWN ->
            Vec3(
                velocity.x * BOUNCE_FRICTION_FACTOR,
                -velocity.y * BOUNCE_SPEED_COEFFICIENT,
                velocity.z * BOUNCE_FRICTION_FACTOR,
            )

        Direction.WEST, Direction.EAST ->
            Vec3(
                -velocity.x * BOUNCE_SPEED_COEFFICIENT,
                velocity.y * BOUNCE_FRICTION_FACTOR,
                velocity.z * BOUNCE_FRICTION_FACTOR,
            )

        Direction.NORTH, Direction.SOUTH ->
            Vec3(
                velocity.x * BOUNCE_FRICTION_FACTOR,
                velocity.y * BOUNCE_FRICTION_FACTOR,
                -velocity.z * BOUNCE_SPEED_COEFFICIENT,
            )
    }

    @SubscribeEvent
    fun onRenderLevelStage(event: RenderLevelStageEvent) {
        if (event.stage != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return
        }

        val mc = Minecraft.getInstance()
        val player = mc.player ?: return
        val itemInHand = player.mainHandItem
        val item = itemInHand.item

        if (item !is CounterStrikeGrenadeItem) return
        if (!mc.options.renderDebug) return

        val throwSpeed = ThrowActionHandler.currentThrowSpeed ?: return
        val chargeStart = ThrowActionHandler.chargeStartTime ?: return
        val chargeDuration = Duration.between(chargeStart, Instant.now()).toMillis()
        if (chargeDuration < 1000) return

        val poseStack = event.poseStack
        val camera = mc.gameRenderer.mainCamera
        val cameraPos = camera.position

        val speedFactor =
            (throwSpeed - ModConfig.THROW_SPEED_WEAK.get()) / (ModConfig.THROW_SPEED_STRONG.get() - ModConfig.THROW_SPEED_WEAK.get())
        val launchSpeed = linearInterpolate(
            ModConfig.THROW_SPEED_WEAK.get(),
            ModConfig.THROW_SPEED_STRONG.get(),
            speedFactor,
        )

        var velocity = player.lookAngle.normalize().scale(launchSpeed)
        var position = player.eyePosition

        val pathPoints = mutableListOf<Vec3>()
        pathPoints.add(position)

        for (i in 0 until SIMULATION_TICKS) {
            val tickStartPosition = position
            val nextPosition = tickStartPosition.add(velocity)

            val clipResult = mc.level?.clip(
                ClipContext(tickStartPosition, nextPosition, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player),
            ) as? BlockHitResult

            if (clipResult?.type != net.minecraft.world.phys.HitResult.Type.MISS) {
                // On hit, path visually ends at the wall.
                clipResult?.let { pathPoints.add(it.location) }

                // The entity's position is reset to the pre-collision position for the next tick's calculation.
                // Our `position` variable already holds this, so we don't update it to `clipResult.location`.
                position = tickStartPosition

                // Calculate bounce velocity
                var bounceVelocity = clipResult?.let { bounce(velocity, it.direction) }
                if (bounceVelocity != null) {
                    bounceVelocity = bounceVelocity.scale(0.5)
                }
                if (bounceVelocity != null) {
                    velocity = bounceVelocity
                }

                // Check if the grenade should come to rest on the ground
                if (clipResult != null) {
                    if (clipResult.direction == Direction.UP && velocity.lengthSqr() < REST_THRESHOLD_SQR) {
                        // Snap final point to the ground and stop.
                        pathPoints.removeLast() // remove the impact point
                        pathPoints.add(Vec3(clipResult.location.x, clipResult.location.y + 0.01, clipResult.location.z)) // Snap to ground
                        break
                    }
                }
                // Gravity is not applied on a bounce tick, and we loop with the new velocity from the old position.
                continue
            }

            // No hit, full movement
            position = nextPosition
            velocity = velocity.add(0.0, -GRAVITY.toDouble(), 0.0)
            pathPoints.add(position)
        }

        // --- Start Rendering ---
        poseStack.pushPose()
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z)

        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()
        RenderSystem.setShader(GameRenderer::getPositionColorShader)
        RenderSystem.disableDepthTest()
        RenderSystem.lineWidth(2.0f)

        val bufferSource = mc.renderBuffers().bufferSource()
        val vertexConsumer = bufferSource.getBuffer(RenderType.lineStrip())

        val matrix = poseStack.last().pose()
        for (point in pathPoints) {
            vertexConsumer.vertex(matrix, point.x.toFloat(), point.y.toFloat(), point.z.toFloat())
                .color(1.0f, 1.0f, 1.0f, 0.8f)
                .normal(0.0f, 1.0f, 0.0f)
                .endVertex()
        }

        bufferSource.endBatch(RenderType.lineStrip())

        RenderSystem.enableDepthTest()
        RenderSystem.disableBlend()
        poseStack.popPose()
    }
}
