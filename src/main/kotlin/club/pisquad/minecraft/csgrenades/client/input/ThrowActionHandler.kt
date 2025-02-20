package club.pisquad.minecraft.csgrenades.client.input

import club.pisquad.minecraft.csgrenades.*
import club.pisquad.minecraft.csgrenades.enums.GrenadeType
import club.pisquad.minecraft.csgrenades.item.CounterStrikeGrenadeItem
import club.pisquad.minecraft.csgrenades.network.CsGrenadePacketHandler
import club.pisquad.minecraft.csgrenades.network.message.GrenadeThrownMessage
import net.minecraft.client.Minecraft
import net.minecraft.core.Rotations
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.api.distmarker.OnlyIn
import net.minecraftforge.event.TickEvent
import net.minecraftforge.event.TickEvent.ClientTickEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod
import org.lwjgl.glfw.GLFW
import java.time.Duration
import java.time.Instant
import kotlin.math.min

@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = CounterStrikeGrenades.ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = [Dist.CLIENT])
object ThrowActionHandler {
    private var grenadeLastThrow: Instant = Instant.now()
    private var primaryButtonPressed: Boolean = false
    private var secondaryButtonPressed: Boolean = false
    private var throwSpeedTransientTarget: Double? = null
    private var throwSpeedTransientOrigin: Double? = null
    var currentThrowSpeed: Double? = null
    private var transientBeginTime: Instant = Instant.now()
    private var previousSlot = -1

    @SubscribeEvent
    fun onClientTick(event: ClientTickEvent) {
        if (event.phase == TickEvent.Phase.START) return
        // Test if any screen is opened (e.g. inventory, chat, menu, etc.)
        if (Minecraft.getInstance().screen != null) return

        val player = Minecraft.getInstance().player ?: return
        val selectedSlot = player.inventory.selected

        val (primaryButtonPressed, secondaryButtonPressed) = getButtonState()

        val itemInHand = player.getItemInHand(InteractionHand.MAIN_HAND)

        if (itemInHand.item is CounterStrikeGrenadeItem
            && previousSlot == selectedSlot
        ) {
            if (Duration.between(this.grenadeLastThrow, Instant.now()).toMillis() > GRENADE_THROW_COOLDOWN) {
                when (Pair(this.primaryButtonPressed, this.secondaryButtonPressed)) {
                    Pair(false, false) -> {
                        when (Pair(primaryButtonPressed, secondaryButtonPressed)) {
                            Pair(true, true) -> {
                                this.setNewTransientTarget(MODERATE_THROW_SPEED, MODERATE_THROW_SPEED)
                            }

                            Pair(true, false) -> {
                                this.setNewTransientTarget(STRONG_THROW_SPEED, STRONG_THROW_SPEED)
                            }

                            Pair(false, true) -> {
                                this.setNewTransientTarget(WEAK_THROW_SPEED, WEAK_THROW_SPEED)
                            }
                        }
                    }

                    Pair(true, false) -> {
                        when (Pair(primaryButtonPressed, secondaryButtonPressed)) {
                            Pair(true, true) -> {
                                this.setNewTransientTarget(MODERATE_THROW_SPEED, null)
                            }

                            Pair(false, true) -> {
                                this.setNewTransientTarget(WEAK_THROW_SPEED, null)
                            }
                        }
                    }

                    Pair(false, true) -> {
                        when (Pair(primaryButtonPressed, secondaryButtonPressed)) {
                            Pair(true, true) -> {
                                this.setNewTransientTarget(MODERATE_THROW_SPEED, null)
                            }

                            Pair(true, false) -> {
                                this.setNewTransientTarget(STRONG_THROW_SPEED, null)
                            }
                        }
                    }

                    Pair(true, true) -> {
                        when (Pair(primaryButtonPressed, secondaryButtonPressed)) {
                            Pair(true, false) -> {
                                this.setNewTransientTarget(STRONG_THROW_SPEED, null)
                            }

                            Pair(false, true) -> {
                                this.setNewTransientTarget(WEAK_THROW_SPEED, null)
                            }
                        }
                    }
                }
                this.updateCurrentSpeed()
                if (!primaryButtonPressed && !secondaryButtonPressed) {
                    if (this.primaryButtonPressed || this.secondaryButtonPressed) {
                        val grenadeType = (itemInHand.item as CounterStrikeGrenadeItem).grenadeType
                        throwAction(this.currentThrowSpeed ?: 0.0, grenadeType)
                        setItemCoolDown(player)
                        cleanProgress()
                        this.grenadeLastThrow = Instant.now()
                    }
                }
                this.primaryButtonPressed = primaryButtonPressed
                this.secondaryButtonPressed = secondaryButtonPressed
            }
        } else {
            this.primaryButtonPressed = false
            this.secondaryButtonPressed = false
            this.cleanProgress()
        }
        this.previousSlot = selectedSlot
    }

    private fun cleanProgress() {
        this.currentThrowSpeed = null
        this.throwSpeedTransientTarget = null
        this.throwSpeedTransientOrigin = null
    }

    private fun setNewTransientTarget(speedTarget: Double, speedOrigin: Double?) {
        this.throwSpeedTransientOrigin = speedOrigin ?: this.currentThrowSpeed
        this.throwSpeedTransientTarget = speedTarget
        this.transientBeginTime = Instant.now()
    }

    private fun updateCurrentSpeed() {
        if (throwSpeedTransientOrigin == null || throwSpeedTransientTarget == null) return
        this.currentThrowSpeed = linearInterpolate(
            this.throwSpeedTransientOrigin!!,
            this.throwSpeedTransientTarget!!,
            min(
                1.0,
                Duration.between(this.transientBeginTime, Instant.now()).toMillis()
                    .toDouble() / THROW_TYPE_TRANSIENT_TIME
            )
        )
    }


    private fun getButtonState(): Pair<Boolean, Boolean> {
        val window = Minecraft.getInstance().window.window
        return Pair(
            GLFW.glfwGetMouseButton(
                window, GLFW.GLFW_MOUSE_BUTTON_LEFT
            ) == 1, GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == 1
        )
    }
}

fun throwAction(throwSpeed: Double, grenadeType: GrenadeType) {
    val player: Player = Minecraft.getInstance().player ?: return
    val speedFactor = (throwSpeed - WEAK_THROW_SPEED) / (STRONG_THROW_SPEED - WEAK_THROW_SPEED)
    val playerSpeedFactor =
        linearInterpolate(WEAK_THROW_PLAYER_SPEED_FACTOR, STRONG_THROW_PLAYER_SPEED_FACTOR, speedFactor)

    val speed = player.deltaMovement.scale(playerSpeedFactor)
        .add(
            player.lookAngle.normalize().scale(linearInterpolate(WEAK_THROW_SPEED, STRONG_THROW_SPEED, speedFactor))
        )
        .length()
    val playerPos = player.position()
    CsGrenadePacketHandler.INSTANCE.sendToServer(
        GrenadeThrownMessage(
            player.uuid,
            speed,
            grenadeType,
            Vec3(playerPos.x, playerPos.y + PLAYER_EYESIGHT_OFFSET, playerPos.z),
            Rotations(player.xRot, player.yRot, 0.0f),
        )
    )
}

fun setItemCoolDown(player: Player) {
    player.inventory.items.forEach {
        if (it.item is CounterStrikeGrenadeItem) {
            player.cooldowns.addCooldown(it.item, GRENADE_THROW_COOLDOWN / 1000 * 20)
        }
    }

}