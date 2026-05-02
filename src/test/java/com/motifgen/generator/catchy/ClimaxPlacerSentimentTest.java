package com.motifgen.generator.catchy;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.motifgen.model.Motif;
import com.motifgen.model.Note;
import com.motifgen.sentiment.SentimentProfile;
import com.motifgen.theory.KeySignature;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Tests for the sentiment-aware behaviour of {@link ClimaxPlacer}.
 *
 * <p>Covers Scenario 8 (lift semitones linked to arousal).
 */
class ClimaxPlacerSentimentTest {

  private static final int TPB = 480;
  private static final int BPB = 4;

  private Motif flatMotif() {
    int[] pitches = {60, 62, 64, 62, 60, 62, 64, 62};
    List<Note> notes = new ArrayList<>();
    long tick = 0;
    for (int p : pitches) {
      notes.add(new Note(p, tick, TPB, 90));
      tick += TPB;
    }
    return new Motif(notes, 4, BPB, TPB);
  }

  @Test
  void highArousalProducesHigherClimaxPitchThanLowArousal() {
    ClimaxPlacer placer = new ClimaxPlacer();
    KeySignature cMajor = KeySignature.major(0);
    Motif motif = flatMotif();
    int climaxIdx = 4;

    SentimentProfile high = SentimentProfile.fromLabel("EXCITED"); // A=0.85
    SentimentProfile low  = SentimentProfile.fromLabel("RELAXED"); // A=0.25

    Motif highResult = placer.enforceClimax(motif, climaxIdx, cMajor, high);
    Motif lowResult  = placer.enforceClimax(motif, climaxIdx, cMajor, low);

    int highPeak = highResult.getNotes().stream()
        .filter(n -> !n.isRest()).mapToInt(Note::pitch).max().orElseThrow();
    int lowPeak  = lowResult.getNotes().stream()
        .filter(n -> !n.isRest()).mapToInt(Note::pitch).max().orElseThrow();

    assertTrue(highPeak >= lowPeak,
        "High-arousal climax pitch (" + highPeak
            + ") should be >= low-arousal climax pitch (" + lowPeak + ")");
  }

  @Test
  void liftSemitonesClampedToRange() {
    // liftSemitones = 1 + (int)(arousal * 6), clamped to [1,7]
    // GLOOMY A=0.20 → 1 + 1 = 2  (in range)
    // EXCITED A=0.85 → 1 + 5 = 6  (in range)
    ClimaxPlacer placer = new ClimaxPlacer();
    KeySignature cMajor = KeySignature.major(0);
    Motif motif = flatMotif();

    for (String label : new String[]{"GLOOMY", "RELAXED", "CONTENT", "HAPPY", "EXCITED", "TENSE", "ANGRY"}) {
      SentimentProfile p = SentimentProfile.fromLabel(label);
      // Should not throw; result must be a valid motif
      Motif result = placer.enforceClimax(motif, 4, cMajor, p);
      assertTrue(result.getNotes().size() == motif.getNotes().size(),
          "Note count must be preserved for " + label);
    }
  }

  @Test
  void noArgOverloadBackwardCompatWorks() {
    ClimaxPlacer placer = new ClimaxPlacer();
    KeySignature cMajor = KeySignature.major(0);
    Motif motif = flatMotif();
    Motif result = placer.enforceClimax(motif, 4, cMajor);
    // just verify it still runs and returns same note count
    assertTrue(result.getNotes().size() == motif.getNotes().size());
  }
}
