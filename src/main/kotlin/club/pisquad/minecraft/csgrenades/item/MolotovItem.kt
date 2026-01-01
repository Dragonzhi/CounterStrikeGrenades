package club.pisquad.minecraft.csgrenades.item

import club.pisquad.minecraft.csgrenades.enums.GrenadeType
import club.pisquad.minecraft.csgrenades.registry.ModSoundEvents

class MolotovItem(properties: Properties) : CounterStrikeGrenadeItem(properties) {
    init {
        drawSound = ModSoundEvents.INCENDIARY_DRAW.get()
        grenadeType = GrenadeType.MOLOTOV
    }
}
