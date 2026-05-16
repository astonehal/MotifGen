package com.motifgen.guitar;

import java.util.List;

/**
 * Sealed result type for guitar playability analysis.
 *
 * <p>Either the melody is {@link Unplayable} (a note falls outside the guitar's pitch range)
 * or it is {@link Playable} (all notes are in range, with a label, fingering, and cost).
 */
public sealed interface GuitarPlayabilityResult
    permits GuitarPlayabilityResult.Unplayable, GuitarPlayabilityResult.Playable {

  /**
   * The melody contains a note whose MIDI pitch is outside the guitar range [40, 88].
   *
   * @param noteIndex zero-based index of the first out-of-range note
   * @param reason    human-readable description of the problem
   */
  record Unplayable(int noteIndex, String reason) implements GuitarPlayabilityResult {}

  /**
   * All notes are within range; the fingering and cost have been computed.
   *
   * @param label    playability classification label
   * @param fingering optimal (string, fret) sequence — one position per non-rest note
   * @param avgCost  average per-transition cost for the optimal fingering
   */
  record Playable(
      String label,
      List<GuitarFingerPosition> fingering,
      double avgCost)
      implements GuitarPlayabilityResult {}
}
