package com.example

import com.example.audio.AudioVisemeAnalyzer
import com.example.avatar.ObjModelParser
import com.example.avatar.ProceduralAvatarFactory
import com.example.model.VisemeType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.PI
import kotlin.math.sin

class ExampleUnitTest {
    @Test
    fun visemeAnalyzer_detectsSilenceAndSpeechOpening() {
        val analyzer = AudioVisemeAnalyzer(fftSize = 512)
        val sampleRate = 16000
        val silence = ShortArray(sampleRate / 2) { 0 }
        val silentTimeline = analyzer.analyzePcmToTimeline(silence, sampleRate)
        val silentRig = silentTimeline.sampleAt(200L)
        assertEquals(VisemeType.SIL, silentRig.viseme)
        assertTrue(silentRig.jawOpen < 0.05f)

        // Generate 450Hz voiced vowel-like tone (strong F1 formant)
        val voiced = ShortArray(sampleRate) { i ->
            (18000.0 * sin(2.0 * PI * 450.0 * i / sampleRate)).toInt().toShort()
        }
        val speechTimeline = analyzer.analyzePcmToTimeline(voiced, sampleRate)
        val midSpeechRig = speechTimeline.sampleAt(500L)
        assertTrue(midSpeechRig.jawOpen > 0.25f)
        assertTrue(midSpeechRig.vocalEnergy > 0.2f)
    }

    @Test
    fun proceduralAvatars_andObjParser_produceValidGeometry() {
        val nova = ProceduralAvatarFactory.buildAvatar("nova")
        assertTrue(nova.totalVertices > 1000)
        assertTrue(nova.totalTriangles > 1000)

        val tmpObj = File.createTempFile("test_head", ".obj")
        try {
            tmpObj.writeText(
                """
                v 0 1 0
                v -1 -1 0.5
                v 1 -1 0.5
                v 0 0 -1
                f 1 2 3
                f 1 3 4
                """.trimIndent()
            )
            val model = ObjModelParser.parse(tmpObj, "test", "TestHead")
            assertEquals(2, model.totalTriangles)
            assertTrue(model.isCustomImported)
        } finally {
            tmpObj.delete()
        }
    }
}
