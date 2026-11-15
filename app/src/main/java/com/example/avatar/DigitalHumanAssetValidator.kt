package com.example.avatar

/** Structural quality gate for imported digital-human assets. */
object DigitalHumanAssetValidator {
    enum class Severity { ERROR, WARNING }
    data class Finding(val severity: Severity, val code: String, val message: String)
    data class Report(val findings: List<Finding>, val vertexCount: Int, val triangleCount: Int, val morphTargetCount: Int) {
        val hasErrors: Boolean get() = findings.any { it.severity == Severity.ERROR }
        val isStructurallyReady: Boolean get() = !hasErrors
        val productionReady: Boolean get() = isStructurallyReady && findings.none { it.severity == Severity.WARNING }
    }
    private val requiredMorphGroups = listOf(
        setOf("jawopen", "mouthopen"), setOf("mouthsmileleft", "smileleft"),
        setOf("mouthsmileright", "smileright"), setOf("mouthpucker", "mouthfunnel"),
        setOf("eyeblinkleft", "blinkleft"), setOf("eyeblinkright", "blinkright"),
        setOf("viseme_aa", "aa"), setOf("viseme_ee", "ee"), setOf("viseme_ih", "ih"),
        setOf("viseme_oh", "oh"), setOf("viseme_ou", "ou"), setOf("viseme_pp", "pp"),
        setOf("viseme_ff", "ff"), setOf("viseme_ss", "ss"), setOf("viseme_ch", "ch"),
        setOf("viseme_dd", "dd")
    )
    fun inspect(model: AvatarModel3D): Report {
        val findings = mutableListOf<Finding>()
        fun error(code: String, message: String) { findings += Finding(Severity.ERROR, code, message) }
        fun warning(code: String, message: String) { findings += Finding(Severity.WARNING, code, message) }
        if (model.parts.isEmpty()) error("empty_model", "The avatar contains no mesh parts.")
        if (model.totalVertices < 1) error("empty_geometry", "The avatar contains no vertices.")
        if (model.totalTriangles < 1) error("empty_surface", "The avatar contains no triangles.")
        model.parts.forEachIndexed { index, part ->
            if (part.positions.size % 3 != 0) error("invalid_positions_$index", "Mesh $index has an invalid POSITION array.")
            if (part.normals.size != part.positions.size) error("invalid_normals_$index", "Mesh $index NORMAL count does not match POSITION.")
            if (part.uvs.size != part.vertexCount * 2) warning("missing_uv_$index", "Mesh $index has incomplete TEXCOORD_0.")
            if (part.indices.isEmpty() || part.indices.size % 3 != 0) error("invalid_indices_$index", "Mesh $index has no valid triangle index list.")
            else if (part.indices.any { it < 0 || it >= part.vertexCount }) error("index_out_of_range_$index", "Mesh $index contains an out-of-range index.")
            if (part.baseColor.size < 4) error("invalid_material_$index", "Mesh $index has no RGBA base color.")
            part.skinData?.let { skin ->
                if (skin.jointIndices.size != part.vertexCount * 4 || skin.weights.size != part.vertexCount * 4) {
                    error("invalid_skin_influence_count_$index", "Mesh $index must provide four joint indices and weights per vertex.")
                } else {
                    if (skin.jointIndices.any { it < 0 }) error("invalid_skin_joint_index_$index", "Mesh $index contains a negative joint index.")
                    for (vertex in 0 until skin.vertexCount) {
                        val sum = (0..3).sumOf { skin.weights[vertex * 4 + it].toDouble() }.toFloat()
                        if (skin.weights.slice(vertex * 4 until vertex * 4 + 4).any { !it.isFinite() || it < 0f } || (sum > 0f && kotlin.math.abs(sum - 1f) > 0.02f)) {
                            error("invalid_skin_weights_$index", "Mesh $index has invalid or non-normalized skin weights.")
                            break
                        }
                    }
                }
            }
            if (part.morphTargets.any { it.positionDeltas.size != part.positions.size }) error("invalid_morph_position_$index", "Mesh $index has a morph POSITION delta mismatch.")
            if (part.morphTargets.any { it.normalDeltas != null && it.normalDeltas.size != part.positions.size }) error("invalid_morph_normal_$index", "Mesh $index has a morph NORMAL delta mismatch.")
            if (part.morphTargets.any { it.normalDeltas == null }) warning("missing_morph_normals_$index", "Mesh $index has morph targets without NORMAL deltas.")
            if (part.textureBitmap == null) warning("missing_base_color_texture_$index", "Mesh $index has no decoded base-color texture.")
        }
        val names = model.parts.flatMap { it.morphTargets }.map { it.name.trim().lowercase() }.toSet()
        requiredMorphGroups.forEachIndexed { index, aliases ->
            val found = names.any { candidate -> aliases.any { alias -> candidate == alias || candidate.endsWith("_" + alias) } }
            if (!found) warning("missing_facial_target_$index", "A required facial target is missing: " + aliases.first() + ".")
        }
        if (model.parts.none { it.morphTargets.isNotEmpty() }) warning("no_facial_rig", "No facial morph targets were found; production facial animation is unavailable.")
        return Report(findings, model.totalVertices, model.totalTriangles, model.totalMorphTargets)
    }
}
