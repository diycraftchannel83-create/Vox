package com.example.model

import org.junit.Assert.assertEquals
import org.junit.Test

class FacialRigStateTest {
    @Test fun jawAndVowelTargetsMapToRigWeights() {
        val rig = FacialRigState(jawOpen=0.7f, mouthPucker=0.8f, mouthWide=0.6f)
        assertEquals(0.7f, rig.weightForMorphName("jawOpen"), 0.0001f)
        assertEquals(0.8f, rig.weightForMorphName("viseme_oh"), 0.0001f)
        assertEquals(0.8f, rig.weightForMorphName("viseme_ou"), 0.0001f)
        assertEquals(0.6f*0.85f + 0.12f*0.35f, rig.weightForMorphName("viseme_ee"), 0.0001f)
    }

    @Test fun blinkAndSmileTargetsMapToRigWeights() {
        val rig = FacialRigState(eyeBlink=0.9f, smile=0.5f)
        assertEquals(0.9f, rig.weightForMorphName("eyeBlinkLeft"), 0.0001f)
        assertEquals(0.9f, rig.weightForMorphName("eyeBlinkRight"), 0.0001f)
        assertEquals(0.5f*0.35f, rig.weightForMorphName("mouthSmileLeft"), 0.0001f)
    }

    @Test fun leftAndRightControlsCanDiffer() {
        val rig = FacialRigState(
            eyeBlink=0.1f, leftEyeBlink=0.85f, rightEyeBlink=0.15f,
            smile=0.1f, leftSmile=0.8f, rightSmile=0.2f,
            leftBrowRaise=0.7f, rightBrowRaise=0.25f
        )
        assertEquals(0.85f, rig.weightForMorphName("eyeBlinkLeft"), 0.0001f)
        assertEquals(0.15f, rig.weightForMorphName("eyeBlinkRight"), 0.0001f)
        assertEquals(0.8f, rig.weightForMorphName("mouthSmileLeft"), 0.0001f)
        assertEquals(0.2f, rig.weightForMorphName("mouthSmileRight"), 0.0001f)
        assertEquals(0.7f, rig.weightForMorphName("browInnerUp"), 0.0001f)
        assertEquals(0.25f, rig.weightForMorphName("browOuterUp"), 0.0001f)
    }

    @Test fun timelineInterpolatesAsymmetricControls() {
        val timeline = VisemeTimeline(100, 100, listOf(
            VisemeFrame(0, FacialRigState(leftEyeBlink=0f, rightEyeBlink=1f)),
            VisemeFrame(100, FacialRigState(leftEyeBlink=1f, rightEyeBlink=0f))
        ))
        val sample = timeline.sampleAt(50)
        assertEquals(0.5f, sample.leftEyeBlink ?: -1f, 0.0001f)
        assertEquals(0.5f, sample.rightEyeBlink ?: -1f, 0.0001f)
    }

    @Test fun unknownMorphTargetsRemainNeutral() {
        assertEquals(0f, FacialRigState().weightForMorphName("unmapped_target"), 0.0001f)
    }
}
