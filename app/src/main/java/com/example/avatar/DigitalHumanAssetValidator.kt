package com.example.avatar

/**
 * Evidence required before a digital-human asset can be labelled production-ready.
 *
 * Structural validation alone cannot prove photorealism, licensing, device performance,
 * or preview/export parity. These approvals must be recorded after independent review.
 */
data class DigitalHumanProductionEvidence(
    val visualReviewPassed: Boolean = false,
    val commercialRedistributionCleared: Boolean = false,
    val sourceAndAttributionRecorded: Boolean = false,
    val devicePerformancePassed: Boolean = false,
    val previewExportParityPassed: Boolean = false
) {
    val complete: Boolean
        get() = visualReviewPassed &&
            commercialRedistributionCleared &&
            sourceAndAttributionRecorded &&
            devicePerformancePassed &&
            previewExportParityPassed
}

/** Structural and release-evidence quality gate for imported digital-human assets. */
object DigitalHumanAssetValidator {
    enum class Severity { ERROR, WARNING }
    data class Finding(val severity: Severity, val code: String, val message: String)

    data class Report(
        val findings: List<Finding>,
        val vertexCount: Int,
        val triangleCount: Int,
        val morphTargetCount: Int,
        val evidence: DigitalHumanProductionEvidence
    ) {
        val hasErrors: Boolean get() = findings.any { it.severity == Severity.ERROR }
        val isStructurallyReady: Boolean get() = !hasErrors

        /**
         * This is deliberately stricter than structural validity. It is impossible for a
         * mesh-only or unreviewed asset to pass this gate by merely containing valid arrays.
         */
        val productionReady: Boolean
            get() = isStructurallyReady &&
                findings.none { it.severity == Severity.WARNING } &&
                evidence.complete
    }

    private val requiredMorphGroups = listOf(
        setOf("jawopen", "mouthopen"),
        setOf("jawforward", "jaw_forward"),
        setOf("mouthclose", "mouthpress"),
        setOf("mouthsmileleft", "smileleft"),
        setOf("mouthsmileright", "smileright"),
        setOf("mouthpucker", "mouthfunnel"),
        setOf("eyeblinkleft", "blinkleft"),
        setOf("eyeblinkright", "blinkright"),
        setOf("browinnerup", "browraise"),
        setOf("browouterupleft", "browouterup"),
        setOf("browouterupright", "browouterup"),
        setOf("viseme_aa", "aa"),
        setOf("viseme_ee", "ee"),
        setOf("viseme_ih", "ih"),
        setOf("viseme_oh", "oh"),
        setOf("viseme_ou", "ou"),
        setOf("viseme_pp", "pp"),
        setOf("viseme_ff", "ff"),
        setOf("viseme_th", "th"),
        setOf("viseme_dd", "dd"),
        setOf("viseme_kk", "kk"),
        setOf("viseme_ch", "ch"),
        setOf("viseme_ss", "ss"),
        setOf("viseme_nn", "nn"),
        setOf("viseme_rr", "rr")
    )

    fun inspect(
        model: AvatarModel3D,
        evidence: DigitalHumanProductionEvidence = DigitalHumanProductionEvidence()
    ): Report {
        val findings = mutableListOf<Finding>()
        fun error(code: String, message: String) { findings += Finding(Severity.ERROR, code, message) }
        fun warning(code: String, message: String) { findings += Finding(Severity.WARNING, code, message) }

        if (model.parts.isEmpty()) error("empty_model", "The avatar contains no mesh parts.")
        if (model.totalVertices < 1) error("empty_geometry", "The avatar contains no vertices.")
        if (model.totalTriangles < 1) error("empty_surface", "The avatar contains no triangles.")
        if (model.normalizationMatrix.size != 16 || model.normalizationMatrix.any { !it.isFinite() }) {
            error("invalid_normalization_matrix", "The model normalization matrix must contain 16 finite values.")
        }

        model.parts.forEachIndexed { index, part ->
            if (part.positions.isEmpty() || part.positions.size % 3 != 0) {
                error("invalid_positions_$index", "Mesh $index has an invalid POSITION array.")
            }
            if (part.positions.any { !it.isFinite() }) {
                error("non_finite_positions_$index", "Mesh $index contains non-finite vertex coordinates.")
            }
            if (part.normals.size != part.positions.size) {
                error("invalid_normals_$index", "Mesh $index NORMAL count does not match POSITION.")
            }
            if (part.normals.any { !it.isFinite() }) {
                error("non_finite_normals_$index", "Mesh $index contains non-finite normal values.")
            }
            if (part.uvs.size != part.vertexCount * 2) {
                warning("invalid_uvs_$index", "Mesh $index has incomplete TEXCOORD_0 data.")
            }
            if (part.uvs.any { !it.isFinite() }) {
                error("non_finite_uvs_$index", "Mesh $index contains non-finite UV values.")
            }
            if (part.nodeWorldMatrix.size != 16 || part.nodeWorldMatrix.any { !it.isFinite() }) {
                error("invalid_node_transform_$index", "Mesh $index has an invalid node transform.")
            }
            if (part.indices.isEmpty() || part.indices.size % 3 != 0) {
                error("invalid_indices_$index", "Mesh $index has no valid triangle index list.")
            }
            if (part.indices.any { it < 0 || it >= part.vertexCount }) {
                error("index_out_of_range_$index", "Mesh $index contains an out-of-range index.")
            }
            if (part.baseColor.size != 4 || part.baseColor.any { !it.isFinite() || it !in 0f..1f }) {
                error("invalid_base_color_$index", "Mesh $index must have four finite base-color values in the 0..1 range.")
            }
            if (!part.metallic.isFinite() || part.metallic !in 0f..1f) {
                error("invalid_metallic_$index", "Mesh $index has an invalid metallic factor.")
            }
            if (!part.roughness.isFinite() || part.roughness !in 0f..1f) {
                error("invalid_roughness_$index", "Mesh $index has an invalid roughness factor.")
            }
            if (part.emissive.size != 3 || part.emissive.any { !it.isFinite() || it < 0f }) {
                error("invalid_emissive_$index", "Mesh $index has invalid emissive values.")
            }

            if (part.skinIndex != null && part.skinData == null) {
                error("missing_skin_influences_$index", "Mesh $index references a skin but has no JOINTS_0 / WEIGHTS_0 data.")
            }
            if (part.skinData != null && part.skinIndex == null) {
                warning("unbound_skin_influences_$index", "Mesh $index has vertex influences but is not attached to a glTF skin.")
            }
            part.skinData?.let { skin ->
                if (skin.jointIndices.size != part.vertexCount * 4 || skin.weights.size != part.vertexCount * 4) {
                    error("invalid_skin_influence_count_$index", "Mesh $index must provide four joint indices and weights per vertex.")
                } else {
                    if (skin.jointIndices.any { it < 0 }) {
                        error("invalid_skin_joint_index_$index", "Mesh $index contains a negative joint index.")
                    }
                    val skinIndex = part.skinIndex
                    if (skinIndex != null && skinIndex !in model.skins.indices) {
                        error("invalid_skin_reference_$index", "Mesh $index references a missing skin.")
                    }
                    if (skinIndex != null && skinIndex in model.skins.indices) {
                        val jointCount = model.skins[skinIndex].joints.size
                        for (vertex in 0 until skin.vertexCount) {
                            val base = vertex * 4
                            val weights = (0..3).map { skin.weights[base + it] }
                            if (weights.any { !it.isFinite() || it < 0f }) {
                                error("invalid_skin_weights_$index", "Mesh $index has negative or non-finite skin weights.")
                                break
                            }
                            val sum = weights.sum()
                            if (sum <= 1e-8f) {
                                warning("zero_skin_weights_$index", "Mesh $index has vertices with zero total skin weight; those vertices remain undeformed.")
                                break
                            }
                            if (kotlin.math.abs(sum - 1f) > 0.02f) {
                                error("unnormalized_skin_weights_$index", "Mesh $index has skin weights that do not sum to 1.")
                                break
                            }
                            for (influence in 0..3) {
                                if (weights[influence] > 0f && skin.jointIndices[base + influence] !in 0 until jointCount) {
                                    error("skin_joint_out_of_range_$index", "Mesh $index references a joint outside its skin.")
                                    break
                                }
                            }
                        }
                    }
                }
            }

            part.morphTargets.forEachIndexed { morphIndex, morph ->
                if (morph.positionDeltas.size != part.positions.size) {
                    error("invalid_morph_position_${index}_$morphIndex", "Mesh $index morph ${morph.name} has a POSITION delta mismatch.")
                }
                if (morph.positionDeltas.any { !it.isFinite() }) {
                    error("non_finite_morph_position_${index}_$morphIndex", "Mesh $index morph ${morph.name} contains non-finite POSITION deltas.")
                }
                morph.normalDeltas?.let { normals ->
                    if (normals.size != part.positions.size) {
                        error("invalid_morph_normal_${index}_$morphIndex", "Mesh $index morph ${morph.name} has a NORMAL delta mismatch.")
                    }
                    if (normals.any { !it.isFinite() }) {
                        error("non_finite_morph_normal_${index}_$morphIndex", "Mesh $index morph ${morph.name} contains non-finite NORMAL deltas.")
                    }
                } ?: warning("missing_morph_normals_${index}_$morphIndex", "Mesh $index morph ${morph.name} has no NORMAL deltas.")
            }
            if (part.textureBitmap == null) {
                warning("missing_base_color_texture_$index", "Mesh $index has no decoded base-color texture.")
            }
        }

        model.skins.forEachIndexed { skinIndex, skin ->
            if (skin.joints.isEmpty()) error("empty_skin_$skinIndex", "Skin $skinIndex contains no joints.")
            skin.joints.forEachIndexed { jointIndex, joint ->
                if (joint.nodeIndex < 0) error("invalid_joint_node_${skinIndex}_$jointIndex", "Skin $skinIndex contains an invalid node index.")
                if (joint.parentIndex !in -1 until skin.joints.size) {
                    error("invalid_joint_parent_${skinIndex}_$jointIndex", "Skin $skinIndex contains an invalid parent joint reference.")
                }
                if (joint.bindLocalMatrix.size != 16 || joint.bindLocalMatrix.any { !it.isFinite() }) {
                    error("invalid_joint_bind_${skinIndex}_$jointIndex", "Skin $skinIndex contains an invalid joint bind matrix.")
                }
                if (joint.inverseBindMatrix.size != 16 || joint.inverseBindMatrix.any { !it.isFinite() }) {
                    error("invalid_inverse_bind_${skinIndex}_$jointIndex", "Skin $skinIndex contains an invalid inverse-bind matrix.")
                }
            }
            val state = IntArray(skin.joints.size)
            fun visit(index: Int) {
                if (index !in skin.joints.indices || state[index] == 2) return
                if (state[index] == 1) {
                    error("joint_hierarchy_cycle_$skinIndex", "Skin $skinIndex contains a cyclic joint hierarchy.")
                    return
                }
                state[index] = 1
                val parent = skin.joints[index].parentIndex
                if (parent >= 0) visit(parent)
                state[index] = 2
            }
            skin.joints.indices.forEach(::visit)
        }

        val names = model.parts.flatMap { it.morphTargets }.map { it.name.trim().lowercase() }.toSet()
        requiredMorphGroups.forEachIndexed { index, aliases ->
            val found = names.any { candidate ->
                aliases.any { alias -> candidate == alias || candidate.endsWith("_" + alias) }
            }
            if (!found) {
                warning("missing_facial_target_$index", "A required facial target is missing: ${aliases.first()}.")
            }
        }
        if (model.parts.none { it.morphTargets.isNotEmpty() }) {
            warning("no_facial_rig", "No facial morph targets were found; production facial animation is unavailable.")
        }
        if (model.skins.isEmpty() || model.parts.none { it.skinData != null && it.skinIndex != null }) {
            warning("no_skeletal_skin", "No bound skeletal skin was found; production head/body animation is unavailable.")
        }
        // The current renderer only consumes base-color textures and scalar factors. Until
        // normal/roughness/occlusion maps are parsed and rendered, PBR skin is not production-ready.
        warning("pbr_texture_pipeline_incomplete", "Normal, roughness, and occlusion texture maps are not yet represented by the runtime renderer.")

        if (!evidence.visualReviewPassed) {
            warning("visual_review_missing", "A human visual review of facial anatomy, skin, eyes, hair, and expressions has not been approved.")
        }
        if (!evidence.commercialRedistributionCleared) {
            warning("commercial_license_missing", "Commercial redistribution rights for this exact asset have not been verified.")
        }
        if (!evidence.sourceAndAttributionRecorded) {
            warning("asset_attribution_missing", "Asset source, creator, license text, and attribution have not been recorded.")
        }
        if (!evidence.devicePerformancePassed) {
            warning("device_performance_missing", "Target-device GPU, memory, thermal, and frame-time checks have not passed.")
        }
        if (!evidence.previewExportParityPassed) {
            warning("preview_export_parity_missing", "Preview and encoded MP4 output have not passed visual and audio parity review.")
        }

        return Report(findings, model.totalVertices, model.totalTriangles, model.totalMorphTargets, evidence)
    }
}
