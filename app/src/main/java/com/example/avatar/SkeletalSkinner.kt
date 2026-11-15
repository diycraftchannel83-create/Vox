package com.example.avatar

import kotlin.math.sqrt

/**
 * CPU reference implementation of glTF linear-blend skinning.
 * Matrices are column-major 4x4, matching glTF and Android OpenGL conventions.
 */
object SkeletalSkinner {

    data class DeformedMesh(val positions: FloatArray, val normals: FloatArray)

    /** Builds mesh-local joint matrices: inverse(meshWorld) * jointWorld * inverseBind. */
    fun buildPalette(skin: AvatarSkin, meshWorld: FloatArray, localPose: List<FloatArray>? = null): List<FloatArray> {
        require(meshWorld.size == 16) { "meshWorld must be a 4x4 matrix." }
        require(localPose == null || localPose.size == skin.joints.size) {
            "Pose joint count does not match the skin."
        }
        val inverseMesh = invert(meshWorld)
        val world = ArrayList<FloatArray>(skin.joints.size)
        for (i in skin.joints.indices) {
            val joint = skin.joints[i]
            val local = localPose?.get(i) ?: joint.bindLocalMatrix
            require(local.size == 16 && joint.inverseBindMatrix.size == 16) {
                "Joint matrices must contain 16 values."
            }
            val jointWorld = if (joint.parentIndex in world.indices) {
                multiply(world[joint.parentIndex], local)
            } else {
                local.copyOf()
            }
            world += jointWorld
        }
        return skin.joints.indices.map { index ->
            multiply(multiply(inverseMesh, world[index]), skin.joints[index].inverseBindMatrix)
        }
    }

    /** Applies up to four normalized vertex influences to positions and normals. */
    fun deform(
        positions: FloatArray,
        normals: FloatArray,
        skinData: VertexSkinData,
        palette: List<FloatArray>
    ): DeformedMesh {
        require(positions.size % 3 == 0 && normals.size == positions.size) {
            "Position and normal arrays must have matching VEC3 data."
        }
        val vertexCount = positions.size / 3
        require(skinData.vertexCount == vertexCount && skinData.weights.size == vertexCount * 4) {
            "Skin influence count does not match vertex count."
        }
        require(palette.all { it.size == 16 }) { "Every skin matrix must be 4x4." }

        val outPositions = FloatArray(positions.size)
        val outNormals = FloatArray(normals.size)
        for (vertex in 0 until vertexCount) {
            val base = vertex * 3
            var total = 0f
            for (influence in 0..3) {
                val slot = vertex * 4 + influence
                val weight = skinData.weights[slot].coerceAtLeast(0f)
                val jointIndex = skinData.jointIndices[slot]
                if (weight <= 0f) continue
                require(jointIndex in palette.indices) { "Vertex references missing joint $jointIndex." }
                val matrix = palette[jointIndex]
                val x = positions[base]; val y = positions[base + 1]; val z = positions[base + 2]
                outPositions[base] += (matrix[0] * x + matrix[4] * y + matrix[8] * z + matrix[12]) * weight
                outPositions[base + 1] += (matrix[1] * x + matrix[5] * y + matrix[9] * z + matrix[13]) * weight
                outPositions[base + 2] += (matrix[2] * x + matrix[6] * y + matrix[10] * z + matrix[14]) * weight

                val nx = normals[base]; val ny = normals[base + 1]; val nz = normals[base + 2]
                outNormals[base] += (matrix[0] * nx + matrix[4] * ny + matrix[8] * nz) * weight
                outNormals[base + 1] += (matrix[1] * nx + matrix[5] * ny + matrix[9] * nz) * weight
                outNormals[base + 2] += (matrix[2] * nx + matrix[6] * ny + matrix[10] * nz) * weight
                total += weight
            }
            if (total <= 1e-8f) {
                for (axis in 0..2) {
                    outPositions[base + axis] = positions[base + axis]
                    outNormals[base + axis] = normals[base + axis]
                }
            } else {
                val inv = 1f / total
                for (axis in 0..2) {
                    outPositions[base + axis] *= inv
                    outNormals[base + axis] *= inv
                }
                val n = sqrt(
                    outNormals[base] * outNormals[base] +
                        outNormals[base + 1] * outNormals[base + 1] +
                        outNormals[base + 2] * outNormals[base + 2]
                ).coerceAtLeast(1e-8f)
                for (axis in 0..2) outNormals[base + axis] /= n
            }
        }
        return DeformedMesh(outPositions, outNormals)
    }

    fun multiply(a: FloatArray, b: FloatArray): FloatArray {
        require(a.size == 16 && b.size == 16)
        val out = FloatArray(16)
        for (column in 0..3) for (row in 0..3) {
            var value = 0f
            for (k in 0..3) value += a[k * 4 + row] * b[column * 4 + k]
            out[column * 4 + row] = value
        }
        return out
    }

    fun invert(matrix: FloatArray): FloatArray {
        require(matrix.size == 16)
        val a = Array(4) { row -> DoubleArray(8) { col ->
            when {
                col < 4 -> matrix[col * 4 + row].toDouble()
                col - 4 == row -> 1.0
                else -> 0.0
            }
        } }
        for (pivot in 0..3) {
            var best = pivot
            for (row in pivot + 1..3) if (kotlin.math.abs(a[row][pivot]) > kotlin.math.abs(a[best][pivot])) best = row
            require(kotlin.math.abs(a[best][pivot]) > 1e-12) { "Matrix is singular and cannot be inverted." }
            val swap = a[pivot]; a[pivot] = a[best]; a[best] = swap
            val divisor = a[pivot][pivot]
            for (col in 0..7) a[pivot][col] /= divisor
            for (row in 0..3) if (row != pivot) {
                val factor = a[row][pivot]
                for (col in 0..7) a[row][col] -= factor * a[pivot][col]
            }
        }
        return FloatArray(16) { index -> a[index % 4][4 + index / 4].toFloat() }
    }
}
