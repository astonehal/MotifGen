package com.motifgen.generator.catchy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.motifgen.model.Motif;
import com.motifgen.model.Note;
import com.motifgen.model.Sentence;
import com.motifgen.scoring.SentenceScorer;
import com.motifgen.theory.KeySignature;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class AnnealingRefinerTest {

  private static final int TPB = 480;
  private static final int BPB = 4;

  private Motif phraseFromPitches(int[] pitches) {
    List<Note> notes = new ArrayList<>();
    long tick = 0;
    for (int p : pitches) {
      notes.add(new Note(p, tick, TPB, 90));
      tick += TPB;
    }
    return new Motif(notes, 4, BPB, TPB);
  }

  private Sentence fourPhraseSentence(int[] a, int[] b, int[] c, int[] d, KeySignature key) {
    return new Sentence(
        List.of(phraseFromPitches(a), phraseFromPitches(b),
            phraseFromPitches(c), phraseFromPitches(d)),
        "a a' b a''", key.name(), 0.0);
  }

  @Test
  void refinedScoreIsNeverWorseThanInput() {
    AnnealingRefiner refiner = new AnnealingRefiner(123L, 30);
    SentenceScorer scorer = new SentenceScorer();
    KeySignature cMajor = KeySignature.major(0);

    Motif seed = phraseFromPitches(new int[] {60, 62, 64, 65, 67, 65, 64, 62});
    Sentence initial = fourPhraseSentence(
        new int[] {60, 62, 64, 65, 67, 65, 64, 62},
        new int[] {60, 62, 64, 65, 67, 65, 64, 62},
        new int[] {48, 90, 55, 100, 40, 70, 80, 50},
        new int[] {60, 62, 64, 65, 67, 65, 64, 62},
        cMajor);

    double initialScore = scorer.score(initial).getScore();
    Sentence refined = refiner.refine(initial, seed, cMajor);

    assertTrue(refined.getScore() >= initialScore - 1e-6,
        "refinement must not worsen score: initial=" + initialScore
            + " refined=" + refined.getScore());
  }

  @Test
  void refinedSentenceHasSameStructureAndKey() {
    AnnealingRefiner refiner = new AnnealingRefiner(7L, 10);
    KeySignature cMajor = KeySignature.major(0);

    Motif seed = phraseFromPitches(new int[] {60, 62, 64, 65, 67, 65, 64, 62});
    Sentence initial = fourPhraseSentence(
        new int[] {60, 62, 64, 65, 67, 65, 64, 62},
        new int[] {62, 64, 65, 67, 69, 67, 65, 64},
        new int[] {64, 65, 67, 69, 71, 69, 67, 65},
        new int[] {60, 62, 64, 65, 67, 65, 64, 62},
        cMajor);

    Sentence refined = refiner.refine(initial, seed, cMajor);

    assertEquals(initial.getStructure(), refined.getStructure());
    assertEquals(initial.getKeyName(), refined.getKeyName());
    assertEquals(initial.getPhrases().size(), refined.getPhrases().size());
  }

  @Test
  void zeroIterationsReturnsInputEssentiallyUnchanged() {
    AnnealingRefiner refiner = new AnnealingRefiner(1L, 0);
    SentenceScorer scorer = new SentenceScorer();
    KeySignature cMajor = KeySignature.major(0);

    Motif seed = phraseFromPitches(new int[] {60, 62, 64, 65, 67, 65, 64, 62});
    Sentence initial = fourPhraseSentence(
        new int[] {60, 62, 64, 65, 67, 65, 64, 62},
        new int[] {60, 62, 64, 65, 67, 65, 64, 62},
        new int[] {62, 64, 65, 67, 69, 67, 65, 64},
        new int[] {60, 62, 64, 65, 67, 65, 64, 62},
        cMajor);

    Sentence refined = refiner.refine(initial, seed, cMajor);

    assertEquals(scorer.score(initial).getScore(), refined.getScore(), 1e-6);
  }

  @Test
  void immutablePhrasesAreNeverMutated() {
    AnnealingRefiner refiner = new AnnealingRefiner(2024L, 50);
    KeySignature cMajor = KeySignature.major(0);

    int[] aPitches = {60, 62, 64, 65, 67, 65, 64, 62};
    Motif aPhrase = phraseFromPitches(aPitches);

    Sentence initial = new Sentence(
        List.of(aPhrase,
            phraseFromPitches(new int[] {72, 74, 76, 77, 79, 77, 76, 74}),
            phraseFromPitches(new int[] {48, 50, 52, 53, 55, 53, 52, 50}),
            aPhrase),
        "a b c a", cMajor.name(), 0.0);

    Sentence refined = refiner.refine(initial, aPhrase, cMajor,
        java.util.Set.of(0, 3));

    var refinedA0 = refined.getPhrases().get(0).getNotes();
    var refinedA3 = refined.getPhrases().get(3).getNotes();
    for (int i = 0; i < aPitches.length; i++) {
      assertEquals(aPitches[i], refinedA0.get(i).pitch(),
          "immutable phrase 0 must remain identical at index " + i);
      assertEquals(aPitches[i], refinedA3.get(i).pitch(),
          "immutable phrase 3 must remain identical at index " + i);
    }
  }

  @Test
  void allPhrasesImmutableProducesAnIdenticalSentence() {
    AnnealingRefiner refiner = new AnnealingRefiner(7L, 20);
    KeySignature cMajor = KeySignature.major(0);
    Motif seed = phraseFromPitches(new int[] {60, 62, 64, 65, 67, 65, 64, 62});

    Sentence initial = fourPhraseSentence(
        new int[] {60, 62, 64, 65, 67, 65, 64, 62},
        new int[] {62, 64, 65, 67, 69, 67, 65, 64},
        new int[] {64, 65, 67, 69, 71, 69, 67, 65},
        new int[] {60, 62, 64, 65, 67, 65, 64, 62},
        cMajor);

    Sentence refined = refiner.refine(initial, seed, cMajor,
        java.util.Set.of(0, 1, 2, 3));

    for (int p = 0; p < 4; p++) {
      var origNotes = initial.getPhrases().get(p).getNotes();
      var newNotes = refined.getPhrases().get(p).getNotes();
      assertEquals(origNotes.size(), newNotes.size());
      for (int i = 0; i < origNotes.size(); i++) {
        assertEquals(origNotes.get(i).pitch(), newNotes.get(i).pitch());
      }
    }
  }

  @Test
  void rhythmMutationFavoursVarietyNotUniformity() {
    AnnealingRefiner refiner = new AnnealingRefiner(2024L, 200);
    KeySignature cMajor = KeySignature.major(0);

    Motif seed = phraseFromPitches(new int[] {60, 62, 64, 65, 67, 65, 64, 62});
    // All four phrases use uniform quarter notes; expect refinement to
    // introduce at least one off-modal duration somewhere.
    Sentence initial = fourPhraseSentence(
        new int[] {60, 62, 64, 65, 67, 65, 64, 62},
        new int[] {64, 62, 60, 62, 64, 65, 67, 65},
        new int[] {62, 60, 62, 64, 65, 67, 65, 64},
        new int[] {60, 62, 64, 65, 67, 65, 64, 62},
        cMajor);

    Sentence refined = refiner.refine(initial, seed, cMajor);

    java.util.Set<Long> distinctDurations = new java.util.HashSet<>();
    for (Motif phrase : refined.getPhrases()) {
      for (Note n : phrase.getNotes()) {
        if (!n.isRest()) distinctDurations.add(n.durationTicks());
      }
    }
    assertTrue(distinctDurations.size() >= 2,
        "Refiner should introduce rhythmic variety, got durations: "
            + distinctDurations);
  }
}
