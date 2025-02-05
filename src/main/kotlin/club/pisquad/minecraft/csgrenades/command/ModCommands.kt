package club.pisquad.minecraft.csgrenades.command

import net.minecraftforge.event.RegisterCommandsEvent
import net.minecraftforge.eventbus.api.SubscribeEvent

object ModCommands {
    @SubscribeEvent
    fun onRegisterCommands(event: RegisterCommandsEvent) {
        ChangeSettingCommand.register(event.dispatcher)
        RegisterObjectivesCommand.register(event.dispatcher)
    }
}