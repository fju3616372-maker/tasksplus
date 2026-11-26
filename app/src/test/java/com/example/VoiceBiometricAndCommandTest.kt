package com.example

import com.example.ai.GeminiVoiceCommandEngine
import com.example.audio.VoiceBiometricEngine
import com.example.audio.VoiceLockStrictness
import com.example.data.model.TaskEntity
import java.io.File
import kotlin.math.PI
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceBiometricAndCommandTest {

  private fun synthesizeSpeakerPcm(
    fundamentalHz: Double,
    formantHarmonics: List<Pair<Double, Double>>,
    durationSeconds: Double = 1.2,
    amplitudeScale: Double = 0.45
  ): ShortArray {
    val sampleRate = VoiceBiometricEngine.SAMPLE_RATE
    val total = (sampleRate * durationSeconds).toInt()
    return ShortArray(total) { n ->
      val t = n.toDouble() / sampleRate
      // Gentle syllable-like amplitude envelope so frames behave like natural speech
      val env = 0.65 + 0.35 * sin(2.0 * PI * 3.5 * t)
      var sample = 0.45 * sin(2.0 * PI * fundamentalHz * t)
      for ((freqHz, weight) in formantHarmonics) {
        sample += weight * sin(2.0 * PI * freqHz * t)
      }
      val clamped = (sample * env * amplitudeScale).coerceIn(-0.98, 0.98)
      (clamped * 32767.0).toInt().toShort()
    }
  }

  @Test
  fun acousticVoiceprint_distinguishesSameSpeakerFromDifferentSpeaker() {
    // Speaker A: deeper baritone voice (~118 Hz + lower vocal tract formants)
    val speakerAEnroll1 = synthesizeSpeakerPcm(
      fundamentalHz = 118.0,
      formantHarmonics = listOf(354.0 to 0.55, 590.0 to 0.40, 1062.0 to 0.25, 2242.0 to 0.12),
      amplitudeScale = 0.45
    )
    val speakerAEnroll2 = synthesizeSpeakerPcm(
      fundamentalHz = 122.0,
      formantHarmonics = listOf(366.0 to 0.52, 610.0 to 0.38, 1098.0 to 0.22, 2318.0 to 0.14),
      amplitudeScale = 0.35 // different loudness to verify volume invariance
    )
    val speakerACommand = synthesizeSpeakerPcm(
      fundamentalHz = 120.0,
      formantHarmonics = listOf(360.0 to 0.54, 600.0 to 0.39, 1080.0 to 0.24, 2280.0 to 0.13),
      amplitudeScale = 0.50
    )

    // Speaker B: higher-pitched voice (~245 Hz + brighter upper vocal tract formants)
    val speakerBImpostor = synthesizeSpeakerPcm(
      fundamentalHz = 245.0,
      formantHarmonics = listOf(735.0 to 0.25, 1470.0 to 0.48, 2695.0 to 0.55, 3430.0 to 0.40),
      amplitudeScale = 0.45
    )

    val vp1 = VoiceBiometricEngine.extractVoiceprint(speakerAEnroll1)
    val vp2 = VoiceBiometricEngine.extractVoiceprint(speakerAEnroll2)
    assertTrue("Expected enough voiced frames in enrollment", vp1.hasEnoughSpeech && vp2.hasEnoughSpeech)

    val enrolledMaster = VoiceBiometricEngine.combineVoiceprints(listOf(vp1, vp2))
    val sameCandidate = VoiceBiometricEngine.extractVoiceprint(speakerACommand)
    val otherCandidate = VoiceBiometricEngine.extractVoiceprint(speakerBImpostor)

    val sameResult = VoiceBiometricEngine.compareVoiceprints(enrolledMaster, sameCandidate)
    val diffResult = VoiceBiometricEngine.compareVoiceprints(enrolledMaster, otherCandidate)

    assertTrue("Same speaker should score >= 82%, got ${sameResult.scorePercent}%", sameResult.scorePercent >= 82)
    assertTrue("Different speaker should score <= 45%, got ${diffResult.scorePercent}%", diffResult.scorePercent <= 45)
    assertTrue(
      "Same speaker (${sameResult.scorePercent}%) must decisively beat different speaker (${diffResult.scorePercent}%)",
      sameResult.scorePercent - diffResult.scorePercent >= 40
    )
  }

  @Test
  fun wavEncoderAndDecoder_roundTripPcmSamplesAccurately() {
    val tempFile = File.createTempFile("voice_id_test_", ".wav")
    try {
      val input = ShortArray(800) { i -> ((i * 37) % 10_000 - 5_000).toShort() }
      VoiceBiometricEngine.writePcmToWav(input, tempFile)
      assertTrue(tempFile.exists())
      assertEquals(44L + input.size * 2L, tempFile.length())

      val decoded = VoiceBiometricEngine.readPcmFromWav(tempFile)
      assertEquals(input.size, decoded.size)
      for (i in input.indices) {
        assertEquals(input[i], decoded[i])
      }
    } finally {
      tempFile.delete()
    }
  }

  @Test
  fun hybridVerificationGate_approvesOwnerAndBlocksImpostors() {
    // Verified owner in Balanced mode
    val ownerPass = GeminiVoiceCommandEngine.evaluateHybridVerification(
      acousticScorePercent = 82,
      aiIsSameSpeaker = true,
      aiConfidencePercent = 88,
      aiReason = "Matching timbre and pitch.",
      strictness = VoiceLockStrictness.BALANCED
    )
    assertTrue(ownerPass.isVerifiedOwner)
    assertTrue(ownerPass.fusedScorePercent >= 80)

    // Blocked when Gemini detects a different speaker even if acoustic score is moderate
    val aiReject = GeminiVoiceCommandEngine.evaluateHybridVerification(
      acousticScorePercent = 70,
      aiIsSameSpeaker = false,
      aiConfidencePercent = 20,
      aiReason = "Different vocal cadence and higher pitch.",
      strictness = VoiceLockStrictness.BALANCED
    )
    assertFalse(aiReject.isVerifiedOwner)

    // Blocked in Strict mode when acoustic floor is not met
    val strictReject = GeminiVoiceCommandEngine.evaluateHybridVerification(
      acousticScorePercent = 58,
      aiIsSameSpeaker = true,
      aiConfidencePercent = 85,
      aiReason = "Borderline match.",
      strictness = VoiceLockStrictness.STRICT
    )
    assertFalse(strictReject.isVerifiedOwner)
  }

  @Test
  fun fuzzyTaskMatcher_resolvesExactIdSubstringAndTokenOverlap() {
    val tasks = listOf(
      TaskEntity(id = "t1", title = "Buy almond milk and eggs"),
      TaskEntity(id = "t2", title = "Book dentist appointment"),
      TaskEntity(id = "t3", title = "Submit quarterly expense report")
    )

    val byId = GeminiVoiceCommandEngine.findBestMatchingTask("", "t2", tasks)
    assertEquals("t2", byId?.id)

    val bySubstring = GeminiVoiceCommandEngine.findBestMatchingTask("dentist appointment", "", tasks)
    assertEquals("t2", bySubstring?.id)

    val byTokens = GeminiVoiceCommandEngine.findBestMatchingTask("milk and eggs", "", tasks)
    assertNotNull(byTokens)
    assertEquals("t1", byTokens?.id)

    val noMatch = GeminiVoiceCommandEngine.findBestMatchingTask("wash the sailboat", "", tasks)
    assertNull(noMatch)
  }
}
