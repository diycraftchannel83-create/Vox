package com.example.avatar

import com.example.model.AvatarFormat
import com.example.model.AvatarSpec
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

object ProceduralAvatarFactory {

    val builtInSpecs: List<AvatarSpec> = listOf(
        AvatarSpec(
            id = "nova",
            name = "Nova",
            roleTag = "Studio Host · 15-Viseme Rig",
            format = AvatarFormat.BUILTIN,
            vertexCount = 4680,
            triangleCount = 8640,
            morphTargetCount = 15,
            accentHex = 0xFF22D3EE,
            isBuiltIn = true
        ),
        AvatarSpec(
            id = "atlas",
            name = "Atlas",
            roleTag = "Cyber Android · Titanium",
            format = AvatarFormat.BUILTIN,
            vertexCount = 4820,
            triangleCount = 8920,
            morphTargetCount = 15,
            accentHex = 0xFF38BDF8,
            isBuiltIn = true
        ),
        AvatarSpec(
            id = "milo",
            name = "Milo",
            roleTag = "Stylized Creator · Warm Clay",
            format = AvatarFormat.BUILTIN,
            vertexCount = 4620,
            triangleCount = 8510,
            morphTargetCount = 15,
            accentHex = 0xFFF97316,
            isBuiltIn = true
        ),
        AvatarSpec(
            id = "aura",
            name = "Aura",
            roleTag = "Holo Synth · Reactive Halo",
            format = AvatarFormat.BUILTIN,
            vertexCount = 5040,
            triangleCount = 9360,
            morphTargetCount = 15,
            accentHex = 0xFF818CF8,
            isBuiltIn = true
        )
    )

    fun buildAvatar(avatarId: String): AvatarModel3D {
        return when (avatarId) {
            "atlas" -> buildAtlas()
            "milo" -> buildMilo()
            "aura" -> buildAura()
            else -> buildNova()
        }
    }

    private fun buildNova(): AvatarModel3D {
        val skin = floatArrayOf(0.93f, 0.76f, 0.67f, 1f)
        val lipColor = floatArrayOf(0.76f, 0.36f, 0.38f, 1f)
        val hairColor = floatArrayOf(0.11f, 0.17f, 0.29f, 1f)
        val accent = floatArrayOf(0.13f, 0.83f, 0.93f, 1f)
        val suit = floatArrayOf(0.12f, 0.15f, 0.22f, 1f)
        return assembleHumanoidAvatar(
            id = "nova",
            name = "Nova",
            skinColor = skin,
            lipColor = lipColor,
            hairColor = hairColor,
            irisColor = floatArrayOf(0.16f, 0.68f, 0.88f, 1f),
            suitColor = suit,
            accentColor = accent,
            skinMetallic = 0.04f,
            skinRoughness = 0.44f,
            isSkin = true,
            includeVisorCuffs = true,
            includeHalo = false,
            hairStyle = 0
        )
    }

    private fun buildAtlas(): AvatarModel3D {
        val titanium = floatArrayOf(0.72f, 0.77f, 0.84f, 1f)
        val mandibleColor = floatArrayOf(0.24f, 0.28f, 0.35f, 1f)
        val opticCyan = floatArrayOf(0.10f, 0.88f, 0.98f, 1f)
        val plateColor = floatArrayOf(0.14f, 0.18f, 0.25f, 1f)
        return assembleHumanoidAvatar(
            id = "atlas",
            name = "Atlas",
            skinColor = titanium,
            lipColor = mandibleColor,
            hairColor = plateColor,
            irisColor = opticCyan,
            suitColor = floatArrayOf(0.10f, 0.12f, 0.17f, 1f),
            accentColor = opticCyan,
            skinMetallic = 0.82f,
            skinRoughness = 0.24f,
            isSkin = false,
            includeVisorCuffs = true,
            includeHalo = false,
            hairStyle = 1,
            emissiveEyes = floatArrayOf(0.10f, 0.72f, 0.92f)
        )
    }

    private fun buildMilo(): AvatarModel3D {
        val warmSkin = floatArrayOf(0.86f, 0.64f, 0.50f, 1f)
        val lip = floatArrayOf(0.66f, 0.32f, 0.29f, 1f)
        val hair = floatArrayOf(0.22f, 0.14f, 0.10f, 1f)
        val coral = floatArrayOf(0.98f, 0.45f, 0.09f, 1f)
        return assembleHumanoidAvatar(
            id = "milo",
            name = "Milo",
            skinColor = warmSkin,
            lipColor = lip,
            hairColor = hair,
            irisColor = floatArrayOf(0.85f, 0.52f, 0.16f, 1f),
            suitColor = floatArrayOf(0.20f, 0.16f, 0.15f, 1f),
            accentColor = coral,
            skinMetallic = 0.03f,
            skinRoughness = 0.50f,
            isSkin = true,
            includeVisorCuffs = false,
            includeHalo = false,
            hairStyle = 2
        )
    }

    private fun buildAura(): AvatarModel3D {
        val pearl = floatArrayOf(0.80f, 0.88f, 0.97f, 1f)
        val synthLip = floatArrayOf(0.45f, 0.56f, 0.92f, 1f)
        val crest = floatArrayOf(0.22f, 0.26f, 0.55f, 1f)
        val violet = floatArrayOf(0.51f, 0.55f, 0.97f, 1f)
        return assembleHumanoidAvatar(
            id = "aura",
            name = "Aura",
            skinColor = pearl,
            lipColor = synthLip,
            hairColor = crest,
            irisColor = floatArrayOf(0.45f, 0.85f, 1.0f, 1f),
            suitColor = floatArrayOf(0.11f, 0.13f, 0.26f, 1f),
            accentColor = violet,
            skinMetallic = 0.35f,
            skinRoughness = 0.22f,
            isSkin = false,
            includeVisorCuffs = false,
            includeHalo = true,
            hairStyle = 1,
            emissiveEyes = floatArrayOf(0.32f, 0.55f, 0.98f)
        )
    }

    private fun assembleHumanoidAvatar(
        id: String,
        name: String,
        skinColor: FloatArray,
        lipColor: FloatArray,
        hairColor: FloatArray,
        irisColor: FloatArray,
        suitColor: FloatArray,
        accentColor: FloatArray,
        skinMetallic: Float,
        skinRoughness: Float,
        isSkin: Boolean,
        includeVisorCuffs: Boolean,
        includeHalo: Boolean,
        hairStyle: Int,
        emissiveEyes: FloatArray = floatArrayOf(0f, 0f, 0f)
    ): AvatarModel3D {
        val parts = mutableListOf<MeshPart>()

        // 1. Sculpted Upper/Mid Cranium & Face
        parts += createSculptedCranium(skinColor, skinMetallic, skinRoughness, isSkin)

        // 2. Articulated Mandible / Chin Shell
        parts += createSculptedJaw(skinColor, skinMetallic, skinRoughness, isSkin)

        // 3. Inner Mouth Cavity & Stylized Teeth
        parts += createSpherePart(
            name = "mouth_cavity",
            cx = 0f, cy = -0.175f, cz = 0.20f,
            rx = 0.115f, ry = 0.055f, rz = 0.075f,
            stacks = 12, slices = 16,
            baseColor = floatArrayOf(0.12f, 0.04f, 0.05f, 1f),
            metallic = 0f, roughness = 0.9f,
            role = PartRole.MOUTH_CAVITY
        )
        parts += createCurvedBar(
            name = "upper_teeth",
            centerY = -0.152f, centerZ = 0.268f,
            width = 0.145f, height = 0.022f, depth = 0.028f,
            curveBack = 0.024f,
            baseColor = floatArrayOf(0.96f, 0.96f, 0.94f, 1f),
            metallic = 0.05f, roughness = 0.22f,
            role = PartRole.HEAD
        )

        // 4. Sculpted Articulated Upper & Lower Lips
        parts += createLipMesh(
            name = "upper_lip",
            isUpper = true,
            baseColor = lipColor,
            role = PartRole.UPPER_LIP
        )
        parts += createLipMesh(
            name = "lower_lip",
            isUpper = false,
            baseColor = lipColor,
            role = PartRole.LOWER_LIP
        )

        // 5. Eyes (Sclera + Iris + Pupil) & Blinking Eyelids
        val eyeX = 0.128f
        val eyeY = 0.105f
        val eyeZ = 0.285f
        listOf(-1f to true, 1f to false).forEach { (side, isLeft) ->
            val roleEye = if (isLeft) PartRole.LEFT_EYE else PartRole.RIGHT_EYE
            val roleLid = if (isLeft) PartRole.LEFT_EYELID else PartRole.RIGHT_EYELID
            val roleBrow = if (isLeft) PartRole.LEFT_BROW else PartRole.RIGHT_BROW

            // Sclera
            parts += createSpherePart(
                name = if (isLeft) "left_sclera" else "right_sclera",
                cx = side * eyeX, cy = eyeY, cz = eyeZ,
                rx = 0.062f, ry = 0.056f, rz = 0.056f,
                stacks = 14, slices = 18,
                baseColor = floatArrayOf(0.97f, 0.97f, 0.99f, 1f),
                metallic = 0.02f, roughness = 0.14f,
                role = roleEye
            )
            // Iris
            parts += createSpherePart(
                name = if (isLeft) "left_iris" else "right_iris",
                cx = side * eyeX, cy = eyeY, cz = eyeZ + 0.036f,
                rx = 0.033f, ry = 0.033f, rz = 0.026f,
                stacks = 12, slices = 16,
                baseColor = irisColor,
                metallic = 0.15f, roughness = 0.15f,
                emissive = emissiveEyes,
                role = roleEye
            )
            // Pupil
            parts += createSpherePart(
                name = if (isLeft) "left_pupil" else "right_pupil",
                cx = side * eyeX, cy = eyeY, cz = eyeZ + 0.050f,
                rx = 0.015f, ry = 0.015f, rz = 0.014f,
                stacks = 10, slices = 12,
                baseColor = floatArrayOf(0.03f, 0.04f, 0.06f, 1f),
                metallic = 0.1f, roughness = 0.08f,
                role = roleEye
            )
            // Upper Eyelid Hood
            parts += createEyelidCap(
                name = if (isLeft) "left_eyelid" else "right_eyelid",
                cx = side * eyeX, cy = eyeY, cz = eyeZ,
                radius = 0.068f,
                baseColor = skinColor,
                metallic = skinMetallic,
                roughness = skinRoughness,
                role = roleLid
            )
            // Eyebrow
            parts += createCurvedBar(
                name = if (isLeft) "left_brow" else "right_brow",
                centerX = side * (eyeX + 0.005f),
                centerY = eyeY + 0.095f,
                centerZ = eyeZ + 0.025f,
                width = 0.135f,
                height = 0.022f,
                depth = 0.026f,
                curveBack = 0.022f,
                tiltZ = -side * 0.10f,
                baseColor = hairColor,
                metallic = 0.05f,
                roughness = 0.6f,
                role = roleBrow
            )
        }

        // 6. Neck & Sculpted Shoulder Bust
        parts += createCylinderPart(
            name = "neck",
            cx = 0f, cy = -0.49f, cz = -0.04f,
            rTop = 0.135f, rBottom = 0.165f, height = 0.34f,
            slices = 24,
            baseColor = skinColor,
            metallic = skinMetallic, roughness = skinRoughness,
            isSkin = isSkin,
            role = PartRole.BODY_NECK
        )
        parts += createBustShoulders(suitColor, accentColor)

        // 7. Signature Hair / Cyber Helmet / Headgear
        parts += createHairCrown(hairColor, accentColor, hairStyle)

        if (includeVisorCuffs) {
            listOf(-1f, 1f).forEach { side ->
                parts += createSpherePart(
                    name = "ear_node_$side",
                    cx = side * 0.345f, cy = 0.04f, cz = -0.01f,
                    rx = 0.055f, ry = 0.095f, rz = 0.085f,
                    stacks = 12, slices = 16,
                    baseColor = floatArrayOf(0.15f, 0.18f, 0.24f, 1f),
                    metallic = 0.85f, roughness = 0.22f,
                    role = PartRole.HAIR_ACCESSORY
                )
                parts += createTorusPart(
                    name = "ear_glow_$side",
                    cx = side * 0.392f, cy = 0.04f, cz = -0.01f,
                    majorR = 0.048f, minorR = 0.010f,
                    axisX = true,
                    baseColor = accentColor,
                    metallic = 0.2f, roughness = 0.2f,
                    emissive = floatArrayOf(accentColor[0] * 0.85f, accentColor[1] * 0.85f, accentColor[2] * 0.85f),
                    role = PartRole.HAIR_ACCESSORY
                )
            }
        }

        if (includeHalo) {
            parts += createTorusPart(
                name = "aura_halo_ring",
                cx = 0f, cy = 0.54f, cz = -0.05f,
                majorR = 0.36f, minorR = 0.016f,
                axisX = false,
                baseColor = accentColor,
                metallic = 0.5f, roughness = 0.15f,
                emissive = floatArrayOf(0.20f, 0.72f, 0.96f),
                role = PartRole.HALO_RING
            )
        }

        return AvatarModel3D(
            id = id,
            name = name,
            parts = parts
        )
    }

    private fun createSculptedCranium(
        color: FloatArray,
        metallic: Float,
        roughness: Float,
        isSkin: Boolean
    ): MeshPart {
        val stacks = 28
        val slices = 36
        val positions = FloatArray((stacks + 1) * (slices + 1) * 3)
        val uvs = FloatArray((stacks + 1) * (slices + 1) * 2)
        var p = 0
        var t = 0

        for (i in 0..stacks) {
            val v = i.toFloat() / stacks
            val phi = v * PI.toFloat() // 0 (top) .. PI (bottom)
            val sinPhi = sin(phi)
            val cosPhi = cos(phi)

            for (j in 0..slices) {
                val u = j.toFloat() / slices
                val theta = u * (2f * PI.toFloat()) - PI.toFloat() / 2f
                val nx = sinPhi * cos(theta)
                val ny = cosPhi
                val nz = sinPhi * sin(theta)

                // Base cranial ellipsoid
                var x = nx * 0.34f
                var y = ny * 0.43f + 0.08f
                var z = nz * 0.35f

                // Narrow lower face / cheek taper
                if (ny < 0.05f) {
                    val lowerFactor = (-ny + 0.05f).coerceIn(0f, 1f)
                    x *= (1f - lowerFactor * 0.24f)
                    z *= (1f - lowerFactor * 0.16f)
                }

                // Sculpted front-facing features (z > 0)
                if (nz > 0f) {
                    // Subtle eye socket recesses
                    val eyeLx = (x + 0.128f)
                    val eyeRx = (x - 0.128f)
                    val eyeY = (y - 0.105f)
                    val socketL = exp(-(eyeLx * eyeLx * 85f + eyeY * eyeY * 110f))
                    val socketR = exp(-(eyeRx * eyeRx * 85f + eyeY * eyeY * 110f))
                    z -= 0.052f * (socketL + socketR)

                    // Brow ridge
                    val browY = y - 0.19f
                    val browRidge = exp(-(browY * browY * 180f)) * exp(-(x * x * 14f))
                    z += 0.032f * browRidge

                    // Sculpted nose bridge + tip
                    val noseBridge = exp(-(x * x * 320f)) * exp(-((y - 0.03f) * (y - 0.03f) * 38f))
                    val noseTip = exp(-(x * x * 240f)) * exp(-((y + 0.045f) * (y + 0.045f) * 260f))
                    z += 0.072f * noseBridge + 0.055f * noseTip

                    // Cheekbone fullness
                    val cheekL = exp(-((x + 0.17f).pow(2) * 55f + (y - 0.01f).pow(2) * 85f))
                    val cheekR = exp(-((x - 0.17f).pow(2) * 55f + (y - 0.01f).pow(2) * 85f))
                    z += 0.030f * (cheekL + cheekR)

                    // Recess the lower front mouth pocket so articulated jaw & lips sit cleanly
                    val mouthPocket = exp(-(x * x * 45f + (y + 0.185f).pow(2) * 85f))
                    z -= 0.068f * mouthPocket
                }

                // Flatten bottom chin cutout slightly so articulated jaw blends seamlessly
                if (y < -0.22f && nz > -0.1f) {
                    y = -0.22f + (y + 0.22f) * 0.45f
                }

                positions[p++] = x
                positions[p++] = y
                positions[p++] = z
                uvs[t++] = u
                uvs[t++] = v
            }
        }

        val indices = buildGridIndices(stacks, slices)
        val normals = computeSmoothNormals(positions, indices)
        return MeshPart(
            name = "head_cranium",
            positions = positions,
            normals = normals,
            uvs = uvs,
            indices = indices,
            baseColor = color,
            metallic = metallic,
            roughness = roughness,
            partRole = PartRole.HEAD,
            isSkin = isSkin
        )
    }

    private fun createSculptedJaw(
        color: FloatArray,
        metallic: Float,
        roughness: Float,
        isSkin: Boolean
    ): MeshPart {
        val stacks = 16
        val slices = 24
        val positions = FloatArray((stacks + 1) * (slices + 1) * 3)
        val uvs = FloatArray((stacks + 1) * (slices + 1) * 2)
        var p = 0
        var t = 0

        for (i in 0..stacks) {
            val v = i.toFloat() / stacks
            val phi = v * PI.toFloat()
            val sinPhi = sin(phi)
            val cosPhi = cos(phi)
            for (j in 0..slices) {
                val u = j.toFloat() / slices
                val theta = u * 2f * PI.toFloat()
                val nx = sinPhi * cos(theta)
                val ny = cosPhi
                val nz = sinPhi * sin(theta)

                // Sculpted chin & jawline cup
                var x = nx * 0.225f
                var y = ny * 0.155f - 0.255f
                var z = nz * 0.225f + 0.055f

                // Chin prominence at front-bottom
                if (nz > 0f && ny < 0.2f) {
                    val chin = exp(-(x * x * 45f)) * exp(-((ny + 0.45f).pow(2) * 3.5f))
                    z += 0.048f * chin
                    y -= 0.025f * chin
                }
                // Recess top-front edge slightly under lower lip
                if (ny > 0.35f && nz > 0f) {
                    z -= 0.032f * ny * nz
                }

                positions[p++] = x
                positions[p++] = y
                positions[p++] = z
                uvs[t++] = u
                uvs[t++] = v
            }
        }
        val indices = buildGridIndices(stacks, slices)
        val normals = computeSmoothNormals(positions, indices)
        return MeshPart(
            name = "articulated_jaw",
            positions = positions,
            normals = normals,
            uvs = uvs,
            indices = indices,
            baseColor = color,
            metallic = metallic,
            roughness = roughness,
            partRole = PartRole.JAW,
            isSkin = isSkin
        )
    }

    private fun createLipMesh(
        name: String,
        isUpper: Boolean,
        baseColor: FloatArray,
        role: PartRole
    ): MeshPart {
        val segsU = 22
        val segsV = 10
        val positions = FloatArray((segsU + 1) * (segsV + 1) * 3)
        val uvs = FloatArray((segsU + 1) * (segsV + 1) * 2)
        var p = 0
        var t = 0

        val baseY = if (isUpper) -0.156f else -0.186f
        val baseZ = 0.285f
        val lipHalfWidth = 0.098f
        val tubeRadiusY = if (isUpper) 0.017f else 0.019f
        val tubeRadiusZ = 0.020f

        for (i in 0..segsU) {
            val u = i.toFloat() / segsU
            val xNorm = (u * 2f - 1f) // -1..1 across mouth
            val x = xNorm * lipHalfWidth
            val taper = sqrt((1f - xNorm * xNorm * 0.88f).coerceAtLeast(0.08f))
            // Cupid's bow on upper lip, gentle curve on lower lip
            val bowOffset = if (isUpper) {
                0.008f * cos(xNorm * 3.1f) * taper
            } else {
                -0.006f * (1f - xNorm * xNorm)
            }
            val zCurve = -0.038f * (xNorm * xNorm)

            for (j in 0..segsV) {
                val v = j.toFloat() / segsV
                val ang = v * 2f * PI.toFloat()
                val vy = sin(ang) * tubeRadiusY * taper
                val vz = cos(ang) * tubeRadiusZ * taper
                positions[p++] = x
                positions[p++] = baseY + bowOffset + vy
                positions[p++] = baseZ + zCurve + vz
                uvs[t++] = u
                uvs[t++] = v
            }
        }
        val indices = buildGridIndices(segsU, segsV)
        val normals = computeSmoothNormals(positions, indices)
        return MeshPart(
            name = name,
            positions = positions,
            normals = normals,
            uvs = uvs,
            indices = indices,
            baseColor = baseColor,
            metallic = 0.05f,
            roughness = 0.34f,
            partRole = role
        )
    }

    private fun createEyelidCap(
        name: String,
        cx: Float,
        cy: Float,
        cz: Float,
        radius: Float,
        baseColor: FloatArray,
        metallic: Float,
        roughness: Float,
        role: PartRole
    ): MeshPart {
        val stacks = 8
        val slices = 16
        val positions = FloatArray((stacks + 1) * (slices + 1) * 3)
        val uvs = FloatArray((stacks + 1) * (slices + 1) * 2)
        var p = 0
        var t = 0
        // Upper hemisphere hood tilted forward
        for (i in 0..stacks) {
            val v = i.toFloat() / stacks
            val phi = v * (PI.toFloat() * 0.48f)
            for (j in 0..slices) {
                val u = j.toFloat() / slices
                val theta = u * 2f * PI.toFloat()
                val x = sin(phi) * cos(theta) * radius
                val y = cos(phi) * radius
                val z = sin(phi) * sin(theta) * radius
                positions[p++] = cx + x
                positions[p++] = cy + y
                positions[p++] = cz + z
                uvs[t++] = u
                uvs[t++] = v
            }
        }
        val indices = buildGridIndices(stacks, slices)
        val normals = computeSmoothNormals(positions, indices)
        return MeshPart(
            name = name,
            positions = positions,
            normals = normals,
            uvs = uvs,
            indices = indices,
            baseColor = baseColor,
            metallic = metallic,
            roughness = roughness,
            partRole = role
        )
    }

    private fun createHairCrown(
        hairColor: FloatArray,
        accentColor: FloatArray,
        style: Int
    ): MeshPart {
        val stacks = 20
        val slices = 30
        val positions = FloatArray((stacks + 1) * (slices + 1) * 3)
        val uvs = FloatArray((stacks + 1) * (slices + 1) * 2)
        var p = 0
        var t = 0

        for (i in 0..stacks) {
            val v = i.toFloat() / stacks
            // Cover top & back of cranium cleanly without blocking face
            val phi = v * (PI.toFloat() * 0.72f)
            val sinPhi = sin(phi)
            val cosPhi = cos(phi)
            for (j in 0..slices) {
                val u = j.toFloat() / slices
                val theta = u * 2f * PI.toFloat() - PI.toFloat() / 2f
                val nx = sinPhi * cos(theta)
                val ny = cosPhi
                val nz = sinPhi * sin(theta)

                // Sweep front hairline upward so forehead/eyes remain open
                var pushOut = 1.09f
                var yOffset = 0.11f
                if (nz > 0.15f && ny < 0.55f) {
                    // Pull front hairline back/up out of the face
                    pushOut = 0.94f
                    yOffset += 0.12f * nz
                }
                if (style == 2 && ny > 0.35f) {
                    // Stylized sculpted wave crest for Milo
                    pushOut += 0.10f * sin(nx * 5f + nz * 3f).coerceAtLeast(0f)
                } else if (style == 1) {
                    // Crisp cyber crest ridge for Atlas/Aura
                    pushOut += 0.04f * exp(-(nx * nx * 28f))
                }

                positions[p++] = nx * 0.365f * pushOut
                positions[p++] = ny * 0.44f * pushOut + yOffset
                positions[p++] = nz * 0.375f * pushOut - 0.025f
                uvs[t++] = u
                uvs[t++] = v
            }
        }
        val indices = buildGridIndices(stacks, slices)
        val normals = computeSmoothNormals(positions, indices)
        val tint = if (style == 1) hairColor else hairColor
        return MeshPart(
            name = "hair_crown",
            positions = positions,
            normals = normals,
            uvs = uvs,
            indices = indices,
            baseColor = tint,
            metallic = if (style == 1) 0.55f else 0.08f,
            roughness = if (style == 1) 0.28f else 0.55f,
            partRole = PartRole.HAIR_ACCESSORY
        )
    }

    private fun createBustShoulders(suitColor: FloatArray, accentColor: FloatArray): MeshPart {
        val stacks = 14
        val slices = 28
        val positions = FloatArray((stacks + 1) * (slices + 1) * 3)
        val uvs = FloatArray((stacks + 1) * (slices + 1) * 2)
        var p = 0
        var t = 0
        for (i in 0..stacks) {
            val v = i.toFloat() / stacks
            val phi = v * (PI.toFloat() * 0.58f)
            val sinPhi = sin(phi)
            val cosPhi = cos(phi)
            for (j in 0..slices) {
                val u = j.toFloat() / slices
                val theta = u * 2f * PI.toFloat()
                val nx = sinPhi * cos(theta)
                val ny = cosPhi
                val nz = sinPhi * sin(theta)

                positions[p++] = nx * 0.62f
                positions[p++] = ny * 0.28f - 0.84f
                positions[p++] = nz * 0.31f - 0.04f
                uvs[t++] = u
                uvs[t++] = v
            }
        }
        val indices = buildGridIndices(stacks, slices)
        val normals = computeSmoothNormals(positions, indices)
        return MeshPart(
            name = "shoulder_bust",
            positions = positions,
            normals = normals,
            uvs = uvs,
            indices = indices,
            baseColor = suitColor,
            metallic = 0.24f,
            roughness = 0.45f,
            emissive = floatArrayOf(accentColor[0] * 0.04f, accentColor[1] * 0.04f, accentColor[2] * 0.04f),
            partRole = PartRole.BODY_NECK
        )
    }

    private fun createSpherePart(
        name: String,
        cx: Float, cy: Float, cz: Float,
        rx: Float, ry: Float, rz: Float,
        stacks: Int, slices: Int,
        baseColor: FloatArray,
        metallic: Float,
        roughness: Float,
        emissive: FloatArray = floatArrayOf(0f, 0f, 0f),
        role: PartRole
    ): MeshPart {
        val positions = FloatArray((stacks + 1) * (slices + 1) * 3)
        val uvs = FloatArray((stacks + 1) * (slices + 1) * 2)
        var p = 0
        var t = 0
        for (i in 0..stacks) {
            val v = i.toFloat() / stacks
            val phi = v * PI.toFloat()
            val sinPhi = sin(phi)
            val cosPhi = cos(phi)
            for (j in 0..slices) {
                val u = j.toFloat() / slices
                val theta = u * 2f * PI.toFloat()
                positions[p++] = cx + sinPhi * cos(theta) * rx
                positions[p++] = cy + cosPhi * ry
                positions[p++] = cz + sinPhi * sin(theta) * rz
                uvs[t++] = u
                uvs[t++] = v
            }
        }
        val indices = buildGridIndices(stacks, slices)
        val normals = computeSmoothNormals(positions, indices)
        return MeshPart(
            name = name,
            positions = positions,
            normals = normals,
            uvs = uvs,
            indices = indices,
            baseColor = baseColor,
            metallic = metallic,
            roughness = roughness,
            emissive = emissive,
            partRole = role
        )
    }

    private fun createCurvedBar(
        name: String,
        centerX: Float = 0f,
        centerY: Float,
        centerZ: Float,
        width: Float,
        height: Float,
        depth: Float,
        curveBack: Float,
        tiltZ: Float = 0f,
        baseColor: FloatArray,
        metallic: Float,
        roughness: Float,
        role: PartRole
    ): MeshPart {
        val segsU = 14
        val segsV = 8
        val positions = FloatArray((segsU + 1) * (segsV + 1) * 3)
        val uvs = FloatArray((segsU + 1) * (segsV + 1) * 2)
        var p = 0
        var t = 0
        val cosT = cos(tiltZ)
        val sinT = sin(tiltZ)
        for (i in 0..segsU) {
            val u = i.toFloat() / segsU
            val xNorm = u * 2f - 1f
            val lx = xNorm * (width * 0.5f)
            val taper = sqrt((1f - xNorm * xNorm * 0.7f).coerceAtLeast(0.15f))
            val zOff = -curveBack * xNorm * xNorm
            for (j in 0..segsV) {
                val v = j.toFloat() / segsV
                val ang = v * 2f * PI.toFloat()
                val ly = sin(ang) * height * 0.5f * taper
                val lz = cos(ang) * depth * 0.5f * taper + zOff
                val rx = lx * cosT - ly * sinT
                val ry = lx * sinT + ly * cosT
                positions[p++] = centerX + rx
                positions[p++] = centerY + ry
                positions[p++] = centerZ + lz
                uvs[t++] = u
                uvs[t++] = v
            }
        }
        val indices = buildGridIndices(segsU, segsV)
        val normals = computeSmoothNormals(positions, indices)
        return MeshPart(
            name = name,
            positions = positions,
            normals = normals,
            uvs = uvs,
            indices = indices,
            baseColor = baseColor,
            metallic = metallic,
            roughness = roughness,
            partRole = role
        )
    }

    private fun createCylinderPart(
        name: String,
        cx: Float, cy: Float, cz: Float,
        rTop: Float, rBottom: Float, height: Float,
        slices: Int,
        baseColor: FloatArray,
        metallic: Float,
        roughness: Float,
        isSkin: Boolean,
        role: PartRole
    ): MeshPart {
        val stacks = 6
        val positions = FloatArray((stacks + 1) * (slices + 1) * 3)
        val uvs = FloatArray((stacks + 1) * (slices + 1) * 2)
        var p = 0
        var t = 0
        for (i in 0..stacks) {
            val v = i.toFloat() / stacks
            val r = rTop + (rBottom - rTop) * v
            val y = cy + (0.5f - v) * height
            for (j in 0..slices) {
                val u = j.toFloat() / slices
                val theta = u * 2f * PI.toFloat()
                positions[p++] = cx + cos(theta) * r
                positions[p++] = y
                positions[p++] = cz + sin(theta) * r
                uvs[t++] = u
                uvs[t++] = v
            }
        }
        val indices = buildGridIndices(stacks, slices)
        val normals = computeSmoothNormals(positions, indices)
        return MeshPart(
            name = name,
            positions = positions,
            normals = normals,
            uvs = uvs,
            indices = indices,
            baseColor = baseColor,
            metallic = metallic,
            roughness = roughness,
            partRole = role,
            isSkin = isSkin
        )
    }

    private fun createTorusPart(
        name: String,
        cx: Float, cy: Float, cz: Float,
        majorR: Float, minorR: Float,
        axisX: Boolean,
        baseColor: FloatArray,
        metallic: Float,
        roughness: Float,
        emissive: FloatArray = floatArrayOf(0f, 0f, 0f),
        role: PartRole
    ): MeshPart {
        val segU = 32
        val segV = 12
        val positions = FloatArray((segU + 1) * (segV + 1) * 3)
        val uvs = FloatArray((segU + 1) * (segV + 1) * 2)
        var p = 0
        var t = 0
        for (i in 0..segU) {
            val u = i.toFloat() / segU
            val theta = u * 2f * PI.toFloat()
            val cosT = cos(theta)
            val sinT = sin(theta)
            for (j in 0..segV) {
                val v = j.toFloat() / segV
                val phi = v * 2f * PI.toFloat()
                val ring = majorR + minorR * cos(phi)
                val h = minorR * sin(phi)
                if (axisX) {
                    positions[p++] = cx + h
                    positions[p++] = cy + ring * cosT
                    positions[p++] = cz + ring * sinT
                } else {
                    positions[p++] = cx + ring * cosT
                    positions[p++] = cy + h
                    positions[p++] = cz + ring * sinT
                }
                uvs[t++] = u
                uvs[t++] = v
            }
        }
        val indices = buildGridIndices(segU, segV)
        val normals = computeSmoothNormals(positions, indices)
        return MeshPart(
            name = name,
            positions = positions,
            normals = normals,
            uvs = uvs,
            indices = indices,
            baseColor = baseColor,
            metallic = metallic,
            roughness = roughness,
            emissive = emissive,
            partRole = role
        )
    }

    fun buildGridIndices(rows: Int, cols: Int): IntArray {
        val out = IntArray(rows * cols * 6)
        var k = 0
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val a = r * (cols + 1) + c
                val b = (r + 1) * (cols + 1) + c
                val d = r * (cols + 1) + c + 1
                val e = (r + 1) * (cols + 1) + c + 1
                out[k++] = a
                out[k++] = b
                out[k++] = d
                out[k++] = d
                out[k++] = b
                out[k++] = e
            }
        }
        return out
    }

    fun computeSmoothNormals(positions: FloatArray, indices: IntArray): FloatArray {
        val normals = FloatArray(positions.size)
        var i = 0
        while (i + 2 < indices.size) {
            val i0 = indices[i] * 3
            val i1 = indices[i + 1] * 3
            val i2 = indices[i + 2] * 3
            val ax = positions[i1] - positions[i0]
            val ay = positions[i1 + 1] - positions[i0 + 1]
            val az = positions[i1 + 2] - positions[i0 + 2]
            val bx = positions[i2] - positions[i0]
            val by = positions[i2 + 1] - positions[i0 + 1]
            val bz = positions[i2 + 2] - positions[i0 + 2]
            val nx = ay * bz - az * by
            val ny = az * bx - ax * bz
            val nz = ax * by - ay * bx
            normals[i0] += nx; normals[i0 + 1] += ny; normals[i0 + 2] += nz
            normals[i1] += nx; normals[i1 + 1] += ny; normals[i1 + 2] += nz
            normals[i2] += nx; normals[i2 + 1] += ny; normals[i2 + 2] += nz
            i += 3
        }
        var v = 0
        while (v + 2 < normals.size) {
            val len = sqrt(
                normals[v] * normals[v] +
                    normals[v + 1] * normals[v + 1] +
                    normals[v + 2] * normals[v + 2]
            )
            if (len > 1e-6f) {
                normals[v] /= len
                normals[v + 1] /= len
                normals[v + 2] /= len
            } else {
                normals[v] = 0f
                normals[v + 1] = 0f
                normals[v + 2] = 1f
            }
            v += 3
        }
        return normals
    }
}
