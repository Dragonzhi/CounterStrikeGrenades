package club.pisquad.minecraft.csgrenades.network.message

import club.pisquad.minecraft.csgrenades.SoundTypes
import club.pisquad.minecraft.csgrenades.SoundUtils
import club.pisquad.minecraft.csgrenades.network.serializer.Vec3Serializer
import club.pisquad.minecraft.csgrenades.registry.ModSoundEvents
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.sounds.SoundSource
import net.minecraft.util.RandomSource
import net.minecraft.world.phys.Vec3
import net.minecraftforge.network.NetworkEvent
import java.util.function.Supplier

@Serializable
class FireGrenadeMessage(
    val messageType: MessageType,
    @Serializable(with = Vec3Serializer::class) val position: Vec3,
) {
    @Serializable
    enum class MessageType {
        GroundExploded,
        AirExploded,
        ExtinguishedBySmoke,
    }

    companion object {
        fun encoder(msg: FireGrenadeMessage, buffer: FriendlyByteBuf) {
            buffer.writeUtf(Json.encodeToString(msg))
        }

        fun decoder(buffer: FriendlyByteBuf): FireGrenadeMessage {
            val text = buffer.readUtf()
            return Json.decodeFromString<FireGrenadeMessage>(text)
        }

        fun handler(msg: FireGrenadeMessage, ctx: Supplier<NetworkEvent.Context>) {
            val context = ctx.get()
            context.packetHandled = true
            when (msg.messageType) {
                MessageType.GroundExploded -> {
                    playGroundExplodedSound(msg.position)
                }

                MessageType.AirExploded -> {
                    playAirExplodedSound(msg.position)
                    playAirExplodedAnimation(msg.position)
                }

                MessageType.ExtinguishedBySmoke -> {
                    playExtinguishSound(msg.position)
                }
            }
        }
    }
}

private fun playGroundExplodedSound(position: Vec3) {
    val distance = position.distanceTo(Minecraft.getInstance().player!!.position())
    val randomSource = RandomSource.createNewThreadLocalInstance()
    val extinguishSoundInstance = SimpleSoundInstance(
        ModSoundEvents.INCENDIARY_EXPLODE.get(),
        SoundSource.AMBIENT,
        SoundUtils.getVolumeFromDistance(distance, SoundTypes.INCENDIARY_EXPLODE).toFloat(),
        1f,
        randomSource,
        position.x,
        position.y,
        position.z,
    )
    Minecraft.getInstance().soundManager.play(extinguishSoundInstance)
}

private fun playAirExplodedSound(position: Vec3) {
    val distance = position.distanceTo(Minecraft.getInstance().player!!.position())
    val randomSource = RandomSource.createNewThreadLocalInstance()
    val extinguishSoundInstance = SimpleSoundInstance(
        ModSoundEvents.INCENDIARY_EXPLODE_AIR.get(),
        SoundSource.AMBIENT,
        SoundUtils.getVolumeFromDistance(distance, SoundTypes.INCENDIARY_EXPLODE_AIR).toFloat(),
        1f,
        randomSource,
        position.x,
        position.y,
        position.z,
    )
    Minecraft.getInstance().soundManager.play(extinguishSoundInstance)
}

private fun playExtinguishSound(position: Vec3) {
    val distance = position.distanceTo(Minecraft.getInstance().player!!.position())
    val randomSource = RandomSource.createNewThreadLocalInstance()
    val extinguishSoundInstance = SimpleSoundInstance(
        ModSoundEvents.INCENDIARY_POP.get(),
        SoundSource.AMBIENT,
        SoundUtils.getVolumeFromDistance(distance, SoundTypes.INCENDIARY_POP).toFloat(),
        0.8f,
        randomSource,
        position.x,
        position.y,
        position.z,
    )
    Minecraft.getInstance().soundManager.play(extinguishSoundInstance)
}

private fun playAirExplodedAnimation(position: Vec3) {
    val particleEngine = Minecraft.getInstance().particleEngine
    val particleCount = 500
    val randomSource = RandomSource.createNewThreadLocalInstance()

    repeat(particleCount) {
        particleEngine.createParticle(
            ParticleTypes.FLAME,
            position.x,
            position.y,
            position.z,
            randomSource.nextDouble() - 0.5,
            randomSource.nextDouble() - 0.5,
            randomSource.nextDouble() - 0.5,
        )?.lifetime = 10
    }
}
