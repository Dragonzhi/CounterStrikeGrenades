package club.pisquad.minecraft.csgrenades.registry

import club.pisquad.minecraft.csgrenades.serializer.BlockPosListEntityDataSerializer
import net.minecraft.network.syncher.EntityDataSerializers

object ModSerializers {
    val blockPosListEntityDataSerializer = BlockPosListEntityDataSerializer()
    fun register() {
        EntityDataSerializers.registerSerializer(blockPosListEntityDataSerializer)
    }
}
