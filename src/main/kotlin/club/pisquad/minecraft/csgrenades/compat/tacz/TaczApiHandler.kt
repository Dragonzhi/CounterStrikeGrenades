package club.pisquad.minecraft.csgrenades.compat.tacz

import com.tacz.guns.api.TimelessAPI
import com.tacz.guns.client.sound.SoundPlayManager
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.Entity
import net.minecraftforge.fml.ModList

object TaczApiHandler {
    fun playGunSound(entity: Entity, gunId: ResourceLocation) {
        // Ensure this code only runs when Tacz is loaded
        if (!ModList.get().isLoaded("tacz")) {
            return
        }

        val soundName = "shoot"
        val volume = 0.8f
        val pitch = 0.9f + entity.level().random.nextFloat() * 0.125f
        val distance = 48 // Default gun fire sound distance from Tacz config is 48

        // This logic mimics Tacz's SoundPlayManager.playMessageSound
        TimelessAPI.getGunDisplay(gunId, gunId).ifPresent { gunDisplay ->
            val soundId = gunDisplay.getSounds(soundName)
            if (soundId != null) {
                SoundPlayManager.playClientSound(entity, soundId, volume, pitch, distance)
            }
        }
    }
}
