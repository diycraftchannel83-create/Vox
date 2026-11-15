package com.example.model

import org.junit.Assert.assertEquals
import org.junit.Test

class FacialRigStateTest {

    @Test
    fun jawAndVowelTargetsMapToRigWeights() {
        val rig = FacialRigState(
            jawOpen = 0.7f,
            mouthPucker = 0.8f,
            mouthWide = 0.6f
        )

        assertEquals(0.7f, rig.weightForMorphName("jawOpen"), 0.0001f)
        assertEquals(0.8f, rig.weightForMorphName("viseme_oh"), 0.0001f)
        assertEquals(0.8f, rig.weightForMorphName("viseme_ou"), 0.0001f)
        assertEquals(0.6f * 0.85f + 0.12f * 0.35f,
            rig.weightForMorphName("viseme_ee"), 0.0001f)
    }

    @Test
    fun blinkAndSmileTargetsMapToRigWeights() {
        val rig = FacialRigState(eyeBlink = 0.9f, smile = 0.5f)

        assertEquals(0.9f, rig.weightForMorphName("eyeBlinkLeft"), 0.0001f)
        assertEquals(0.9f, rig.weightForMorphName("eyeBlinkRight"), 0.0001f)
        assertEquals(0.5f * 0.35f,
            rig.weightForMorphName("mouthSmileLeft"), 0.0001f)
    }

    @Test
    fun unknownMorphTargetsRemainNeutral() {
        assertEquals(0f, FacialRigState().weightForMorphName("unmapped_target"), 0.0001f)
    }
}
