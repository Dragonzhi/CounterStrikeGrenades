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
    private const val AIR_DRAG = 0.99f
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

        // --- FINAL ACCURACY FIX: Replicate the EXACT speed calculation from ThrowActionHandler ---
        val speedFactor =
            (throwSpeed - ModConfig.THROW_SPEED_WEAK.get()) / (ModConfig.THROW_SPEED_STRONG.get() - ModConfig.THROW_SPEED_WEAK.get())

        val playerSpeedFactor =
            linearInterpolate(
                ModConfig.PLAYER_SPEED_FACTOR_WEAK.get(),
                ModConfig.PLAYER_SPEED_FACTOR_STRONG.get(),
                speedFactor,
            )

        val interpolatedThrowSpeed = linearInterpolate(
            ModConfig.THROW_SPEED_WEAK.get(),
            ModConfig.THROW_SPEED_STRONG.get(),
            speedFactor,
        )

        // This is the magnitude of the initial velocity vector, exactly as calculated in `throwAction` before being sent to the server.
        val launchSpeed = player.deltaMovement.scale(playerSpeedFactor)
            .add(
                player.lookAngle.normalize().scale(interpolatedThrowSpeed),
            )
            .length()

        // The server only gets the look direction and the final speed magnitude, so we simulate that.
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
                clipResult?.let { pathPoints.add(it.location) }
                position = tickStartPosition

                var bounceVelocity = clipResult?.let { bounce(velocity, it.direction) }
                if (bounceVelocity != null) {
                    bounceVelocity = bounceVelocity.scale(0.5)
                }
                if (bounceVelocity != null) {
                    velocity = bounceVelocity
                }

                if (clipResult != null) {
                    if (clipResult.direction == Direction.UP && velocity.lengthSqr() < REST_THRESHOLD_SQR) {
                        pathPoints.removeLast()
                        pathPoints.add(Vec3(clipResult.location.x, clipResult.location.y + 0.01, clipResult.location.z))
                        break
                    }
                }
                continue
            }

            position = nextPosition
            velocity = velocity.add(0.0, -GRAVITY.toDouble(), 0.0)
            velocity = velocity.scale(AIR_DRAG.toDouble())
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

        val (r, g, b) = try {
            val hex = ModConfig.TRAJECTORY_PREVIEW_COLOR.get()
            val cleanHex = hex.removePrefix("#")
            val colorInt = cleanHex.toInt(16)
            val red = (colorInt shr 16 and 0xFF) / 255.0f
            val green = (colorInt shr 8 and 0xFF) / 255.0f
            val blue = (colorInt and 0xFF) / 255.0f
            Triple(red, green, blue)
        } catch (e: Exception) {
            CounterStrikeGrenades.Logger.warn("Invalid trajectory preview color format. Using default white.")
            Triple(1.0f, 1.0f, 1.0f) // Default to white
        }

        val matrix = poseStack.last().pose()
        val totalPoints = pathPoints.size
        val startAlpha = 0.9f
        val endAlpha = 0.2f

        if (totalPoints < 2) return // Not enough points to draw a line

        for ((i, point) in pathPoints.withIndex()) {
            // Linearly interpolate alpha from startAlpha to endAlpha
            val progress = i.toFloat() / (totalPoints - 1)
            val currentAlpha = startAlpha + (endAlpha - startAlpha) * progress

            vertexConsumer.vertex(matrix, point.x.toFloat(), point.y.toFloat(), point.z.toFloat())
                .color(r, g, b, currentAlpha)
                .normal(0.0f, 1.0f, 0.0f)
                .endVertex()
        }

        bufferSource.endBatch(RenderType.lineStrip())

        RenderSystem.enableDepthTest()
        RenderSystem.disableBlend()
        poseStack.popPose()
    }
}
