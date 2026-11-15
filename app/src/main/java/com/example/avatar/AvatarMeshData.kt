package com.example.avatar

import android.graphics.Bitmap

enum class PartRole {
    HEAD,
    JAW,
    UPPER_LIP,
    LOWER_LIP,
    MOUTH_CAVITY,
    LEFT_EYE,
    RIGHT_EYE,
    LEFT_EYELID,
    RIGHT_EYELID,
    LEFT_BROW,
    RIGHT_BROW,
    HAIR_ACCESSORY,
    BODY_NECK,
    HALO_RING,
    CUSTOM_DEFORMABLE
}

data class MorphTargetData(
    val name: String,
    val positionDeltas: FloatArray, // length = vertexCount * 3
    val normalDeltas: FloatArray? = null // optional glTF NORMAL target deltas
)

/** Four glTF vertex influences from JOINTS_0 / WEIGHTS_0. Indices are local to the skin. */
data class VertexSkinData(
    val jointIndices: IntArray,
    val weights: FloatArray
) {
    val vertexCount: Int get() = jointIndices.size / 4
}

data class AvatarJoint(
    val name: String,
    val nodeIndex: Int,
    val parentIndex: Int,
    val bindLocalMatrix: FloatArray,
    val inverseBindMatrix: FloatArray
)

data class AvatarSkin(
    val joints: List<AvatarJoint>,
    val skeletonRootNode: Int? = null,
    val meshNodeIndex: Int? = null
)

/** Skinning data is imported now; GPU skinning is a separate renderer milestone. */
data class MeshPart(
    val name: String,
    val positions: FloatArray,
    val normals: FloatArray,
    val uvs: FloatArray,
    val indices: IntArray,
    val baseColor: FloatArray, // RGBA [0..1]
    val metallic: Float = 0.08f,
    val roughness: Float = 0.48f,
    val emissive: FloatArray = floatArrayOf(0f, 0f, 0f),
    val textureBitmap: Bitmap? = null,
    val partRole: PartRole = PartRole.HEAD,
    val morphTargets: List<MorphTargetData> = emptyList(),
    val isSkin: Boolean = false,
    val skinData: VertexSkinData? = null,
    val skinIndex: Int? = null,
    /** glTF node transform; vertices remain in mesh-local coordinates. */
    val nodeWorldMatrix: FloatArray = FloatArray(16).also { android.opengl.Matrix.setIdentityM(it, 0) }
) {
    val vertexCount: Int get() = positions.size / 3
    val triangleCount: Int get() = indices.size / 3
}

data class AvatarModel3D(
    val id: String,
    val name: String,
    val parts: List<MeshPart>,
    val autoJawMinY: Float = -0.35f,
    val autoJawMaxY: Float = -0.05f,
    val autoMouthCenterY: Float = -0.14f,
    val autoMouthFrontZ: Float = 0.18f,
    val isCustomImported: Boolean = false,
    val skins: List<AvatarSkin> = emptyList(),
    /** Model-space normalization applied after each mesh node transform. */
    val normalizationMatrix: FloatArray = FloatArray(16).also { android.opengl.Matrix.setIdentityM(it, 0) }
) {
    val totalVertices: Int get() = parts.sumOf { it.vertexCount }
    val totalTriangles: Int get() = parts.sumOf { it.triangleCount }
    val totalMorphTargets: Int get() = parts.sumOf { it.morphTargets.size }
}
