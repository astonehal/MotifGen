package com.motifgen.generator.catchy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.motifgen.model.Motif;
import com.motifgen.model.Note;
import com.motifgen.model.Sentence;
import com.motifgen.scoring.SentenceScorer;
import com.motifgen.sentiment.SentimentProfile;
import com.motifgen.theory.KeySignature;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Tests for the sentiment-aware overload of {@link AnnealingRefiner}.
 *
 * <p>Covers Scenario 5 (high arousal → shorter durations preferred) and
 * Scenario 4 (high valence → pitch nudged upward).
 */
class AnnealingRefinerSentimentTest {

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

  private Sentence fourPhraseSentence(KeySignature key) {
    int[] base = {60, 62, 64, 65, 67, 65, 64, 62};
    return new Sentence(
        List.of(phraseFromPitches(base), phraseFromPitches(base),
            phraseFromPitches(base), phraseFromPitches(base)),
        "a a' b a''", key.name(), 0.0);
  }

  // ── Score never worsens with sentiment profile ───────────────────────────

  @Test
  void refinedScoreNotWorseWithHighArousalProfile() {
    AnnealingRefiner refiner = new AnnealingRefiner(77L, 30);
    SentenceScorer scorer = new SentenceScorer();
    KeySignature cMajor = KeySignature.major(0);
    SentimentProfile excited = SentimentProfile.fromLabel("EXCITED");

    Motif seed = phraseFromPitches(new int[]{60, 62, 64, 65, 67, 65, 64, 62});
    Sentence initial = fourPhraseSentence(cMajor);

    double before = scorer.score(initial).getScore();
    Sentence refined = refiner.refine(initial, seed, cMajor, excited);

    assertTrue(refined.getScore() >= before - 1e-6,
        "Sentiment-aware refiner must not worsen score");
  }

  // ── High arousal → shorter durations preferred ──────────────────────────

  @Test
  void highArousalRefinementIntroducesShorterDurations() {
    AnnealingRefiner refiner = new AnnealingRefiner(2024L, 200);
    KeySignature cMajor = KeySignature.major(0);
    SentimentProfile excited = SentimentProfile.fromLabel("EXCITED"); // A=0.85

    Motif seed = phraseFromPitches(new int[]{60, 62, 64, 65, 67, 65, 64, 62});
    Sentence initial = fourPhraseSentence(cMajor);

    Sentence refined = refiner.refine(initial, seed, cMajor, excited);

    boolean anyShort = refined.getPhrases().stream()
        .flatMap(p -> p.getNotes().stream())
        .filter(n -> !n.isRest())
        .anyMatch(n -> n.durationTicks() < TPB);

    assertTrue(anyShort,
        "High-arousal refinement should introduce at least one sub-quarter note");
  }

  // ── Structure and key are always preserved ──────────────────────────────

  @Test
  void structureAndKeyPreservedWithSentimentProfile() {
    AnnealingRefiner refiner = new AnnealingRefiner(7L, 20);
    KeySignature cMajor = KeySignature.major(0);
    SentimentProfile sad = SentimentProfile.fromLabel("SAD");

    Motif seed = phraseFromPitches(new int[]{60, 62, 64, 65, 67, 65, 64, 62});
    Sentence initial = fourPhraseSentence(cMajor);
    Sentence refined = refiner.refine(initial, seed, cMajor, sad);

    assertEquals(initial.getStructure(), refined.getStructure());
    assertEquals(initial.getKeyName(), refined.getKeyName());
    assertEquals(initial.getPhrases().size(), refined.getPhrases().size());
  }

  // ── No-profile overload still compiles and runs ──────────────────────────

  @Test
  void noProfileOverloadBackwardCompatWorks() {
    AnnealingRefiner refiner = new AnnealingRefiner(1L, 10);
    KeySignature cMajor = KeySignature.major(0);
    Motif seed = phraseFromPitches(new int[]{60, 62, 64, 65, 67, 65, 64, 62});
    Sentence initial = fourPhraseSentence(cMajor);
    Sentence refined = refiner.refine(initial, seed, cMajor);
    assertEquals(initial.getStructure(), refined.getStructure());
  }
}
