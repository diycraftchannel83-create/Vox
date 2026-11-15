package com.example.avatar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class SkeletalSkinnerTest {
    private fun identity() = FloatArray(16).also { it[0]=1f; it[5]=1f; it[10]=1f; it[15]=1f }
    private fun translated(x: Float, y: Float = 0f, z: Float = 0f) = identity().also {
        it[12]=x; it[13]=y; it[14]=z
    }

    @Test fun identityPalettePreservesBindPose() {
        val skin = AvatarSkin(listOf(AvatarJoint("root", 0, -1, identity(), identity())))
        val palette = SkeletalSkinner.buildPalette(skin, emptyMap(), identity())
        val result = SkeletalSkinner.deform(
            floatArrayOf(0.2f, 0.3f, 0.4f), floatArrayOf(0f, 0f, 1f),
            VertexSkinData(intArrayOf(0,0,0,0), floatArrayOf(1f,0f,0f,0f)), palette
        )
        assertArrayEquals(floatArrayOf(0.2f,0.3f,0.4f), result.first, 1e-5f)
        assertArrayEquals(floatArrayOf(0f,0f,1f), result.second, 1e-5f)
    }

    @Test fun weightedSkinningAppliesJointTranslation() {
        val palette = listOf(translated(1f), translated(0f, 2f))
        val result = SkeletalSkinner.deform(
            floatArrayOf(0f,0f,0f), null,
            VertexSkinData(intArrayOf(0,1,0,0), floatArrayOf(0.25f,0.75f,0f,0f)), palette
        )
        assertArrayEquals(floatArrayOf(0.25f,1.5f,0f), result.first, 1e-5f)
    }

    @Test fun childJointWorldTransformIncludesParent() {
        val skin = AvatarSkin(listOf(
            AvatarJoint("root", 0, -1, identity(), identity()),
            AvatarJoint("jaw", 1, 0, translated(0f, 1f), identity())
        ))
        val world = SkeletalSkinner.buildJointWorldMatrices(
            skin, mapOf(0 to translated(2f), 1 to translated(0f, 3f))
        )
        assertEquals(2f, world[1][12], 1e-5f)
        assertEquals(4f, world[1][13], 1e-5f)
    }
}
