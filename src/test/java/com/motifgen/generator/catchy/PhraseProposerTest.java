package com.motifgen.generator.catchy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.motifgen.theory.KeySignature;
import java.util.List;
import org.junit.jupiter.api.Test;

class PhraseProposerTest {

  private static final int TPB = 480;

  @Test
  void proposalsAllLieWithinMaxRangeOfPriorPitch() {
    PhraseProposer proposer = new PhraseProposer(42L);
    KeySignature cMajor = KeySignature.major(0);

    List<PhraseProposer.Proposal> proposals = proposer.propose(60, TPB, cMajor);

    assertFalse(proposals.isEmpty());
    for (PhraseProposer.Proposal p : proposals) {
      int interval = Math.abs(p.pitch() - 60);
      assertTrue(interval <= 14,
          "proposal pitch " + p.pitch() + " exceeds ±14 semitones from 60");
    }
  }

  @Test
  void allProposedPitchesBelongToKey() {
    PhraseProposer proposer = new PhraseProposer(42L);
    KeySignature gMajor = KeySignature.major(7);

    List<PhraseProposer.Proposal> proposals = proposer.propose(67, TPB, gMajor);

    for (PhraseProposer.Proposal p : proposals) {
      assertTrue(gMajor.containsPitchClass(p.pitch() % 12),
          "pitch " + p.pitch() + " is out of key " + gMajor.name());
    }
  }

  @Test
  void proposalsIncludeMultipleRhythmOptions() {
    PhraseProposer proposer = new PhraseProposer(42L);
    KeySignature cMajor = KeySignature.major(0);

    List<PhraseProposer.Proposal> proposals = proposer.propose(60, TPB, cMajor);

    long distinctDurations = proposals.stream()
        .map(PhraseProposer.Proposal::durationTicks)
        .distinct()
        .count();

    assertTrue(distinctDurations >= 2,
        "expected at least two distinct rhythm options, got " + distinctDurations);
  }

  @Test
  void proposalWeightsArePositiveAndSumToMoreThanZero() {
    PhraseProposer proposer = new PhraseProposer(42L);
    KeySignature cMajor = KeySignature.major(0);

    List<PhraseProposer.Proposal> proposals = proposer.propose(60, TPB, cMajor);

    double sum = 0;
    for (PhraseProposer.Proposal p : proposals) {
      assertTrue(p.weight() > 0, "weight must be positive: " + p);
      sum += p.weight();
    }
    assertTrue(sum > 0);
  }

  @Test
  void stepwiseProposalsWeightedHigherThanLeaps() {
    PhraseProposer proposer = new PhraseProposer(42L);
    KeySignature cMajor = KeySignature.major(0);

    List<PhraseProposer.Proposal> proposals = proposer.propose(60, TPB, cMajor);

    double stepwiseWeight = 0;
    double leapWeight = 0;
    for (PhraseProposer.Proposal p : proposals) {
      int interval = Math.abs(p.pitch() - 60);
      if (interval <= 2) {
        stepwiseWeight += p.weight();
      } else if (interval >= 5) {
        leapWeight += p.weight();
      }
    }
    assertTrue(stepwiseWeight > leapWeight,
        "stepwise moves should be favoured over leaps: step=" + stepwiseWeight
            + " leap=" + leapWeight);
  }

  @Test
  void determinismFromSeed() {
    PhraseProposer a = new PhraseProposer(99L);
    PhraseProposer b = new PhraseProposer(99L);
    KeySignature key = KeySignature.major(0);

    List<PhraseProposer.Proposal> pa = a.propose(60, TPB, key);
    List<PhraseProposer.Proposal> pb = b.propose(60, TPB, key);

    assertEquals(pa, pb);
  }
}
