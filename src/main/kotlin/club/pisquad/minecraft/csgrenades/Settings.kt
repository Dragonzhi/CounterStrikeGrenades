package club.pisquad.minecraft.csgrenades

import club.pisquad.minecraft.csgrenades.network.CsGrenadePacketHandler
import club.pisquad.minecraft.csgrenades.network.message.SettingsChangeMessage
import kotlinx.serialization.Serializable
import net.minecraftforge.network.PacketDistributor

@Serializable
data class CsGrenadeConfig(
    var ignoreBarrierBlock: Boolean = false
)

object CsGrenadeConfigManager {
    var config: CsGrenadeConfig = CsGrenadeConfig()

    fun update(config: CsGrenadeConfig) {
        this.config = config
        CsGrenadePacketHandler.INSTANCE.send(
            PacketDistributor.ALL.noArg(),
            SettingsChangeMessage(config)
        )
    }

}

const val PLAYER_EYESIGHT_OFFSET = 1.62
const val GRENADE_THROW_COOLDOWN = 1000
const val GRENADE_ENTITY_SIZE = 0.3f

// Grenade throw acton settings
// There are two types of throw actions, strong and weak, just like in CSGO
const val STRONG_THROW_SPEED: Double = 1.3
const val WEAK_THROW_SPEED: Double = 0.4
const val MODERATE_THROW_SPEED = (STRONG_THROW_SPEED + WEAK_THROW_SPEED) / 2
const val THROW_TYPE_TRANSIENT_TIME: Long = 1000
const val STRONG_THROW_PLAYER_SPEED_FACTOR: Double = 1.3
const val WEAK_THROW_PLAYER_SPEED_FACTOR: Double = 0.5
const val FOV_EFFECT_AMOUNT = 0.12

// SMOKE GRENADES
const val SMOKE_GRENADE_RADIUS = 6
const val SMOKE_FUSE_TIME_AFTER_LAND = 0.5
const val SMOKE_GRENADE_PARTICLE_COUNT = 4000
const val SMOKE_GRENADE_SPREAD_TIME = 0.3
const val SMOKE_GRENADE_TOTAL_GENERATION_TIME = 2.0
const val SMOKE_GRENADE_SMOKE_LIFETIME = 20
const val SMOKE_GRENADE_TIME_BEFORE_REGENERATE = 1.0
const val SMOKE_GRENADE_REGENERATE_TIME = 3.0

// HE GRENADES
const val HEGRENADE_BASE_DAMAGE = 30
const val HEGRENADE_DAMAGE_RANGE = 5.0

// Incendiary
const val FIREGRENADE_RANGE = 6
const val FIREGRENADE_LIFETIME = 7.0
const val FIREGRENADE_FUSE_TIME = 2.0
const val INCENDIARY_PARTICLE_DENSITY = 1
const val INCENDIARY_PARTICLE_LIFETIME = 30
const val FIRE_EXTINGUISH_RANGE = SMOKE_GRENADE_RADIUS
const val FIRE_MAX_SPREAD_DOWNWARD = 10