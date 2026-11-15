package com.example.render

import android.graphics.Bitmap
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.opengl.Matrix
import com.example.avatar.AvatarModel3D
import com.example.avatar.MeshPart
import com.example.avatar.PartRole
import com.example.avatar.ProceduralAvatarFactory
import com.example.model.FacialRigState
import com.example.model.LightingPreset
import com.example.model.StageBackdrop
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.IntBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.cos
import kotlin.math.sin

class AvatarGlRenderer(
    initialModel: AvatarModel3D = ProceduralAvatarFactory.buildAvatar("nova")
) : GLSurfaceView.Renderer {

    private class GpuPart(
        val part: MeshPart,
        val vao: Int,
        val vboPos: Int,
        val vboNorm: Int,
        val vboUv: Int,
        val ibo: Int,
        val textureId: Int,
        val indexCount: Int,
        val dynamicPosScratch: FloatArray?,
        val dynamicPosBuffer: FloatBuffer?,
        val dynamicNormScratch: FloatArray?,
        val dynamicNormBuffer: FloatBuffer?
    )

    private var avatarProgram: ShaderProgram? = null
    private var backdropProgram: ShaderProgram? = null
    private var backdropVao = 0
    private var backdropVbo = 0

    private val gpuParts = mutableListOf<GpuPart>()
    private var pendingModel: AvatarModel3D? = initialModel
    private var activeModel: AvatarModel3D = initialModel

    @Volatile
    var rigState: FacialRigState = FacialRigState()

    @Volatile
    var lightingPreset: LightingPreset = LightingPreset.CINEMA_KEY

    @Volatile
    var stageBackdrop: StageBackdrop = StageBackdrop.OBSIDIAN_STUDIO

    @Volatile
    var cameraYawDeg: Float = 0f

    @Volatile
    var cameraPitchDeg: Float = 4f

    @Volatile
    var cameraZoom: Float = 1.0f

    private var viewportWidth = 720
    private var viewportHeight = 1280

    private val projMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)
    private val normalMatrix = FloatArray(9)

    private var frameCounter = 0
    private var fpsClockNs = System.nanoTime()
    var onFpsUpdate: ((Int) -> Unit)? = null

    fun setModel(model: AvatarModel3D) {
        pendingModel = model
    }

    fun resetCamera() {
        cameraYawDeg = 0f
        cameraPitchDeg = 4f
        cameraZoom = 1.0f
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)

        avatarProgram = ShaderProgram(
            ShaderProgram.AVATAR_VERTEX_SHADER,
            ShaderProgram.AVATAR_FRAGMENT_SHADER
        )
        backdropProgram = ShaderProgram(
            ShaderProgram.BACKDROP_VERTEX_SHADER,
            ShaderProgram.BACKDROP_FRAGMENT_SHADER
        )
        initBackdropQuad()
        pendingModel?.let {
            uploadModelToGpu(it)
            pendingModel = null
        } ?: uploadModelToGpu(activeModel)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        viewportWidth = width.coerceAtLeast(1)
        viewportHeight = height.coerceAtLeast(1)
        GLES30.glViewport(0, 0, viewportWidth, viewportHeight)
        val aspect = viewportWidth.toFloat() / viewportHeight.toFloat()
        Matrix.perspectiveM(projMatrix, 0, 34f, aspect, 0.1f, 25f)
    }

    override fun onDrawFrame(gl: GL10?) {
        pendingModel?.let {
            uploadModelToGpu(it)
            pendingModel = null
        }

        renderFrame(rigState)

        frameCounter++
        val now = System.nanoTime()
        if (now - fpsClockNs >= 500_000_000L) {
            val fps = ((frameCounter * 1_000_000_000.0) / (now - fpsClockNs)).toInt()
            onFpsUpdate?.invoke(fps)
            frameCounter = 0
            fpsClockNs = now
        }
    }

    fun renderFrame(rig: FacialRigState) {
        GLES30.glClearColor(0.03f, 0.04f, 0.06f, 1f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)

        // 1. Draw Studio Backdrop
        drawBackdrop(rig.vocalEnergy)

        // 2. Setup Camera & Head Motion Matrix
        val dist = (2.15f / cameraZoom.coerceIn(0.6f, 1.8f))
        val yawRad = Math.toRadians(cameraYawDeg.toDouble()).toFloat()
        val pitchRad = Math.toRadians(cameraPitchDeg.coerceIn(-35f, 35f).toDouble()).toFloat()
        val camX = dist * sin(yawRad) * cos(pitchRad)
        val camY = dist * sin(pitchRad) + 0.04f
        val camZ = dist * cos(yawRad) * cos(pitchRad)

        Matrix.setLookAtM(viewMatrix, 0, camX, camY, camZ, 0f, -0.04f, 0f, 0f, 1f, 0f)

        val prog = avatarProgram ?: return
        prog.use()

        GLES30.glUniformMatrix4fv(prog.uniformLocation("uView"), 1, false, viewMatrix, 0)
        GLES30.glUniformMatrix4fv(prog.uniformLocation("uProjection"), 1, false, projMatrix, 0)
        GLES30.glUniform3f(prog.uniformLocation("uCameraPos"), camX, camY, camZ)

        applyLightingUniforms(prog)

        GLES30.glUniform1f(prog.uniformLocation("uJawOpen"), rig.jawOpen)
        GLES30.glUniform1f(prog.uniformLocation("uMouthWide"), rig.mouthWide)
        GLES30.glUniform1f(prog.uniformLocation("uMouthPucker"), rig.mouthPucker)
        GLES30.glUniform1f(prog.uniformLocation("uLipPress"), rig.lipPress)
        GLES30.glUniform1f(prog.uniformLocation("uUpperLipRaise"), rig.upperLipRaise)
        GLES30.glUniform1f(prog.uniformLocation("uEyeBlink"), rig.eyeBlink)
        GLES30.glUniform1f(prog.uniformLocation("uLeftEyeBlink"), rig.leftEyeBlink ?: rig.eyeBlink)
        GLES30.glUniform1f(prog.uniformLocation("uRightEyeBlink"), rig.rightEyeBlink ?: rig.eyeBlink)
        GLES30.glUniform1f(prog.uniformLocation("uBrowRaise"), rig.browRaise)
        GLES30.glUniform1f(prog.uniformLocation("uLeftBrowRaise"), rig.leftBrowRaise ?: rig.browRaise)
        GLES30.glUniform1f(prog.uniformLocation("uRightBrowRaise"), rig.rightBrowRaise ?: rig.browRaise)
        GLES30.glUniform1f(prog.uniformLocation("uEyeSquint"), rig.eyeSquint)
        GLES30.glUniform1f(prog.uniformLocation("uEyeWide"), rig.eyeWide)
        GLES30.glUniform1f(prog.uniformLocation("uVocalEnergy"), rig.vocalEnergy)

        val hasAnyMorphs = gpuParts.any { it.part.morphTargets.isNotEmpty() }
        GLES30.glUniform1i(
            prog.uniformLocation("uAutoRigMode"),
            if (activeModel.isCustomImported && !hasAnyMorphs) 1 else 0
        )

        // Build head motion matrix vs stationary bust matrix
        val headModelMat = FloatArray(16)
        Matrix.setIdentityM(headModelMat, 0)
        Matrix.rotateM(headModelMat, 0, Math.toDegrees(rig.headYaw.toDouble()).toFloat(), 0f, 1f, 0f)
        Matrix.rotateM(headModelMat, 0, Math.toDegrees(rig.headPitch.toDouble()).toFloat(), 1f, 0f, 0f)
        Matrix.rotateM(headModelMat, 0, Math.toDegrees(rig.headRoll.toDouble()).toFloat(), 0f, 0f, 1f)

        val bodyModelMat = FloatArray(16)
        Matrix.setIdentityM(bodyModelMat, 0)
        Matrix.rotateM(bodyModelMat, 0, Math.toDegrees((rig.headYaw * 0.25f).toDouble()).toFloat(), 0f, 1f, 0f)

        for (gp in gpuParts) {
            val mat = if (gp.part.partRole == PartRole.BODY_NECK) bodyModelMat else headModelMat
            System.arraycopy(mat, 0, modelMatrix, 0, 16)
            computeNormalMatrix(modelMatrix, normalMatrix)

            GLES30.glUniformMatrix4fv(prog.uniformLocation("uModel"), 1, false, modelMatrix, 0)
            GLES30.glUniformMatrix3fv(prog.uniformLocation("uNormalMatrix"), 1, false, normalMatrix, 0)
            GLES30.glUniform1i(prog.uniformLocation("uPartRole"), gp.part.partRole.ordinal)

            val bc = gp.part.baseColor
            GLES30.glUniform4f(prog.uniformLocation("uBaseColor"), bc[0], bc[1], bc[2], bc[3])
            GLES30.glUniform1f(prog.uniformLocation("uMetallic"), gp.part.metallic)
            GLES30.glUniform1f(prog.uniformLocation("uRoughness"), gp.part.roughness)
            val em = gp.part.emissive
            GLES30.glUniform3f(prog.uniformLocation("uEmissive"), em[0], em[1], em[2])
            GLES30.glUniform1i(prog.uniformLocation("uIsSkin"), if (gp.part.isSkin) 1 else 0)

            if (gp.textureId != 0) {
                GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, gp.textureId)
                GLES30.glUniform1i(prog.uniformLocation("uBaseColorTex"), 0)
                GLES30.glUniform1i(prog.uniformLocation("uHasTexture"), 1)
            } else {
                GLES30.glUniform1i(prog.uniformLocation("uHasTexture"), 0)
            }

            // If imported glTF/GLB part has morph targets, evaluate blended vertex deltas on CPU -> VBO
            if (gp.part.morphTargets.isNotEmpty() && gp.dynamicPosScratch != null && gp.dynamicPosBuffer != null) {
                val base = gp.part.positions
                val dst = gp.dynamicPosScratch
                System.arraycopy(base, 0, dst, 0, base.size)
                for (mt in gp.part.morphTargets) {
                    val w = rig.weightForMorphName(mt.name)
                    if (w > 0.002f) {
                        val d = mt.positionDeltas
                        for (k in dst.indices) {
                            dst[k] += d[k] * w
                        }
                    }
                }
                gp.dynamicPosBuffer.clear()
                gp.dynamicPosBuffer.put(dst)
                gp.dynamicPosBuffer.position(0)
                GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, gp.vboPos)
                GLES30.glBufferSubData(GLES30.GL_ARRAY_BUFFER, 0, dst.size * 4, gp.dynamicPosBuffer)
                GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)

                // Morph normals together with positions so skin shading stays coherent
                // around animated lips, cheeks, eyelids, and jaw geometry.
                if (gp.dynamicNormScratch != null && gp.dynamicNormBuffer != null) {
                    val baseNormals = gp.part.normals
                    val normalDst = gp.dynamicNormScratch
                    System.arraycopy(baseNormals, 0, normalDst, 0, baseNormals.size)
                    for (mt in gp.part.morphTargets) {
                        val w = rig.weightForMorphName(mt.name)
                        val deltas = mt.normalDeltas
                        if (w > 0.002f && deltas != null && deltas.size == normalDst.size) {
                            for (k in normalDst.indices) normalDst[k] += deltas[k] * w
                        }
                    }
                    var n = 0
                    while (n + 2 < normalDst.size) {
                        val len = kotlin.math.sqrt(
                            normalDst[n] * normalDst[n] +
                                normalDst[n + 1] * normalDst[n + 1] +
                                normalDst[n + 2] * normalDst[n + 2]
                        ).coerceAtLeast(1e-6f)
                        normalDst[n] /= len
                        normalDst[n + 1] /= len
                        normalDst[n + 2] /= len
                        n += 3
                    }
                    gp.dynamicNormBuffer.clear()
                    gp.dynamicNormBuffer.put(normalDst)
                    gp.dynamicNormBuffer.position(0)
                    GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, gp.vboNorm)
                    GLES30.glBufferSubData(
                        GLES30.GL_ARRAY_BUFFER, 0, normalDst.size * 4, gp.dynamicNormBuffer
                    )
                    GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
                }
            }

            GLES30.glBindVertexArray(gp.vao)
            GLES30.glDrawElements(GLES30.GL_TRIANGLES, gp.indexCount, GLES30.GL_UNSIGNED_INT, 0)
            GLES30.glBindVertexArray(0)
        }
    }

    fun release() {
        clearGpuParts()
        avatarProgram?.release()
        avatarProgram = null
        backdropProgram?.release()
        backdropProgram = null
        if (backdropVao != 0) {
            GLES30.glDeleteVertexArrays(1, intArrayOf(backdropVao), 0)
            backdropVao = 0
        }
        if (backdropVbo != 0) {
            GLES30.glDeleteBuffers(1, intArrayOf(backdropVbo), 0)
            backdropVbo = 0
        }
    }

    private fun drawBackdrop(vocalEnergy: Float) {
        val bp = backdropProgram ?: return
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        bp.use()
        GLES30.glUniform1i(bp.uniformLocation("uBackdropMode"), stageBackdrop.ordinal)
        GLES30.glUniform1f(bp.uniformLocation("uVocalEnergy"), vocalEnergy)
        GLES30.glUniform2f(
            bp.uniformLocation("uResolution"),
            viewportWidth.toFloat(),
            viewportHeight.toFloat()
        )
        GLES30.glBindVertexArray(backdropVao)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        GLES30.glBindVertexArray(0)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
    }

    private fun applyLightingUniforms(prog: ShaderProgram) {
        when (lightingPreset) {
            LightingPreset.CINEMA_KEY -> {
                GLES30.glUniform3f(prog.uniformLocation("uKeyColor"), 2.3f, 2.15f, 1.95f)
                GLES30.glUniform3f(prog.uniformLocation("uFillColor"), 0.55f, 0.78f, 1.05f)
                GLES30.glUniform3f(prog.uniformLocation("uRimColor"), 0.35f, 1.35f, 1.65f)
                GLES30.glUniform3f(prog.uniformLocation("uAmbientSky"), 0.32f, 0.38f, 0.48f)
                GLES30.glUniform3f(prog.uniformLocation("uAmbientGround"), 0.12f, 0.11f, 0.14f)
            }
            LightingPreset.NEON_CYBER -> {
                GLES30.glUniform3f(prog.uniformLocation("uKeyColor"), 1.55f, 2.05f, 2.35f)
                GLES30.glUniform3f(prog.uniformLocation("uFillColor"), 1.35f, 0.52f, 0.25f)
                GLES30.glUniform3f(prog.uniformLocation("uRimColor"), 0.20f, 1.65f, 1.85f)
                GLES30.glUniform3f(prog.uniformLocation("uAmbientSky"), 0.24f, 0.36f, 0.50f)
                GLES30.glUniform3f(prog.uniformLocation("uAmbientGround"), 0.18f, 0.09f, 0.12f)
            }
            LightingPreset.DAYLIGHT_SOFTBOX -> {
                GLES30.glUniform3f(prog.uniformLocation("uKeyColor"), 2.20f, 2.20f, 2.18f)
                GLES30.glUniform3f(prog.uniformLocation("uFillColor"), 0.95f, 0.98f, 1.02f)
                GLES30.glUniform3f(prog.uniformLocation("uRimColor"), 1.05f, 1.15f, 1.25f)
                GLES30.glUniform3f(prog.uniformLocation("uAmbientSky"), 0.45f, 0.48f, 0.52f)
                GLES30.glUniform3f(prog.uniformLocation("uAmbientGround"), 0.22f, 0.22f, 0.24f)
            }
            LightingPreset.GOLDEN_HOUR -> {
                GLES30.glUniform3f(prog.uniformLocation("uKeyColor"), 2.45f, 1.65f, 1.05f)
                GLES30.glUniform3f(prog.uniformLocation("uFillColor"), 0.65f, 0.55f, 1.15f)
                GLES30.glUniform3f(prog.uniformLocation("uRimColor"), 1.65f, 0.88f, 0.40f)
                GLES30.glUniform3f(prog.uniformLocation("uAmbientSky"), 0.36f, 0.30f, 0.44f)
                GLES30.glUniform3f(prog.uniformLocation("uAmbientGround"), 0.18f, 0.12f, 0.10f)
            }
        }
    }

    private fun initBackdropQuad() {
        val quad = floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)
        val vaos = IntArray(1)
        val vbos = IntArray(1)
        GLES30.glGenVertexArrays(1, vaos, 0)
        GLES30.glGenBuffers(1, vbos, 0)
        backdropVao = vaos[0]
        backdropVbo = vbos[0]
        GLES30.glBindVertexArray(backdropVao)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, backdropVbo)
        val buf = toFloatBuffer(quad)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, quad.size * 4, buf, GLES30.GL_STATIC_DRAW)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 0, 0)
        GLES30.glBindVertexArray(0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
    }

    private fun uploadModelToGpu(model: AvatarModel3D) {
        clearGpuParts()
        activeModel = model

        for (part in model.parts) {
            val vaos = IntArray(1)
            val bufs = IntArray(4)
            GLES30.glGenVertexArrays(1, vaos, 0)
            GLES30.glGenBuffers(4, bufs, 0)
            val vao = vaos[0]
            val vboPos = bufs[0]
            val vboNorm = bufs[1]
            val vboUv = bufs[2]
            val ibo = bufs[3]

            val hasMorphs = part.morphTargets.isNotEmpty()
            val posBuf = toFloatBuffer(part.positions)
            val normBuf = toFloatBuffer(part.normals)
            val uvBuf = toFloatBuffer(part.uvs)
            val idxBuf = toIntBuffer(part.indices)

            GLES30.glBindVertexArray(vao)

            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vboPos)
            GLES30.glBufferData(
                GLES30.GL_ARRAY_BUFFER,
                part.positions.size * 4,
                posBuf,
                if (hasMorphs) GLES30.GL_DYNAMIC_DRAW else GLES30.GL_STATIC_DRAW
            )
            GLES30.glEnableVertexAttribArray(0)
            GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 0, 0)

            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vboNorm)
            GLES30.glBufferData(
                GLES30.GL_ARRAY_BUFFER,
                part.normals.size * 4,
                normBuf,
                if (hasMorphs) GLES30.GL_DYNAMIC_DRAW else GLES30.GL_STATIC_DRAW
            )
            GLES30.glEnableVertexAttribArray(1)
            GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, 0, 0)

            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vboUv)
            GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, part.uvs.size * 4, uvBuf, GLES30.GL_STATIC_DRAW)
            GLES30.glEnableVertexAttribArray(2)
            GLES30.glVertexAttribPointer(2, 2, GLES30.GL_FLOAT, false, 0, 0)

            GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, ibo)
            GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, part.indices.size * 4, idxBuf, GLES30.GL_STATIC_DRAW)

            GLES30.glBindVertexArray(0)
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)

            val texId = part.textureBitmap?.let { uploadTexture(it) } ?: 0

            gpuParts += GpuPart(
                part = part,
                vao = vao,
                vboPos = vboPos,
                vboNorm = vboNorm,
                vboUv = vboUv,
                ibo = ibo,
                textureId = texId,
                indexCount = part.indices.size,
                dynamicPosScratch = if (hasMorphs) FloatArray(part.positions.size) else null,
                dynamicPosBuffer = if (hasMorphs) {
                    ByteBuffer.allocateDirect(part.positions.size * 4)
                        .order(ByteOrder.nativeOrder()).asFloatBuffer()
                } else null,
                dynamicNormScratch = if (hasMorphs) FloatArray(part.normals.size) else null,
                dynamicNormBuffer = if (hasMorphs) {
                    ByteBuffer.allocateDirect(part.normals.size * 4)
                        .order(ByteOrder.nativeOrder()).asFloatBuffer()
                } else null
            )
        }
    }

    private fun uploadTexture(bitmap: Bitmap): Int {
        val tex = IntArray(1)
        GLES30.glGenTextures(1, tex, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tex[0])
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR_MIPMAP_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_REPEAT)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_REPEAT)
        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bitmap, 0)
        GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
        return tex[0]
    }

    private fun clearGpuParts() {
        for (gp in gpuParts) {
            GLES30.glDeleteVertexArrays(1, intArrayOf(gp.vao), 0)
            GLES30.glDeleteBuffers(4, intArrayOf(gp.vboPos, gp.vboNorm, gp.vboUv, gp.ibo), 0)
            if (gp.textureId != 0) {
                GLES30.glDeleteTextures(1, intArrayOf(gp.textureId), 0)
            }
        }
        gpuParts.clear()
    }

    private fun computeNormalMatrix(m: FloatArray, out: FloatArray) {
        out[0] = m[0]; out[1] = m[1]; out[2] = m[2]
        out[3] = m[4]; out[4] = m[5]; out[5] = m[6]
        out[6] = m[8]; out[7] = m[9]; out[8] = m[10]
    }

    private fun toFloatBuffer(arr: FloatArray): FloatBuffer =
        ByteBuffer.allocateDirect(arr.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
            put(arr)
            position(0)
        }

    private fun toIntBuffer(arr: IntArray): IntBuffer =
        ByteBuffer.allocateDirect(arr.size * 4).order(ByteOrder.nativeOrder()).asIntBuffer().apply {
            put(arr)
            position(0)
        }
}
