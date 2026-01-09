package club.pisquad.minecraft.csgrenades.client.input

import club.pisquad.minecraft.csgrenades.CounterStrikeGrenades
import club.pisquad.minecraft.csgrenades.config.ModConfig
import club.pisquad.minecraft.csgrenades.enums.GrenadeType
import club.pisquad.minecraft.csgrenades.event.GrenadeThrowEvent
import club.pisquad.minecraft.csgrenades.item.CounterStrikeGrenadeItem
import club.pisquad.minecraft.csgrenades.linearInterpolate
import club.pisquad.minecraft.csgrenades.network.CsGrenadePacketHandler
import club.pisquad.minecraft.csgrenades.network.message.GrenadeThrownMessage
import net.minecraft.client.Minecraft
import net.minecraft.core.Rotations
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.player.Player
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.api.distmarker.OnlyIn
import net.minecraftforge.common.MinecraftForge
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
    var chargeStartTime: Instant? = null
    private var transientBeginTime: Instant = Instant.now()
    private var previousSlot = -1
    private var screenOpened = false
    private var buttonPressedWhenScreenOpen = false

    @SubscribeEvent
    fun onClientTick(event: ClientTickEvent) {
        if (event.phase == TickEvent.Phase.START) return

        val (primaryButtonPressed, secondaryButtonPressed) = getButtonState()

        // Test if any screen is opened (e.g. inventory, chat, menu, etc.)
        if (Minecraft.getInstance().screen != null) {
            this.screenOpened = true
            if (this.primaryButtonPressed || this.secondaryButtonPressed) {
                this.cleanProgress()
                this.buttonPressedWhenScreenOpen = true
            }
            return
        } else if (screenOpened) {
            this.buttonPressedWhenScreenOpen = primaryButtonPressed || secondaryButtonPressed
            this.screenOpened = false
            return
        }
        if (buttonPressedWhenScreenOpen) {
            if (!(primaryButtonPressed || secondaryButtonPressed)) {
                this.buttonPressedWhenScreenOpen = false
                return
            }
            return
        }

        val player = Minecraft.getInstance().player ?: return
        val selectedSlot = player.inventory.selected

        val itemInHand = player.getItemInHand(InteractionHand.MAIN_HAND)

        if (itemInHand.item is CounterStrikeGrenadeItem &&
            previousSlot == selectedSlot
        ) {
            if (Duration.between(this.grenadeLastThrow, Instant.now())
                    .toMillis() > ModConfig.GRENADE_THROW_COOLDOWN.get()
            ) {
                when (Pair(this.primaryButtonPressed, this.secondaryButtonPressed)) {
                    Pair(false, false) -> {
                        if (primaryButtonPressed || secondaryButtonPressed) {
                            this.chargeStartTime = Instant.now()
                        }
                        when (Pair(primaryButtonPressed, secondaryButtonPressed)) {
                            Pair(true, true) -> {
                                this.setNewTransientTarget(
                                    ModConfig.THROW_SPEED_MODERATE.get(),
                                    ModConfig.THROW_SPEED_MODERATE.get(),
                                )
                            }

                            Pair(true, false) -> {
                                this.setNewTransientTarget(
                                    ModConfig.THROW_SPEED_STRONG.get(),
                                    ModConfig.THROW_SPEED_STRONG.get(),
                                )
                            }

                            Pair(false, true) -> {
                                this.setNewTransientTarget(
                                    ModConfig.THROW_SPEED_WEAK.get(),
                                    ModConfig.THROW_SPEED_WEAK.get(),
                                )
                            }
                        }
                    }

                    Pair(true, false) -> {
                        when (Pair(primaryButtonPressed, secondaryButtonPressed)) {
                            Pair(true, true) -> {
                                this.setNewTransientTarget(ModConfig.THROW_SPEED_MODERATE.get(), null)
                            }

                            Pair(false, true) -> {
                                this.setNewTransientTarget(ModConfig.THROW_SPEED_WEAK.get(), null)
                            }
                        }
                    }

                    Pair(false, true) -> {
                        when (Pair(primaryButtonPressed, secondaryButtonPressed)) {
                            Pair(true, true) -> {
                                this.setNewTransientTarget(ModConfig.THROW_SPEED_MODERATE.get(), null)
                            }

                            Pair(true, false) -> {
                                this.setNewTransientTarget(ModConfig.THROW_SPEED_STRONG.get(), null)
                            }
                        }
                    }

                    Pair(true, true) -> {
                        when (Pair(primaryButtonPressed, secondaryButtonPressed)) {
                            Pair(true, false) -> {
                                this.setNewTransientTarget(ModConfig.THROW_SPEED_STRONG.get(), null)
                            }

                            Pair(false, true) -> {
                                this.setNewTransientTarget(ModConfig.THROW_SPEED_WEAK.get(), null)
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
        this.primaryButtonPressed = false
        this.secondaryButtonPressed = false
        this.currentThrowSpeed = null
        this.throwSpeedTransientTarget = null
        this.throwSpeedTransientOrigin = null
        this.chargeStartTime = null
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
                    .toDouble() / ModConfig.THROW_TYPE_TRANSIENT_TIME.get(),
            ),
        )
    }

    private fun getButtonState(): Pair<Boolean, Boolean> {
        val window = Minecraft.getInstance().window.window
        return Pair(
            GLFW.glfwGetMouseButton(
                window,
                GLFW.GLFW_MOUSE_BUTTON_LEFT,
            ) == 1,
            GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == 1,
        )
    }
}

fun throwAction(throwSpeed: Double, grenadeType: GrenadeType) {
    val player: Player = Minecraft.getInstance().player ?: return
    val itemInHand = player.getItemInHand(InteractionHand.MAIN_HAND) // Moved up for event

    // NEW: Fire the GrenadeThrowEvent
    val event = GrenadeThrowEvent(player, itemInHand)
    if (MinecraftForge.EVENT_BUS.post(event)) {
        // Event was canceled, stop the throw action
        return
    }

    val speedFactor =
        (throwSpeed - ModConfig.THROW_SPEED_WEAK.get()) / (ModConfig.THROW_SPEED_STRONG.get() - ModConfig.THROW_SPEED_WEAK.get())
    val playerSpeedFactor =
        linearInterpolate(
            ModConfig.PLAYER_SPEED_FACTOR_WEAK.get(),
            ModConfig.PLAYER_SPEED_FACTOR_STRONG.get(),
            speedFactor,
        )

    val speed = player.deltaMovement.scale(playerSpeedFactor)
        .add(
            player.lookAngle.normalize()
                .scale(
                    linearInterpolate(
                        ModConfig.THROW_SPEED_WEAK.get(),
                        ModConfig.THROW_SPEED_STRONG.get(),
                        speedFactor,
                    ),
                ),
        )
        .length()

    // --- NBT Reading Logic for Decoy ---
    var customSound: String? = null
    if (grenadeType == GrenadeType.DECOY_GRENADE) {
        val itemInHand = player.getItemInHand(InteractionHand.MAIN_HAND)
        val tag = itemInHand.tag
        // 8 is the NBT tag ID for String
        if (tag != null && tag.contains("DecoySound", 8)) {
            customSound = tag.getString("DecoySound")
        }
    }
    // --- End NBT Reading ---

    CsGrenadePacketHandler.INSTANCE.sendToServer(
        GrenadeThrownMessage(
            player.uuid,
            speed,
            grenadeType,
            player.eyePosition,
            Rotations(player.xRot, player.yRot, 0.0f),
            customSound,
        ),
    )
}

fun setItemCoolDown(player: Player) {
    player.inventory.items.forEach {
        if (it.item is CounterStrikeGrenadeItem) {
            player.cooldowns.addCooldown(it.item, ModConfig.GRENADE_THROW_COOLDOWN.get() / 1000 * 20)
        }
    }
}
