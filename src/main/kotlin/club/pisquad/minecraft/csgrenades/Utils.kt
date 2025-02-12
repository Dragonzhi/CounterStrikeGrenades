package club.pisquad.minecraft.csgrenades

import club.pisquad.minecraft.csgrenades.entity.SmokeGrenadeEntity
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.core.Vec3i
import net.minecraft.util.RandomSource
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3
import kotlin.random.Random

/**
 *Since KFF is not mapping those methods correctly
 */

fun Vec3.toVec3i(): Vec3i {
    return Vec3i(x.toInt(), y.toInt(), z.toInt())
}

fun BlockPos.toVec3(): Vec3 {
    return Vec3(x.toDouble(), y.toDouble(), z.toDouble())
}

fun Vec3i.toVec3(): Vec3 {
    return Vec3(x.toDouble(), y.toDouble(), z.toDouble())
}

fun getTimeFromTickCount(tickCount: Double): Double {
    return tickCount / 20.0
}

fun getRandomLocationFromSphere(center: Vec3, radius: Double): Vec3 {
    val randomSource = RandomSource.createNewThreadLocalInstance()
    while (true) {
        val posDelta = Vec3(
            randomSource.nextDouble() * radius * 2 - radius,
            randomSource.nextDouble() * radius * 2 - radius,
            randomSource.nextDouble() * radius * 2 - radius
        )
        if (posDelta.length() < radius) {
            return center.add(posDelta)
        }
    }
}

fun getRandomLocationFromCircle(center: Vec2, radius: Double): Vec2 {
    val randomSource = RandomSource.createNewThreadLocalInstance()
    while (true) {
        val posDelta = Vec2(
            (randomSource.nextDouble() * radius * 2 - radius).toFloat(),
            (randomSource.nextDouble() * radius * 2 - radius).toFloat()
        )
        if (posDelta.length() < radius) {
            return center.add(posDelta)
        }
    }
}

fun getRandomLocationFromBlockSurface(position: BlockPos): Vec3 {
    val x = Random.nextDouble()
    val z = Random.nextDouble()
    return Vec3(position.x + x, position.y + 1.0, position.z + z)
}


fun isPositionInSmoke(pos: Vec3): Boolean {
    val level = Minecraft.getInstance().level ?: return false
    return level.getEntitiesOfClass(
        SmokeGrenadeEntity::class.java,
        AABB(BlockPos(pos.toVec3i())).inflate(
            SMOKE_GRENADE_RADIUS.toDouble(), SMOKE_GRENADE_FALLDOWN_HEIGHT.toDouble(),
            SMOKE_GRENADE_RADIUS.toDouble()
        )
    ).any {
        it.getSpreadBlocks().any { block -> block.toVec3().distanceToSqr(pos) < 1.0 }
    }
}

fun getBlockPosAround2D(pos: Vec3, radius: Int): List<BlockPos> {
    val posVec3 = BlockPos.containing(pos)
    val begin = posVec3.offset(-radius, 0, -radius)
    val result = mutableListOf<BlockPos>()
    repeat((radius * 2)+1) { xOffset ->
        repeat((radius * 2)+1) { zOffset ->
            result.add(BlockPos(begin.offset(xOffset, 0, zOffset)))
        }
    }
    val center2D = Vec2(pos.x.toFloat(), pos.z.toFloat())
    return result.filter { center2D.distanceToSqr(Vec2(it.x.toFloat(), it.z.toFloat())) < radius * radius }
}

fun getBlocksAround3D(pos: Vec3, xRange: Int, yRange: Int, zRange: Int): List<BlockPos> {
    val posVec3 = BlockPos.containing(pos)
    val begin = posVec3.offset(-xRange, -yRange, -zRange)
    val result = mutableListOf<BlockPos>()
    repeat((xRange * 2) + 1) { xOffset ->
        repeat((yRange * 2) + 1) { yOffset ->
            repeat((zRange * 2) + 1) { zOffset ->
                result.add(
                    BlockPos(
                        begin.offset(
                            xOffset,
                            yOffset,
                            zOffset
                        )
                    )
                )
            }
        }
    }
    return result
}

fun linearInterpolate(from: Double, to: Double, t: Double): Double {
    return from + (to - from) * t
}

fun randomDirection(origin: BlockPos): BlockPos {
    return when ((Random.nextInt(4))) {
        0 -> origin.north()
        1 -> origin.south()
        2 -> origin.west()
        else -> origin.east()
    }
}