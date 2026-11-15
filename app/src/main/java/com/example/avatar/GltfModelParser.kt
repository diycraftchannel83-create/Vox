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
            val declaredLength = bb.getInt(8)
            require(declaredLength == bytes.size) { "GLB header length does not match the file size." }
            var offset = 12
            var jsonStr: String? = null
            var binData: ByteArray? = null
            while (offset + 8 <= bytes.size) {
                val chunkLength = bb.getInt(offset)
                val chunkType = bb.getInt(offset + 4)
                require(chunkLength >= 0) { "GLB contains a negative chunk length." }
                val dataStart = offset + 8
                val dataEnd = dataStart.toLong() + chunkLength.toLong()
                require(dataEnd <= bytes.size.toLong()) { "GLB chunk exceeds the file boundary." }
                val safeDataEnd = dataEnd.toInt()
                when (chunkType) {
                    0x4E4F534A -> jsonStr = String(bytes, dataStart, safeDataEnd - dataStart, Charsets.UTF_8)
                    0x004E4942 -> binData = bytes.copyOfRange(dataStart, safeDataEnd)
                }
                offset = safeDataEnd
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
                traverseNode(rootNodes.getInt(i), identity, nodes, meshTransforms, BooleanArray(nodes.length()))
            }
        } else if (nodes.length() > 0) {
            // A glTF file may omit the scenes array. In that case render the node forest
            // rather than discarding node transforms and skin references.
            val hasParent = BooleanArray(nodes.length())
            for (nodeIndex in 0 until nodes.length()) {
                val children = nodes.getJSONObject(nodeIndex).optJSONArray("children") ?: continue
                for (i in 0 until children.length()) {
                    val child = children.getInt(i)
                    require(child in hasParent.indices) { "glTF node child index is out of range." }
                    hasParent[child] = true
                }
            }
            val identity = FloatArray(16).also { Matrix.setIdentityM(it, 0) }
            for (nodeIndex in hasParent.indices) {
                if (!hasParent[nodeIndex]) traverseNode(nodeIndex, identity, nodes, meshTransforms, BooleanArray(nodes.length()))
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
                uri.isEmpty() && binChunk == null -> error("glTF buffer $i has no embedded data. Import a self-contained GLB or embed the buffer as a data URI.")
                uri.startsWith("data:") -> {
                    val comma = uri.indexOf(',')
                    require(comma >= 0) { "glTF buffer $i has an invalid data URI." }
                    Base64.decode(uri.substring(comma + 1), Base64.DEFAULT)
                }
                else -> error("External glTF buffer '$uri' is not supported. Package the model as a self-contained GLB.")
            }
            val expectedLength = bufObj.optInt("byteLength", bytes.size)
            require(bytes.size >= expectedLength) { "glTF buffer $i is shorter than its declared byteLength." }
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
            require(comma >= 0) { "glTF image has an invalid data URI." }
            Base64.decode(uri.substring(comma + 1), Base64.DEFAULT)
        } else if (uri.isNotEmpty()) {
            error("External glTF image '$uri' is not supported. Embed textures in the GLB file.")
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
        val count = acc.getInt("count")
        require(count == expectedCount) { "inverseBindMatrices count must match the skin joint count." }
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
        activePath: BooleanArray
    ) {
        require(nodeIdx in 0 until nodes.length()) { "glTF scene references an out-of-range node index." }
        require(!activePath[nodeIdx]) { "glTF node hierarchy contains a cycle at node $nodeIdx." }
        activePath[nodeIdx] = true
        try {
            val node = nodes.getJSONObject(nodeIdx)
            val local = nodeLocalMatrix(node)
            require(local.size == 16 && local.all { it.isFinite() }) { "glTF node $nodeIdx has an invalid transform." }
            val world = FloatArray(16)
            Matrix.multiplyMM(world, 0, parentMat, 0, local, 0)

            if (node.has("mesh")) {
                val meshIndex = node.getInt("mesh")
                require(meshIndex >= 0) { "glTF node $nodeIdx has a negative mesh index." }
                outMeshes.add(MeshNodeInstance(meshIndex, world, nodeIdx, node.optInt("skin", -1)))
            }
            val children = node.optJSONArray("children")
            if (children != null) {
                for (i in 0 until children.length()) {
                    traverseNode(children.getInt(i), world, nodes, outMeshes, activePath)
                }
            }
        } finally {
            activePath[nodeIdx] = false
        }
    }
}
