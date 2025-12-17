package club.pisquad.minecraft.csgrenades.client

import club.pisquad.minecraft.csgrenades.CounterStrikeGrenades
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundSource
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.client.event.sound.PlaySoundEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod

@Mod.EventBusSubscriber(modid = CounterStrikeGrenades.ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = [Dist.CLIENT])
object SoundListener {

    var lastPlayerGunSound: ResourceLocation? = null

    @SubscribeEvent
    fun onPlaySound(event: PlaySoundEvent) {
        val sound = event.sound ?: return

        // We only care about sounds played by players
        if (sound.source != SoundSource.PLAYERS) {
            return
        }

        val soundLocation = sound.location
        val taczNamespace = "tacz"

        // Check if the sound belongs to the Tacz mod
        if (soundLocation.namespace == taczNamespace) {
            // Further check to ensure it's likely a gun firing sound, not other sounds like reloads.
            // This is a heuristic guess based on common naming schemes.
            val path = soundLocation.path
            if (path.contains("fire", ignoreCase = true) || path.contains("shoot", ignoreCase = true) || path.contains("gun", ignoreCase = true)) {
                lastPlayerGunSound = soundLocation
            }
        }
    }
}
