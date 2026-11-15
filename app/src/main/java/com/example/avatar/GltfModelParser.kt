package com.example.avatar

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.opengl.Matrix
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min

object GltfModelParser {

    fun parse(file: File, id: String, name: String): AvatarModel3D {
        val bytes = file.readBytes()
        require(bytes.size >= 12) { "Invalid 3D model file." }

        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val magic = bb.getInt(0)

        val jsonText: String
        val binChunk: ByteArray?

        if (magic == 0x46546C67) { // "glTF" binary GLB
            val version = bb.getInt(4)
            require(version == 2) { "Only glTF 2.0 GLB files are supported." }
            var offset = 12
            var jsonStr: String? = null
            var binData: ByteArray? = null
            while (offset + 8 <= bytes.size) {
                val chunkLength = bb.getInt(offset)
                val chunkType = bb.getInt(offset + 4)
                val dataStart = offset + 8
                val dataEnd = (dataStart + chunkLength).coerceAtMost(bytes.size)
                when (chunkType) {
                    0x4E4F534A -> jsonStr = String(bytes, dataStart, dataEnd - dataStart, Charsets.UTF_8)
                    0x004E4942 -> binData = bytes.copyOfRange(dataStart, dataEnd)
                }
                offset = dataEnd
            }
            jsonText = requireNotNull(jsonStr) { "GLB missing JSON chunk." }
            binChunk = binData
        } else {
            jsonText = String(bytes, Charsets.UTF_8)
            binChunk = null
        }

        val root = JSONObject(jsonText)
        val buffers = resolveBuffers(root, binChunk)
        val bufferViews = root.optJSONArray("bufferViews") ?: JSONArray()
        val accessors = root.optJSONArray("accessors") ?: JSONArray()
        val meshes = root.optJSONArray("meshes") ?: JSONArray()
        val materials = root.optJSONArray("materials") ?: JSONArray()
        val textures = root.optJSONArray("textures") ?: JSONArray()
        val images = root.optJSONArray("images") ?: JSONArray()
        val nodes = root.optJSONArray("nodes") ?: JSONArray()
        val skinsJson = root.optJSONArray("skins") ?: JSONArray()

        val decodedBitmaps = HashMap<Int, Bitmap>()
        val parts = ArrayList<MeshPart>()

        // Traverse scene nodes to collect world transforms per mesh
        val meshTransforms = ArrayList<MeshNodeInstance>()
        val scenes = root.optJSONArray("scenes")
        val sceneIdx = root.optInt("scene", 0)
        if (scenes != null && scenes.length() > 0 && nodes.length() > 0) {
            val scene = scenes.optJSONObject(sceneIdx) ?: scenes.getJSONObject(0)
            val rootNodes = scene.optJSONArray("nodes") ?: JSONArray()
            val identity = FloatArray(16).also { Matrix.setIdentityM(it, 0) }
            for (i in 0 until rootNodes.length()) {
                traverseNode(rootNodes.getInt(i), identity, nodes, meshTransforms, null)
            }
        } else {
            val identity = FloatArray(16).also { Matrix.setIdentityM(it, 0) }
            for (m in 0 until meshes.length()) {
                meshTransforms.add(MeshNodeInstance(m, identity, null, -1))
            }
        }

        val avatarSkins = parseSkins(skinsJson, nodes, accessors, bufferViews, buffers)

        for (instance in meshTransforms) {
            val meshIdx = instance.meshIndex
            val worldMat = instance.worldMatrix
            val meshObj = meshes.optJSONObject(meshIdx) ?: continue
            val meshName = meshObj.optString("name", "mesh_$meshIdx")
            val targetNames = meshObj.optJSONObject("extras")?.optJSONArray("targetNames")
            val primitives = meshObj.optJSONArray("primitives") ?: continue

            for (pIdx in 0 until primitives.length()) {
                val prim = primitives.getJSONObject(pIdx)
                val attrs = prim.optJSONObject("attributes") ?: continue
                val posAccIdx = attrs.optInt("POSITION", -1)
                if (posAccIdx < 0) continue

                val positions = readVec3Float(accessors.getJSONObject(posAccIdx), bufferViews, buffers)
                val vCount = positions.size / 3
                if (vCount == 0) continue

                val normals = if (attrs.has("NORMAL")) {
                    val rawN = readVec3Float(accessors.getJSONObject(attrs.getInt("NORMAL")), bufferViews, buffers)
                    rawN
                } else null

                val skinData = if (attrs.has("JOINTS_0") && attrs.has("WEIGHTS_0")) {
                    val joints = readVec4Ints(accessors.getJSONObject(attrs.getInt("JOINTS_0")), bufferViews, buffers, vCount)
                    val weights = readVec4Weights(accessors.getJSONObject(attrs.getInt("WEIGHTS_0")), bufferViews, buffers, vCount)
                    VertexSkinData(joints, weights)
                } else null

                val uvs = if (attrs.has("TEXCOORD_0")) {
                    readVec2Float(accessors.getJSONObject(attrs.getInt("TEXCOORD_0")), bufferViews, buffers, vCount)
                } else {
                    FloatArray(vCount * 2)
                }

                val indices = if (prim.has("indices")) {
                    readIndices(accessors.getJSONObject(prim.getInt("indices")), bufferViews, buffers)
                } else {
                    IntArray(vCount) { it }
                }

                val finalNormals = normals ?: ProceduralAvatarFactory.computeSmoothNormals(positions, indices)

                // Read material & embedded baseColorTexture
                var baseColor = floatArrayOf(0.85f, 0.87f, 0.92f, 1f)
                var metallic = 0.12f
                var roughness = 0.45f
                var emissive = floatArrayOf(0f, 0f, 0f)
                var texBitmap: Bitmap? = null

                val matIdx = prim.optInt("material", -1)
                if (matIdx in 0 until materials.length()) {
                    val mat = materials.getJSONObject(matIdx)
                    val pbr = mat.optJSONObject("pbrMetallicRoughness")
                    if (pbr != null) {
                        pbr.optJSONArray("baseColorFactor")?.let { arr ->
                            if (arr.length() >= 4) {
                                baseColor = floatArrayOf(
                                    arr.getDouble(0).toFloat(),
                                    arr.getDouble(1).toFloat(),
                                    arr.getDouble(2).toFloat(),
                                    arr.getDouble(3).toFloat()
                                )
                            }
                        }
                        metallic = pbr.optDouble("metallicFactor", 0.12).toFloat()
                        roughness = pbr.optDouble("roughnessFactor", 0.45).toFloat()
                        val texRef = pbr.optJSONObject("baseColorTexture")
                        if (texRef != null) {
                            val texIdx = texRef.optInt("index", -1)
                            if (texIdx in 0 until textures.length()) {
                                val srcIdx = textures.getJSONObject(texIdx).optInt("source", -1)
                                if (srcIdx in 0 until images.length()) {
                                    texBitmap = decodedBitmaps.getOrPut(srcIdx) {
                                        decodeGltfImage(images.getJSONObject(srcIdx), bufferViews, buffers)
                                    }
                                }
                            }
                        }
                    }
                    mat.optJSONArray("emissiveFactor")?.let { arr ->
                        if (arr.length() >= 3) {
                            emissive = floatArrayOf(
                                arr.getDouble(0).toFloat(),
                                arr.getDouble(1).toFloat(),
                                arr.getDouble(2).toFloat()
                            )
                        }
                    }
                }

                // Parse morph targets
                val morphList = ArrayList<MorphTargetData>()
                val targets = prim.optJSONArray("targets")
                if (targets != null) {
                    for (tIdx in 0 until targets.length()) {
                        val tObj = targets.getJSONObject(tIdx)
                        val deltaPosIdx = tObj.optInt("POSITION", -1)
                        if (deltaPosIdx >= 0) {
                            val deltas = readVec3Float(accessors.getJSONObject(deltaPosIdx), bufferViews, buffers)
                            if (deltas.size == positions.size) {
                                val normalDeltas = tObj.optInt("NORMAL", -1)
                                    .takeIf { it >= 0 }
                                    ?.let { readVec3Float(accessors.getJSONObject(it), bufferViews, buffers) }
                                    ?.takeIf { it.size == positions.size }
                                val tName = targetNames?.optString(tIdx, "morph_$tIdx") ?: "morph_$tIdx"
                                morphList.add(MorphTargetData(tName, deltas, normalDeltas))
                            }
                        }
                    }
                }

                parts += MeshPart(
                    name = "${meshName}_$pIdx",
                    positions = positions,
                    normals = finalNormals,
                    uvs = uvs,
                    indices = indices,
                    baseColor = baseColor,
                    metallic = metallic,
                    roughness = roughness,
                    emissive = emissive,
                    textureBitmap = texBitmap,
                    partRole = PartRole.CUSTOM_DEFORMABLE,
                    morphTargets = morphList,
                    isSkin = true,
                    skinData = skinData,
                    skinIndex = instance.skinIndex.takeIf { it >= 0 },
                    nodeWorldMatrix = worldMat.copyOf()
                )
            }
        }

        require(parts.isNotEmpty()) { "No valid 3D meshes found in glTF/GLB file." }
        val normalizationMatrix = calculateNormalizationMatrix(parts)

        return AvatarModel3D(
            id = id,
            name = name,
            parts = parts,
            autoJawMinY = -0.45f,
            autoJawMaxY = -0.04f,
            autoMouthCenterY = -0.15f,
            autoMouthFrontZ = 0.14f,
            isCustomImported = true,
            skins = avatarSkins,
            normalizationMatrix = normalizationMatrix
        )
    }

    private fun resolveBuffers(root: JSONObject, binChunk: ByteArray?): List<ByteArray> {
        val out = ArrayList<ByteArray>()
        val buffersArr = root.optJSONArray("buffers") ?: return out
        for (i in 0 until buffersArr.length()) {
            val bufObj = buffersArr.getJSONObject(i)
            val uri = bufObj.optString("uri", "")
            val bytes = when {
                uri.isEmpty() && i == 0 && binChunk != null -> binChunk
                uri.startsWith("data:") -> {
                    val comma = uri.indexOf(',')
                    if (comma >= 0) Base64.decode(uri.substring(comma + 1), Base64.DEFAULT) else ByteArray(0)
                }
                else -> ByteArray(0)
            }
            out.add(bytes)
        }
        return out
    }

    private fun decodeGltfImage(
        imgObj: JSONObject,
        bufferViews: JSONArray,
        buffers: List<ByteArray>
    ): Bitmap {
        val uri = imgObj.optString("uri", "")
        val rawBytes = if (uri.startsWith("data:")) {
            val comma = uri.indexOf(',')
            Base64.decode(uri.substring(comma + 1), Base64.DEFAULT)
        } else if (imgObj.has("bufferView")) {
            val bv = bufferViews.getJSONObject(imgObj.getInt("bufferView"))
            val bufIdx = bv.optInt("buffer", 0)
            val offset = bv.optInt("byteOffset", 0)
            val len = bv.getInt("byteLength")
            val buf = buffers[bufIdx]
            buf.copyOfRange(offset, (offset + len).coerceAtMost(buf.size))
        } else {
            ByteArray(0)
        }
        return if (rawBytes.isNotEmpty()) {
            BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size)
                ?: Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        } else {
            Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        }
    }

    private data class MeshNodeInstance(
        val meshIndex: Int, val worldMatrix: FloatArray, val nodeIndex: Int?, val skinIndex: Int
    )

    private fun parseSkins(
        skins: JSONArray, nodes: JSONArray, accessors: JSONArray,
        bufferViews: JSONArray, buffers: List<ByteArray>
    ): List<AvatarSkin> {
        val parents = IntArray(nodes.length()) { -1 }
        for (nodeIndex in 0 until nodes.length()) {
            val children = nodes.getJSONObject(nodeIndex).optJSONArray("children") ?: continue
            for (i in 0 until children.length()) {
                val child = children.getInt(i)
                if (child in parents.indices) parents[child] = nodeIndex
            }
        }
        return List(skins.length()) { skinIndex ->
            val skin = skins.getJSONObject(skinIndex)
            val jointNodes = skin.optJSONArray("joints") ?: JSONArray()
            val nodeToJoint = HashMap<Int, Int>()
            for (i in 0 until jointNodes.length()) nodeToJoint[jointNodes.getInt(i)] = i
            val inverseBindAccessor = skin.optInt("inverseBindMatrices", -1)
            val inverseBind = if (inverseBindAccessor >= 0) {
                readMat4Float(accessors.getJSONObject(inverseBindAccessor), bufferViews, buffers, jointNodes.length())
            } else FloatArray(jointNodes.length() * 16).also { out ->
                for (i in 0 until jointNodes.length()) Matrix.setIdentityM(out, i * 16)
            }
            val joints = List(jointNodes.length()) { jointIndex ->
                val nodeIndex = jointNodes.getInt(jointIndex)
                var ancestor = parents.getOrElse(nodeIndex) { -1 }
                while (ancestor >= 0 && ancestor !in nodeToJoint) {
                    ancestor = parents.getOrElse(ancestor) { -1 }
                }
                val parentJoint = nodeToJoint[ancestor] ?: -1

                // Preserve transforms of non-joint nodes between two joints.
                val chain = ArrayList<Int>()
                var cursor = nodeIndex
                while (cursor >= 0 && cursor != ancestor) {
                    chain.add(cursor)
                    cursor = parents.getOrElse(cursor) { -1 }
                }
                val local = FloatArray(16).also { Matrix.setIdentityM(it, 0) }
                for (chainIndex in chain.indices.reversed()) {
                    val next = FloatArray(16)
                    Matrix.multiplyMM(next, 0, local, 0, nodeLocalMatrix(nodes.getJSONObject(chain[chainIndex])), 0)
                    System.arraycopy(next, 0, local, 0, 16)
                }
                AvatarJoint(
                    name = nodes.getJSONObject(nodeIndex).optString("name", "joint_$jointIndex"),
                    nodeIndex = nodeIndex,
                    parentIndex = parentJoint,
                    bindLocalMatrix = local,
                    inverseBindMatrix = inverseBind.copyOfRange(jointIndex * 16, jointIndex * 16 + 16)
                )
            }
            AvatarSkin(joints, skin.optInt("skeleton", -1).takeIf { it >= 0 })
        }
    }

    private fun readMat4Float(
        acc: JSONObject, bufferViews: JSONArray, buffers: List<ByteArray>, expectedCount: Int
    ): FloatArray {
        require(acc.optString("type") == "MAT4" && acc.optInt("componentType") == 5126) {
            "inverseBindMatrices must use FLOAT MAT4 accessors."
        }
        val count = min(acc.getInt("count"), expectedCount)
        val viewIndex = acc.optInt("bufferView", -1)
        require(viewIndex >= 0) { "inverseBindMatrices accessor has no bufferView." }
        val view = bufferViews.getJSONObject(viewIndex)
        val bytes = buffers[view.optInt("buffer", 0)]
        val stride = view.optInt("byteStride", 64).coerceAtLeast(64)
        val start = view.optInt("byteOffset", 0) + acc.optInt("byteOffset", 0)
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val out = FloatArray(expectedCount * 16)
        for (i in 0 until count) for (j in 0 until 16) {
            val offset = start + i * stride + j * 4
            require(offset + 4 <= bytes.size) { "inverseBindMatrices accessor exceeds buffer bounds." }
            out[i * 16 + j] = bb.getFloat(offset)
        }
        return out
    }

    private fun nodeLocalMatrix(node: JSONObject): FloatArray {
        val local = FloatArray(16).also { Matrix.setIdentityM(it, 0) }
        val matrix = node.optJSONArray("matrix")
        if (matrix != null && matrix.length() == 16) {
            for (i in 0 until 16) local[i] = matrix.getDouble(i).toFloat()
            return local
        }
        node.optJSONArray("translation")?.takeIf { it.length() >= 3 }?.let {
            Matrix.translateM(local, 0, it.getDouble(0).toFloat(), it.getDouble(1).toFloat(), it.getDouble(2).toFloat())
        }
        node.optJSONArray("rotation")?.takeIf { it.length() >= 4 }?.let {
            val rot = quatToMatrix(it.getDouble(0).toFloat(), it.getDouble(1).toFloat(), it.getDouble(2).toFloat(), it.getDouble(3).toFloat())
            val tmp = FloatArray(16); Matrix.multiplyMM(tmp, 0, local, 0, rot, 0); System.arraycopy(tmp, 0, local, 0, 16)
        }
        node.optJSONArray("scale")?.takeIf { it.length() >= 3 }?.let {
            Matrix.scaleM(local, 0, it.getDouble(0).toFloat(), it.getDouble(1).toFloat(), it.getDouble(2).toFloat())
        }
        return local
    }

    private fun traverseNode(
        nodeIdx: Int,
        parentMat: FloatArray,
        nodes: JSONArray,
        outMeshes: MutableList<MeshNodeInstance>,
        parentNodeIndex: Int? = null
    ) {
        if (nodeIdx !in 0 until nodes.length()) return
        val node = nodes.getJSONObject(nodeIdx)
        val local = FloatArray(16)
        Matrix.setIdentityM(local, 0)

        val matArr = node.optJSONArray("matrix")
        if (matArr != null && matArr.length() == 16) {
            for (i in 0 until 16) local[i] = matArr.getDouble(i).toFloat()
        } else {
            val t = node.optJSONArray("translation")
            val r = node.optJSONArray("rotation")
            val s = node.optJSONArray("scale")
            if (t != null && t.length() >= 3) {
                Matrix.translateM(local, 0, t.getDouble(0).toFloat(), t.getDouble(1).toFloat(), t.getDouble(2).toFloat())
            }
            if (r != null && r.length() >= 4) {
                val qMat = quatToMatrix(
                    r.getDouble(0).toFloat(),
                    r.getDouble(1).toFloat(),
                    r.getDouble(2).toFloat(),
                    r.getDouble(3).toFloat()
                )
                val tmp = FloatArray(16)
                Matrix.multiplyMM(tmp, 0, local, 0, qMat, 0)
                System.arraycopy(tmp, 0, local, 0, 16)
            }
            if (s != null && s.length() >= 3) {
                Matrix.scaleM(local, 0, s.getDouble(0).toFloat(), s.getDouble(1).toFloat(), s.getDouble(2).toFloat())
            }
        }

        val world = FloatArray(16)
        Matrix.multiplyMM(world, 0, parentMat, 0, local, 0)

        if (node.has("mesh")) {
            outMeshes.add(MeshNodeInstance(node.getInt("mesh"), world, nodeIdx, node.optInt("skin", -1)))
        }
        val children = node.optJSONArray("children")
        if (children != null) {
            for (i in 0 until children.length()) {
                traverseNode(children.getInt(i), world, nodes, outMeshes, nodeIdx)
            }
        }
    }

    private fun quatToMatrix(x: Float, y: Float, z: Float, w: Float): FloatArray {
        val m = FloatArray(16)
        val xx = x * x; val yy = y * y; val zz = z * z
        val xy = x * y; val xz = x * z; val yz = y * z
        val wx = w * x; val wy = w * y; val wz = w * z
        m[0] = 1f - 2f * (yy + zz); m[4] = 2f * (xy - wz); m[8] = 2f * (xz + wy); m[12] = 0f
        m[1] = 2f * (xy + wz); m[5] = 1f - 2f * (xx + zz); m[9] = 2f * (yz - wx); m[13] = 0f
        m[2] = 2f * (xz - wy); m[6] = 2f * (yz + wx); m[10] = 1f - 2f * (xx + yy); m[14] = 0f
        m[3] = 0f; m[7] = 0f; m[11] = 0f; m[15] = 1f
        return m
    }

    private fun readVec4Ints(
        acc: JSONObject, bufferViews: JSONArray, buffers: List<ByteArray>, expectedCount: Int
    ): IntArray {
        val count = min(acc.getInt("count"), expectedCount)
        val componentType = acc.getInt("componentType")
        val componentBytes = when (componentType) { 5121 -> 1; 5123 -> 2; else -> error("Unsupported JOINTS_0 componentType: $componentType") }
        val bvIndex = acc.optInt("bufferView", -1)
        require(bvIndex >= 0) { "JOINTS_0 accessor has no bufferView." }
        val view = bufferViews.getJSONObject(bvIndex)
        val bytes = buffers[view.optInt("buffer", 0)]
        val elementBytes = componentBytes * 4
        val stride = view.optInt("byteStride", elementBytes).coerceAtLeast(elementBytes)
        val start = view.optInt("byteOffset", 0) + acc.optInt("byteOffset", 0)
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val out = IntArray(expectedCount * 4)
        for (v in 0 until count) for (c in 0..3) {
            val offset = start + v * stride + c * componentBytes
            require(offset + componentBytes <= bytes.size) { "JOINTS_0 accessor exceeds buffer bounds." }
            out[v * 4 + c] = if (componentType == 5121) bytes[offset].toInt() and 0xFF else bb.getShort(offset).toInt() and 0xFFFF
        }
        return out
    }

    private fun readVec4Weights(
        acc: JSONObject, bufferViews: JSONArray, buffers: List<ByteArray>, expectedCount: Int
    ): FloatArray {
        val count = min(acc.getInt("count"), expectedCount)
        val componentType = acc.getInt("componentType")
        val normalized = acc.optBoolean("normalized", false)
        val componentBytes = when (componentType) { 5121 -> 1; 5123 -> 2; 5126 -> 4; else -> error("Unsupported WEIGHTS_0 componentType: $componentType") }
        val bvIndex = acc.optInt("bufferView", -1)
        require(bvIndex >= 0) { "WEIGHTS_0 accessor has no bufferView." }
        val view = bufferViews.getJSONObject(bvIndex)
        val bytes = buffers[view.optInt("buffer", 0)]
        val elementBytes = componentBytes * 4
        val stride = view.optInt("byteStride", elementBytes).coerceAtLeast(elementBytes)
        val start = view.optInt("byteOffset", 0) + acc.optInt("byteOffset", 0)
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val out = FloatArray(expectedCount * 4)
        for (v in 0 until count) {
            var total = 0f
            for (c in 0..3) {
                val offset = start + v * stride + c * componentBytes
                require(offset + componentBytes <= bytes.size) { "WEIGHTS_0 accessor exceeds buffer bounds." }
                val value = when (componentType) {
                    5121 -> (bytes[offset].toInt() and 0xFF).toFloat() / if (normalized) 255f else 1f
                    5123 -> (bb.getShort(offset).toInt() and 0xFFFF).toFloat() / if (normalized) 65535f else 1f
                    else -> bb.getFloat(offset)
                }.coerceAtLeast(0f)
                out[v * 4 + c] = value
                total += value
            }
            if (total > 1e-8f) for (c in 0..3) out[v * 4 + c] /= total
        }
        return out
    }

    private fun readVec3Float(
        acc: JSONObject,
        bufferViews: JSONArray,
        buffers: List<ByteArray>
    ): FloatArray {
        val count = acc.getInt("count")
        val bvIdx = acc.optInt("bufferView", -1)
        if (bvIdx < 0) return FloatArray(count * 3)
        val bv = bufferViews.getJSONObject(bvIdx)
        val buf = buffers[bv.optInt("buffer", 0)]
        val baseOffset = bv.optInt("byteOffset", 0) + acc.optInt("byteOffset", 0)
        val stride = bv.optInt("byteStride", 12).coerceAtLeast(12)
        val bb = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN)
        val out = FloatArray(count * 3)
        for (i in 0 until count) {
            val pos = baseOffset + i * stride
            if (pos + 12 <= buf.size) {
                out[i * 3] = bb.getFloat(pos)
                out[i * 3 + 1] = bb.getFloat(pos + 4)
                out[i * 3 + 2] = bb.getFloat(pos + 8)
            }
        }
        return out
    }

    private fun readVec2Float(
        acc: JSONObject,
        bufferViews: JSONArray,
        buffers: List<ByteArray>,
        expectedCount: Int
    ): FloatArray {
        val count = min(acc.getInt("count"), expectedCount)
        val bvIdx = acc.optInt("bufferView", -1)
        if (bvIdx < 0) return FloatArray(expectedCount * 2)
        val bv = bufferViews.getJSONObject(bvIdx)
        val buf = buffers[bv.optInt("buffer", 0)]
        val baseOffset = bv.optInt("byteOffset", 0) + acc.optInt("byteOffset", 0)
        val stride = bv.optInt("byteStride", 8).coerceAtLeast(8)
        val bb = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN)
        val out = FloatArray(expectedCount * 2)
        for (i in 0 until count) {
            val pos = baseOffset + i * stride
            if (pos + 8 <= buf.size) {
                out[i * 2] = bb.getFloat(pos)
                out[i * 2 + 1] = bb.getFloat(pos + 4)
            }
        }
        return out
    }

    private fun readIndices(
        acc: JSONObject,
        bufferViews: JSONArray,
        buffers: List<ByteArray>
    ): IntArray {
        val count = acc.getInt("count")
        val compType = acc.getInt("componentType")
        val bvIdx = acc.optInt("bufferView", -1)
        if (bvIdx < 0) return IntArray(count) { it }
        val bv = bufferViews.getJSONObject(bvIdx)
        val buf = buffers[bv.optInt("buffer", 0)]
        val baseOffset = bv.optInt("byteOffset", 0) + acc.optInt("byteOffset", 0)
        val bb = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN)
        val out = IntArray(count)
        when (compType) {
            5121 -> for (i in 0 until count) out[i] = buf[baseOffset + i].toInt() and 0xFF
            5123 -> for (i in 0 until count) out[i] = bb.getShort(baseOffset + i * 2).toInt() and 0xFFFF
            5125 -> for (i in 0 until count) out[i] = bb.getInt(baseOffset + i * 4)
            else -> for (i in 0 until count) out[i] = i
        }
        return out
    }

    private fun transformPoints(pts: FloatArray, mat: FloatArray) {
        val v = FloatArray(4)
        val r = FloatArray(4)
        var i = 0
        while (i + 2 < pts.size) {
            v[0] = pts[i]; v[1] = pts[i + 1]; v[2] = pts[i + 2]; v[3] = 1f
            Matrix.multiplyMV(r, 0, mat, 0, v, 0)
            pts[i] = r[0]; pts[i + 1] = r[1]; pts[i + 2] = r[2]
            i += 3
        }
    }

    private fun transformDeltas(deltas: FloatArray, mat: FloatArray) {
        val v = FloatArray(4)
        val r = FloatArray(4)
        var i = 0
        while (i + 2 < deltas.size) {
            v[0] = deltas[i]; v[1] = deltas[i + 1]; v[2] = deltas[i + 2]; v[3] = 0f
            Matrix.multiplyMV(r, 0, mat, 0, v, 0)
            deltas[i] = r[0]; deltas[i + 1] = r[1]; deltas[i + 2] = r[2]
            i += 3
        }
    }

    private fun transformNormals(norms: FloatArray, mat: FloatArray) {
        val v = FloatArray(4)
        val r = FloatArray(4)
        var i = 0
        while (i + 2 < norms.size) {
            v[0] = norms[i]; v[1] = norms[i + 1]; v[2] = norms[i + 2]; v[3] = 0f
            Matrix.multiplyMV(r, 0, mat, 0, v, 0)
            val len = kotlin.math.sqrt(r[0] * r[0] + r[1] * r[1] + r[2] * r[2]).coerceAtLeast(1e-6f)
            norms[i] = r[0] / len; norms[i + 1] = r[1] / len; norms[i + 2] = r[2] / len
            i += 3
        }
    }

    private fun calculateNormalizationMatrix(parts: List<MeshPart>): FloatArray {
        var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE
        var minY = Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        var minZ = Float.MAX_VALUE; var maxZ = -Float.MAX_VALUE
        val point = FloatArray(4)
        val transformed = FloatArray(4)
        for (part in parts) {
            var i = 0
            while (i + 2 < part.positions.size) {
                point[0] = part.positions[i]; point[1] = part.positions[i + 1]
                point[2] = part.positions[i + 2]; point[3] = 1f
                Matrix.multiplyMV(transformed, 0, part.nodeWorldMatrix, 0, point, 0)
                minX = min(minX, transformed[0]); maxX = max(maxX, transformed[0])
                minY = min(minY, transformed[1]); maxY = max(maxY, transformed[1])
                minZ = min(minZ, transformed[2]); maxZ = max(maxZ, transformed[2])
                i += 3
            }
        }
        val cx = (minX + maxX) * 0.5f
        val cy = (minY + maxY) * 0.5f
        val cz = (minZ + maxZ) * 0.5f
        val span = max(maxX - minX, max(maxY - minY, maxZ - minZ)).coerceAtLeast(1e-4f)
        val scale = 1.48f / span
        return FloatArray(16).also {
            Matrix.setIdentityM(it, 0)
            Matrix.translateM(it, 0, -cx * scale, -cy * scale - 0.02f, -cz * scale)
            Matrix.scaleM(it, 0, scale, scale, scale)
        }
    }
}

        nodeIdx: Int,
        parentMat: FloatArray,
        nodes: JSONArray,
        outMeshes: MutableList<MeshNodeInstance>,
        parentNodeIndex: Int? = null
    ) {
        if (nodeIdx !in 0 until nodes.length()) return
        val node = nodes.getJSONObject(nodeIdx)
        val local = FloatArray(16)
        Matrix.setIdentityM(local, 0)

        val matArr = node.optJSONArray("matrix")
        if (matArr != null && matArr.length() == 16) {
            for (i in 0 until 16) local[i] = matArr.getDouble(i).toFloat()
        } else {
            val t = node.optJSONArray("translation")
            val r = node.optJSONArray("rotation")
            val s = node.optJSONArray("scale")
            if (t != null && t.length() >= 3) {
                Matrix.translateM(local, 0, t.getDouble(0).toFloat(), t.getDouble(1).toFloat(), t.getDouble(2).toFloat())
            }
            if (r != null && r.length() >= 4) {
                val qMat = quatToMatrix(
                    r.getDouble(0).toFloat(),
                    r.getDouble(1).toFloat(),
                    r.getDouble(2).toFloat(),
                    r.getDouble(3).toFloat()
                )
                val tmp = FloatArray(16)
                Matrix.multiplyMM(tmp, 0, local, 0, qMat, 0)
                System.arraycopy(tmp, 0, local, 0, 16)
            }
            if (s != null && s.length() >= 3) {
                Matrix.scaleM(local, 0, s.getDouble(0).toFloat(), s.getDouble(1).toFloat(), s.getDouble(2).toFloat())
            }
        }

        val world = FloatArray(16)
        Matrix.multiplyMM(world, 0, parentMat, 0, local, 0)

        if (node.has("mesh")) {
            outMeshes.add(MeshNodeInstance(node.getInt("mesh"), world, nodeIdx, node.optInt("skin", -1)))
        }
        val children = node.optJSONArray("children")
        if (children != null) {
            for (i in 0 until children.length()) {
                traverseNode(children.getInt(i), world, nodes, outMeshes, nodeIdx)
            }
        }
    }

    private fun quatToMatrix(x: Float, y: Float, z: Float, w: Float): FloatArray {
        val m = FloatArray(16)
        val xx = x * x; val yy = y * y; val zz = z * z
        val xy = x * y; val xz = x * z; val yz = y * z
        val wx = w * x; val wy = w * y; val wz = w * z
        m[0] = 1f - 2f * (yy + zz); m[4] = 2f * (xy - wz); m[8] = 2f * (xz + wy); m[12] = 0f
        m[1] = 2f * (xy + wz); m[5] = 1f - 2f * (xx + zz); m[9] = 2f * (yz - wx); m[13] = 0f
        m[2] = 2f * (xz - wy); m[6] = 2f * (yz + wx); m[10] = 1f - 2f * (xx + yy); m[14] = 0f
        m[3] = 0f; m[7] = 0f; m[11] = 0f; m[15] = 1f
        return m
    }

    private fun readVec4Ints(
        acc: JSONObject, bufferViews: JSONArray, buffers: List<ByteArray>, expectedCount: Int
    ): IntArray {
        val count = min(acc.getInt("count"), expectedCount)
        val componentType = acc.getInt("componentType")
        val componentBytes = when (componentType) { 5121 -> 1; 5123 -> 2; else -> error("Unsupported JOINTS_0 componentType: $componentType") }
        val bvIndex = acc.optInt("bufferView", -1)
        require(bvIndex >= 0) { "JOINTS_0 accessor has no bufferView." }
        val view = bufferViews.getJSONObject(bvIndex)
        val bytes = buffers[view.optInt("buffer", 0)]
        val elementBytes = componentBytes * 4
        val stride = view.optInt("byteStride", elementBytes).coerceAtLeast(elementBytes)
        val start = view.optInt("byteOffset", 0) + acc.optInt("byteOffset", 0)
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val out = IntArray(expectedCount * 4)
        for (v in 0 until count) for (c in 0..3) {
            val offset = start + v * stride + c * componentBytes
            require(offset + componentBytes <= bytes.size) { "JOINTS_0 accessor exceeds buffer bounds." }
            out[v * 4 + c] = if (componentType == 5121) bytes[offset].toInt() and 0xFF else bb.getShort(offset).toInt() and 0xFFFF
        }
        return out
    }

    private fun readVec4Weights(
        acc: JSONObject, bufferViews: JSONArray, buffers: List<ByteArray>, expectedCount: Int
    ): FloatArray {
        val count = min(acc.getInt("count"), expectedCount)
        val componentType = acc.getInt("componentType")
        val normalized = acc.optBoolean("normalized", false)
        val componentBytes = when (componentType) { 5121 -> 1; 5123 -> 2; 5126 -> 4; else -> error("Unsupported WEIGHTS_0 componentType: $componentType") }
        val bvIndex = acc.optInt("bufferView", -1)
        require(bvIndex >= 0) { "WEIGHTS_0 accessor has no bufferView." }
        val view = bufferViews.getJSONObject(bvIndex)
        val bytes = buffers[view.optInt("buffer", 0)]
        val elementBytes = componentBytes * 4
        val stride = view.optInt("byteStride", elementBytes).coerceAtLeast(elementBytes)
        val start = view.optInt("byteOffset", 0) + acc.optInt("byteOffset", 0)
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val out = FloatArray(expectedCount * 4)
        for (v in 0 until count) {
            var total = 0f
            for (c in 0..3) {
                val offset = start + v * stride + c * componentBytes
                require(offset + componentBytes <= bytes.size) { "WEIGHTS_0 accessor exceeds buffer bounds." }
                val value = when (componentType) {
                    5121 -> (bytes[offset].toInt() and 0xFF).toFloat() / if (normalized) 255f else 1f
                    5123 -> (bb.getShort(offset).toInt() and 0xFFFF).toFloat() / if (normalized) 65535f else 1f
                    else -> bb.getFloat(offset)
                }.coerceAtLeast(0f)
                out[v * 4 + c] = value
                total += value
            }
            if (total > 1e-8f) for (c in 0..3) out[v * 4 + c] /= total
        }
        return out
    }

    private fun readVec3Float(
        acc: JSONObject,
        bufferViews: JSONArray,
        buffers: List<ByteArray>
    ): FloatArray {
        val count = acc.getInt("count")
        val bvIdx = acc.optInt("bufferView", -1)
        if (bvIdx < 0) return FloatArray(count * 3)
        val bv = bufferViews.getJSONObject(bvIdx)
        val buf = buffers[bv.optInt("buffer", 0)]
        val baseOffset = bv.optInt("byteOffset", 0) + acc.optInt("byteOffset", 0)
        val stride = bv.optInt("byteStride", 12).coerceAtLeast(12)
        val bb = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN)
        val out = FloatArray(count * 3)
        for (i in 0 until count) {
            val pos = baseOffset + i * stride
            if (pos + 12 <= buf.size) {
                out[i * 3] = bb.getFloat(pos)
                out[i * 3 + 1] = bb.getFloat(pos + 4)
                out[i * 3 + 2] = bb.getFloat(pos + 8)
            }
        }
        return out
    }

    private fun readVec2Float(
        acc: JSONObject,
        bufferViews: JSONArray,
        buffers: List<ByteArray>,
        expectedCount: Int
    ): FloatArray {
        val count = min(acc.getInt("count"), expectedCount)
        val bvIdx = acc.optInt("bufferView", -1)
        if (bvIdx < 0) return FloatArray(expectedCount * 2)
        val bv = bufferViews.getJSONObject(bvIdx)
        val buf = buffers[bv.optInt("buffer", 0)]
        val baseOffset = bv.optInt("byteOffset", 0) + acc.optInt("byteOffset", 0)
        val stride = bv.optInt("byteStride", 8).coerceAtLeast(8)
        val bb = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN)
        val out = FloatArray(expectedCount * 2)
        for (i in 0 until count) {
            val pos = baseOffset + i * stride
            if (pos + 8 <= buf.size) {
                out[i * 2] = bb.getFloat(pos)
                out[i * 2 + 1] = bb.getFloat(pos + 4)
            }
        }
        return out
    }

    private fun readIndices(
        acc: JSONObject,
        bufferViews: JSONArray,
        buffers: List<ByteArray>
    ): IntArray {
        val count = acc.getInt("count")
        val compType = acc.getInt("componentType")
        val bvIdx = acc.optInt("bufferView", -1)
        if (bvIdx < 0) return IntArray(count) { it }
        val bv = bufferViews.getJSONObject(bvIdx)
        val buf = buffers[bv.optInt("buffer", 0)]
        val baseOffset = bv.optInt("byteOffset", 0) + acc.optInt("byteOffset", 0)
        val bb = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN)
        val out = IntArray(count)
        when (compType) {
            5121 -> for (i in 0 until count) out[i] = buf[baseOffset + i].toInt() and 0xFF
            5123 -> for (i in 0 until count) out[i] = bb.getShort(baseOffset + i * 2).toInt() and 0xFFFF
            5125 -> for (i in 0 until count) out[i] = bb.getInt(baseOffset + i * 4)
            else -> for (i in 0 until count) out[i] = i
        }
        return out
    }

    private fun transformPoints(pts: FloatArray, mat: FloatArray) {
        val v = FloatArray(4)
        val r = FloatArray(4)
        var i = 0
        while (i + 2 < pts.size) {
            v[0] = pts[i]; v[1] = pts[i + 1]; v[2] = pts[i + 2]; v[3] = 1f
            Matrix.multiplyMV(r, 0, mat, 0, v, 0)
            pts[i] = r[0]; pts[i + 1] = r[1]; pts[i + 2] = r[2]
            i += 3
        }
    }

    private fun transformDeltas(deltas: FloatArray, mat: FloatArray) {
        val v = FloatArray(4)
        val r = FloatArray(4)
        var i = 0
        while (i + 2 < deltas.size) {
            v[0] = deltas[i]; v[1] = deltas[i + 1]; v[2] = deltas[i + 2]; v[3] = 0f
            Matrix.multiplyMV(r, 0, mat, 0, v, 0)
            deltas[i] = r[0]; deltas[i + 1] = r[1]; deltas[i + 2] = r[2]
            i += 3
        }
    }

    private fun transformNormals(norms: FloatArray, mat: FloatArray) {
        val v = FloatArray(4)
        val r = FloatArray(4)
        var i = 0
        while (i + 2 < norms.size) {
            v[0] = norms[i]; v[1] = norms[i + 1]; v[2] = norms[i + 2]; v[3] = 0f
            Matrix.multiplyMV(r, 0, mat, 0, v, 0)
            val len = kotlin.math.sqrt(r[0] * r[0] + r[1] * r[1] + r[2] * r[2]).coerceAtLeast(1e-6f)
            norms[i] = r[0] / len; norms[i + 1] = r[1] / len; norms[i + 2] = r[2] / len
            i += 3
        }
    }

    private fun normalizeAvatarParts(parts: List<MeshPart>) {
        var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE
        var minY = Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        var minZ = Float.MAX_VALUE; var maxZ = -Float.MAX_VALUE
        for (part in parts) {
            val pos = part.positions
            var i = 0
            while (i + 2 < pos.size) {
                minX = min(minX, pos[i]); maxX = max(maxX, pos[i])
                minY = min(minY, pos[i + 1]); maxY = max(maxY, pos[i + 1])
                minZ = min(minZ, pos[i + 2]); maxZ = max(maxZ, pos[i + 2])
                i += 3
            }
        }
        val cx = (minX + maxX) * 0.5f
        val cy = (minY + maxY) * 0.5f
        val cz = (minZ + maxZ) * 0.5f
        val maxSpan = max(maxX - minX, max(maxY - minY, maxZ - minZ)).coerceAtLeast(1e-4f)
        val scale = 1.48f / maxSpan

        for (part in parts) {
            val pos = part.positions
            var i = 0
            while (i + 2 < pos.size) {
                pos[i] = (pos[i] - cx) * scale
                pos[i + 1] = (pos[i + 1] - cy) * scale - 0.02f
                pos[i + 2] = (pos[i + 2] - cz) * scale
                i += 3
            }
            for (mt in part.morphTargets) {
                val d = mt.positionDeltas
                for (k in d.indices) d[k] *= scale
            }
        }
    }
}
