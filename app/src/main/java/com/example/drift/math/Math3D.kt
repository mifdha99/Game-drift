package com.example.drift.math

import androidx.compose.ui.graphics.Color
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

data class Vec3(
    var x: Float = 0f,
    var y: Float = 0f,
    var z: Float = 0f
) {
    fun set(nx: Float, ny: Float, nz: Float): Vec3 {
        x = nx
        y = ny
        z = nz
        return this
    }

    fun copyOf(): Vec3 = Vec3(x, y, z)

    operator fun plus(other: Vec3): Vec3 = Vec3(x + other.x, y + other.y, z + other.z)
    operator fun minus(other: Vec3): Vec3 = Vec3(x - other.x, y - other.y, z - other.z)
    operator fun times(scalar: Float): Vec3 = Vec3(x * scalar, y * scalar, z * scalar)

    fun dot(other: Vec3): Float = x * other.x + y * other.y + z * other.z

    fun cross(other: Vec3): Vec3 = Vec3(
        y * other.z - z * other.y,
        z * other.x - x * other.z,
        x * other.y - y * other.x
    )

    fun length(): Float = sqrt(x * x + y * y + z * z)

    fun normalized(): Vec3 {
        val len = length()
        return if (len > 1e-5f) Vec3(x / len, y / len, z / len) else Vec3(0f, 1f, 0f)
    }
}

data class Face3D(
    val v0: Vec3,
    val v1: Vec3,
    val v2: Vec3,
    val v3: Vec3? = null,
    val baseColor: Color,
    val emissive: Boolean = false,
    val doubleSided: Boolean = false
)

class ProjectedFace(
    val sx: FloatArray = FloatArray(4),
    val sy: FloatArray = FloatArray(4),
    var vertexCount: Int = 3,
    var depth: Float = 0f,
    var shadedColor: Color = Color.White,
    var strokeColor: Color? = null
)

object Math3D {
    const val DEG_TO_RAD = (Math.PI / 180.0).toFloat()
    const val RAD_TO_DEG = (180.0 / Math.PI).toFloat()

    fun clamp(value: Float, minVal: Float, maxVal: Float): Float =
        max(minVal, min(maxVal, value))

    fun lerp(start: Float, end: Float, fraction: Float): Float =
        start + (end - start) * clamp(fraction, 0f, 1f)

    fun lerpAngle(a: Float, b: Float, t: Float): Float {
        var diff = (b - a) % (2f * Math.PI.toFloat())
        if (diff < -Math.PI.toFloat()) diff += 2f * Math.PI.toFloat()
        if (diff > Math.PI.toFloat()) diff -= 2f * Math.PI.toFloat()
        return a + diff * clamp(t, 0f, 1f)
    }

    fun normalizeAngle(angle: Float): Float {
        var a = angle % (2f * Math.PI.toFloat())
        if (a < -Math.PI.toFloat()) a += 2f * Math.PI.toFloat()
        if (a > Math.PI.toFloat()) a -= 2f * Math.PI.toFloat()
        return a
    }

    fun transformLocalToWorld(
        local: Vec3,
        posX: Float,
        posY: Float,
        posZ: Float,
        yaw: Float,
        pitch: Float = 0f,
        roll: Float = 0f
    ): Vec3 {
        // Apply roll (around Z), pitch (around X), then yaw (around Y)
        var lx = local.x
        var ly = local.y
        var lz = local.z

        if (roll != 0f) {
            val cr = cos(roll)
            val sr = sin(roll)
            val nx = lx * cr - ly * sr
            val ny = lx * sr + ly * cr
            lx = nx
            ly = ny
        }

        if (pitch != 0f) {
            val cp = cos(pitch)
            val sp = sin(pitch)
            val ny = ly * cp - lz * sp
            val nz = ly * sp + lz * cp
            ly = ny
            lz = nz
        }

        val cy = cos(yaw)
        val sy = sin(yaw)
        val wx = lx * cy + lz * sy + posX
        val wy = ly + posY
        val wz = -lx * sy + lz * cy + posZ
        return Vec3(wx, wy, wz)
    }

    fun shadeColor(base: Color, normal: Vec3, lightDir: Vec3, emissive: Boolean): Color {
        if (emissive) return base
        val ndotl = max(0f, normal.dot(lightDir))
        val ambient = 0.42f
        val diffuse = 0.68f * ndotl
        val factor = clamp(ambient + diffuse, 0.25f, 1.15f)
        return Color(
            red = clamp(base.red * factor, 0f, 1f),
            green = clamp(base.green * factor, 0f, 1f),
            blue = clamp(base.blue * factor, 0f, 1f),
            alpha = base.alpha
        )
    }
}
