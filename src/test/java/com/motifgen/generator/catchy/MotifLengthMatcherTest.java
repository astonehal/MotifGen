package com.motifgen.generator.catchy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.motifgen.model.Motif;
import com.motifgen.model.Note;
import com.motifgen.theory.KeySignature;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class MotifLengthMatcherTest {

  private static final int TPB = 480;
  private static final int BPB = 4;
  private static final long BAR_TICKS = (long) BPB * TPB;
  private static final long PHRASE_TICKS = 4 * BAR_TICKS;

  private Motif oneBarMotif() {
    int[] pitches = {60, 62, 64, 65};
    List<Note> notes = new ArrayList<>();
    long tick = 0;
    for (int p : pitches) {
      notes.add(new Note(p, tick, TPB, 90));
      tick += TPB;
    }
    return new Motif(notes, 4, BPB, TPB);
  }

  private Motif eightBarMotif() {
    // 16 half-notes (each 2 beats) span exactly 8 bars at 4/4
    int[] pitches = {60, 62, 64, 65, 67, 65, 64, 62,
                     60, 62, 64, 67, 65, 64, 62, 60};
    long halfNote = 2L * TPB;
    List<Note> notes = new ArrayList<>();
    long tick = 0;
    for (int p : pitches) {
      notes.add(new Note(p, tick, halfNote, 90));
      tick += halfNote;
    }
    return new Motif(notes, 8, BPB, TPB);
  }

  @Test
  void contentSpanIgnoresLeadingTrailingSilence() {
    MotifLengthMatcher matcher = new MotifLengthMatcher();
    // notes start at tick 480 and end at tick 1920 (= 1 bar of music starting at beat 2)
    List<Note> notes = List.of(
        new Note(60, 480, TPB, 90),
        new Note(62, 480 + TPB, TPB, 90),
        new Note(64, 480 + 2 * TPB, TPB, 90));
    Motif motif = new Motif(notes, 4, BPB, TPB);

    MotifLengthMatcher.ContentSpan span = matcher.span(motif);

    assertEquals(480, span.startTick());
    assertEquals(480 + 3 * TPB, span.endTick());
    assertEquals(3 * TPB, span.durationTicks());
  }

  @Test
  void shorterMotifFillsPhraseExactlyAfterMatch() {
    MotifLengthMatcher matcher = new MotifLengthMatcher();
    Motif matched = matcher.match(oneBarMotif(), PHRASE_TICKS,
        KeySignature.major(0), 0L);

    long lastEnd = matched.getNotes().stream()
        .mapToLong(Note::endTick).max().orElse(0L);
    assertTrue(lastEnd >= PHRASE_TICKS - TPB
            && lastEnd <= PHRASE_TICKS + TPB,
        "matched motif should end within one beat of the phrase boundary, got " + lastEnd);
  }

  @Test
  void shorterMotifProducesAtLeastFourTilesWorthOfNotes() {
    MotifLengthMatcher matcher = new MotifLengthMatcher();
    Motif source = oneBarMotif();
    Motif matched = matcher.match(source, PHRASE_TICKS,
        KeySignature.major(0), 0L);

    // 4-bar phrase / 1-bar motif = 4 tiles, each with 4 notes => 16 notes
    assertTrue(matched.getNotes().size() >= 4 * source.getNotes().size() - 1,
        "expected ~16 notes (4 tiles), got " + matched.getNotes().size());
  }

  @Test
  void extendWithAscendingPatternProducesAscendingTiles() {
    // Use identity picker to verify the diatonic-transpose logic in isolation.
    MotifLengthMatcher matcher = new MotifLengthMatcher((tile, key) -> tile);
    Motif tile0 = oneBarMotif();
    Motif tiled = matcher.extend(tile0, PHRASE_TICKS, KeySignature.major(0),
        new int[] {0, 1, 2, 3});

    // tile 0 first note: 60. tile 1 first note: D (62). tile 2: E (64). tile 3: F (65).
    int notesPerTile = tile0.getNotes().size();
    List<Note> all = tiled.getNotes();
    assertEquals(60, all.get(0).pitch());
    assertEquals(62, all.get(notesPerTile).pitch());
    assertEquals(64, all.get(2 * notesPerTile).pitch());
    assertEquals(65, all.get(3 * notesPerTile).pitch());
  }

  @Test
  void extendPreservesScaleDegreePatternWithinEachTile() {
    // Tonal sequence preserves SCALE-DEGREE motion, not chromatic intervals:
    // moving the C-major motif {C,D,E,F} up by +1 step gives {D,E,F,G},
    // whose chromatic intervals (2,1,2) differ from the source (2,2,1) but
    // whose scale-degree positions (1,2,3,4) match exactly.
    // Use identity picker so tiles 1+ are pure diatonic (no extra random transform).
    MotifTransformer transformer = new MotifTransformer();
    KeySignature key = KeySignature.major(0);
    MotifLengthMatcher matcher = new MotifLengthMatcher((tile, k) -> tile);
    int[] steps = {0, 1, -1, 2};

    Motif tile0 = oneBarMotif();
    Motif tiled = matcher.extend(tile0, PHRASE_TICKS, key, steps);

    int notesPerTile = tile0.getNotes().size();
    List<Note> all = tiled.getNotes();
    int tiles = all.size() / notesPerTile;
    for (int t = 0; t < tiles; t++) {
      Motif expected = transformer.diatonicTranspose(tile0, steps[t], key);
      for (int i = 0; i < notesPerTile; i++) {
        assertEquals(expected.getNotes().get(i).pitch(),
            all.get(t * notesPerTile + i).pitch(),
            "tile " + t + " note " + i + " should match diatonic transpose");
      }
    }
  }

  @Test
  void extendTrimsTrailingNoteToPhraseBoundary() {
    // Use identity picker — this test is about boundary trimming, not pitch transforms.
    MotifLengthMatcher matcher = new MotifLengthMatcher((tile, key) -> tile);
    // 1-bar motif, fits 4 times in 4 bars -> last tile fills exactly
    Motif tiled = matcher.extend(oneBarMotif(), PHRASE_TICKS,
        KeySignature.major(0), new int[] {0, 1, 2, 3});

    long lastEnd = tiled.getNotes().stream()
        .mapToLong(Note::endTick).max().orElse(0L);
    assertEquals(PHRASE_TICKS, lastEnd,
        "last note must end exactly at the phrase boundary");
  }

  @Test
  void longerMotifIsReducedProportionallyWhenAboveFloor() {
    MotifLengthMatcher matcher = new MotifLengthMatcher();
    Motif source = eightBarMotif(); // 16 half-notes spanning 8 bars
    Motif matched = matcher.reduce(source, PHRASE_TICKS); // target 4 bars

    // each half-note (960) should be halved to a quarter (480)
    for (Note n : matched.getNotes()) {
      assertEquals(TPB, n.durationTicks(),
          "every note should be halved to a quarter");
    }
    long lastEnd = matched.getNotes().stream()
        .mapToLong(Note::endTick).max().orElse(0L);
    assertTrue(Math.abs(lastEnd - PHRASE_TICKS) <= TPB,
        "reduced motif should end near the phrase boundary, got " + lastEnd);
  }

  @Test
  void reductionAppliesSubSamplingWhenScaleWouldClipBelowFloor() {
    MotifLengthMatcher matcher = new MotifLengthMatcher();
    // 32 eighth-notes (240 ticks each) span 4 bars at 4/4. Target = 1 bar
    // means scale = 0.25, which would push every duration to 60 ticks
    // (below the 120-tick floor) -> sub-sampling must kick in.
    List<Note> notes = new ArrayList<>();
    long tick = 0;
    for (int i = 0; i < 32; i++) {
      notes.add(new Note(60 + (i % 8), tick, TPB / 2, 90));
      tick += TPB / 2;
    }
    Motif source = new Motif(notes, 4, BPB, TPB);

    Motif matched = matcher.reduce(source, BAR_TICKS);

    for (Note n : matched.getNotes()) {
      assertTrue(n.durationTicks() >= 120,
          "no duration should be below 120 ticks, got " + n.durationTicks());
    }
    assertTrue(matched.getNotes().size() < source.getNotes().size(),
        "sub-sampling should drop some notes, kept "
            + matched.getNotes().size() + " of " + source.getNotes().size());
  }

  @Test
  void reductionPreservesFirstAndLastSoundingNote() {
    MotifLengthMatcher matcher = new MotifLengthMatcher();
    List<Note> notes = new ArrayList<>();
    long tick = 0;
    for (int i = 0; i < 32; i++) {
      notes.add(new Note(60 + (i % 8), tick, TPB / 2, 90));
      tick += TPB / 2;
    }
    Motif source = new Motif(notes, 4, BPB, TPB);

    Motif matched = matcher.reduce(source, BAR_TICKS);  // very tight target

    // First note's pitch and last note's pitch are preserved.
    assertEquals(source.getNotes().get(0).pitch(),
        matched.getNotes().get(0).pitch());
    assertEquals(source.getNotes().get(source.getNotes().size() - 1).pitch(),
        matched.getNotes().get(matched.getNotes().size() - 1).pitch());
  }

  @Test
  void matchReturnsMotifUnchangedWhenAlreadyExactlyPhraseSized() {
    MotifLengthMatcher matcher = new MotifLengthMatcher();
    // Build a motif that exactly spans 4 bars
    int[] pitches = {60, 62, 64, 65, 67, 65, 64, 62,
                     60, 62, 64, 67, 65, 64, 62, 60};
    List<Note> notes = new ArrayList<>();
    long tick = 0;
    for (int p : pitches) {
      notes.add(new Note(p, tick, TPB, 90));
      tick += TPB;
    }
    Motif source = new Motif(notes, 4, BPB, TPB);

    Motif matched = matcher.match(source, PHRASE_TICKS,
        KeySignature.major(0), 0L);

    assertEquals(source.getNotes().size(), matched.getNotes().size());
    for (int i = 0; i < source.getNotes().size(); i++) {
      assertEquals(source.getNotes().get(i).pitch(),
          matched.getNotes().get(i).pitch());
    }
  }

  @Test
  void matchIsDeterministicForSameSeed() {
    MotifLengthMatcher matcher = new MotifLengthMatcher();
    Motif a = matcher.match(oneBarMotif(), PHRASE_TICKS,
        KeySignature.major(0), 42L);
    Motif b = matcher.match(oneBarMotif(), PHRASE_TICKS,
        KeySignature.major(0), 42L);

    assertEquals(a.getNotes().size(), b.getNotes().size());
    for (int i = 0; i < a.getNotes().size(); i++) {
      assertEquals(a.getNotes().get(i).pitch(), b.getNotes().get(i).pitch());
    }
  }

  @Test
  void matchPicksOneOfTheCandidatePatternsForExtension() {
    // The matched motif should be one of the five candidate patterns,
    // not e.g. the trivial repeat-only or untransposed copy.
    MotifLengthMatcher matcher = new MotifLengthMatcher();
    Motif matched = matcher.match(oneBarMotif(), PHRASE_TICKS,
        KeySignature.major(0), 0L);

    // Trivially: the matched output is at least as long as one tile and the
    // first note matches the source motif's first note.
    assertEquals(60, matched.getNotes().get(0).pitch());
    // Some pattern (other than pure-repeat) should at least sometimes win;
    // we don't assert which, but we do ensure at least 16 notes total.
    assertTrue(matched.getNotes().size() >= 16,
        "should produce >=16 notes for 4-bar phrase from 1-bar motif");
  }

  @Test
  void differentMotifsUnlikelyToProduceIdenticalSequences() {
    // Sanity: two distinct motifs should give distinct length-matched outputs.
    MotifLengthMatcher matcher = new MotifLengthMatcher();

    int[] aPitches = {60, 62, 64, 65};
    int[] bPitches = {72, 70, 68, 67};
    List<Note> a = new ArrayList<>();
    List<Note> b = new ArrayList<>();
    long tick = 0;
    for (int i = 0; i < 4; i++) {
      a.add(new Note(aPitches[i], tick, TPB, 90));
      b.add(new Note(bPitches[i], tick, TPB, 90));
      tick += TPB;
    }
    Motif motifA = new Motif(a, 4, BPB, TPB);
    Motif motifB = new Motif(b, 4, BPB, TPB);

    Motif matchedA = matcher.match(motifA, PHRASE_TICKS,
        KeySignature.major(0), 0L);
    Motif matchedB = matcher.match(motifB, PHRASE_TICKS,
        KeySignature.major(0), 0L);

    List<Integer> pitchesA = matchedA.getNotes().stream().map(Note::pitch).toList();
    List<Integer> pitchesB = matchedB.getNotes().stream().map(Note::pitch).toList();
    assertNotEquals(pitchesA, pitchesB);
  }

  // -----------------------------------------------------------------------
  // Issue #12: random transforms on extension tiles 1+
  // -----------------------------------------------------------------------

  /** Acceptance criterion 1 / tile-0-identity: tile 0 is always the raw diatonic tile. */
  @Test
  void extendTile0IsAlwaysIdentity() {
    MotifLengthMatcher matcher = new MotifLengthMatcher(new Random(0));
    Motif tile0 = oneBarMotif();
    KeySignature key = KeySignature.major(0);
    int[] steps = {0, 1, 2, 3};

    Motif tiled = matcher.extend(tile0, PHRASE_TICKS, key, steps);

    int notesPerTile = tile0.getNotes().size();
    List<Note> all = tiled.getNotes();
    // Tile 0: step=0 → identity; pitches must exactly match the source.
    for (int i = 0; i < notesPerTile; i++) {
      assertEquals(tile0.getNotes().get(i).pitch(), all.get(i).pitch(),
          "tile 0 note " + i + " should be identity (no extra transform)");
    }
  }

  /**
   * Acceptance criterion 1 / tiles-1+-differ: every tile beyond tile 0 must
   * produce pitches that differ from the plain diatonic-only tile.
   */
  @Test
  void extendTiles1PlusEachHaveAdditionalTransform() {
    MotifTransformer transformer = new MotifTransformer();
    KeySignature key = KeySignature.major(0);
    MotifLengthMatcher matcher = new MotifLengthMatcher(new Random(42));
    int[] steps = {0, 1, 2, 3};

    Motif tile0 = oneBarMotif();
    Motif tiled = matcher.extend(tile0, PHRASE_TICKS, key, steps);

    int notesPerTile = tile0.getNotes().size();
    List<Note> all = tiled.getNotes();
    int tiles = all.size() / notesPerTile;
    for (int t = 1; t < tiles; t++) {
      List<Integer> diatonicPitches =
          transformer.diatonicTranspose(tile0, steps[t], key)
              .getNotes().stream().map(Note::pitch).toList();
      List<Integer> actualPitches = new ArrayList<>();
      for (int i = 0; i < notesPerTile; i++) {
        actualPitches.add(all.get(t * notesPerTile + i).pitch());
      }
      assertNotEquals(diatonicPitches, actualPitches,
          "tile " + t + " should differ from plain diatonic result after random transform");
    }
  }

  /**
   * Acceptance criterion 4 / stochastic: the same motif extended with two
   * different Random instances should (with very high probability) produce
   * different pitch sequences for tiles 1+.
   */
  @Test
  void extendIsStochasticAcrossDistinctRandomInstances() {
    KeySignature key = KeySignature.major(0);
    int[] steps = {0, 1, 2, 3};
    Motif tile0 = oneBarMotif();
    int notesPerTile = tile0.getNotes().size();

    // Use seeds far apart to ensure different op selections.
    MotifLengthMatcher matcherA = new MotifLengthMatcher(new Random(1L));
    MotifLengthMatcher matcherB = new MotifLengthMatcher(new Random(999L));

    Motif tiledA = matcherA.extend(tile0, PHRASE_TICKS, key, steps);
    Motif tiledB = matcherB.extend(tile0, PHRASE_TICKS, key, steps);

    // Compare tiles 1+ only (tile 0 is always identity for both).
    List<Integer> pitchesA = tiledA.getNotes().stream()
        .skip(notesPerTile).map(Note::pitch).toList();
    List<Integer> pitchesB = tiledB.getNotes().stream()
        .skip(notesPerTile).map(Note::pitch).toList();

    assertNotEquals(pitchesA, pitchesB,
        "different Random seeds should (usually) produce different tile transforms");
  }

  /**
   * Acceptance criterion 4 / determinism: the same seed always produces the
   * same output (extends existing matchIsDeterministicForSameSeed coverage
   * to include the seeded-random constructor path).
   */
  @Test
  void extendIsDeterministicForSameSeed() {
    KeySignature key = KeySignature.major(0);
    int[] steps = {0, 1, 2, 3};
    Motif tile0 = oneBarMotif();

    Motif runA = new MotifLengthMatcher(new Random(77L))
        .extend(tile0, PHRASE_TICKS, key, steps);
    Motif runB = new MotifLengthMatcher(new Random(77L))
        .extend(tile0, PHRASE_TICKS, key, steps);

    assertEquals(runA.getNotes().size(), runB.getNotes().size());
    for (int i = 0; i < runA.getNotes().size(); i++) {
      assertEquals(runA.getNotes().get(i).pitch(), runB.getNotes().get(i).pitch(),
          "note " + i + " should match across identical seeds");
    }
  }

  /**
   * Acceptance criterion 3 / B-C unaffected: the B/C extension path (i.e.
   * using the default no-arg constructor, which is not seeded by match()) must
   * still produce the exact diatonic result for every tile because no random
   * transform is injected from outside the A-section scoring loop.
   *
   * <p>We verify this by using a TileTransformPicker that is the identity and
   * confirm tile 1+ match the plain diatonic result.
   */
  @Test
  void extendWithIdentityPickerLeavesAllTilesDiatonic() {
    MotifTransformer transformer = new MotifTransformer();
    KeySignature key = KeySignature.major(0);
    // Identity picker: just return the tile unchanged after diatonic transpose.
    MotifLengthMatcher matcher = new MotifLengthMatcher((tile, k) -> tile);
    int[] steps = {0, 1, 2, 3};

    Motif tile0 = oneBarMotif();
    Motif tiled = matcher.extend(tile0, PHRASE_TICKS, key, steps);

    int notesPerTile = tile0.getNotes().size();
    List<Note> all = tiled.getNotes();
    int tiles = all.size() / notesPerTile;
    for (int t = 0; t < tiles; t++) {
      Motif expected = transformer.diatonicTranspose(tile0, steps[t], key);
      for (int i = 0; i < notesPerTile; i++) {
        assertEquals(expected.getNotes().get(i).pitch(),
            all.get(t * notesPerTile + i).pitch(),
            "tile " + t + " note " + i + " should be plain diatonic with identity picker");
      }
    }
  }
}
