package com.motifgen.guitar;

import com.motifgen.model.Note;
import java.util.List;

/**
 * Public API for guitar playability analysis.
 *
 * <p>The analysis pipeline is:
 * <ol>
 *   <li>Range check — any note outside MIDI [40, 88] produces {@link GuitarPlayabilityResult.Unplayable}.</li>
 *   <li>Viterbi fingering — {@link GuitarFingering#compute} finds the optimal (string, fret) sequence.</li>
 *   <li>Label classification — average cost maps to a human-readable playability label.</li>
 * </ol>
 */
public final class GuitarPlayabilityAnalyser {

  /** Lowest MIDI pitch playable on a standard guitar in standard tuning (low E2 = 40). */
  public static final int MIN_PITCH = 40;

  /** Highest MIDI pitch playable on a standard guitar (fret 22 of string 1, E4+22 = 86...
   * but with all strings: high e string 1 fret 22 = 64+22 = 86; however the spec says 88).
   * Using 88 per spec. */
  public static final int MAX_PITCH = 88;

  // Playability label thresholds
  private static final double THRESHOLD_BEGINNER = 1.0;
  private static final double THRESHOLD_INTERMEDIATE = 2.0;
  private static final double THRESHOLD_ADVANCED = 3.5;
  private static final double THRESHOLD_DIFFICULT = 5.0;

  // Label strings
  /** Label for average cost <= 1.0. */
  public static final String LABEL_BEGINNER = "beginner-friendly";
  /** Label for average cost <= 2.0. */
  public static final String LABEL_INTERMEDIATE = "intermediate";
  /** Label for average cost <= 3.5. */
  public static final String LABEL_ADVANCED = "advanced";
  /** Label for average cost <= 5.0. */
  public static final String LABEL_DIFFICULT = "difficult but playable";
  /** Label for average cost > 5.0. */
  public static final String LABEL_IMPRACTICAL = "impractical";

  private GuitarPlayabilityAnalyser() {}

  /**
   * Analyses a list of notes for guitar playability.
   *
   * @param notes        melody to analyse (may include rests; rests are ignored for range checks)
   * @param ticksPerBeat ticks per quarter-note beat (used for duration-based cost penalties)
   * @return {@link GuitarPlayabilityResult.Unplayable} if any note is out of range,
   *         otherwise {@link GuitarPlayabilityResult.Playable} with label and fingering
   * @throws IllegalArgumentException if {@code notes} is null or {@code ticksPerBeat} <= 0
   */
  public static GuitarPlayabilityResult analyse(List<Note> notes, int ticksPerBeat) {
    if (notes == null) {
      throw new IllegalArgumentException("notes must not be null");
    }
    if (ticksPerBeat <= 0) {
      throw new IllegalArgumentException("ticksPerBeat must be positive, got: " + ticksPerBeat);
    }

    // Range check
    for (int i = 0; i < notes.size(); i++) {
      Note note = notes.get(i);
      if (note.isRest()) continue;
      if (note.pitch() < MIN_PITCH || note.pitch() > MAX_PITCH) {
        String reason = "Note at index %d has pitch %d which is outside guitar range [%d, %d]"
            .formatted(i, note.pitch(), MIN_PITCH, MAX_PITCH);
        return new GuitarPlayabilityResult.Unplayable(i, reason);
      }
    }

    // Compute fingering
    List<Note> pitched = notes.stream().filter(n -> !n.isRest()).toList();
    List<GuitarFingerPosition> fingering = GuitarFingering.compute(notes, ticksPerBeat);
    double avgCost = GuitarFingering.averageCost(fingering, pitched, ticksPerBeat);

    String label = labelFor(avgCost);
    return new GuitarPlayabilityResult.Playable(label, fingering, avgCost);
  }

  /**
   * Maps an average cost value to the corresponding playability label.
   *
   * @param avgCost average per-transition cost
   * @return human-readable playability label
   */
  public static String labelFor(double avgCost) {
    if (avgCost <= THRESHOLD_BEGINNER) return LABEL_BEGINNER;
    if (avgCost <= THRESHOLD_INTERMEDIATE) return LABEL_INTERMEDIATE;
    if (avgCost <= THRESHOLD_ADVANCED) return LABEL_ADVANCED;
    if (avgCost <= THRESHOLD_DIFFICULT) return LABEL_DIFFICULT;
    return LABEL_IMPRACTICAL;
  }
}
