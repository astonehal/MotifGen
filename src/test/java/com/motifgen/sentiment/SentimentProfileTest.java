package com.motifgen.sentiment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link SentimentProfile}.
 *
 * <p>Covers Scenario 1 (fromLabel), Scenario 2 (fromVA), Scenario 3 (random).
 */
class SentimentProfileTest {

  // ── Scenario 1: named sentiment ─────────────────────────────────────────────

  @Test
  void fromLabelHappyYieldsCorrectVA() {
    SentimentProfile p = SentimentProfile.fromLabel("happy");
    assertEquals(0.75, p.valence(), 1e-9);
    assertEquals(0.70, p.arousal(), 1e-9);
    assertEquals("HAPPY", p.closestLabel());
  }

  @Test
  void fromLabelIsCaseInsensitive() {
    SentimentProfile lower = SentimentProfile.fromLabel("happy");
    SentimentProfile upper = SentimentProfile.fromLabel("HAPPY");
    SentimentProfile mixed = SentimentProfile.fromLabel("HaPpY");
    assertEquals(lower.valence(), upper.valence(), 1e-9);
    assertEquals(lower.valence(), mixed.valence(), 1e-9);
  }

  @Test
  void fromLabelAllEightSentimentsResolve() {
    String[] labels = {"HAPPY", "EXCITED", "RELAXED", "CONTENT", "SAD", "GLOOMY", "TENSE", "ANGRY"};
    for (String label : labels) {
      SentimentProfile p = SentimentProfile.fromLabel(label);
      assertEquals(label, p.closestLabel(),
          "fromLabel(" + label + ") should have closestLabel=" + label);
    }
  }

  @Test
  void fromLabelUnknownThrowsIllegalArgument() {
    assertThrows(IllegalArgumentException.class,
        () -> SentimentProfile.fromLabel("FURIOUS"));
  }

  // ── Scenario 2: direct V/A ──────────────────────────────────────────────────

  @Test
  void fromVAPreservesExactValues() {
    SentimentProfile p = SentimentProfile.fromVA(0.7, 0.4);
    assertEquals(0.7, p.valence(), 1e-9);
    assertEquals(0.4, p.arousal(), 1e-9);
  }

  @Test
  void fromVAReportsClosestLabel() {
    // (0.7, 0.4) is closest to CONTENT (0.65, 0.40) or RELAXED (0.70, 0.25)?
    // Distance to CONTENT = sqrt((0.05)^2 + (0.0)^2) = 0.05
    // Distance to RELAXED = sqrt((0.0)^2 + (0.15)^2) = 0.15
    // => CONTENT
    SentimentProfile p = SentimentProfile.fromVA(0.7, 0.4);
    assertEquals("CONTENT", p.closestLabel());
  }

  @Test
  void fromVAClampsBelowZero() {
    SentimentProfile p = SentimentProfile.fromVA(-0.1, -0.5);
    assertTrue(p.valence() >= 0.0, "valence must be >= 0");
    assertTrue(p.arousal() >= 0.0, "arousal must be >= 0");
  }

  @Test
  void fromVAClampsAboveOne() {
    SentimentProfile p = SentimentProfile.fromVA(1.5, 2.0);
    assertTrue(p.valence() <= 1.0, "valence must be <= 1");
    assertTrue(p.arousal() <= 1.0, "arousal must be <= 1");
  }

  // ── Scenario 3: random ──────────────────────────────────────────────────────

  @Test
  void randomProfileHasVAInUnitRange() {
    Random rng = new Random(42L);
    for (int i = 0; i < 20; i++) {
      SentimentProfile p = SentimentProfile.random(rng);
      assertTrue(p.valence() >= 0.0 && p.valence() <= 1.0,
          "valence out of range: " + p.valence());
      assertTrue(p.arousal() >= 0.0 && p.arousal() <= 1.0,
          "arousal out of range: " + p.arousal());
    }
  }

  @Test
  void randomProfileHasNonNullLabel() {
    Random rng = new Random(99L);
    SentimentProfile p = SentimentProfile.random(rng);
    assertNotNull(p.closestLabel());
    assertTrue(p.closestLabel().length() > 0);
  }

  @Test
  void randomProfilesVaryAcrossMultipleCalls() {
    Random rng = new Random(7L);
    SentimentProfile first = SentimentProfile.random(rng);
    boolean anyDifferent = false;
    for (int i = 0; i < 10; i++) {
      SentimentProfile next = SentimentProfile.random(rng);
      if (next.valence() != first.valence() || next.arousal() != first.arousal()) {
        anyDifferent = true;
        break;
      }
    }
    assertTrue(anyDifferent, "random() should not always produce the same profile");
  }
}
