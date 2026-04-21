package com.motifgen.theory;

import com.motifgen.model.Motif;
import com.motifgen.model.Note;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class KeyDetectorTest {

  private static final int TPB = 480;
  private static final int BPB = 4;

  private Motif motifFromPitches(int... pitches) {
    List<Note> notes = new ArrayList<>();
    long tick = 0;
    for (int p : pitches) {
      notes.add(new Note(p, tick, TPB, 90));
      tick += TPB;
    }
    return new Motif(notes, 4, BPB, TPB);
  }

  @Test
  void detectKeyReturnsAll24KeysSortedDescendingByCorrelation() {
    Motif cMajor = motifFromPitches(60, 62, 64, 65, 67, 69, 71, 72);
    List<KeyDetector.KeyResult> results = KeyDetector.detectKey(cMajor);
    assertEquals(24, results.size());
    for (int i = 1; i < results.size(); i++) {
      assertTrue(results.get(i - 1).correlation() >= results.get(i).correlation(),
          "Results not sorted descending at index " + i);
    }
  }

  @Test
  void bestKeyForCMajorScaleIsCMajor() {
    Motif cMajor = motifFromPitches(60, 62, 64, 65, 67, 69, 71, 72);
    KeySignature best = KeyDetector.bestKey(cMajor);
    assertEquals("C major", best.name());
  }

  @Test
  void bestKeyForAMinorNaturalScaleIsAMinorOrCMajor() {
    // A natural minor: A B C D E F G A
    Motif aMinor = motifFromPitches(69, 71, 72, 74, 76, 77, 79, 81);
    KeySignature best = KeyDetector.bestKey(aMinor);
    // The Krumhansl-Schmuckler correlations between relative major/minor
    // are very close; accept either.
    assertTrue(best.name().equals("A minor") || best.name().equals("C major"),
        "Expected A minor or C major, got: " + best.name());
  }

  @Test
  void topKeysReturnsRequestedNumberAndRespectsCap() {
    Motif m = motifFromPitches(60, 64, 67);
    List<KeyDetector.KeyResult> top3 = KeyDetector.topKeys(m, 3);
    assertEquals(3, top3.size());

    List<KeyDetector.KeyResult> topHuge = KeyDetector.topKeys(m, 100);
    assertEquals(24, topHuge.size()); // capped at total key count
  }

  @Test
  void keyResultCompareToIsDescendingByCorrelation() {
    KeyDetector.KeyResult low = new KeyDetector.KeyResult(KeySignature.major(0), 0.1);
    KeyDetector.KeyResult high = new KeyDetector.KeyResult(KeySignature.major(7), 0.9);
    assertTrue(high.compareTo(low) < 0);
    assertTrue(low.compareTo(high) > 0);
    assertEquals(0, high.compareTo(new KeyDetector.KeyResult(KeySignature.major(0), 0.9)));
  }

  @Test
  void motifWithOnlyRestsReturnsResultsAllZero() {
    Motif allRests = new Motif(
        List.of(new Note(Note.REST, 0, TPB, 0),
                new Note(Note.REST, TPB, TPB, 0)),
        4, BPB, TPB);
    List<KeyDetector.KeyResult> results = KeyDetector.detectKey(allRests);
    assertEquals(24, results.size());
    // With zero distribution, all correlations are 0 (denominator is 0)
    for (KeyDetector.KeyResult r : results) {
      assertEquals(0.0, r.correlation());
    }
  }

  @Test
  void longerNotesDominatePitchClassWeighting() {
    // One very long C and many short notes in E minor -> long C pulls toward C major
    List<Note> notes = new ArrayList<>();
    notes.add(new Note(60, 0, TPB * 32, 90)); // very long C
    notes.add(new Note(76, TPB * 32, 100, 90));
    notes.add(new Note(79, TPB * 32 + 100, 100, 90));
    Motif m = new Motif(notes, 4, BPB, TPB);
    KeySignature best = KeyDetector.bestKey(m);
    assertEquals(0, best.root()); // C-rooted key (major or minor)
  }
}
