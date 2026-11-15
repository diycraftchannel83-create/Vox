package com.example.model

import kotlinx.serialization.Serializable

enum class VisemeType(val code: String, val label: String) {
    SIL("SIL", "Rest"), PP("PP", "P / B / M"), FF("FF", "F / V"),
    TH("TH", "TH"), DD("DD", "T / D / N"), KK("KK", "K / G"),
    CH("CH", "CH / SH"), SS("SS", "S / Z"), NN("NN", "N / L"),
    RR("RR", "R"), AA("AA", "AH"), EE("EE", "EH / AE"),
    IH("IH", "IY / IH"), OH("OH", "AW / OW"), OU("OU", "OO / UW")
}

/**
 * Normalized facial controls. Nullable left/right values fall back to the legacy
 * symmetric control, preserving compatibility with existing audio analysis.
 */
data class FacialRigState(
    val viseme: VisemeType = VisemeType.SIL,
    val jawOpen: Float = 0f,
    val mouthWide: Float = 0f,
    val mouthPucker: Float = 0f,
    val lipPress: Float = 0f,
    val upperLipRaise: Float = 0f,
    val tongueLift: Float = 0f,
    val eyeBlink: Float = 0f,
    val browRaise: Float = 0f,
    val smile: Float = 0.12f,
    val headPitch: Float = 0f,
    val headYaw: Float = 0f,
    val headRoll: Float = 0f,
    val vocalEnergy: Float = 0f,
    val leftEyeBlink: Float? = null,
    val rightEyeBlink: Float? = null,
    val leftSmile: Float? = null,
    val rightSmile: Float? = null,
    val leftBrowRaise: Float? = null,
    val rightBrowRaise: Float? = null,
    val eyeSquint: Float = 0f,
    val eyeWide: Float = 0f,
    val cheekRaise: Float = 0f,
    val noseSneer: Float = 0f,
    val jawForward: Float = 0f
) {
    fun weightForMorphName(rawName: String): Float {
        val name = rawName.lowercase().replace('-', '_').replace(' ', '_')
        return when {
            "jawopen" in name || "mouthopen" in name || name == "viseme_aa" || name == "aa" -> jawOpen
            "jawforward" in name || "jaw_forward" in name -> jawForward
            "mouthpucker" in name || "mouthfunnel" in name || "viseme_oh" == name || "viseme_ou" == name ||
                name == "viseme_o" || name == "viseme_u" -> mouthPucker
            "mouthwide" in name || "mouthstretch" in name || "viseme_ee" == name || "viseme_ih" == name ->
                mouthWide.unit()
            "mouthsmileleft" in name || "smileleft" in name -> side(leftSmile, smile, mouthWide)
            "mouthsmileright" in name || "smileright" in name -> side(rightSmile, smile, mouthWide)
            "mouthsmile" in name || "mouthstretch" in name || "viseme_ee" == name || "viseme_ih" == name ->
                blendSmile(smile, mouthWide)
            "mouthclose" in name || "mouthpress" in name || "viseme_pp" == name -> lipPress
            "upperup" in name || "viseme_ff" == name -> upperLipRaise
            "viseme_ss" == name || "viseme_ch" == name || "viseme_dd" == name ->
                (tongueLift * 0.7f + jawOpen * 0.35f).coerceIn(0f, 1f)
            "eyeblinkleft" in name || "blinkleft" in name -> (leftEyeBlink ?: eyeBlink).unit()
            "eyeblinkright" in name || "blinkright" in name -> (rightEyeBlink ?: eyeBlink).unit()
            "eyeblink" in name || "blink" in name -> eyeBlink.unit()
            "eyesquint" in name || "squint" in name -> eyeSquint.unit()
            "eyewide" in name || "eyeswide" in name -> eyeWide.unit()
            "browinnerup" in name || "browraiseleft" in name || "browleft" in name ->
                (leftBrowRaise ?: browRaise).unit()
            "browouterup" in name || "browraiseright" in name || "browright" in name ->
                (rightBrowRaise ?: browRaise).unit()
            "brow" in name -> browRaise.unit()
            "cheekraise" in name || "cheeksquint" in name -> cheekRaise.unit()
            "nosesneer" in name -> noseSneer.unit()
            else -> 0f
        }
    }

    private fun side(value: Float?, fallback: Float, width: Float): Float =
        ((value ?: fallback) + width * 0.85f).coerceIn(0f, 1f)

    private fun blendSmile(value: Float, width: Float): Float =
        (width.coerceAtLeast(0f) * 0.85f + value * 0.35f).coerceIn(0f, 1f)

    private fun Float.unit(): Float = coerceIn(0f, 1f)
}

enum class LightingPreset(val displayName: String, val subtitle: String) {
    CINEMA_KEY("Cinema Key", "Warm tungsten key & cool rim"),
    NEON_CYBER("Neon Cyber", "Electric cyan & coral dual rim"),
    DAYLIGHT_SOFTBOX("Softbox", "Balanced neutral studio wrap"),
    GOLDEN_HOUR("Golden", "Sunset amber & indigo fill")
}

enum class StageBackdrop(val displayName: String) {
    OBSIDIAN_STUDIO("Obsidian"), GRADIENT_STAGE("Stage Glow"),
    TECH_GRID("Holo Grid"), CHROMA_GREEN("Chroma Key")
}

enum class VideoAspectRatio(val label: String, val width: Int, val height: Int) {
    PORTRAIT_9_16("9:16", 720, 1280), SQUARE_1_1("1:1", 720, 720),
    LANDSCAPE_16_9("16:9", 1280, 720)
}

enum class AvatarFormat(val label: String) {
    BUILTIN("Built-in 3D Rig"), GLB("GLB Binary"), GLTF("glTF 2.0"), OBJ("Wavefront OBJ")
}

data class AvatarSpec(
    val id: String, val name: String, val roleTag: String, val format: AvatarFormat,
    val filePath: String? = null, val vertexCount: Int = 0, val triangleCount: Int = 0,
    val morphTargetCount: Int = 0, val accentHex: Long = 0xFF22D3EE,
    val isBuiltIn: Boolean = true
)

data class VisemeFrame(val timeMs: Long, val rig: FacialRigState)

data class VisemeTimeline(val durationMs: Long, val stepMs: Long, val frames: List<VisemeFrame>) {
    fun sampleAt(timeMs: Long): FacialRigState {
        if (frames.isEmpty()) return FacialRigState()
        val safeStep = stepMs.coerceAtLeast(1L)
        val clamped = timeMs.coerceIn(0L, durationMs.coerceAtLeast(0L))
        val idxFloat = clamped.toFloat() / safeStep.toFloat()
        val idx0 = idxFloat.toInt().coerceIn(0, frames.lastIndex)
        val idx1 = (idx0 + 1).coerceAtMost(frames.lastIndex)
        val frac = (idxFloat - idx0).coerceIn(0f, 1f)
        val a = frames[idx0].rig
        val b = frames[idx1].rig
        fun optionalLerp(x: Float?, y: Float?): Float? =
            if (x == null && y == null) null else lerp(x ?: 0f, y ?: 0f, frac)
        return FacialRigState(
            viseme = if (frac < 0.5f) a.viseme else b.viseme,
            jawOpen = lerp(a.jawOpen, b.jawOpen, frac),
            mouthWide = lerp(a.mouthWide, b.mouthWide, frac),
            mouthPucker = lerp(a.mouthPucker, b.mouthPucker, frac),
            lipPress = lerp(a.lipPress, b.lipPress, frac),
            upperLipRaise = lerp(a.upperLipRaise, b.upperLipRaise, frac),
            tongueLift = lerp(a.tongueLift, b.tongueLift, frac),
            eyeBlink = lerp(a.eyeBlink, b.eyeBlink, frac),
            browRaise = lerp(a.browRaise, b.browRaise, frac),
            smile = lerp(a.smile, b.smile, frac),
            headPitch = lerp(a.headPitch, b.headPitch, frac),
            headYaw = lerp(a.headYaw, b.headYaw, frac),
            headRoll = lerp(a.headRoll, b.headRoll, frac),
            vocalEnergy = lerp(a.vocalEnergy, b.vocalEnergy, frac),
            leftEyeBlink = optionalLerp(a.leftEyeBlink, b.leftEyeBlink),
            rightEyeBlink = optionalLerp(a.rightEyeBlink, b.rightEyeBlink),
            leftSmile = optionalLerp(a.leftSmile, b.leftSmile),
            rightSmile = optionalLerp(a.rightSmile, b.rightSmile),
            leftBrowRaise = optionalLerp(a.leftBrowRaise, b.leftBrowRaise),
            rightBrowRaise = optionalLerp(a.rightBrowRaise, b.rightBrowRaise),
            eyeSquint = lerp(a.eyeSquint, b.eyeSquint, frac),
            eyeWide = lerp(a.eyeWide, b.eyeWide, frac),
            cheekRaise = lerp(a.cheekRaise, b.cheekRaise, frac),
            noseSneer = lerp(a.noseSneer, b.noseSneer, frac),
            jawForward = lerp(a.jawForward, b.jawForward, frac)
        )
    }

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t
}

data class AudioClipInfo(
    val id: String, val title: String, val wavFilePath: String, val durationMs: Long,
    val sampleRate: Int, val waveformPeaks: List<Float>, val timeline: VisemeTimeline,
    val sourceLabel: String
)

data class ExportedClip(
    val id: String, val title: String, val videoPath: String, val galleryUri: String?,
    val avatarName: String, val durationMs: Long, val width: Int, val height: Int,
    val fps: Int, val fileSizeBytes: Long, val createdAtMs: Long
)

@Serializable
sealed interface StudioRoute {
    @Serializable data object Studio : StudioRoute
    @Serializable data object Avatars : StudioRoute
    @Serializable data object Clips : StudioRoute
}
