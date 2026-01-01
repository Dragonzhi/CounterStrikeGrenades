package club.pisquad.minecraft.csgrenades.item

import club.pisquad.minecraft.csgrenades.enums.GrenadeType
import club.pisquad.minecraft.csgrenades.registry.ModSoundEvents

class HEGrenadeItem(properties: Properties) : CounterStrikeGrenadeItem(properties) {

    init {
        drawSound = ModSoundEvents.HEGRENADE_DRAW.get()
        grenadeType = GrenadeType.HEGRENADE
    }
}
