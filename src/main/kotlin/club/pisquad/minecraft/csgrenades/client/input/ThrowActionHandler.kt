package club.pisquad.minecraft.csgrenades.client.input

import club.pisquad.minecraft.csgrenades.*
import club.pisquad.minecraft.csgrenades.enums.GrenadeType
import club.pisquad.minecraft.csgrenades.item.*
import club.pisquad.minecraft.csgrenades.network.CsGrenadePacketHandler
import club.pisquad.minecraft.csgrenades.network.message.GrenadeThrowType
import club.pisquad.minecraft.csgrenades.network.message.GrenadeThrownMessage
import net.minecraft.client.Minecraft
import net.minecraft.core.Rotations
import net.minecraft.util.Tuple
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.event.TickEvent
import net.minecraftforge.event.TickEvent.ClientTickEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod
import org.lwjgl.glfw.GLFW
import java.time.Duration
import java.time.Instant

@Mod.EventBusSubscriber(modid = CounterStrikeGrenades.ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = [Dist.CLIENT])
object ThrowActionHandler {
    private var primaryButtonPressed = false
    private var secondaryButtonPressed = false
    private var grenadeLastThrow: Instant = Instant.now()

    @SubscribeEvent
    fun onClientTick(event: ClientTickEvent) {
        if (event.phase == TickEvent.Phase.START) return

        if (Duration.between(this.grenadeLastThrow, Instant.now()).toMillis() < GRENADE_THROW_COOLDOWN) return

        val player = Minecraft.getInstance().player ?: return

        val hand: InteractionHand? = when {
            player.getItemInHand(InteractionHand.MAIN_HAND).item is CounterStrikeGrenadeItem -> InteractionHand.MAIN_HAND
            player.getItemInHand(InteractionHand.OFF_HAND).item is CounterStrikeGrenadeItem -> InteractionHand.OFF_HAND
            else -> null
        }
        if (hand == null) return

        val grenadeType = when (player.getItemInHand(hand).item) {
            is FlashBangItem -> GrenadeType.FLASH_BANG
            is HEGrenadeItem -> GrenadeType.HEGRENADE
            is IncendiaryItem -> GrenadeType.INCENDIARY
            is SmokeGrenadeItem -> GrenadeType.SMOKE_GRENADE
            else -> null
        }
        if (grenadeType == null) return


        val buttonState = getButtonState()

        if (primaryButtonPressed && !buttonState.a && !buttonState.b) {
            throwAction(GrenadeThrowType.Strong, hand, grenadeType)
            this.grenadeLastThrow = Instant.now()
        } else if (secondaryButtonPressed && !buttonState.b && !buttonState.a) {
            throwAction(GrenadeThrowType.Weak, hand, grenadeType)
            this.grenadeLastThrow = Instant.now()
        }

        this.primaryButtonPressed = buttonState.a
        this.secondaryButtonPressed = buttonState.b
    }

    private fun getButtonState(): Tuple<Boolean, Boolean> {
        val window = Minecraft.getInstance().window.window
        return Tuple(
            GLFW.glfwGetMouseButton(
                window, GLFW.GLFW_MOUSE_BUTTON_LEFT
            ) == 1, GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == 1
        )
    }
}

fun throwAction(throwType: GrenadeThrowType, hand: InteractionHand, grenadeType: GrenadeType) {
    val player: Player = Minecraft.getInstance().player ?: return

    val playerSpeedFactor = when (throwType) {
        GrenadeThrowType.Strong -> STRONG_THROW_PLAYER_SPEED_FACTOR
        GrenadeThrowType.Weak -> WEAK_THROW_PLAYER_SPEED_FACTOR
    }

    val speed = player.deltaMovement.scale(playerSpeedFactor)
        .add(player.lookAngle.normalize().scale(throwType.speed))
        .length()
    val playerPos = player.position()
    CsGrenadePacketHandler.INSTANCE.sendToServer(
        GrenadeThrownMessage(
            player.uuid,
            hand,
            speed,
            grenadeType,
            Vec3(playerPos.x, playerPos.y + PLAYER_EYESIGHT_OFFSET, playerPos.z),
            Rotations(player.xRot, player.yRot, 0.0f),
        )
    )
}