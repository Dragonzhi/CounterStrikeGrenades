package club.pisquad.minecraft.csgrenades.network.message

import club.pisquad.minecraft.csgrenades.CsGrenadeConfig
import club.pisquad.minecraft.csgrenades.CsGrenadeConfigManager
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.minecraft.network.FriendlyByteBuf
import net.minecraftforge.network.NetworkEvent
import java.util.function.Supplier

@Serializable
class SettingsChangeMessage(
    val setting: CsGrenadeConfig
) {
    companion object {
        fun encoder(msg: SettingsChangeMessage, buffer: FriendlyByteBuf) {
            buffer.writeUtf(Json.encodeToString(msg))
        }

        fun decoder(buffer: FriendlyByteBuf): SettingsChangeMessage {
            val text = buffer.readUtf()
            return Json.decodeFromString<SettingsChangeMessage>(text)
        }

        fun handler(msg: SettingsChangeMessage, ctx: Supplier<NetworkEvent.Context>) {
            CsGrenadeConfigManager.config = msg.setting
        }
    }
}