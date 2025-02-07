package club.pisquad.minecraft.csgrenades.item

import club.pisquad.minecraft.csgrenades.enums.GrenadeType
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level

private var drawSoundPlayedSlot: Int = -1


open class CounterStrikeGrenadeItem(properties: Properties) : Item(properties.stacksTo(1)) {

    lateinit var grenadeType: GrenadeType

    // Sounds
    var drawSound: SoundEvent = SoundEvents.EMPTY

    override fun inventoryTick(stack: ItemStack, level: Level, entity: Entity, slotId: Int, isSelected: Boolean) {
        if (!level.isClientSide) return
        if (entity !is Player) return

        if (isSelected && drawSoundPlayedSlot != slotId) {
            entity.playSound(drawSound, 0.2f, 1.0f)
            drawSoundPlayedSlot = slotId
        }
        if (entity.inventory.selected != drawSoundPlayedSlot) {
            drawSoundPlayedSlot = -1
        }

    }
}