package club.pisquad.minecraft.csgrenades.item

import club.pisquad.minecraft.csgrenades.enums.GrenadeType
import club.pisquad.minecraft.csgrenades.registery.ModSoundEvents

class DecoyGrenadeItem(properties: Properties) : CounterStrikeGrenadeItem(properties) {
    init {
        drawSound = ModSoundEvents.DECOY_GRENADE_DRAW.get()
        grenadeType = GrenadeType.DECOY_GRENADE
    }
}
