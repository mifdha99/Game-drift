package com.example.drift.track

import androidx.compose.ui.graphics.Color
import com.example.drift.math.Math3D
import com.example.drift.math.Vec3
import com.example.drift.model.SurfaceType
import com.example.drift.physics.Vehicle
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

data class RoadNode(
    val x: Float,
    val z: Float,
    val halfWidth: Float = 8.2f,
    val isClippingZone: Boolean = false,
    val clippingSide: Int = 0 // -1 left curb, +1 right curb, 0 none
)

data class RoadSegment(
    val index: Int,
    val p0: Vec3,
    val p1: Vec3,
    val left0: Vec3,
    val right0: Vec3,
    val left1: Vec3,
    val right1: Vec3,
    val forwardX: Float,
    val forwardZ: Float,
    val normalX: Float,
    val normalZ: Float,
    val length: Float,
    val halfWidth: Float,
    val isClippingZone: Boolean,
    val clippingSide: Int,
    val hasBarrierLeft: Boolean,
    val hasBarrierRight: Boolean
)

data class ConeProp(
    val initialX: Float,
    val initialZ: Float,
    var x: Float,
    var y: Float,
    var z: Float,
    var vx: Float = 0f,
    var vy: Float = 0f,
    var vz: Float = 0f,
    var knocked: Boolean = false
) {
    fun reset() {
        x = initialX
        y = 0f
        z = initialZ
        vx = 0f
        vy = 0f
        vz = 0f
        knocked = false
    }
}

data class BuildingProp(
    val center: Vec3,
    val width: Float,
    val height: Float,
    val depth: Float,
    val yaw: Float,
    val color: Color,
    val neonColor: Color? = null
)

data class StreetLightProp(
    val x: Float,
    val z: Float,
    val yaw: Float
)

data class CollisionResult(
    val hitBarrier: Boolean = false,
    val hitCone: Boolean = false,
    val nearBarrierProximity: Boolean = false,
    val inClippingZone: Boolean = false,
    val impactPoint: Vec3? = null,
    val impactIntensity: Float = 0f
)

class Track {
    val segments = ArrayList<RoadSegment>()
    val cones = ArrayList<ConeProp>()
    val buildings = ArrayList<BuildingProp>()
    val streetLights = ArrayList<StreetLightProp>()
    var lastCheckpointSegment: Int = 0

    init {
        buildJapaneseIndustrialTougeTrack()
    }

    private fun buildJapaneseIndustrialTougeTrack() {
        // Closed-loop Japanese Industrial Dock + Mountain Touge Circuit
        // Includes: Main Straight -> Sweeper -> Tight Hairpin -> Technical S-Chicane -> Open Drift Yard -> Banked Parabolica
        val controlPoints = listOf(
            RoadNode(0f, 0f, 8.5f, false, 0),
            RoadNode(0f, 42f, 8.5f, false, 0),
            RoadNode(6f, 82f, 8.5f, true, 1),       // Clipping Zone 1: Entry Sweeper Apex
            RoadNode(34f, 114f, 9.0f, true, 1),
            RoadNode(72f, 120f, 9.5f, false, 0),
            RoadNode(104f, 98f, 10.0f, true, -1),   // Clipping Zone 2: Hairpin Inner Clip
            RoadNode(110f, 64f, 9.5f, true, -1),
            RoadNode(84f, 44f, 9.0f, false, 0),     // S-Corner Entry
            RoadNode(58f, 32f, 9.0f, true, 1),      // Clipping Zone 3: S-Switchback
            RoadNode(62f, -2f, 9.5f, true, -1),
            RoadNode(96f, -26f, 11.5f, false, 0),   // Wide Open Industrial Drift Pad
            RoadNode(112f, -66f, 12.0f, true, 1),   // Clipping Zone 4: Dockyard Outer Wall
            RoadNode(78f, -98f, 10.0f, true, 1),
            RoadNode(34f, -96f, 9.0f, false, 0),
            RoadNode(4f, -68f, 8.5f, true, -1),     // Clipping Zone 5: Final Corner Clip
            RoadNode(-2f, -34f, 8.5f, false, 0)
        )

        // Subdivide with Catmull-Rom spline for smooth low-poly drift curves
        val smoothNodes = ArrayList<RoadNode>()
        val n = controlPoints.size
        val subdivisions = 5
        for (i in 0 until n) {
            val p0 = controlPoints[(i - 1 + n) % n]
            val p1 = controlPoints[i]
            val p2 = controlPoints[(i + 1) % n]
            val p3 = controlPoints[(i + 2) % n]

            for (step in 0 until subdivisions) {
                val t = step.toFloat() / subdivisions
                val t2 = t * t
                val t3 = t2 * t

                val x = 0.5f * (
                    (2f * p1.x) +
                        (-p0.x + p2.x) * t +
                        (2f * p0.x - 5f * p1.x + 4f * p2.x - p3.x) * t2 +
                        (-p0.x + 3f * p1.x - 3f * p2.x + p3.x) * t3
                    )
                val z = 0.5f * (
                    (2f * p1.z) +
                        (-p0.z + p2.z) * t +
                        (2f * p0.z - 5f * p1.z + 4f * p2.z - p3.z) * t2 +
                        (-p0.z + 3f * p1.z - 3f * p2.z + p3.z) * t3
                    )
                val hw = Math3D.lerp(p1.halfWidth, p2.halfWidth, t)
                smoothNodes.add(
                    RoadNode(
                        x = x,
                        z = z,
                        halfWidth = hw,
                        isClippingZone = p1.isClippingZone,
                        clippingSide = p1.clippingSide
                    )
                )
            }
        }

        // Build road segments
        val count = smoothNodes.size
        for (i in 0 until count) {
            val curr = smoothNodes[i]
            val next = smoothNodes[(i + 1) % count]
            val dx = next.x - curr.x
            val dz = next.z - curr.z
            val len = max(0.1f, sqrt(dx * dx + dz * dz))
            val fx = dx / len
            val fz = dz / len
            // Right normal in world (+X right when facing +Z)
            val nx = fz
            val nz = -fx

            val p0 = Vec3(curr.x, 0f, curr.z)
            val p1 = Vec3(next.x, 0f, next.z)
            val left0 = Vec3(curr.x - nx * curr.halfWidth, 0f, curr.z - nz * curr.halfWidth)
            val right0 = Vec3(curr.x + nx * curr.halfWidth, 0f, curr.z + nz * curr.halfWidth)

            // Compute next tangent for seamless edge joining
            val next2 = smoothNodes[(i + 2) % count]
            val dx2 = next2.x - next.x
            val dz2 = next2.z - next.z
            val len2 = max(0.1f, sqrt(dx2 * dx2 + dz2 * dz2))
            val nx2 = dz2 / len2
            val nz2 = -dx2 / len2

            val left1 = Vec3(next.x - nx2 * next.halfWidth, 0f, next.z - nz2 * next.halfWidth)
            val right1 = Vec3(next.x + nx2 * next.halfWidth, 0f, next.z + nz2 * next.halfWidth)

            // Leave a few runoff openings in the industrial yard, barriers everywhere else
            val isOpenYard = i in 48..56
            segments.add(
                RoadSegment(
                    index = i,
                    p0 = p0,
                    p1 = p1,
                    left0 = left0,
                    right0 = right0,
                    left1 = left1,
                    right1 = right1,
                    forwardX = fx,
                    forwardZ = fz,
                    normalX = nx,
                    normalZ = nz,
                    length = len,
                    halfWidth = curr.halfWidth,
                    isClippingZone = curr.isClippingZone,
                    clippingSide = curr.clippingSide,
                    hasBarrierLeft = !isOpenYard || i % 2 == 0,
                    hasBarrierRight = true
                )
            )

            // Place street lights every 4 segments alternating sides
            if (i % 4 == 0) {
                val side = if ((i / 4) % 2 == 0) -1f else 1f
                val dist = curr.halfWidth + 2.4f
                val lx = curr.x + nx * dist * side
                val lz = curr.z + nz * dist * side
                val lightYaw = atan2(-nx * side, -nz * side)
                streetLights.add(StreetLightProp(lx, lz, lightYaw))
            }

            // Place traffic cones near clipping zones and open yard
            if (curr.isClippingZone && i % 2 == 0) {
                val side = if (curr.clippingSide != 0) curr.clippingSide.toFloat() else 1f
                val coneOffset = (curr.halfWidth - 1.15f) * side
                val cx = curr.x + nx * coneOffset
                val cz = curr.z + nz * coneOffset
                cones.add(ConeProp(cx, cz, cx, 0f, cz))
            }
        }

        // Add Japanese industrial warehouses, shipping containers & neon towers around the circuit
        buildings.add(
            BuildingProp(
                center = Vec3(-22f, 0f, 25f),
                width = 16f,
                height = 14f,
                depth = 34f,
                yaw = 0f,
                color = Color(0xFF1A2230),
                neonColor = Color(0xFF00F0FF)
            )
        )
        buildings.add(
            BuildingProp(
                center = Vec3(-24f, 0f, 75f),
                width = 18f,
                height = 22f,
                depth = 24f,
                yaw = 0.15f,
                color = Color(0xFF151C28),
                neonColor = Color(0xFFFF2A54)
            )
        )
        buildings.add(
            BuildingProp(
                center = Vec3(52f, 0f, 76f),
                width = 22f,
                height = 12f,
                depth = 18f,
                yaw = 0.4f,
                color = Color(0xFF202938),
                neonColor = Color(0xFFFF9E00)
            )
        )
        buildings.add(
            BuildingProp(
                center = Vec3(138f, 0f, 84f),
                width = 16f,
                height = 19f,
                depth = 28f,
                yaw = -0.25f,
                color = Color(0xFF18202C),
                neonColor = Color(0xFF00E676)
            )
        )
        // Shipping containers near the open industrial drift yard
        buildings.add(
            BuildingProp(
                center = Vec3(136f, 0f, -45f),
                width = 12f,
                height = 6.5f,
                depth = 26f,
                yaw = 0.2f,
                color = Color(0xFF8B2635),
                neonColor = Color(0xFFFF9E00)
            )
        )
        buildings.add(
            BuildingProp(
                center = Vec3(54f, 0f, -52f),
                width = 14f,
                height = 9f,
                depth = 20f,
                yaw = -0.5f,
                color = Color(0xFF1B3B5A),
                neonColor = Color(0xFF00F0FF)
            )
        )
        buildings.add(
            BuildingProp(
                center = Vec3(-22f, 0f, -42f),
                width = 16f,
                height = 18f,
                depth = 28f,
                yaw = 0f,
                color = Color(0xFF161D29),
                neonColor = Color(0xFFFF2A54)
            )
        )
    }

    fun getCheckpointTransform(): Triple<Float, Float, Float> {
        val seg = segments[lastCheckpointSegment.coerceIn(0, segments.lastIndex)]
        val heading = atan2(seg.forwardX, seg.forwardZ)
        return Triple(seg.p0.x, seg.p0.z, heading)
    }

    fun resetCones() {
        for (c in cones) c.reset()
    }

    fun evaluateSurfaceAndCollisions(
        dt: Float,
        vehicle: Vehicle
    ): Pair<SurfaceType, CollisionResult> {
        // Find closest road segment to vehicle
        var bestSeg = segments[0]
        var bestDistSq = Float.MAX_VALUE
        var bestProj = 0f
        var bestLatOffset = 0f

        for (seg in segments) {
            val vx = vehicle.posX - seg.p0.x
            val vz = vehicle.posZ - seg.p0.z
            val proj = Math3D.clamp(vx * seg.forwardX + vz * seg.forwardZ, 0f, seg.length)
            val closestX = seg.p0.x + seg.forwardX * proj
            val closestZ = seg.p0.z + seg.forwardZ * proj
            val dx = vehicle.posX - closestX
            val dz = vehicle.posZ - closestZ
            val distSq = dx * dx + dz * dz
            if (distSq < bestDistSq) {
                bestDistSq = distSq
                bestSeg = seg
                bestProj = proj
                bestLatOffset = vx * seg.normalX + vz * seg.normalZ
            }
        }

        // Update checkpoint when on or near road
        val absLat = kotlin.math.abs(bestLatOffset)
        if (absLat < bestSeg.halfWidth + 2.5f) {
            lastCheckpointSegment = bestSeg.index
        }

        // Determine surface under tires
        val surface = when {
            absLat <= bestSeg.halfWidth - 0.85f -> SurfaceType.ASPHALT
            absLat <= bestSeg.halfWidth + 0.65f -> SurfaceType.PAINT
            else -> SurfaceType.GRASS
        }

        // Check clipping zone bonus proximity
        val inClipping = bestSeg.isClippingZone && (
            (bestSeg.clippingSide > 0 && bestLatOffset > bestSeg.halfWidth * 0.42f && bestLatOffset <= bestSeg.halfWidth + 0.5f) ||
                (bestSeg.clippingSide < 0 && bestLatOffset < -bestSeg.halfWidth * 0.42f && bestLatOffset >= -bestSeg.halfWidth - 0.5f)
            )

        // Check barrier proximity and collision
        val barrierDist = bestSeg.halfWidth + 1.35f
        val nearBarrier = absLat > barrierDist - 2.1f && absLat < barrierDist

        var hitBarrier = false
        var impactPoint: Vec3? = null
        var impactIntensity = 0f

        val hitLeftBarrier = bestSeg.hasBarrierLeft && bestLatOffset < -barrierDist
        val hitRightBarrier = bestSeg.hasBarrierRight && bestLatOffset > barrierDist

        if (hitLeftBarrier || hitRightBarrier) {
            hitBarrier = true
            val sign = if (hitRightBarrier) 1f else -1f
            val clampedOffset = sign * (barrierDist - 0.15f)
            val baseX = bestSeg.p0.x + bestSeg.forwardX * bestProj
            val baseZ = bestSeg.p0.z + bestSeg.forwardZ * bestProj
            vehicle.posX = baseX + bestSeg.normalX * clampedOffset
            vehicle.posZ = baseZ + bestSeg.normalZ * clampedOffset

            // Reflect lateral velocity off the barrier and damp speed
            val normalVel = vehicle.velX * bestSeg.normalX + vehicle.velZ * bestSeg.normalZ
            impactIntensity = kotlin.math.abs(normalVel)
            if (normalVel * sign > 0f) {
                vehicle.velX -= bestSeg.normalX * normalVel * 1.45f
                vehicle.velZ -= bestSeg.normalZ * normalVel * 1.45f
            }
            vehicle.velX *= 0.76f
            vehicle.velZ *= 0.76f
            vehicle.yawRate *= 0.5f

            impactPoint = Vec3(
                baseX + bestSeg.normalX * sign * barrierDist,
                0.55f,
                baseZ + bestSeg.normalZ * sign * barrierDist
            )
        }

        // Update cones & check cone collisions
        var hitCone = false
        for (cone in cones) {
            if (cone.knocked) {
                cone.x += cone.vx * dt
                cone.y = max(0f, cone.y + cone.vy * dt)
                cone.z += cone.vz * dt
                cone.vy -= 9.8f * dt
                cone.vx *= (1f - 1.8f * dt).coerceAtLeast(0f)
                cone.vz *= (1f - 1.8f * dt).coerceAtLeast(0f)
            } else {
                val dx = cone.x - vehicle.posX
                val dz = cone.z - vehicle.posZ
                if (dx * dx + dz * dz < 2.2f) {
                    cone.knocked = true
                    cone.vx = vehicle.velX * 1.15f + dx * 3f
                    cone.vy = 3.8f
                    cone.vz = vehicle.velZ * 1.15f + dz * 3f
                    // Slight speed scrub on cone hit
                    vehicle.velX *= 0.96f
                    vehicle.velZ *= 0.96f
                    hitCone = true
                }
            }
        }

        return surface to CollisionResult(
            hitBarrier = hitBarrier,
            hitCone = hitCone,
            nearBarrierProximity = nearBarrier,
            inClippingZone = inClipping,
            impactPoint = impactPoint,
            impactIntensity = impactIntensity
        )
    }
}
