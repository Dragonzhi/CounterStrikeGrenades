package club.pisquad.minecraft.csgrenades.command

import net.minecraftforge.event.RegisterCommandsEvent
import net.minecraftforge.eventbus.api.SubscribeEvent

object ModCommands {
    @SubscribeEvent
    fun onRegisterCommands(event: RegisterCommandsEvent) {
        RegisterObjectivesCommand.register(event.dispatcher)
        SetConfigCommand.register(event.dispatcher)
    }
}