package com.motifgen.generator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.motifgen.guitar.PlayabilityGate;
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

  // -----------------------------------------------------------------------
  // PlayabilityGate integration
  // -----------------------------------------------------------------------

  /**
   * A permissive gate that passes everything — verifies the gate path does not
   * drop candidates when all pass.
   */
  @Test
  void generatorWithPermissiveGateReturnsSameSizeAsWithoutGate() {
    // Subclass gate to force every candidate to pass
    PlayabilityGate permissiveGate = new PlayabilityGate() {
      @Override
      public PlayabilityGate.GateResult evaluate(Sentence sentence, int ticksPerBeat) {
        PlayabilityGate.GateResult real = super.evaluate(sentence, ticksPerBeat);
        return real.passed() ? real
            : new PlayabilityGate.GateResult(true, sentence, real.avgCost(), List.of());
      }
    };

    SentenceGenerator gated   = new SentenceGenerator(99L, permissiveGate);
    SentenceGenerator ungated = new SentenceGenerator(99L, null);

    List<Sentence> gatedResult   = gated.generate(cMajor4Bars());
    List<Sentence> ungatedResult = ungated.generate(cMajor4Bars());

    assertFalse(gatedResult.isEmpty(), "Permissive gate must not drop all candidates");
    assertEquals(ungatedResult.size(), gatedResult.size(),
        "Permissive gate should keep the same number of candidates as no gate");
  }

  @Test
  void generatorWithGateAttachesPlayabilityMetadataToPassingCandidates() {
    PlayabilityGate gate = new PlayabilityGate();
    SentenceGenerator gen = new SentenceGenerator(55L, gate);
    List<Sentence> results = gen.generate(cMajor4Bars());

    assertFalse(results.isEmpty(), "Should return at least one candidate");
    // When the gate is active every returned sentence that passed should be labelled
    List<String> validLabels = List.of(
        "beginner-friendly", "intermediate", "advanced",
        "difficult but playable", "impractical");
    for (Sentence s : results) {
      String label = s.getMetadataValue(PlayabilityGate.METADATA_KEY_LABEL);
      // label is non-null only on sentences that passed the gate;
      // fallback sentences (all failed) may lack the label — just skip those
      if (label != null) {
        assertTrue(validLabels.contains(label),
            "Playability label must be one of the known values, got: " + label);
      }
    }
  }

  @Test
  void generatorWithGateFallsBackWhenAllCandidatesFail() {
    // A rejecting gate that always fails every sentence
    PlayabilityGate rejectAll = new PlayabilityGate() {
      @Override
      public PlayabilityGate.GateResult evaluate(Sentence sentence, int ticksPerBeat) {
        return new PlayabilityGate.GateResult(
            false, sentence, Double.MAX_VALUE, List.of("forced rejection"));
      }
    };

    SentenceGenerator gen = new SentenceGenerator(77L, rejectAll);
    List<Sentence> results = gen.generate(cMajor4Bars());

    // Fall-back: the full scored list is returned rather than an empty list
    assertFalse(results.isEmpty(),
        "Generator must fall back to best-scored candidate when all gate checks fail");
    assertEquals(20, results.size(),
        "Fallback should return all 20 candidates (scored list), got " + results.size());
  }

  @Test
  void generatorWithNullGateReturnsUnfilteredCandidates() {
    SentenceGenerator gen = new SentenceGenerator(13L, null);
    List<Sentence> results = gen.generate(cMajor4Bars());
    assertEquals(20, results.size(),
        "Null gate must not filter anything; expected 20 candidates");
  }

  @Test
  void everyAPhrasePreservesMotifIntervalPattern() {
    SentenceGenerator gen = new SentenceGenerator(2024L);
    Motif motif = cMajor4Bars();
    List<Sentence> candidates = gen.generate(motif);

    java.util.List<Integer> motifPitches = motif.getNotes().stream()
        .map(Note::pitch).toList();

    for (Sentence s : candidates) {
      String[] roles = s.getStructure().split(" ");
      java.util.List<Motif> phrases = s.getPhrases();
      for (int p = 0; p < roles.length; p++) {
        if (!roles[p].startsWith("a")) continue;
        java.util.List<Integer> aPitches = phrases.get(p).getNotes().stream()
            .map(Note::pitch).toList();
        for (int i = 1; i < motifPitches.size(); i++) {
          int motifInterval = motifPitches.get(i) - motifPitches.get(i - 1);
          int aInterval = aPitches.get(i) - aPitches.get(i - 1);
          assertEquals(motifInterval, aInterval,
              "A phrase " + p + " of " + s + " breaks motif interval at idx " + i);
        }
      }
    }
  }
}
