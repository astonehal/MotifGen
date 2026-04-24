package com.motifgen.generator.catchy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.motifgen.model.Note;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class IncrementalScorerTest {

  private static final int TPB = 480;

  private List<Note> notes(int[] pitches) {
    List<Note> out = new ArrayList<>();
    long tick = 0;
    for (int p : pitches) {
      out.add(new Note(p, tick, TPB, 90));
      tick += TPB;
    }
    return out;
  }

  @Test
  void emptyPartialScoresZero() {
    IncrementalScorer scorer = new IncrementalScorer();
    assertEquals(0.0, scorer.scorePartial(List.of()));
  }

  @Test
  void scoreIsInUnitInterval() {
    IncrementalScorer scorer = new IncrementalScorer();
    double score = scorer.scorePartial(notes(new int[] {60, 62, 64, 65, 67, 65, 64, 62}));
    assertTrue(score >= 0.0 && score <= 1.0,
        "partial score must be in [0,1], got " + score);
  }

  @Test
  void compactSingableMelodyScoresHigherThanSparseRandomOne() {
    IncrementalScorer scorer = new IncrementalScorer();

    double compact = scorer.scorePartial(notes(new int[] {60, 62, 64, 62, 60, 62, 64, 62}));
    double jagged = scorer.scorePartial(notes(new int[] {60, 80, 48, 90, 50, 85, 55, 100}));

    assertTrue(compact > jagged,
        "compact singable melody should score higher than jagged one: compact="
            + compact + " jagged=" + jagged);
  }

  @Test
  void repeatedHookRaisesScoreOverAllDistinctPitches() {
    IncrementalScorer scorer = new IncrementalScorer();

    double withHook = scorer.scorePartial(notes(new int[] {60, 62, 64, 60, 62, 64, 60, 62}));
    double allDistinct = scorer.scorePartial(notes(new int[] {60, 61, 62, 63, 64, 65, 66, 67}));

    assertTrue(withHook > allDistinct,
        "recurring 3-note hook should score higher than all-distinct: withHook="
            + withHook + " allDistinct=" + allDistinct);
  }
}
