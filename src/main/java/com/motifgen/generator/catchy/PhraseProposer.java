package com.motifgen.generator.catchy;

import com.motifgen.theory.KeySignature;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Generates constrained (pitch, duration, weight) proposals for the next note
 * in a phrase. All pitches stay within ±14 semitones of the anchor and belong
 * to the supplied key. Rhythm options cover quarter, eighth and half notes
 * with weights biased toward simpler durations. Stepwise pitch moves are
 * weighted higher than leaps.
 */
public final class PhraseProposer {

  private static final int MAX_INTERVAL_SEMITONES = 14;
  private static final int[] IN_KEY_OFFSETS = {-12, -7, -5, -4, -2, -1, 0, 1, 2, 4, 5, 7, 12};

  private final Random random;

  public PhraseProposer(long seed) {
    this.random = new Random(seed);
  }

  public List<Proposal> propose(int anchorPitch, int ticksPerBeat, KeySignature key) {
    List<Proposal> proposals = new ArrayList<>();

    long quarter = ticksPerBeat;
    long eighth = Math.max(1, ticksPerBeat / 2);
    long half = ticksPerBeat * 2L;
    double[] rhythmWeights = {0.5, 0.2, 0.3}; // quarter, eighth, half
    long[] rhythmDurations = {quarter, eighth, half};

    for (int offset : IN_KEY_OFFSETS) {
      int candidate = anchorPitch + offset;
      if (Math.abs(offset) > MAX_INTERVAL_SEMITONES) continue;
      if (candidate < 0 || candidate > 127) continue;
      if (!key.containsPitchClass(((candidate % 12) + 12) % 12)) continue;

      double pitchWeight = pitchWeight(offset);
      // Small jitter from the RNG so ties between equivalent offsets resolve
      // deterministically-but-not-uniformly across seeds.
      double jitter = 1.0 + 0.05 * random.nextDouble();
      for (int i = 0; i < rhythmDurations.length; i++) {
        proposals.add(new Proposal(candidate, rhythmDurations[i],
            pitchWeight * rhythmWeights[i] * jitter));
      }
    }

    return proposals;
  }

  private static double pitchWeight(int offset) {
    int abs = Math.abs(offset);
    if (abs == 0) return 0.8;           // unison
    if (abs <= 2) return 1.0;           // stepwise — most favoured
    if (abs <= 4) return 0.55;          // third
    if (abs <= 7) return 0.3;           // fourth / fifth
    return 0.1;                         // octave or wider
  }

  /** A single proposed note: pitch, duration, and proposal weight. */
  public record Proposal(int pitch, long durationTicks, double weight) {}
}
