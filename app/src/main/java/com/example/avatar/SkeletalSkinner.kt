package com.example.avatar

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * CPU reference implementation of glTF linear-blend skinning.
 *
 * Contract: positions/normals passed to deform() must be in the mesh node's
 * local coordinate space. Do not feed the currently normalized/world-baked
 * positions produced by GltfModelParser until that importer is updated to
 * preserve mesh-local geometry and its normalization transform.
 *
 * Matrices use Android/OpenGL column-major layout.
 */
object SkeletalSkinner {
    fun buildJointWorldMatrices(
        skin: AvatarSkin,
        nodeLocalMatrices: Map<Int, FloatArray>
    ): List<FloatArray> {
        val resolved = ArrayList<FloatArray>(skin.joints.size)
        val visiting = BooleanArray(skin.joints.size)

        fun resolve(index: Int): FloatArray {
            require(index in skin.joints.indices) { "Joint parent index is out of range." }
            if (resolved.size > index && resolved[index].isNotEmpty()) return resolved[index]
            require(!visiting[index]) { "Joint hierarchy contains a cycle." }
            visiting[index] = true
            val joint = skin.joints[index]
            val local = nodeLocalMatrices[joint.nodeIndex] ?: joint.bindLocalMatrix
            require(local.size == 16) { "Joint local transform must be a 4x4 matrix." }
            val world = if (joint.parentIndex >= 0) {
                multiply(resolve(joint.parentIndex), local)
            } else local.copyOf()
            while (resolved.size <= index) resolved.add(FloatArray(0))
            resolved[index] = world
            visiting[index] = false
            return world
        }

        skin.joints.indices.forEach(::resolve)
        return resolved
    }

    /**
     * Returns mesh-local joint matrices: inverse(meshWorld) * jointWorld * inverseBind.
     */
    fun buildPalette(
        skin: AvatarSkin,
        nodeLocalMatrices: Map<Int, FloatArray>,
        meshWorldMatrix: FloatArray
    ): List<FloatArray> {
        require(meshWorldMatrix.size == 16) { "Mesh world transform must be a 4x4 matrix." }
        val inverseMesh = invert(meshWorldMatrix)
        return buildJointWorldMatrices(skin, nodeLocalMatrices).mapIndexed { index, jointWorld ->
            multiply(multiply(inverseMesh, jointWorld), skin.joints[index].inverseBindMatrix)
        }
    }

    fun deform(
        positions: FloatArray,
        normals: FloatArray?,
        influences: VertexSkinData,
        palette: List<FloatArray>
    ): Pair<FloatArray, FloatArray?> {
        require(positions.size % 3 == 0) { "POSITION array must contain VEC3 values." }
        val vertexCount = positions.size / 3
        require(influences.jointIndices.size == vertexCount * 4 &&
            influences.weights.size == vertexCount * 4) { "Skin influence count does not match vertices." }
        require(normals == null || normals.size == positions.size) { "NORMAL count does not match POSITION." }
        require(palette.all { it.size == 16 }) { "Every joint palette entry must be a 4x4 matrix." }

        val outPositions = FloatArray(positions.size)
        val outNormals = normals?.let { FloatArray(it.size) }
        for (vertex in 0 until vertexCount) {
            val base = vertex * 3
            var totalWeight = 0f
            for (influence in 0..3) {
                val slot = vertex * 4 + influence
                val weight = influences.weights[slot]
                if (weight <= 0f) continue
                val joint = influences.jointIndices[slot]
                require(joint in palette.indices) { "Vertex references a joint outside the palette." }
                val matrix = palette[joint]
                val x = positions[base]; val y = positions[base + 1]; val z = positions[base + 2]
                outPositions[base] += (matrix[0] * x + matrix[4] * y + matrix[8] * z + matrix[12]) * weight
                outPositions[base + 1] += (matrix[1] * x + matrix[5] * y + matrix[9] * z + matrix[13]) * weight
                outPositions[base + 2] += (matrix[2] * x + matrix[6] * y + matrix[10] * z + matrix[14]) * weight
                if (normals != null && outNormals != null) {
                    val nx = normals[base]; val ny = normals[base + 1]; val nz = normals[base + 2]
                    outNormals[base] += (matrix[0] * nx + matrix[4] * ny + matrix[8] * nz) * weight
                    outNormals[base + 1] += (matrix[1] * nx + matrix[5] * ny + matrix[9] * nz) * weight
                    outNormals[base + 2] += (matrix[2] * nx + matrix[6] * ny + matrix[10] * nz) * weight
                }
                totalWeight += weight
            }
            if (totalWeight <= 1e-8f) {
                for (axis in 0..2) {
                    outPositions[base + axis] = positions[base + axis]
                    if (normals != null && outNormals != null) outNormals[base + axis] = normals[base + axis]
                }
            } else if (normals != null && outNormals != null) {
                val n = sqrt(outNormals[base] * outNormals[base] +
                    outNormals[base + 1] * outNormals[base + 1] +
                    outNormals[base + 2] * outNormals[base + 2]).coerceAtLeast(1e-8f)
                outNormals[base] /= n; outNormals[base + 1] /= n; outNormals[base + 2] /= n
            }
        }
        return outPositions to outNormals
    }

    private fun multiply(a: FloatArray, b: FloatArray): FloatArray {
        require(a.size == 16 && b.size == 16)
        val out = FloatArray(16)
        for (column in 0..3) for (row in 0..3) {
            var sum = 0f
            for (k in 0..3) sum += a[k * 4 + row] * b[column * 4 + k]
            out[column * 4 + row] = sum
        }
        return out
    }

    private fun invert(matrix: FloatArray): FloatArray {
        require(matrix.size == 16)
        val a = Array(4) { row ->
            DoubleArray(8) { column ->
                when {
                    column < 4 -> matrix[column * 4 + row].toDouble()
                    column - 4 == row -> 1.0
                    else -> 0.0
                }
            }
        }
        for (pivot in 0..3) {
            var best = pivot
            for (row in pivot + 1..3) if (abs(a[row][pivot]) > abs(a[best][pivot])) best = row
            require(abs(a[best][pivot]) > 1e-10) { "Mesh world transform is singular." }
            val swap = a[pivot]; a[pivot] = a[best]; a[best] = swap
            val divisor = a[pivot][pivot]
            for (column in 0..7) a[pivot][column] /= divisor
            for (row in 0..3) if (row != pivot) {
                val factor = a[row][pivot]
                for (column in 0..7) a[row][column] -= factor * a[pivot][column]
            }
        }
        return FloatArray(16) { index -> a[index % 4][4 + index / 4].toFloat() }
    }
}
