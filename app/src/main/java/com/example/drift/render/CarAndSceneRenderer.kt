package com.example.drift.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.drift.effects.EffectsSystem
import com.example.drift.effects.ParticleType
import com.example.drift.math.Face3D
import com.example.drift.math.Math3D
import com.example.drift.math.ProjectedFace
import com.example.drift.math.Vec3
import com.example.drift.model.CarPalette
import com.example.drift.model.GarageUpgrades
import com.example.drift.model.GraphicsQuality
import com.example.drift.physics.Vehicle
import com.example.drift.score.CameraController
import com.example.drift.track.Track
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

class CarAndSceneRenderer {
    private val reusablePath = Path()
    private val facePool = Array(1800) { ProjectedFace() }
    private var activeFaceCount = 0

    // Directional moonlight / street light direction
    private val sunLightDir = Vec3(0.38f, 0.84f, -0.38f).normalized()

    // Camera basis vectors
    private var camPos = Vec3()
    private var camForward = Vec3(0f, 0f, 1f)
    private var camRight = Vec3(1f, 0f, 0f)
    private var camUp = Vec3(0f, 1f, 0f)
    private var focalLength = 1f
    private var halfW = 1f
    private var halfH = 1f

    private fun setupCameraBasis(
        eye: Vec3,
        target: Vec3,
        fovDeg: Float,
        viewportW: Float,
        viewportH: Float
    ) {
        camPos.set(eye.x, eye.y, eye.z)
        camForward = (target - eye).normalized()
        val worldUp = Vec3(0f, 1f, 0f)
        camRight = worldUp.cross(camForward).normalized()
        camUp = camForward.cross(camRight).normalized()

        halfW = viewportW * 0.5f
        halfH = viewportH * 0.5f
        val fovRad = fovDeg * Math3D.DEG_TO_RAD
        focalLength = halfH / max(0.2f, tan(fovRad * 0.5f))
    }

    private fun projectPoint(world: Vec3, outX: FloatArray, outY: FloatArray, idx: Int): Float {
        val dx = world.x - camPos.x
        val dy = world.y - camPos.y
        val dz = world.z - camPos.z

        val cz = dx * camForward.x + dy * camForward.y + dz * camForward.z
        if (cz < 0.35f) return -1f

        val cx = dx * camRight.x + dy * camRight.y + dz * camRight.z
        val cy = dx * camUp.x + dy * camUp.y + dz * camUp.z

        val invZ = focalLength / cz
        outX[idx] = halfW + cx * invZ
        outY[idx] = halfH - cy * invZ
        return cz
    }

    private fun enqueueWorldQuad(
        v0: Vec3,
        v1: Vec3,
        v2: Vec3,
        v3: Vec3,
        color: Color,
        emissive: Boolean = false,
        strokeColor: Color? = null,
        depthBias: Float = 0f
    ) {
        if (activeFaceCount >= facePool.size) return
        val pf = facePool[activeFaceCount]
        val z0 = projectPoint(v0, pf.sx, pf.sy, 0)
        if (z0 < 0f) return
        val z1 = projectPoint(v1, pf.sx, pf.sy, 1)
        if (z1 < 0f) return
        val z2 = projectPoint(v2, pf.sx, pf.sy, 2)
        if (z2 < 0f) return
        val z3 = projectPoint(v3, pf.sx, pf.sy, 3)
        if (z3 < 0f) return

        // Quick screen bounds check
        val minX = minOf(pf.sx[0], pf.sx[1], pf.sx[2], pf.sx[3])
        val maxX = maxOf(pf.sx[0], pf.sx[1], pf.sx[2], pf.sx[3])
        val minY = minOf(pf.sy[0], pf.sy[1], pf.sy[2], pf.sy[3])
        val maxY = maxOf(pf.sy[0], pf.sy[1], pf.sy[2], pf.sy[3])
        if (maxX < -100f || minX > halfW * 2f + 100f || maxY < -100f || minY > halfH * 2f + 100f) return

        pf.vertexCount = 4
        pf.depth = (z0 + z1 + z2 + z3) * 0.25f + depthBias

        if (emissive) {
            pf.shadedColor = color
        } else {
            val e1 = v1 - v0
            val e2 = v2 - v0
            val normal = e1.cross(e2).normalized()
            pf.shadedColor = Math3D.shadeColor(color, normal, sunLightDir, false)
        }
        pf.strokeColor = strokeColor
        activeFaceCount++
    }

    private fun enqueueWorldTri(
        v0: Vec3,
        v1: Vec3,
        v2: Vec3,
        color: Color,
        emissive: Boolean = false,
        strokeColor: Color? = null,
        depthBias: Float = 0f
    ) {
        if (activeFaceCount >= facePool.size) return
        val pf = facePool[activeFaceCount]
        val z0 = projectPoint(v0, pf.sx, pf.sy, 0)
        if (z0 < 0f) return
        val z1 = projectPoint(v1, pf.sx, pf.sy, 1)
        if (z1 < 0f) return
        val z2 = projectPoint(v2, pf.sx, pf.sy, 2)
        if (z2 < 0f) return

        pf.vertexCount = 3
        pf.depth = (z0 + z1 + z2) * 0.3333f + depthBias
        if (emissive) {
            pf.shadedColor = color
        } else {
            val normal = (v1 - v0).cross(v2 - v0).normalized()
            pf.shadedColor = Math3D.shadeColor(color, normal, sunLightDir, false)
        }
        pf.strokeColor = strokeColor
        activeFaceCount++
    }

    fun renderDrivingScene(
        drawScope: DrawScope,
        vehicle: Vehicle,
        track: Track,
        camera: CameraController,
        effects: EffectsSystem,
        upgrades: GarageUpgrades,
        quality: GraphicsQuality
    ) = with(drawScope) {
        val w = size.width
        val h = size.height
        if (w <= 1f || h <= 1f) return

        setupCameraBasis(camera.camPos, camera.lookAtPos, camera.currentFov, w, h)
        activeFaceCount = 0

        // 1. Sky & Horizon Backdrop (Japanese Touge Midnight/Twilight Atmosphere)
        val horizonRatio = Math3D.clamp(0.42f + camForward.y * 0.45f, 0.2f, 0.68f)
        val horizonY = h * horizonRatio

        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF060913),
                    Color(0xFF0F172A),
                    Color(0xFF1E1B38),
                    Color(0xFF2D1E3E)
                ),
                startY = 0f,
                endY = horizonY
            ),
            topLeft = Offset.Zero,
            size = Size(w, horizonY)
        )

        // Ground base fill
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF0E1716),
                    Color(0xFF0B1114),
                    Color(0xFF080C10)
                ),
                startY = horizonY,
                endY = h
            ),
            topLeft = Offset(0f, horizonY),
            size = Size(w, h - horizonY)
        )

        // Stylized low-poly mountain silhouette on horizon
        drawMountainSilhouette(w, horizonY, camera.currentYaw)

        val maxDistSq = quality.drawDistance * quality.drawDistance

        // 2. Enqueue Track Road Segments, Curbs, Clipping Zones & Barriers
        for (seg in track.segments) {
            val dx = seg.p0.x - camPos.x
            val dz = seg.p0.z - camPos.z
            val distSq = dx * dx + dz * dz
            if (distSq > maxDistSq) continue

            // Asphalt road surface
            val roadColor = if (seg.index % 2 == 0) Color(0xFF1E242D) else Color(0xFF222933)
            enqueueWorldQuad(
                seg.left0, seg.right0, seg.right1, seg.left1,
                color = roadColor,
                emissive = true,
                depthBias = 1.5f
            )

            // Center dashed line or start/finish line
            if (seg.index == 0) {
                // Start/finish checkered banner strip
                val sl0 = Vec3(
                    Math3D.lerp(seg.left0.x, seg.right0.x, 0.1f),
                    0.02f,
                    Math3D.lerp(seg.left0.z, seg.right0.z, 0.1f)
                )
                val sr0 = Vec3(
                    Math3D.lerp(seg.left0.x, seg.right0.x, 0.9f),
                    0.02f,
                    Math3D.lerp(seg.left0.z, seg.right0.z, 0.9f)
                )
                val sl1 = Vec3(
                    sl0.x + seg.forwardX * 1.8f,
                    0.02f,
                    sl0.z + seg.forwardZ * 1.8f
                )
                val sr1 = Vec3(
                    sr0.x + seg.forwardX * 1.8f,
                    0.02f,
                    sr0.z + seg.forwardZ * 1.8f
                )
                enqueueWorldQuad(sl0, sr0, sr1, sl1, Color(0xFFF0F4F8), emissive = true, depthBias = 1.1f)
            } else if (seg.index % 2 == 0) {
                val c0L = Vec3(seg.p0.x - seg.normalX * 0.16f, 0.02f, seg.p0.z - seg.normalZ * 0.16f)
                val c0R = Vec3(seg.p0.x + seg.normalX * 0.16f, 0.02f, seg.p0.z + seg.normalZ * 0.16f)
                val c1L = Vec3(seg.p1.x - seg.normalX * 0.16f, 0.02f, seg.p1.z - seg.normalZ * 0.16f)
                val c1R = Vec3(seg.p1.x + seg.normalX * 0.16f, 0.02f, seg.p1.z + seg.normalZ * 0.16f)
                enqueueWorldQuad(
                    c0L, c0R, c1R, c1L,
                    color = Color(0xFF64748B),
                    emissive = true,
                    depthBias = 1.2f
                )
            }

            // Left and Right Painted Curbs (Red/White or Neon Cyan/Amber on Clipping Zones)
            val curbW = 0.75f
            val curbColor = if (seg.isClippingZone) {
                if (seg.index % 2 == 0) Color(0xFF00F0FF) else Color(0xFFFF9E00)
            } else {
                if (seg.index % 2 == 0) Color(0xFFE53935) else Color(0xFFECEFF1)
            }

            val lOuter0 = Vec3(seg.left0.x - seg.normalX * curbW, 0.03f, seg.left0.z - seg.normalZ * curbW)
            val lOuter1 = Vec3(seg.left1.x - seg.normalX * curbW, 0.03f, seg.left1.z - seg.normalZ * curbW)
            enqueueWorldQuad(lOuter0, seg.left0, seg.left1, lOuter1, curbColor, emissive = seg.isClippingZone, depthBias = 1.1f)

            val rOuter0 = Vec3(seg.right0.x + seg.normalX * curbW, 0.03f, seg.right0.z + seg.normalZ * curbW)
            val rOuter1 = Vec3(seg.right1.x + seg.normalX * curbW, 0.03f, seg.right1.z + seg.normalZ * curbW)
            enqueueWorldQuad(seg.right0, rOuter0, rOuter1, seg.right1, curbColor, emissive = seg.isClippingZone, depthBias = 1.1f)

            // Concrete Barriers with red/white street drift stripes
            if (distSq < maxDistSq * 0.75f) {
                val bDist = seg.halfWidth + 1.35f
                val bHeight = 0.78f
                val bColor = if (seg.index % 2 == 0) Color(0xFF334155) else Color(0xFF475569)
                val stripeColor = if (seg.index % 2 == 0) Color(0xFFFF2A54) else Color(0xFFF8FAFC)

                if (seg.hasBarrierLeft) {
                    val bl0 = Vec3(seg.p0.x - seg.normalX * bDist, 0f, seg.p0.z - seg.normalZ * bDist)
                    val bl1 = Vec3(seg.p1.x - seg.normalX * bDist, 0f, seg.p1.z - seg.normalZ * bDist)
                    val blTop0 = Vec3(bl0.x, bHeight, bl0.z)
                    val blTop1 = Vec3(bl1.x, bHeight, bl1.z)
                    enqueueWorldQuad(bl0, bl1, blTop1, blTop0, bColor, strokeColor = stripeColor)
                }
                if (seg.hasBarrierRight) {
                    val br0 = Vec3(seg.p0.x + seg.normalX * bDist, 0f, seg.p0.z + seg.normalZ * bDist)
                    val br1 = Vec3(seg.p1.x + seg.normalX * bDist, 0f, seg.p1.z + seg.normalZ * bDist)
                    val brTop0 = Vec3(br0.x, bHeight, br0.z)
                    val brTop1 = Vec3(br1.x, bHeight, br1.z)
                    enqueueWorldQuad(br1, br0, brTop0, brTop1, bColor, strokeColor = stripeColor)
                }
            }
        }

        // 3. Enqueue Tire Skid Marks on Asphalt
        for (mark in effects.skidMarks) {
            val dx = mark.x0 - camPos.x
            val dz = mark.z0 - camPos.z
            if (dx * dx + dz * dz > 7500f) continue
            val m0 = Vec3(mark.x0 - 0.12f, 0.04f, mark.z0)
            val m1 = Vec3(mark.x0 + 0.12f, 0.04f, mark.z0)
            val m2 = Vec3(mark.x1 + 0.12f, 0.04f, mark.z1)
            val m3 = Vec3(mark.x1 - 0.12f, 0.04f, mark.z1)
            enqueueWorldQuad(
                m0, m1, m2, m3,
                color = Color(0xFF090B0E).copy(alpha = mark.alpha),
                emissive = true,
                depthBias = 0.85f
            )
        }

        // 4. Enqueue Buildings, Shipping Containers & Neon Signs
        for (b in track.buildings) {
            val dx = b.center.x - camPos.x
            val dz = b.center.z - camPos.z
            if (dx * dx + dz * dz > maxDistSq * 1.3f) continue
            enqueueBox3D(
                posX = b.center.x,
                posY = 0f,
                posZ = b.center.z,
                halfW = b.width * 0.5f,
                height = b.height,
                halfD = b.depth * 0.5f,
                yaw = b.yaw,
                baseColor = b.color,
                roofColor = Color(0xFF0F141D),
                neonBannerColor = b.neonColor
            )
        }

        // 5. Enqueue Street Lights (only if MEDIUM or HIGH graphics)
        if (quality != GraphicsQuality.LOW) {
            for (light in track.streetLights) {
                val dx = light.x - camPos.x
                val dz = light.z - camPos.z
                if (dx * dx + dz * dz > 11000f) continue
                val pBase1 = Vec3(light.x - 0.1f, 0f, light.z)
                val pBase2 = Vec3(light.x + 0.1f, 0f, light.z)
                val pTop1 = Vec3(light.x - 0.08f, 6.2f, light.z)
                val pTop2 = Vec3(light.x + 0.08f, 6.2f, light.z)
                enqueueWorldQuad(pBase1, pBase2, pTop2, pTop1, Color(0xFF475569), emissive = true)

                // Lamp head glow
                val armX = light.x + sin(light.yaw) * 1.8f
                val armZ = light.z + cos(light.yaw) * 1.8f
                val l0 = Vec3(armX - 0.35f, 6.1f, armZ - 0.35f)
                val l1 = Vec3(armX + 0.35f, 6.1f, armZ - 0.35f)
                val l2 = Vec3(armX + 0.35f, 6.1f, armZ + 0.35f)
                val l3 = Vec3(armX - 0.35f, 6.1f, armZ + 0.35f)
                enqueueWorldQuad(l0, l1, l2, l3, Color(0xFFFFE082), emissive = true)
            }
        }

        // 6. Enqueue Knockable 3D Traffic Cones
        for (cone in track.cones) {
            val dx = cone.x - camPos.x
            val dz = cone.z - camPos.z
            if (dx * dx + dz * dz > 6400f) continue
            val r = 0.28f
            val hCone = 0.68f
            val apex = Vec3(cone.x, cone.y + hCone, cone.z)
            val b0 = Vec3(cone.x - r, cone.y, cone.z - r)
            val b1 = Vec3(cone.x + r, cone.y, cone.z - r)
            val b2 = Vec3(cone.x + r, cone.y, cone.z + r)
            val b3 = Vec3(cone.x - r, cone.y, cone.z + r)
            val coneOrange = Color(0xFFFF6D00)
            enqueueWorldTri(b0, b1, apex, coneOrange, emissive = true)
            enqueueWorldTri(b1, b2, apex, coneOrange, emissive = true)
            enqueueWorldTri(b2, b3, apex, coneOrange, emissive = true)
            enqueueWorldTri(b3, b0, apex, coneOrange, emissive = true)
        }

        // 7. Enqueue Headlight Ground Beams ahead of car
        val hlLeft0 = Math3D.transformLocalToWorld(Vec3(-0.62f, 0.05f, 2.15f), vehicle.posX, 0f, vehicle.posZ, vehicle.yaw)
        val hlRight0 = Math3D.transformLocalToWorld(Vec3(0.62f, 0.05f, 2.15f), vehicle.posX, 0f, vehicle.posZ, vehicle.yaw)
        val hlFarLeft = Math3D.transformLocalToWorld(Vec3(-2.8f, 0.05f, 13.5f), vehicle.posX, 0f, vehicle.posZ, vehicle.yaw)
        val hlFarRight = Math3D.transformLocalToWorld(Vec3(2.8f, 0.05f, 13.5f), vehicle.posX, 0f, vehicle.posZ, vehicle.yaw)
        enqueueWorldQuad(
            hlLeft0, hlRight0, hlFarRight, hlFarLeft,
            color = Color(0xFF00F0FF).copy(alpha = 0.13f),
            emissive = true,
            depthBias = 0.6f
        )

        // 8. Enqueue Original 3D Low-Poly Japanese Drift Coupe ("KAZE RS-13 SHINOBI")
        enqueueOriginalDriftCar(
            posX = vehicle.posX,
            posY = vehicle.posY,
            posZ = vehicle.posZ,
            yaw = vehicle.yaw,
            pitch = vehicle.pitchAngleRad,
            roll = vehicle.rollAngleRad,
            steerRad = vehicle.effectiveSteerRad,
            frontWheelSpin = vehicle.frontWheelRotationRad,
            rearWheelSpin = vehicle.rearWheelRotationRad,
            isBraking = vehicle.brakeInput > 0.05f || vehicle.handbrakeInput,
            upgrades = upgrades
        )

        // 9. Enqueue 3D Particles (Tire Smoke, Dust, Sparks, Exhaust Flame)
        for (p in effects.particles) {
            val lifeRatio = (p.age / p.maxLife).coerceIn(0f, 1f)
            val alpha = (1f - lifeRatio) * p.color.alpha
            if (alpha <= 0.02f) continue
            val r = p.radius
            // Billboard quad facing camera
            val rx = camRight.x * r
            val rz = camRight.z * r
            val uy = camUp.y * r
            val v0 = Vec3(p.x - rx, p.y - uy, p.z - rz)
            val v1 = Vec3(p.x + rx, p.y - uy, p.z + rz)
            val v2 = Vec3(p.x + rx, p.y + uy, p.z + rz)
            val v3 = Vec3(p.x - rx, p.y + uy, p.z - rz)
            enqueueWorldQuad(
                v0, v1, v2, v3,
                color = p.color.copy(alpha = alpha),
                emissive = true,
                depthBias = if (p.type == ParticleType.SPARK) -0.3f else -0.1f
            )
        }

        // 10. Sort Projected Faces back-to-front (Painter's Algorithm) and Draw
        flushProjectedFaces()
    }

    fun renderGarageScene(
        drawScope: DrawScope,
        turntableYaw: Float,
        upgrades: GarageUpgrades
    ) = with(drawScope) {
        val w = size.width
        val h = size.height
        if (w <= 1f || h <= 1f) return

        val eye = Vec3(sin(turntableYaw) * 5.2f, 1.85f, cos(turntableYaw) * 5.2f)
        val target = Vec3(0f, 0.55f, 0f)
        setupCameraBasis(eye, target, 52f, w, h)
        activeFaceCount = 0

        // Underground Tokyo Garage Backdrop
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF070A10), Color(0xFF101724), Color(0xFF0A0E17))
            )
        )

        // Neon Turntable Ring
        val segments = 24
        val ringR = 2.75f
        val innerR = 2.55f
        for (i in 0 until segments) {
            val a0 = (i.toFloat() / segments) * 2f * Math.PI.toFloat()
            val a1 = ((i + 1).toFloat() / segments) * 2f * Math.PI.toFloat()
            val p0 = Vec3(sin(a0) * innerR, 0.01f, cos(a0) * innerR)
            val p1 = Vec3(sin(a0) * ringR, 0.01f, cos(a0) * ringR)
            val p2 = Vec3(sin(a1) * ringR, 0.01f, cos(a1) * ringR)
            val p3 = Vec3(sin(a1) * innerR, 0.01f, cos(a1) * innerR)
            val col = if (i % 2 == 0) Color(0xFF00F0FF) else Color(0xFF1E293B)
            enqueueWorldQuad(p0, p1, p2, p3, col, emissive = true, depthBias = 1.0f)

            // Turntable floor pie
            val center = Vec3(0f, 0f, 0f)
            enqueueWorldTri(center, p0, p3, Color(0xFF141B26), emissive = true, depthBias = 1.2f)
        }

        // Render the customizable 3D Drift Car on the turntable
        val maxLockPreview = (upgrades.maxSteeringDegrees * 0.65f) * Math3D.DEG_TO_RAD
        enqueueOriginalDriftCar(
            posX = 0f,
            posY = 0.02f,
            posZ = 0f,
            yaw = 0f,
            pitch = 0f,
            roll = 0f,
            steerRad = maxLockPreview,
            frontWheelSpin = turntableYaw * 2f,
            rearWheelSpin = turntableYaw * 2f,
            isBraking = true,
            upgrades = upgrades
        )

        flushProjectedFaces()
    }

    private fun DrawScope.drawMountainSilhouette(w: Float, horizonY: Float, camYaw: Float) {
        val peaks = floatArrayOf(0.12f, 0.28f, 0.19f, 0.44f, 0.31f, 0.16f, 0.38f, 0.22f, 0.14f)
        val mountainPath = reusablePath
        mountainPath.reset()
        mountainPath.moveTo(0f, horizonY)
        val shift = ((camYaw / (2f * Math.PI.toFloat())) * w * 1.5f) % w
        val step = w / (peaks.size - 1)
        for (i in peaks.indices) {
            val px = i * step
            val idx = ((i + (shift / step).toInt()) % peaks.size + peaks.size) % peaks.size
            val py = horizonY - peaks[idx] * 95f
            mountainPath.lineTo(px, py)
        }
        mountainPath.lineTo(w, horizonY)
        mountainPath.close()
        drawPath(mountainPath, color = Color(0xFF111827))
    }

    private fun enqueueBox3D(
        posX: Float,
        posY: Float,
        posZ: Float,
        halfW: Float,
        height: Float,
        halfD: Float,
        yaw: Float,
        baseColor: Color,
        roofColor: Color,
        neonBannerColor: Color?
    ) {
        val b0 = Math3D.transformLocalToWorld(Vec3(-halfW, 0f, -halfD), posX, posY, posZ, yaw)
        val b1 = Math3D.transformLocalToWorld(Vec3(halfW, 0f, -halfD), posX, posY, posZ, yaw)
        val b2 = Math3D.transformLocalToWorld(Vec3(halfW, 0f, halfD), posX, posY, posZ, yaw)
        val b3 = Math3D.transformLocalToWorld(Vec3(-halfW, 0f, halfD), posX, posY, posZ, yaw)

        val t0 = Vec3(b0.x, posY + height, b0.z)
        val t1 = Vec3(b1.x, posY + height, b1.z)
        val t2 = Vec3(b2.x, posY + height, b2.z)
        val t3 = Vec3(b3.x, posY + height, b3.z)

        enqueueWorldQuad(b0, b1, t1, t0, baseColor)
        enqueueWorldQuad(b1, b2, t2, t1, baseColor)
        enqueueWorldQuad(b2, b3, t3, t2, baseColor)
        enqueueWorldQuad(b3, b0, t0, t3, baseColor)
        enqueueWorldQuad(t0, t1, t2, t3, roofColor)

        if (neonBannerColor != null) {
            val nY0 = posY + height * 0.68f
            val nY1 = posY + height * 0.82f
            val n0 = Vec3(b0.x, nY0, b0.z)
            val n1 = Vec3(b1.x, nY0, b1.z)
            val n2 = Vec3(b1.x, nY1, b1.z)
            val n3 = Vec3(b0.x, nY1, b0.z)
            enqueueWorldQuad(n0, n1, n2, n3, neonBannerColor, emissive = true, depthBias = -0.05f)
        }
    }

    /**
     * Original 3D Low-Poly Japanese Street Drift Coupe ("KAZE RS-13 SHINOBI")
     * Features widebody fenders, aggressive front splitter, hood vent, fastback cabin,
     * tall GT wing spoiler, dual exhaust, headlights, brake lights, and 4 rotating/steering 3D wheels.
     */
    private fun enqueueOriginalDriftCar(
        posX: Float,
        posY: Float,
        posZ: Float,
        yaw: Float,
        pitch: Float,
        roll: Float,
        steerRad: Float,
        frontWheelSpin: Float,
        rearWheelSpin: Float,
        isBraking: Boolean,
        upgrades: GarageUpgrades
    ) {
        val bodyColor = CarPalette.bodyColors[upgrades.carColorIndex % CarPalette.bodyColors.size].second
        val rimColor = CarPalette.rimColors[upgrades.rimColorIndex % CarPalette.rimColors.size].second
        val carbonDark = Color(0xFF151921)
        val glassDark = Color(0xFF111C2D)
        val trimBlack = Color(0xFF0D1017)

        fun tr(lx: Float, ly: Float, lz: Float): Vec3 =
            Math3D.transformLocalToWorld(Vec3(lx, ly, lz), posX, posY, posZ, yaw, pitch, roll)

        // Fake underbody shadow quad on ground
        val shFL = Math3D.transformLocalToWorld(Vec3(-1.02f, 0.03f, 2.22f), posX, 0f, posZ, yaw)
        val shFR = Math3D.transformLocalToWorld(Vec3(1.02f, 0.03f, 2.22f), posX, 0f, posZ, yaw)
        val shRR = Math3D.transformLocalToWorld(Vec3(1.02f, 0.03f, -2.22f), posX, 0f, posZ, yaw)
        val shRL = Math3D.transformLocalToWorld(Vec3(-1.02f, 0.03f, -2.22f), posX, 0f, posZ, yaw)
        enqueueWorldQuad(shFL, shFR, shRR, shRL, Color(0xFF04060A).copy(alpha = 0.78f), emissive = true, depthBias = 0.5f)

        // Key chassis vertices (Width: ~1.90m widebody, Length: ~4.28m, Height: ~1.22m low stance)
        val spFL = tr(-0.94f, 0.14f, 2.18f)
        val spFR = tr(0.94f, 0.14f, 2.18f)
        val bmpFL = tr(-0.92f, 0.20f, 2.10f)
        val bmpFR = tr(0.92f, 0.20f, 2.10f)

        val noseFL = tr(-0.88f, 0.56f, 2.04f)
        val noseFR = tr(0.88f, 0.56f, 2.04f)

        val hoodRL = tr(-0.88f, 0.68f, 0.78f)
        val hoodRR = tr(0.88f, 0.68f, 0.78f)

        // Widebody blister sills
        val sillFL = tr(-0.96f, 0.18f, 1.35f)
        val sillFR = tr(0.96f, 0.18f, 1.35f)
        val sillRL = tr(-0.96f, 0.18f, -1.35f)
        val sillRR = tr(0.96f, 0.18f, -1.35f)

        // Beltline mid/rear
        val beltML = tr(-0.94f, 0.70f, -0.35f)
        val beltMR = tr(0.94f, 0.70f, -0.35f)
        val deckRL = tr(-0.88f, 0.72f, -1.98f)
        val deckRR = tr(0.88f, 0.72f, -1.98f)
        val rearBotL = tr(-0.90f, 0.24f, -2.06f)
        val rearBotR = tr(0.90f, 0.24f, -2.06f)

        // Cabin greenhouse
        val roofFL = tr(-0.68f, 1.16f, 0.16f)
        val roofFR = tr(0.68f, 1.16f, 0.16f)
        val roofRL = tr(-0.66f, 1.14f, -0.82f)
        val roofRR = tr(0.66f, 1.14f, -0.82f)
        val trunkBaseL = tr(-0.78f, 0.72f, -1.52f)
        val trunkBaseR = tr(0.78f, 0.72f, -1.52f)

        // Front carbon lip splitter
        enqueueWorldQuad(spFL, spFR, bmpFR, bmpFL, carbonDark)
        // Front bumper & intercooler mouth
        enqueueWorldQuad(bmpFL, bmpFR, noseFR, noseFL, bodyColor)
        val icBL = tr(-0.48f, 0.22f, 2.09f)
        val icBR = tr(0.48f, 0.22f, 2.09f)
        val icTL = tr(-0.48f, 0.42f, 2.07f)
        val icTR = tr(0.48f, 0.42f, 2.07f)
        enqueueWorldQuad(icBL, icBR, icTR, icTL, trimBlack, emissive = true, depthBias = -0.02f)

        // Headlights (glowing Cyber Cyan/White LED strips)
        val hlL0 = tr(-0.84f, 0.46f, 2.06f)
        val hlL1 = tr(-0.52f, 0.45f, 2.07f)
        val hlL2 = tr(-0.52f, 0.54f, 2.05f)
        val hlL3 = tr(-0.84f, 0.55f, 2.05f)
        enqueueWorldQuad(hlL0, hlL1, hlL2, hlL3, Color(0xFFE0F7FA), emissive = true, depthBias = -0.03f)

        val hlR0 = tr(0.52f, 0.45f, 2.07f)
        val hlR1 = tr(0.84f, 0.46f, 2.06f)
        val hlR2 = tr(0.84f, 0.55f, 2.05f)
        val hlR3 = tr(0.52f, 0.54f, 2.05f)
        enqueueWorldQuad(hlR0, hlR1, hlR2, hlR3, Color(0xFFE0F7FA), emissive = true, depthBias = -0.03f)

        // Sculpted Hood + Aero Vent
        enqueueWorldQuad(noseFL, noseFR, hoodRR, hoodRL, bodyColor)
        val ventFL = tr(-0.36f, 0.63f, 1.55f)
        val ventFR = tr(0.36f, 0.63f, 1.55f)
        val ventRL = tr(-0.42f, 0.66f, 1.10f)
        val ventRR = tr(0.42f, 0.66f, 1.10f)
        enqueueWorldQuad(ventFL, ventFR, ventRR, ventRL, carbonDark, depthBias = -0.02f)

        // Windshield, Roof & Fastback Rear Window
        enqueueWorldQuad(hoodRL, hoodRR, roofFR, roofFL, glassDark, strokeColor = trimBlack)
        enqueueWorldQuad(roofFL, roofFR, roofRR, roofRL, bodyColor)
        enqueueWorldQuad(roofRL, roofRR, trunkBaseR, trunkBaseL, glassDark, strokeColor = trimBlack)

        // Side Windows (Left & Right)
        enqueueWorldQuad(hoodRL, roofFL, roofRL, beltML, glassDark)
        enqueueWorldQuad(beltML, roofRL, trunkBaseL, deckRL, bodyColor)
        enqueueWorldQuad(hoodRR, beltMR, roofRR, roofFR, glassDark)
        enqueueWorldQuad(beltMR, deckRR, trunkBaseR, roofRR, bodyColor)

        // Widebody Left Flank & Side Skirt
        enqueueWorldQuad(bmpFL, noseFL, hoodRL, sillFL, bodyColor)
        enqueueWorldQuad(sillFL, hoodRL, beltML, sillRL, bodyColor)
        enqueueWorldQuad(sillRL, beltML, deckRL, rearBotL, bodyColor)

        // Widebody Right Flank & Side Skirt
        enqueueWorldQuad(noseFR, bmpFR, sillFR, hoodRR, bodyColor)
        enqueueWorldQuad(hoodRR, sillFR, sillRR, beltMR, bodyColor)
        enqueueWorldQuad(beltMR, sillRR, rearBotR, deckRR, bodyColor)

        // Rear Trunk Deck & Rear Tail Panel
        enqueueWorldQuad(trunkBaseL, trunkBaseR, deckRR, deckRL, bodyColor)
        enqueueWorldQuad(rearBotL, rearBotR, deckRR, deckRL, carbonDark)

        // Rear Tail / Brake Lights (Kouki-style full-width LED bar + dual rings)
        val tailColor = if (isBraking) Color(0xFFFF1744) else Color(0xFF990F2B)
        val tlL = tr(-0.84f, 0.50f, -2.04f)
        val tlR = tr(0.84f, 0.50f, -2.04f)
        val tlTopR = tr(0.84f, 0.64f, -2.01f)
        val tlTopL = tr(-0.84f, 0.64f, -2.01f)
        enqueueWorldQuad(tlL, tlR, tlTopR, tlTopL, tailColor, emissive = true, depthBias = -0.04f)

        // Dual Angled Titanium Exhaust Tips
        val exL0 = tr(-0.62f, 0.20f, -2.12f)
        val exL1 = tr(-0.44f, 0.20f, -2.12f)
        val exL2 = tr(-0.44f, 0.30f, -2.12f)
        val exL3 = tr(-0.62f, 0.30f, -2.12f)
        enqueueWorldQuad(exL0, exL1, exL2, exL3, Color(0xFF90A4AE), emissive = true, depthBias = -0.05f)

        // Tall GT Rear Wing Spoiler (stays + blade + endplates)
        val wingFL = tr(-0.86f, 0.98f, -1.78f)
        val wingFR = tr(0.86f, 0.98f, -1.78f)
        val wingRL = tr(-0.86f, 1.03f, -2.06f)
        val wingRR = tr(0.86f, 1.03f, -2.06f)
        enqueueWorldQuad(wingFL, wingFR, wingRR, wingRL, carbonDark, strokeColor = bodyColor, depthBias = -0.05f)

        val stayL0 = tr(-0.48f, 0.72f, -1.82f)
        val stayL1 = tr(-0.48f, 0.72f, -1.96f)
        val stayL2 = tr(-0.48f, 0.99f, -1.96f)
        val stayL3 = tr(-0.48f, 0.99f, -1.82f)
        enqueueWorldQuad(stayL0, stayL1, stayL2, stayL3, trimBlack)

        val stayR0 = tr(0.48f, 0.72f, -1.82f)
        val stayR1 = tr(0.48f, 0.72f, -1.96f)
        val stayR2 = tr(0.48f, 0.99f, -1.96f)
        val stayR3 = tr(0.48f, 0.99f, -1.82f)
        enqueueWorldQuad(stayR0, stayR1, stayR2, stayR3, trimBlack)

        // 4 Independent 3D Deep-Dish Wheels (Front wheels steer + rotate, Rear wheels rotate with wheelspin)
        enqueueWheel3D(
            localCenterX = -0.90f, localCenterY = 0.32f, localCenterZ = 1.24f,
            carX = posX, carY = posY, carZ = posZ, carYaw = yaw,
            steerAngle = steerRad, spinAngle = frontWheelSpin, isLeft = true, rimColor = rimColor
        )
        enqueueWheel3D(
            localCenterX = 0.90f, localCenterY = 0.32f, localCenterZ = 1.24f,
            carX = posX, carY = posY, carZ = posZ, carYaw = yaw,
            steerAngle = steerRad, spinAngle = frontWheelSpin, isLeft = false, rimColor = rimColor
        )
        enqueueWheel3D(
            localCenterX = -0.92f, localCenterY = 0.32f, localCenterZ = -1.28f,
            carX = posX, carY = posY, carZ = posZ, carYaw = yaw,
            steerAngle = 0f, spinAngle = rearWheelSpin, isLeft = true, rimColor = rimColor
        )
        enqueueWheel3D(
            localCenterX = 0.92f, localCenterY = 0.32f, localCenterZ = -1.28f,
            carX = posX, carY = posY, carZ = posZ, carYaw = yaw,
            steerAngle = 0f, spinAngle = rearWheelSpin, isLeft = false, rimColor = rimColor
        )
    }

    private fun enqueueWheel3D(
        localCenterX: Float,
        localCenterY: Float,
        localCenterZ: Float,
        carX: Float,
        carY: Float,
        carZ: Float,
        carYaw: Float,
        steerAngle: Float,
        spinAngle: Float,
        isLeft: Boolean,
        rimColor: Color
    ) {
        val wheelWorldCenter = Math3D.transformLocalToWorld(
            Vec3(localCenterX, localCenterY, localCenterZ),
            carX, carY, carZ, carYaw
        )
        val totalYaw = carYaw + steerAngle
        val radius = 0.32f
        val halfWidth = 0.15f
        val outerX = if (isLeft) -halfWidth else halfWidth
        val innerX = if (isLeft) halfWidth else -halfWidth
        val tireBlack = Color(0xFF12151A)

        val segments = 8
        for (i in 0 until segments) {
            val a0 = spinAngle + (i.toFloat() / segments) * 2f * Math.PI.toFloat()
            val a1 = spinAngle + ((i + 1).toFloat() / segments) * 2f * Math.PI.toFloat()

            val y0 = sin(a0) * radius
            val z0 = cos(a0) * radius
            val y1 = sin(a1) * radius
            val z1 = cos(a1) * radius

            // Tire tread quad
            val t0 = Math3D.transformLocalToWorld(Vec3(outerX, y0, z0), wheelWorldCenter.x, wheelWorldCenter.y, wheelWorldCenter.z, totalYaw)
            val t1 = Math3D.transformLocalToWorld(Vec3(innerX, y0, z0), wheelWorldCenter.x, wheelWorldCenter.y, wheelWorldCenter.z, totalYaw)
            val t2 = Math3D.transformLocalToWorld(Vec3(innerX, y1, z1), wheelWorldCenter.x, wheelWorldCenter.y, wheelWorldCenter.z, totalYaw)
            val t3 = Math3D.transformLocalToWorld(Vec3(outerX, y1, z1), wheelWorldCenter.x, wheelWorldCenter.y, wheelWorldCenter.z, totalYaw)
            enqueueWorldQuad(t0, t1, t2, t3, tireBlack, emissive = true, depthBias = -0.02f)

            // Outer rim face (alternating spokes so rotation is clearly visible)
            val hubCenter = Math3D.transformLocalToWorld(Vec3(outerX, 0f, 0f), wheelWorldCenter.x, wheelWorldCenter.y, wheelWorldCenter.z, totalYaw)
            val rimY0 = sin(a0) * (radius * 0.72f)
            val rimZ0 = cos(a0) * (radius * 0.72f)
            val rimY1 = sin(a1) * (radius * 0.72f)
            val rimZ1 = cos(a1) * (radius * 0.72f)
            val r0 = Math3D.transformLocalToWorld(Vec3(outerX, rimY0, rimZ0), wheelWorldCenter.x, wheelWorldCenter.y, wheelWorldCenter.z, totalYaw)
            val r1 = Math3D.transformLocalToWorld(Vec3(outerX, rimY1, rimZ1), wheelWorldCenter.x, wheelWorldCenter.y, wheelWorldCenter.z, totalYaw)
            val spokeCol = if (i % 2 == 0) rimColor else Color(0xFF1E242E)
            enqueueWorldTri(hubCenter, r0, r1, spokeCol, emissive = true, depthBias = -0.04f)
        }
    }

    private fun DrawScope.flushProjectedFaces() {
        // Simple insertion/Quick sort by depth descending (back-to-front)
        facePool.sortWith(
            Comparator { a, b -> b.depth.compareTo(a.depth) },
            0,
            activeFaceCount
        )

        for (i in 0 until activeFaceCount) {
            val pf = facePool[i]
            reusablePath.reset()
            reusablePath.moveTo(pf.sx[0], pf.sy[0])
            for (v in 1 until pf.vertexCount) {
                reusablePath.lineTo(pf.sx[v], pf.sy[v])
            }
            reusablePath.close()

            drawPath(path = reusablePath, color = pf.shadedColor)
            val sc = pf.strokeColor
            if (sc != null) {
                drawPath(path = reusablePath, color = sc, style = Stroke(width = 1.5f))
            }
        }
    }
}
