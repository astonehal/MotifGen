package com.motifgen.theory;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class KeySignatureTest {

  @Test
  void majorProducesCorrectScaleDegreesAndName() {
    KeySignature c = KeySignature.major(0);
    assertEquals("C major", c.name());
    assertEquals(0, c.root());
    assertFalse(c.minor());
    assertArrayEquals(new int[]{0, 2, 4, 5, 7, 9, 11}, c.scaleDegrees());

    // G major -> F# in scale
    KeySignature g = KeySignature.major(7);
    assertEquals("G major", g.name());
    assertArrayEquals(new int[]{7, 9, 11, 0, 2, 4, 6}, g.scaleDegrees());
  }

  @Test
  void minorProducesCorrectScaleDegreesAndName() {
    KeySignature a = KeySignature.minor(9);
    assertEquals("A minor", a.name());
    assertTrue(a.minor());
    // A natural minor: A B C D E F G  -> pcs 9, 11, 0, 2, 4, 5, 7
    assertArrayEquals(new int[]{9, 11, 0, 2, 4, 5, 7}, a.scaleDegrees());
  }

  @Test
  void relativeMajorMinorRoundTrip() {
    KeySignature c = KeySignature.major(0);
    KeySignature am = c.relative();
    assertEquals("A minor", am.name());
    assertEquals(c.name(), am.relative().name());
  }

  @Test
  void parallelFlipsMode() {
    KeySignature cMaj = KeySignature.major(0);
    KeySignature cMin = cMaj.parallel();
    assertEquals("C minor", cMin.name());
    assertEquals(cMaj.root(), cMin.root());
    assertEquals(cMaj.name(), cMin.parallel().name());
  }

  @Test
  void dominantAndSubdominantPreserveMode() {
    KeySignature c = KeySignature.major(0);
    assertEquals("G major", c.dominant().name());
    assertEquals("F major", c.subdominant().name());

    KeySignature am = KeySignature.minor(9);
    assertEquals("E minor", am.dominant().name());
    assertEquals("D minor", am.subdominant().name());
  }

  @Test
  void containsPitchClassMatchesScaleMembership() {
    KeySignature c = KeySignature.major(0);
    assertTrue(c.containsPitchClass(0));   // C
    assertTrue(c.containsPitchClass(4));   // E
    assertFalse(c.containsPitchClass(1));  // C#
    // Negative pitch class handled by modular reduction
    assertTrue(c.containsPitchClass(-12)); // == 0
    assertFalse(c.containsPitchClass(-11));// == 1
  }

  @Test
  void fitScoreReturnsInKeyProportion() {
    KeySignature c = KeySignature.major(0);
    // All in key
    assertEquals(1.0, c.fitScore(List.of(0, 2, 4)));
    // None in key
    assertEquals(0.0, c.fitScore(List.of(1, 3, 6)));
    // Half in key
    assertEquals(0.5, c.fitScore(List.of(0, 1)));
    // Empty list
    assertEquals(0.0, c.fitScore(List.of()));
  }

  @Test
  void intervalToReturnsSemitoneDistanceModulo12() {
    KeySignature c = KeySignature.major(0);
    KeySignature g = KeySignature.major(7);
    assertEquals(7, c.intervalTo(g));
    assertEquals(5, g.intervalTo(c)); // -7 mod 12 = 5
  }

  @Test
  void allKeysReturns24Entries() {
    List<KeySignature> all = KeySignature.allKeys();
    assertEquals(24, all.size());
  }

  @Test
  void relatedKeysIncludesSelfRelativeParallelDominantSubdominant() {
    KeySignature c = KeySignature.major(0);
    List<KeySignature> related = c.relatedKeys();
    assertEquals(5, related.size());
    assertEquals("C major", related.get(0).name());
    assertEquals("A minor", related.get(1).name());
    assertEquals("C minor", related.get(2).name());
    assertEquals("G major", related.get(3).name());
    assertEquals("F major", related.get(4).name());
  }

  @Test
  void noteNameHandlesSharpSpellingAndNegativeInput() {
    assertEquals("C", KeySignature.noteName(0));
    assertEquals("C#", KeySignature.noteName(1));
    assertEquals("B", KeySignature.noteName(11));
    assertEquals("B", KeySignature.noteName(-1)); // wraps
  }

  @Test
  void noteNameFlatHandlesFlatSpelling() {
    assertEquals("Eb", KeySignature.noteNameFlat(3));
    assertEquals("Bb", KeySignature.noteNameFlat(10));
    assertEquals("C", KeySignature.noteNameFlat(0));
  }
}
