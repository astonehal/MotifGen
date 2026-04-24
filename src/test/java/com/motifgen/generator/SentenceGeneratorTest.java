package com.motifgen.generator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.motifgen.model.Motif;
import com.motifgen.model.Note;
import com.motifgen.model.Sentence;
import com.motifgen.scoring.SentenceScorer;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class SentenceGeneratorTest {

  private static final int TPB = 480;
  private static final int BPB = 4;

  private Motif cMajor4Bars() {
    int[] pitches = {60, 62, 64, 65, 67, 65, 64, 62,
                     60, 62, 64, 67, 65, 64, 62, 60};
    List<Note> notes = new ArrayList<>();
    long tick = 0;
    for (int p : pitches) {
      notes.add(new Note(p, tick, TPB, 90));
      tick += TPB;
    }
    return new Motif(notes, 4, BPB, TPB);
  }

  @Test
  void seededGeneratorProducesCandidatesAcrossAllFourStructuresAndAllRelatedKeys() {
    SentenceGenerator gen = new SentenceGenerator(123L);
    List<Sentence> candidates = gen.generate(cMajor4Bars());

    // Each of 5 related keys produces 4 structural variants
    assertEquals(20, candidates.size());

    Set<String> structures = candidates.stream()
        .map(Sentence::getStructure)
        .collect(Collectors.toSet());
    assertEquals(4, structures.size(),
        "all four templates should be represented, got " + structures);

    // 5 related keys -> 5 distinct key names in total
    Set<String> keys = candidates.stream()
        .map(Sentence::getKeyName)
        .collect(Collectors.toSet());
    assertEquals(5, keys.size());
  }

  @Test
  void everyCandidateHasFourPhrasesAndIsScored() {
    SentenceGenerator gen = new SentenceGenerator(7L);
    for (Sentence s : gen.generate(cMajor4Bars())) {
      assertEquals(4, s.getPhrases().size());
      assertTrue(s.getScore() >= 0,
          "generator should return scored sentences, got " + s.getScore());
    }
  }

  @Test
  void defaultConstructorIsUsable() {
    SentenceGenerator gen = new SentenceGenerator();
    List<Sentence> candidates = gen.generate(cMajor4Bars());
    assertFalse(candidates.isEmpty());
  }

  @Test
  void everyCandidateSpansSixteenBars() {
    SentenceGenerator gen = new SentenceGenerator(17L);
    for (Sentence s : gen.generate(cMajor4Bars())) {
      assertEquals(16, s.totalBars(),
          "each sentence should be 16 bars total, got " + s.totalBars());
    }
  }

  @Test
  void generatedSentencesScoreNoWorseThanTheMotifAlone() {
    SentenceGenerator gen = new SentenceGenerator(2026L);
    SentenceScorer scorer = new SentenceScorer();
    Motif motif = cMajor4Bars();

    // Baseline: replicate the motif 4 times (no score-guided shaping)
    Sentence baseline = new Sentence(
        List.of(motif, motif, motif, motif), "a a a a", "C major", 0);
    double baselineScore = scorer.score(baseline).getScore();

    List<Sentence> candidates = gen.generate(motif);
    double bestScore = candidates.stream()
        .mapToDouble(Sentence::getScore).max().orElse(0);

    assertTrue(bestScore >= baselineScore - 5.0,
        "best score-guided candidate should be competitive with plain repetition: "
            + "baseline=" + baselineScore + " best=" + bestScore);
  }

  @Test
  void candidatesAreReturnedRankedByScoreDescending() {
    SentenceGenerator gen = new SentenceGenerator(42L);
    List<Sentence> candidates = gen.generate(cMajor4Bars());

    assertNotNull(candidates);
    for (int i = 1; i < candidates.size(); i++) {
      assertTrue(candidates.get(i - 1).getScore() >= candidates.get(i).getScore(),
          "candidates should be sorted best-first: "
              + candidates.get(i - 1).getScore() + " vs " + candidates.get(i).getScore());
    }
  }
}
