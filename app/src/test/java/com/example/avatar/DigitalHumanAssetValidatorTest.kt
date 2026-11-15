package com.example.avatar

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DigitalHumanAssetValidatorTest {
    private fun validMesh(morphs: List<MorphTargetData> = emptyList(), normals: FloatArray = floatArrayOf(0f,0f,1f,0f,0f,1f,0f,0f,1f)) =
        MeshPart("head", floatArrayOf(0f,0f,0f,1f,0f,0f,0f,1f,0f), normals,
            floatArrayOf(0f,0f,1f,0f,0f,1f), intArrayOf(0,1,2),
            floatArrayOf(0.8f,0.6f,0.5f,1f), morphTargets = morphs)

    @Test fun productionEvidenceIsIncompleteByDefault() {
        val evidence = DigitalHumanProductionEvidence()
        assertFalse(evidence.complete)
    }

    @Test fun allProductionEvidenceMustBeExplicitlyApproved() {
        val evidence = DigitalHumanProductionEvidence(
            visualReviewPassed = true,
            commercialRedistributionCleared = true,
            sourceAndAttributionRecorded = true,
            devicePerformancePassed = true,
            previewExportParityPassed = true
        )
        assertTrue(evidence.complete)
    }

    @Test fun staticMeshCannotPassProductionGateWithDefaultEvidence() {
        val report = DigitalHumanAssetValidator.inspect(
            AvatarModel3D("static", "Static", listOf(validMesh()))
        )
        assertFalse(report.productionReady)
        assertTrue(report.findings.any { it.code == "pbr_texture_pipeline_incomplete" })
        assertTrue(report.findings.any { it.code == "commercial_license_missing" })
        assertTrue(report.findings.any { it.code == "visual_review_missing" })
    }

    @Test fun emptyModelIsRejected() {
        val report = DigitalHumanAssetValidator.inspect(AvatarModel3D("empty","Empty",emptyList()))
        assertTrue(report.hasErrors)
        assertFalse(report.isStructurallyReady)
    }
    @Test fun mismatchedNormalArrayIsRejected() {
        val report = DigitalHumanAssetValidator.inspect(AvatarModel3D("bad","Bad",listOf(validMesh(normals=floatArrayOf(0f,0f,1f)))))
        assertTrue(report.findings.any { it.code == "invalid_normals_0" })
    }
    @Test fun staticMeshIsNotMarkedProductionReady() {
        val report = DigitalHumanAssetValidator.inspect(AvatarModel3D("static","Static",listOf(validMesh())))
        assertTrue(report.isStructurallyReady)
        assertFalse(report.productionReady)
        assertTrue(report.findings.any { it.code == "no_facial_rig" })
    }
    @Test fun skinJointOutsideSkinIsRejected() {
        val skin = AvatarSkin(listOf(AvatarJoint("root", 0, -1, FloatArray(16), FloatArray(16))))
        val mesh = validMesh().copy(skinData = VertexSkinData(IntArray(12) { if (it % 4 == 0) 2 else 0 }, floatArrayOf(1f,0f,0f,0f,1f,0f,0f,0f,1f,0f,0f,0f)), skinIndex = 0)
        val report = DigitalHumanAssetValidator.inspect(AvatarModel3D("bad-joint","Bad Joint",listOf(mesh), skins = listOf(skin)))
        assertTrue(report.hasErrors)
        assertTrue(report.findings.any { it.code == "skin_joint_out_of_range_0" })
    }

    @Test fun malformedSkinInfluenceCountIsRejected() {
        val mesh = validMesh().copy(skinData = VertexSkinData(intArrayOf(0,0,0,0), floatArrayOf(1f,0f,0f,0f)))
        val report = DigitalHumanAssetValidator.inspect(AvatarModel3D("bad-skin","Bad Skin",listOf(mesh)))
        assertTrue(report.hasErrors)
        assertTrue(report.findings.any { it.code == "invalid_skin_influence_count_0" })
    }

    @Test fun malformedMorphDeltaIsRejected() {
        val morph = MorphTargetData("jawOpen", floatArrayOf(0f,0f,0f))
        val report = DigitalHumanAssetValidator.inspect(AvatarModel3D("bad-morph","Bad Morph",listOf(validMesh(morphs=listOf(morph)))))
        assertTrue(report.hasErrors)
        assertTrue(report.findings.any { it.code == "invalid_morph_position_0" })
    }
}
