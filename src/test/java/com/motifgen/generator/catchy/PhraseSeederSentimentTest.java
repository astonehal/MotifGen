package com.motifgen.generator.catchy;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.motifgen.model.Motif;
import com.motifgen.model.Note;
import com.motifgen.sentiment.SentimentProfile;
import com.motifgen.theory.KeySignature;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Tests for the sentiment-aware overload of {@link PhraseSeeder}.
 *
 * <p>Covers Scenario 5 (variation strength linked to arousal) and
 * Scenario 6 (rhythmic density).
 */
class PhraseSeederSentimentTest {

  private static final int TPB = 480;
  private static final int BPB = 4;

  private Motif cMajorMotif() {
    int[] pitches = {60, 62, 64, 65, 67, 65, 64, 62};
    List<Note> notes = new ArrayList<>();
    long tick = 0;
    for (int p : pitches) {
      notes.add(new Note(p, tick, TPB, 90));
      tick += TPB;
    }
    return new Motif(notes, 4, BPB, TPB);
  }

  private List<Integer> pitches(Motif m) {
    return m.getNotes().stream().filter(n -> !n.isRest()).map(Note::pitch).toList();
  }

  // ── Scenario 5: variation strength ──────────────────────────────────────

  @Test
  void highArousalBPhraseVariesMoreThanLowArousal() {
    Motif motif = cMajorMotif();
    KeySignature key = KeySignature.major(0);

    SentimentProfile high = SentimentProfile.fromLabel("EXCITED"); // A=0.85
    SentimentProfile low  = SentimentProfile.fromLabel("RELAXED"); // A=0.25

    // Use same seed so the only variable is the profile
    PhraseSeeder seederHigh = new PhraseSeeder(42L, high);
    PhraseSeeder seederLow  = new PhraseSeeder(42L, low);

    Motif highPhrase = seederHigh.seed('B', false, motif, key).phrase();
    Motif lowPhrase  = seederLow.seed('B', false, motif, key).phrase();

    List<Integer> origPitches  = pitches(motif);
    List<Integer> highPitches  = pitches(highPhrase);
    List<Integer> lowPitches   = pitches(lowPhrase);

    long highDiff = countDifferences(origPitches, highPitches);
    long lowDiff  = countDifferences(origPitches, lowPitches);

    assertTrue(highDiff >= lowDiff,
        "High-arousal phrase should diverge from original >= low-arousal; "
            + "highDiff=" + highDiff + " lowDiff=" + lowDiff);
  }

  @Test
  void noProfileOverloadBackwardCompatWorks() {
    PhraseSeeder seeder = new PhraseSeeder(0L);
    Motif motif = cMajorMotif();
    PhraseSeeder.SeededPhrase seeded = seeder.seed('B', false, motif, KeySignature.major(0));
    assertFalse(seeded.immutable());
    assertFalse(pitches(seeded.phrase()).isEmpty());
  }

  // ── Scenario 6: high arousal invokes SyncopationApplier ─────────────────

  @Test
  void highArousalSeedingAppliesSyncopationSoStartTicksMayDiffer() {
    Motif motif = cMajorMotif();
    KeySignature key = KeySignature.major(0);

    SentimentProfile high = SentimentProfile.fromLabel("EXCITED"); // A=0.85

    // Run many seeds; at least one should produce a phrase with a shifted start tick
    boolean anyShifted = false;
    for (long s = 0; s < 15; s++) {
      PhraseSeeder seeder = new PhraseSeeder(s, high);
      Motif phrase = seeder.seed('B', false, motif, key).phrase();
      List<Long> origStarts = motif.getNotes().stream().map(Note::startTick).toList();
      List<Long> newStarts  = phrase.getNotes().stream().map(Note::startTick).toList();
      for (int i = 0; i < Math.min(origStarts.size(), newStarts.size()); i++) {
        if (!origStarts.get(i).equals(newStarts.get(i))) {
          anyShifted = true;
          break;
        }
      }
      if (anyShifted) break;
    }
    assertTrue(anyShifted,
        "High-arousal seeding should produce at least one syncopated (shifted) start tick");
  }

  private static long countDifferences(List<Integer> a, List<Integer> b) {
    long count = 0;
    int len = Math.min(a.size(), b.size());
    for (int i = 0; i < len; i++) {
      if (!a.get(i).equals(b.get(i))) count++;
    }
    return count;
  }
}
