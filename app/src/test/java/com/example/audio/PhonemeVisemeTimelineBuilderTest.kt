package com.example.audio

import com.example.model.VisemeType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhonemeVisemeTimelineBuilderTest {
    @Test fun mapsCommonEnglishAndIndonesianSpeechPhonemes() {
        assertEquals(VisemeType.PP, PhonemeVisemeTimelineBuilder.visemeFor("M"))
        assertEquals(VisemeType.FF, PhonemeVisemeTimelineBuilder.visemeFor("V"))
        assertEquals(VisemeType.AA, PhonemeVisemeTimelineBuilder.visemeFor("AH0"))
        assertEquals(VisemeType.IH, PhonemeVisemeTimelineBuilder.visemeFor("IY1"))
        assertEquals(VisemeType.SIL, PhonemeVisemeTimelineBuilder.visemeFor("SIL"))
    }

    @Test fun createsDeterministicFramesFromAlignedTimestamps() {
        val timeline = PhonemeVisemeTimelineBuilder.build(
            durationMs = 100,
            phonemes = listOf(
                TimedPhoneme("M", 0, 40),
                TimedPhoneme("AA", 40, 100)
            ),
            stepMs = 20,
            transitionMs = 0
        )
        assertEquals(6, timeline.frames.size)
        assertEquals(VisemeType.PP, timeline.frames[0].rig.viseme)
        assertEquals(VisemeType.AA, timeline.frames[2].rig.viseme)
        assertTrue(timeline.frames[2].rig.jawOpen > 0f)
    }

    @Test fun clampsSamplesToAudioDurationAndLeavesGapsSilent() {
        val timeline = PhonemeVisemeTimelineBuilder.build(
            durationMs = 60,
            phonemes = listOf(TimedPhoneme("S", 80, 100)),
            stepMs = 20
        )
        assertEquals(4, timeline.frames.size)
        assertEquals(VisemeType.SIL, timeline.frames.last().rig.viseme)
    }
    @Test fun buildsScriptTimedFallbackWithVowelsAndConsonants() {
        val timeline = PhonemeVisemeTimelineBuilder.buildFromText(
            text = "Mama buka pintu",
            durationMs = 1200,
            stepMs = 20,
            transitionMs = 0
        )
        assertEquals(61, timeline.frames.size)
        assertTrue(timeline.frames.any { it.rig.viseme == VisemeType.PP })
        assertTrue(timeline.frames.any { it.rig.viseme == VisemeType.AA })
        assertTrue(timeline.frames.any { it.rig.viseme == VisemeType.OU })
        assertTrue(timeline.frames.any { it.rig.jawOpen > 0f })
    }

    @Test fun emptyScriptProducesSilentTimeline() {
        val timeline = PhonemeVisemeTimelineBuilder.buildFromText(
            text = "   ",
            durationMs = 200,
            stepMs = 20
        )
        assertTrue(timeline.frames.all { it.rig.viseme == VisemeType.SIL })
    }

    @Test fun nonLatinScriptsStillAnimateTheMouth() {
        listOf("مرحبا", "こんにちは", "안녕하세요").forEach { script ->
            val timeline = PhonemeVisemeTimelineBuilder.buildFromText(
                text = script,
                durationMs = 1000,
                stepMs = 20,
                transitionMs = 0
            )
            assertTrue("$script should create speaking frames", timeline.frames.any {
                it.rig.viseme != VisemeType.SIL && it.rig.jawOpen > 0f
            })
        }
    }

}
