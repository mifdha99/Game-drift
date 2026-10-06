package com.example.drift.effects

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import androidx.compose.ui.graphics.Color
import com.example.drift.math.Math3D
import com.example.drift.math.Vec3
import com.example.drift.model.GraphicsQuality
import com.example.drift.model.SurfaceType
import com.example.drift.physics.Vehicle
import com.example.drift.track.CollisionResult
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

enum class ParticleType {
    TIRE_SMOKE,
    DUST,
    SPARK,
    EXHAUST_FLAME
}

class Particle3D(
    var x: Float = 0f,
    var y: Float = 0f,
    var z: Float = 0f,
    var vx: Float = 0f,
    var vy: Float = 0f,
    var vz: Float = 0f,
    var radius: Float = 0.25f,
    var age: Float = 0f,
    var maxLife: Float = 1.0f,
    var type: ParticleType = ParticleType.TIRE_SMOKE,
    var color: Color = Color.White
)

data class SkidMarkSegment(
    val x0: Float,
    val z0: Float,
    val x1: Float,
    val z1: Float,
    val alpha: Float
)

class EffectsSystem {
    val particles = ArrayList<Particle3D>(180)
    val skidMarks = ArrayDeque<SkidMarkSegment>(300)

    private var prevRearLeftPos: Vec3? = null
    private var prevRearRightPos: Vec3? = null
    private var spawnAccumulator: Float = 0f

    fun clear() {
        particles.clear()
        skidMarks.clear()
        prevRearLeftPos = null
        prevRearRightPos = null
    }

    fun update(
        dt: Float,
        vehicle: Vehicle,
        surface: SurfaceType,
        collision: CollisionResult,
        quality: GraphicsQuality
    ) {
        val safeDt = Math3D.clamp(dt, 0.001f, 0.05f)

        // Update existing particles
        val iterator = particles.iterator()
        while (iterator.hasNext()) {
            val p = iterator.next()
            p.age += safeDt
            if (p.age >= p.maxLife) {
                iterator.remove()
                continue
            }
            p.x += p.vx * safeDt
            p.y += p.vy * safeDt
            p.z += p.vz * safeDt

            when (p.type) {
                ParticleType.TIRE_SMOKE -> {
                    p.radius += safeDt * 0.95f
                    p.vx *= (1f - 1.5f * safeDt).coerceAtLeast(0f)
                    p.vz *= (1f - 1.5f * safeDt).coerceAtLeast(0f)
                    p.vy += safeDt * 0.35f
                }
                ParticleType.DUST -> {
                    p.radius += safeDt * 0.65f
                    p.vy -= safeDt * 1.2f
                    if (p.y < 0.05f) p.y = 0.05f
                }
                ParticleType.SPARK -> {
                    p.vy -= safeDt * 9.8f
                    if (p.y < 0.02f) {
                        p.y = 0.02f
                        p.vy = -p.vy * 0.4f
                    }
                }
                ParticleType.EXHAUST_FLAME -> {
                    p.radius *= (1f - 3f * safeDt).coerceAtLeast(0.2f)
                }
            }
        }

        // Compute rear wheel world contact patches
        val rearLeftLocal = Vec3(-0.82f, 0.06f, -1.26f)
        val rearRightLocal = Vec3(0.82f, 0.06f, -1.26f)
        val rlWorld = Math3D.transformLocalToWorld(
            rearLeftLocal, vehicle.posX, vehicle.posY, vehicle.posZ, vehicle.yaw
        )
        val rrWorld = Math3D.transformLocalToWorld(
            rearRightLocal, vehicle.posX, vehicle.posY, vehicle.posZ, vehicle.yaw
        )

        val slip = vehicle.driftSystem.rearWheelSlipRatio
        // Tire smoke & skid marks ONLY appear when rear wheel slip exceeds threshold (burnout, drift, handbrake)
        if (slip > 0.22f && vehicle.speedKmh > 3f || (slip > 0.42f && vehicle.throttleInput > 0.5f)) {
            if (surface != SurfaceType.GRASS) {
                val prevRL = prevRearLeftPos
                val prevRR = prevRearRightPos
                if (prevRL != null && prevRR != null) {
                    val moveDistSq = (rlWorld.x - prevRL.x) * (rlWorld.x - prevRL.x) +
                        (rlWorld.z - prevRL.z) * (rlWorld.z - prevRL.z)
                    if (moveDistSq > 0.08f && moveDistSq < 9.0f) {
                        val markAlpha = Math3D.clamp((slip - 0.2f) * 0.75f, 0.15f, 0.65f)
                        skidMarks.addLast(SkidMarkSegment(prevRL.x, prevRL.z, rlWorld.x, rlWorld.z, markAlpha))
                        skidMarks.addLast(SkidMarkSegment(prevRR.x, prevRR.z, rrWorld.x, rrWorld.z, markAlpha))
                        while (skidMarks.size > quality.maxSkidMarks) {
                            skidMarks.removeFirst()
                        }
                    }
                }
                prevRearLeftPos = rlWorld
                prevRearRightPos = rrWorld
            } else {
                prevRearLeftPos = null
                prevRearRightPos = null
            }

            // Spawn smoke or grass dust particles proportional to slip
            spawnAccumulator += safeDt * (18f + slip * 36f)
            while (spawnAccumulator >= 1f && particles.size < quality.maxParticles) {
                spawnAccumulator -= 1f
                val isGrass = surface == SurfaceType.GRASS
                val pColor = if (isGrass) {
                    Color(0xFF8D7B68).copy(alpha = 0.55f)
                } else {
                    Color(0xFFDEE4EC).copy(alpha = Math3D.clamp(0.22f + slip * 0.38f, 0.2f, 0.62f))
                }
                val pType = if (isGrass) ParticleType.DUST else ParticleType.TIRE_SMOKE
                val life = if (isGrass) 0.55f else (0.65f + slip * 0.45f)

                for (wheelPos in listOf(rlWorld, rrWorld)) {
                    if (particles.size < quality.maxParticles) {
                        particles.add(
                            Particle3D(
                                x = wheelPos.x + (Random.nextFloat() - 0.5f) * 0.22f,
                                y = 0.14f,
                                z = wheelPos.z + (Random.nextFloat() - 0.5f) * 0.22f,
                                vx = -vehicle.velX * 0.15f + (Random.nextFloat() - 0.5f) * 1.4f,
                                vy = 0.55f + Random.nextFloat() * 0.65f,
                                vz = -vehicle.velZ * 0.15f + (Random.nextFloat() - 0.5f) * 1.4f,
                                radius = 0.24f + slip * 0.18f,
                                age = 0f,
                                maxLife = life,
                                type = pType,
                                color = pColor
                            )
                        )
                    }
                }
            }
        } else {
            prevRearLeftPos = null
            prevRearRightPos = null
        }

        // Small sparks when scraping or colliding near a barrier
        if ((collision.hitBarrier || (collision.nearBarrierProximity && vehicle.driftSystem.isDrifting)) &&
            vehicle.speedKmh > 25f
        ) {
            val sparkOrigin = collision.impactPoint ?: Math3D.transformLocalToWorld(
                Vec3(if (vehicle.driftSystem.driftAngleDeg > 0f) -0.95f else 0.95f, 0.35f, -1.6f),
                vehicle.posX, vehicle.posY, vehicle.posZ, vehicle.yaw
            )
            val sparkCount = if (collision.hitBarrier) 6 else 2
            repeat(sparkCount) {
                if (particles.size < quality.maxParticles) {
                    particles.add(
                        Particle3D(
                            x = sparkOrigin.x,
                            y = sparkOrigin.y,
                            z = sparkOrigin.z,
                            vx = (Random.nextFloat() - 0.5f) * 7f,
                            vy = 1.5f + Random.nextFloat() * 4.0f,
                            vz = (Random.nextFloat() - 0.5f) * 7f,
                            radius = 0.08f,
                            age = 0f,
                            maxLife = 0.32f,
                            type = ParticleType.SPARK,
                            color = Color(0xFFFFB300)
                        )
                    )
                }
            }
        }

        // Exhaust flame pop on rev limiter or shift/throttle lift
        if (vehicle.engine.backfireTrigger && particles.size < quality.maxParticles) {
            val exhaustWorld = Math3D.transformLocalToWorld(
                Vec3(-0.52f, 0.24f, -2.08f),
                vehicle.posX, vehicle.posY, vehicle.posZ, vehicle.yaw
            )
            particles.add(
                Particle3D(
                    x = exhaustWorld.x,
                    y = exhaustWorld.y,
                    z = exhaustWorld.z,
                    vx = -sin(vehicle.yaw) * 4f,
                    vy = 0.1f,
                    vz = -cos(vehicle.yaw) * 4f,
                    radius = 0.22f,
                    age = 0f,
                    maxLife = 0.12f,
                    type = ParticleType.EXHAUST_FLAME,
                    color = Color(0xFFFF6D00)
                )
            )
        }
    }
}

class AudioManager {
    @Volatile
    private var isRunning = false
    private var audioThread: Thread? = null

    @Volatile
    var targetRpm: Float = 1000f

    @Volatile
    var targetThrottle: Float = 0f

    @Volatile
    var targetTireSlip: Float = 0f

    @Volatile
    var soundVolume: Float = 0.75f

    @Volatile
    var musicVolume: Float = 0.45f

    @Volatile
    var isPaused: Boolean = false

    fun start() {
        if (isRunning) return
        isRunning = true
        audioThread = Thread({
            runSynthesizerLoop()
        }, "DriftEngineAudioThread").apply {
            isDaemon = true
            start()
        }
    }

    fun stop() {
        isRunning = false
        try {
            audioThread?.join(200)
        } catch (_: Exception) {
        }
        audioThread = null
    }

    private fun runSynthesizerLoop() {
        val sampleRate = 22050
        val bufferSamples = 512
        val buffer = ShortArray(bufferSamples)

        var track: AudioTrack? = null
        try {
            val minBuf = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            if (minBuf <= 0) return

            track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(max(minBuf, bufferSamples * 4))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            track.play()

            var phase1 = 0.0
            var phase2 = 0.0
            var screechPhase = 0.0
            var musicPhase = 0.0
            var beatSampleCounter = 0L
            var smoothedRpm = 1000f

            // Japanese Phonk / Synthwave bassline frequencies (D minor pentatonic)
            val bassNotes = doubleArrayOf(73.42, 73.42, 87.31, 98.00, 110.0, 98.00, 87.31, 65.41)

            while (isRunning) {
                if (isPaused || (soundVolume <= 0.01f && musicVolume <= 0.01f)) {
                    for (i in 0 until bufferSamples) buffer[i] = 0
                    track.write(buffer, 0, bufferSamples)
                    Thread.sleep(20)
                    continue
                }

                val currentTargetRpm = targetRpm
                val throttle = targetThrottle
                val slip = targetTireSlip
                val sfxVol = soundVolume
                val bgmVol = musicVolume

                for (i in 0 until bufferSamples) {
                    smoothedRpm += (currentTargetRpm - smoothedRpm) * 0.008f
                    // 6-cylinder 4-stroke firing frequency: RPM / 60 * 3
                    val baseFreq = (smoothedRpm / 60.0) * 2.2
                    phase1 = (phase1 + baseFreq / sampleRate) % 1.0
                    phase2 = (phase2 + (baseFreq * 1.5) / sampleRate) % 1.0

                    // Multi-harmonic saw + pulse wave for gritty JDM exhaust note
                    val saw = (phase1 * 2.0 - 1.0)
                    val sub = sin(phase1 * Math.PI * 2.0)
                    val harmonic = sin(phase2 * Math.PI * 4.0)
                    val loadGrowl = 0.45 + throttle * 0.55
                    val engineWave = (saw * 0.42 + sub * 0.38 + harmonic * 0.20) * loadGrowl

                    // Tire screech when drifting / spinning wheels
                    val screechEnv = if (slip > 0.25f) ((slip - 0.25f) * 0.45) else 0.0
                    screechPhase = (screechPhase + (1480.0 + sin(phase1 * 25.0) * 180.0) / sampleRate) % 1.0
                    val screechWave = (sin(screechPhase * Math.PI * 2.0) * 0.7 + (Random.nextDouble() - 0.5) * 0.3) * screechEnv

                    // Subtle synthwave pulse bassline for street atmosphere
                    beatSampleCounter++
                    val stepIdx = ((beatSampleCounter / (sampleRate / 4)) % bassNotes.size).toInt()
                    val noteFreq = bassNotes[stepIdx]
                    musicPhase = (musicPhase + noteFreq / sampleRate) % 1.0
                    val stepProgress = (beatSampleCounter % (sampleRate / 4)).toDouble() / (sampleRate / 4)
                    val noteEnv = (1.0 - stepProgress * 0.85).coerceAtLeast(0.0)
                    val musicWave = sin(musicPhase * Math.PI * 2.0) * noteEnv * 0.22

                    val mixed = (engineWave * 0.35 * sfxVol) +
                        (screechWave * 0.28 * sfxVol) +
                        (musicWave * bgmVol)

                    val clamped = mixed.coerceIn(-0.95, 0.95)
                    buffer[i] = (clamped * Short.MAX_VALUE).toInt().toShort()
                }

                track.write(buffer, 0, bufferSamples)
            }
        } catch (_: Throwable) {
            // Ignore audio hardware unavailability on headless JVM / CI
        } finally {
            try {
                track?.stop()
                track?.release()
            } catch (_: Throwable) {
            }
        }
    }
}
