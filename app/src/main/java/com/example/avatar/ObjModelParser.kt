package com.example.avatar

import java.io.File
import kotlin.math.max
import kotlin.math.min

object ObjModelParser {

    fun parse(file: File, id: String, name: String): AvatarModel3D {
        val rawVerts = ArrayList<Float>(4096)
        val rawNorms = ArrayList<Float>(4096)
        val rawUvs = ArrayList<Float>(2048)

        val outPos = ArrayList<Float>(8192)
        val outNorm = ArrayList<Float>(8192)
        val outUv = ArrayList<Float>(4096)
        val outIndices = ArrayList<Int>(8192)
        val vertexMap = HashMap<Long, Int>(4096)
        var hasNormals = false

        file.bufferedReader().useLines { lines ->
            for (rawLine in lines) {
                val line = rawLine.trim()
                if (line.isEmpty() || line.startsWith("#")) continue
                val parts = line.split(Regex("\\s+"))
                when (parts[0]) {
                    "v" -> if (parts.size >= 4) {
                        rawVerts.add(parts[1].toFloatOrNull() ?: 0f)
                        rawVerts.add(parts[2].toFloatOrNull() ?: 0f)
                        rawVerts.add(parts[3].toFloatOrNull() ?: 0f)
                    }
                    "vn" -> if (parts.size >= 4) {
                        hasNormals = true
                        rawNorms.add(parts[1].toFloatOrNull() ?: 0f)
                        rawNorms.add(parts[2].toFloatOrNull() ?: 0f)
                        rawNorms.add(parts[3].toFloatOrNull() ?: 1f)
                    }
                    "vt" -> if (parts.size >= 3) {
                        rawUvs.add(parts[1].toFloatOrNull() ?: 0f)
                        rawUvs.add(1f - (parts[2].toFloatOrNull() ?: 0f))
                    }
                    "f" -> if (parts.size >= 4) {
                        val polyIndices = IntArray(parts.size - 1)
                        val vCount = rawVerts.size / 3
                        val uvCount = rawUvs.size / 2
                        val nCount = rawNorms.size / 3

                        for (k in 1 until parts.size) {
                            val tokens = parts[k].split("/")
                            val viRaw = tokens[0].toIntOrNull() ?: 1
                            val vi = resolveObjIndex(viRaw, vCount)
                            val ti = if (tokens.size > 1 && tokens[1].isNotEmpty()) {
                                resolveObjIndex(tokens[1].toIntOrNull() ?: 1, uvCount)
                            } else -1
                            val ni = if (tokens.size > 2 && tokens[2].isNotEmpty()) {
                                resolveObjIndex(tokens[2].toIntOrNull() ?: 1, nCount)
                            } else -1

                            val key = ((vi.toLong() and 0xFFFFFL) shl 40) or
                                (((ti + 1).toLong() and 0xFFFFFL) shl 20) or
                                ((ni + 1).toLong() and 0xFFFFFL)
                            val existing = vertexMap[key]
                            if (existing != null) {
                                polyIndices[k - 1] = existing
                            } else {
                                val newIdx = outPos.size / 3
                                val vBase = vi * 3
                                outPos.add(rawVerts.getOrElse(vBase) { 0f })
                                outPos.add(rawVerts.getOrElse(vBase + 1) { 0f })
                                outPos.add(rawVerts.getOrElse(vBase + 2) { 0f })

                                if (ti >= 0 && ti * 2 + 1 < rawUvs.size) {
                                    outUv.add(rawUvs[ti * 2])
                                    outUv.add(rawUvs[ti * 2 + 1])
                                } else {
                                    outUv.add(0f)
                                    outUv.add(0f)
                                }

                                if (ni >= 0 && ni * 3 + 2 < rawNorms.size) {
                                    outNorm.add(rawNorms[ni * 3])
                                    outNorm.add(rawNorms[ni * 3 + 1])
                                    outNorm.add(rawNorms[ni * 3 + 2])
                                } else {
                                    outNorm.add(0f)
                                    outNorm.add(0f)
                                    outNorm.add(1f)
                                }
                                vertexMap[key] = newIdx
                                polyIndices[k - 1] = newIdx
                            }
                        }

                        // Fan triangulation for triangles / quads / n-gons
                        for (t in 1 until polyIndices.size - 1) {
                            outIndices.add(polyIndices[0])
                            outIndices.add(polyIndices[t])
                            outIndices.add(polyIndices[t + 1])
                        }
                    }
                }
            }
        }

        require(outPos.isNotEmpty() && outIndices.isNotEmpty()) {
            "OBJ file contains no renderable geometry."
        }

        val positions = outPos.toFloatArray()
        val indices = outIndices.toIntArray()
        normalizePositions(positions)
        val normals = if (hasNormals && outNorm.size == positions.size) {
            outNorm.toFloatArray()
        } else {
            ProceduralAvatarFactory.computeSmoothNormals(positions, indices)
        }
        val uvs = outUv.toFloatArray()

        val part = MeshPart(
            name = name,
            positions = positions,
            normals = normals,
            uvs = uvs,
            indices = indices,
            baseColor = floatArrayOf(0.84f, 0.87f, 0.92f, 1f),
            metallic = 0.14f,
            roughness = 0.42f,
            partRole = PartRole.CUSTOM_DEFORMABLE,
            isSkin = true
        )

        return AvatarModel3D(
            id = id,
            name = name,
            parts = listOf(part),
            autoJawMinY = -0.45f,
            autoJawMaxY = -0.04f,
            autoMouthCenterY = -0.16f,
            autoMouthFrontZ = 0.15f,
            isCustomImported = true
        )
    }

    private fun resolveObjIndex(raw: Int, count: Int): Int {
        if (count <= 0) return 0
        return if (raw > 0) (raw - 1).coerceIn(0, count - 1)
        else (count + raw).coerceIn(0, count - 1)
    }

    private fun normalizePositions(positions: FloatArray) {
        var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE
        var minY = Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        var minZ = Float.MAX_VALUE; var maxZ = -Float.MAX_VALUE
        var i = 0
        while (i + 2 < positions.size) {
            val x = positions[i]; val y = positions[i + 1]; val z = positions[i + 2]
            minX = min(minX, x); maxX = max(maxX, x)
            minY = min(minY, y); maxY = max(maxY, y)
            minZ = min(minZ, z); maxZ = max(maxZ, z)
            i += 3
        }
        val cx = (minX + maxX) * 0.5f
        val cy = (minY + maxY) * 0.5f
        val cz = (minZ + maxZ) * 0.5f
        val maxSpan = max(maxX - minX, max(maxY - minY, maxZ - minZ)).coerceAtLeast(1e-4f)
        val scale = 1.45f / maxSpan
        i = 0
        while (i + 2 < positions.size) {
            positions[i] = (positions[i] - cx) * scale
            positions[i + 1] = (positions[i + 1] - cy) * scale - 0.02f
            positions[i + 2] = (positions[i + 2] - cz) * scale
            i += 3
        }
    }
}
