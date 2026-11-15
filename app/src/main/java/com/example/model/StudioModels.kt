package com.example.model

import kotlinx.serialization.Serializable

enum class VisemeType(val code: String, val label: String) {
    SIL("SIL", "Rest"),
    PP("PP", "P / B / M"),
    FF("FF", "F / V"),
    TH("TH", "TH"),
    DD("DD", "T / D / N"),
    KK("KK", "K / G"),
    CH("CH", "CH / SH"),
    SS("SS", "S / Z"),
    NN("NN", "N / L"),
    RR("RR", "R"),
    AA("AA", "AH"),
    EE("EE", "EH / AE"),
    IH("IH", "IY / IH"),
    OH("OH", "AW / OW"),
    OU("OU", "OO / UW")
}

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
    val vocalEnergy: Float = 0f
) {
    fun weightForMorphName(rawName: String): Float {
        val name = rawName.lowercase()
        return when {
            "jawopen" in name || "mouthopen" in name || "viseme_aa" in name || name == "aa" -> jawOpen
            "mouthpucker" in name || "mouthfunnel" in name || "viseme_o" in name || "viseme_u" in name -> mouthPucker
            "mouthsmile" in name || "mouthstretch" in name || "viseme_e" in name || "viseme_i" in name ->
                (mouthWide.coerceAtLeast(0f) * 0.85f + smile * 0.35f).coerceIn(0f, 1f)
            "mouthclose" in name || "mouthpress" in name || "viseme_pp" in name -> lipPress
            "upperup" in name || "viseme_ff" in name -> upperLipRaise
            "viseme_ss" in name || "viseme_ch" in name || "viseme_dd" in name ->
                (tongueLift * 0.7f + jawOpen * 0.35f).coerceIn(0f, 1f)
            "eyeblink" in name || "blink" in name -> eyeBlink
            "brow" in name -> browRaise
            else -> 0f
        }
    }
}

enum class LightingPreset(val displayName: String, val subtitle: String) {
    CINEMA_KEY("Cinema Key", "Warm tungsten key & cool rim"),
    NEON_CYBER("Neon Cyber", "Electric cyan & coral dual rim"),
    DAYLIGHT_SOFTBOX("Softbox", "Balanced neutral studio wrap"),
    GOLDEN_HOUR("Golden", "Sunset amber & indigo fill")
}

enum class StageBackdrop(val displayName: String) {
    OBSIDIAN_STUDIO("Obsidian"),
    GRADIENT_STAGE("Stage Glow"),
    TECH_GRID("Holo Grid"),
    CHROMA_GREEN("Chroma Key")
}

enum class VideoAspectRatio(
    val label: String,
    val width: Int,
    val height: Int
) {
    PORTRAIT_9_16("9:16", 720, 1280),
    SQUARE_1_1("1:1", 720, 720),
    LANDSCAPE_16_9("16:9", 1280, 720)
}

enum class AvatarFormat(val label: String) {
    BUILTIN("Built-in 3D Rig"),
    GLB("GLB Binary"),
    GLTF("glTF 2.0"),
    OBJ("Wavefront OBJ")
}

data class AvatarSpec(
    val id: String,
    val name: String,
    val roleTag: String,
    val format: AvatarFormat,
    val filePath: String? = null,
    val vertexCount: Int = 0,
    val triangleCount: Int = 0,
    val morphTargetCount: Int = 0,
    val accentHex: Long = 0xFF22D3EE,
    val isBuiltIn: Boolean = true
)

data class VisemeFrame(
    val timeMs: Long,
    val rig: FacialRigState
)

data class VisemeTimeline(
    val durationMs: Long,
    val stepMs: Long,
    val frames: List<VisemeFrame>
) {
    fun sampleAt(timeMs: Long): FacialRigState {
        if (frames.isEmpty()) return FacialRigState()
        val clamped = timeMs.coerceIn(0L, durationMs)
        val idxFloat = clamped.toFloat() / stepMs.toFloat()
        val idx0 = idxFloat.toInt().coerceIn(0, frames.lastIndex)
        val idx1 = (idx0 + 1).coerceAtMost(frames.lastIndex)
        val frac = (idxFloat - idx0).coerceIn(0f, 1f)
        val a = frames[idx0].rig
        val b = frames[idx1].rig
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
            vocalEnergy = lerp(a.vocalEnergy, b.vocalEnergy, frac)
        )
    }

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t
}

data class AudioClipInfo(
    val id: String,
    val title: String,
    val wavFilePath: String,
    val durationMs: Long,
    val sampleRate: Int,
    val waveformPeaks: List<Float>,
    val timeline: VisemeTimeline,
    val sourceLabel: String
)

data class ExportedClip(
    val id: String,
    val title: String,
    val videoPath: String,
    val galleryUri: String?,
    val avatarName: String,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val fps: Int,
    val fileSizeBytes: Long,
    val createdAtMs: Long
)

@Serializable
sealed interface StudioRoute {
    @Serializable
    data object Studio : StudioRoute

    @Serializable
    data object Avatars : StudioRoute

    @Serializable
    data object Clips : StudioRoute
}
