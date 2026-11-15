package com.example.avatar

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class SkeletalSkinnerTest {
    private fun identity() = FloatArray(16).also { for (i in 0..3) it[i * 5] = 1f }

    @Test fun identityPalettePreservesMesh() {
        val positions = floatArrayOf(1f, 2f, 3f)
        val normals = floatArrayOf(0f, 0f, 1f)
        val skin = VertexSkinData(intArrayOf(0, 0, 0, 0), floatArrayOf(1f, 0f, 0f, 0f))
        val result = SkeletalSkinner.deform(positions, normals, skin, listOf(identity()))
        assertArrayEquals(positions, result.positions, 1e-5f)
        assertArrayEquals(normals, result.normals, 1e-5f)
    }

    @Test fun weightedJointTranslationMovesVertex() {
        val translated = identity().also { it[12] = 2f }
        val skin = VertexSkinData(intArrayOf(0, 0, 0, 0), floatArrayOf(1f, 0f, 0f, 0f))
        val result = SkeletalSkinner.deform(
            floatArrayOf(1f, 0f, 0f), floatArrayOf(0f, 0f, 1f), skin, listOf(translated)
        )
        assertEquals(3f, result.positions[0], 1e-5f)
    }

    @Test fun matrixInverseRoundTrips() {
        val transform = identity().also { it[12] = 3f; it[13] = -2f; it[14] = 5f }
        val result = SkeletalSkinner.multiply(transform, SkeletalSkinner.invert(transform))
        assertArrayEquals(identity(), result, 1e-5f)
    }
    @Test fun nonUniformJointScaleUsesInverseTransposeForNormals() {
        val scaled = identity().also { it[0] = 2f }
        val skin = VertexSkinData(intArrayOf(0, 0, 0, 0), floatArrayOf(1f, 0f, 0f, 0f))
        val result = SkeletalSkinner.deform(
            floatArrayOf(0f, 0f, 0f),
            floatArrayOf(1f, 1f, 0f),
            skin,
            listOf(scaled)
        )
        val expectedX = 0.4472136f
        val expectedY = 0.8944272f
        assertEquals(expectedX, result.normals[0], 1e-5f)
        assertEquals(expectedY, result.normals[1], 1e-5f)
    }

}
