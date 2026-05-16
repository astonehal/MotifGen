package com.motifgen.guitar;

import com.motifgen.model.Note;
import com.motifgen.model.Sentence;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Post-refinement gate that rejects melodies whose average per-note transition cost
 * exceeds {@link #REJECTION_THRESHOLD}.
 *
 * <p>When a sentence is rejected, problem spots (transitions with above-average cost)
 * are recorded with human-readable descriptions. When a sentence passes, the playability
 * label is attached to the sentence's metadata.
 */
public final class PlayabilityGate {

  /** Average cost above which a melody is considered impractical and rejected. */
  public static final double REJECTION_THRESHOLD = 5.0;

  /** Metadata key used to store the playability label on a passing sentence. */
  public static final String METADATA_KEY_LABEL = "guitarPlayability";

  /**
   * Result of running the gate on a sentence.
   *
   * @param passed        true if the sentence passed (avgCost <= threshold)
   * @param sentence      the sentence (with metadata attached if passed; unmodified if rejected)
   * @param avgCost       average per-note transition cost
   * @param problemSpots  descriptions of expensive transitions (empty when passed)
   */
  public record GateResult(
      boolean passed,
      Sentence sentence,
      double avgCost,
      List<String> problemSpots) {}

  /**
   * Evaluates the sentence against the playability threshold.
   *
   * @param sentence     sentence to evaluate
   * @param ticksPerBeat ticks per quarter-note beat
   * @return a {@link GateResult} describing the outcome
   * @throws IllegalArgumentException if sentence is null or ticksPerBeat <= 0
   */
  public GateResult evaluate(Sentence sentence, int ticksPerBeat) {
    if (sentence == null) {
      throw new IllegalArgumentException("sentence must not be null");
    }
    if (ticksPerBeat <= 0) {
      throw new IllegalArgumentException("ticksPerBeat must be positive, got: " + ticksPerBeat);
    }

    List<Note> allNotes = sentence.getAllNotes();
    GuitarPlayabilityResult result = GuitarPlayabilityAnalyser.analyse(allNotes, ticksPerBeat);

    if (result instanceof GuitarPlayabilityResult.Unplayable u) {
      List<String> spots = List.of("Note at index %d is out of guitar range: %s"
          .formatted(u.noteIndex(), u.reason()));
      return new GateResult(false, sentence, Double.MAX_VALUE, spots);
    }

    GuitarPlayabilityResult.Playable playable = (GuitarPlayabilityResult.Playable) result;
    double avgCost = playable.avgCost();

    if (avgCost > REJECTION_THRESHOLD) {
      List<String> problemSpots = findProblemSpots(
          playable.fingering(),
          allNotes.stream().filter(n -> !n.isRest()).toList(),
          ticksPerBeat);
      return new GateResult(false, sentence, avgCost, problemSpots);
    }

    Sentence labelled = sentence.withMetadata(METADATA_KEY_LABEL, playable.label());
    return new GateResult(true, labelled, avgCost, Collections.emptyList());
  }

  // -----------------------------------------------------------------------
  // Private helpers
  // -----------------------------------------------------------------------

  /**
   * Identifies transitions with above-average cost and returns descriptions of each.
   *
   * @param fingering    optimal fingering for the melody
   * @param pitched      pitched-only notes in the same order as {@code fingering}
   * @param ticksPerBeat ticks per beat
   * @return list of human-readable problem-spot descriptions
   */
  private List<String> findProblemSpots(
      List<GuitarFingerPosition> fingering, List<Note> pitched, int ticksPerBeat) {
    if (fingering.size() < 2) return Collections.emptyList();

    // Compute all individual transition costs
    double[] costs = new double[fingering.size() - 1];
    double total = 0.0;
    for (int i = 1; i < fingering.size(); i++) {
      double durationBeats = (double) pitched.get(i - 1).durationTicks() / ticksPerBeat;
      costs[i - 1] = GuitarFingering.transitionCost(
          fingering.get(i - 1), fingering.get(i), durationBeats);
      total += costs[i - 1];
    }
    double avg = total / costs.length;

    List<String> spots = new ArrayList<>();
    for (int i = 0; i < costs.length; i++) {
      if (costs[i] > avg) {
        GuitarFingerPosition from = fingering.get(i);
        GuitarFingerPosition to = fingering.get(i + 1);
        spots.add(
            "Expensive transition at note %d→%d: string %d fret %d → string %d fret %d (cost %.2f)"
                .formatted(i, i + 1, from.string(), from.fret(),
                    to.string(), to.fret(), costs[i]));
      }
    }
    return Collections.unmodifiableList(spots);
  }
}
