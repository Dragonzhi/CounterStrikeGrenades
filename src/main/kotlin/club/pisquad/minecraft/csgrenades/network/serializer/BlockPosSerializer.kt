package club.pisquad.minecraft.csgrenades.network.serializer

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.IntArraySerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import net.minecraft.core.BlockPos


class BlockPosSerializer : KSerializer<BlockPos> {
    private val delegateSerializer = IntArraySerializer()

    @OptIn(ExperimentalSerializationApi::class)
    override val descriptor: SerialDescriptor = SerialDescriptor("BlockPos", delegateSerializer.descriptor)

    override fun deserialize(decoder: Decoder): BlockPos {
        val array = decoder.decodeSerializableValue(delegateSerializer)
        return BlockPos(array[0], array[1], array[2])
    }

    override fun serialize(encoder: Encoder, value: BlockPos) {
        val data = intArrayOf(
            value.x,
            value.y,
            value.z
        )
        encoder.encodeSerializableValue(delegateSerializer, data)
    }
}