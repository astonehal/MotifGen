package com.motifgen.guitar;

import com.motifgen.model.Note;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Computes the optimal guitar fingering for a sequence of notes using Viterbi
 * dynamic programming.
 *
 * <p>For each note the algorithm enumerates all (string, fret) pairs where
 * {@code openTuning[string] + fret == pitch} and {@code fret in [0, 22]}, then
 * selects the sequence that minimises total transition cost.
 *
 * <p>Standard tuning MIDI open-string pitches (string index 0 = string 1 = high e):
 * <pre>
 *   string 1 (index 0): E4  = 64
 *   string 2 (index 1): B3  = 59
 *   string 3 (index 2): G3  = 55
 *   string 4 (index 3): D3  = 50
 *   string 5 (index 4): A2  = 45
 *   string 6 (index 5): E2  = 40
 * </pre>
 */
public final class GuitarFingering {

  /** MIDI pitches of open strings indexed by string number minus one (index 0 = string 1). */
  static final int[] OPEN_TUNING = {64, 59, 55, 50, 45, 40};

  /** Maximum fret reachable on a standard guitar. */
  private static final int MAX_FRET = GuitarFingerPosition.MAX_FRET;

  /** Cost applied per unit of fret distance when distance is within easy reach (<=3). */
  private static final double COST_PER_FRET_EASY = 0.4;

  /** Base cost when fret distance is in the moderate range (4–5). */
  private static final double COST_MODERATE_BASE = 2.0;

  /** Additional cost per fret above 3 in the moderate range. */
  private static final double COST_PER_FRET_MODERATE = 1.0;

  /** Base cost when fret distance is large (>5). */
  private static final double COST_LARGE_BASE = 4.0;

  /** Additional cost per fret above 5 in the large range. */
  private static final double COST_PER_FRET_LARGE = 1.5;

  /** Cost per string change. */
  private static final double COST_PER_STRING = 0.3;

  /** Bonus (negative cost) for landing on an open string. */
  private static final double OPEN_STRING_BONUS = -0.5;

  /** Cost per fret above 12 (cramping penalty). */
  private static final double CRAMPING_COST_PER_FRET = 0.3;

  /** Fret threshold above which cramping is penalised. */
  private static final int CRAMPING_THRESHOLD = 12;

  /** Additional cost when a large fret shift must be executed within a short note. */
  private static final double TIME_PRESSURE_COST = 1.0;

  /** Fret distance threshold that triggers a time-pressure check. */
  private static final int TIME_PRESSURE_FRET_THRESHOLD = 3;

  /** Duration in beats below which a large fret shift incurs a time-pressure penalty. */
  private static final double TIME_PRESSURE_BEAT_THRESHOLD = 0.25;

  /** Sentinel cost used to mark unreachable Viterbi states. */
  private static final double INF = Double.MAX_VALUE / 2;

  private GuitarFingering() {}

  /**
   * Computes the minimum-cost fingering for the provided sequence of notes.
   *
   * <p>Rest notes ({@link Note#isRest()}) are skipped; only pitched notes appear
   * in the returned list.
   *
   * @param notes         sequence of notes (may include rests)
   * @param ticksPerBeat  ticks per quarter-note beat (used to convert durations to beats)
   * @return optimal (string, fret) sequence — one entry per non-rest note in input order
   */
  public static List<GuitarFingerPosition> compute(List<Note> notes, int ticksPerBeat) {
    if (notes == null || notes.isEmpty()) {
      return Collections.emptyList();
    }

    List<Note> pitched = notes.stream().filter(n -> !n.isRest()).toList();
    if (pitched.isEmpty()) {
      return Collections.emptyList();
    }

    // candidates[i] = list of (string, fret) options for pitched note i
    List<List<GuitarFingerPosition>> candidates = buildCandidates(pitched);

    int n = pitched.size();
    int[] sizes = new int[n];
    for (int i = 0; i < n; i++) {
      sizes[i] = candidates.get(i).size();
    }

    // dp[i][j] = min cost to reach candidate j of note i
    double[][] dp = new double[n][];
    int[][] parent = new int[n][];
    for (int i = 0; i < n; i++) {
      dp[i] = new double[sizes[i]];
      parent[i] = new int[sizes[i]];
      Arrays.fill(dp[i], INF);
      Arrays.fill(parent[i], -1);
    }

    // Initialise first note with zero cost
    Arrays.fill(dp[0], 0.0);

    // Fill DP
    for (int i = 1; i < n; i++) {
      double durationBeats =
          (double) pitched.get(i - 1).durationTicks() / ticksPerBeat;
      List<GuitarFingerPosition> prevCands = candidates.get(i - 1);
      List<GuitarFingerPosition> currCands = candidates.get(i);

      for (int j = 0; j < currCands.size(); j++) {
        GuitarFingerPosition curr = currCands.get(j);
        for (int k = 0; k < prevCands.size(); k++) {
          if (dp[i - 1][k] >= INF) continue;
          double cost = dp[i - 1][k]
              + transitionCost(prevCands.get(k), curr, durationBeats);
          if (cost < dp[i][j]) {
            dp[i][j] = cost;
            parent[i][j] = k;
          }
        }
      }
    }

    // Back-track from minimum-cost final state
    int bestFinal = 0;
    for (int j = 1; j < sizes[n - 1]; j++) {
      if (dp[n - 1][j] < dp[n - 1][bestFinal]) {
        bestFinal = j;
      }
    }

    int[] path = new int[n];
    path[n - 1] = bestFinal;
    for (int i = n - 1; i > 0; i--) {
      path[i - 1] = parent[i][path[i]];
    }

    List<GuitarFingerPosition> result = new ArrayList<>(n);
    for (int i = 0; i < n; i++) {
      result.add(candidates.get(i).get(path[i]));
    }
    return Collections.unmodifiableList(result);
  }

  /**
   * Computes the transition cost between two consecutive finger positions.
   *
   * @param prev          previous finger position
   * @param next          next finger position
   * @param durationBeats duration of the previous note in beats
   * @return non-negative transition cost (may be reduced by open-string bonus)
   */
  public static double transitionCost(
      GuitarFingerPosition prev, GuitarFingerPosition next, double durationBeats) {
    double cost = 0.0;

    int fretDist = Math.abs(next.fret() - prev.fret());
    if (fretDist <= TIME_PRESSURE_FRET_THRESHOLD) {
      cost += fretDist * COST_PER_FRET_EASY;
    } else if (fretDist <= 5) {
      cost += COST_MODERATE_BASE + (fretDist - TIME_PRESSURE_FRET_THRESHOLD) * COST_PER_FRET_MODERATE;
    } else {
      cost += COST_LARGE_BASE + (fretDist - 5) * COST_PER_FRET_LARGE;
    }

    int stringDist = Math.abs(next.string() - prev.string());
    cost += stringDist * COST_PER_STRING;

    if (next.fret() == 0) {
      cost += OPEN_STRING_BONUS;
    }

    if (next.fret() > CRAMPING_THRESHOLD) {
      cost += (next.fret() - CRAMPING_THRESHOLD) * CRAMPING_COST_PER_FRET;
    }

    if (fretDist > TIME_PRESSURE_FRET_THRESHOLD && durationBeats < TIME_PRESSURE_BEAT_THRESHOLD) {
      cost += TIME_PRESSURE_COST;
    }

    return cost;
  }

  /**
   * Computes the average per-note cost for the given fingering sequence.
   *
   * <p>The cost of the first note is always 0 (no prior position). The returned
   * average is the total cost divided by the number of transitions.
   *
   * @param fingering    optimal fingering produced by {@link #compute}
   * @param notes        pitched-only notes in the same order as {@code fingering}
   * @param ticksPerBeat ticks per beat
   * @return average transition cost, or 0 if fewer than two notes
   */
  public static double averageCost(
      List<GuitarFingerPosition> fingering, List<Note> notes, int ticksPerBeat) {
    if (fingering.size() < 2) return 0.0;
    double total = 0.0;
    for (int i = 1; i < fingering.size(); i++) {
      double durationBeats = (double) notes.get(i - 1).durationTicks() / ticksPerBeat;
      total += transitionCost(fingering.get(i - 1), fingering.get(i), durationBeats);
    }
    return total / (fingering.size() - 1);
  }

  // -----------------------------------------------------------------------
  // Private helpers
  // -----------------------------------------------------------------------

  /** Builds the list of candidate (string, fret) positions for each pitched note. */
  private static List<List<GuitarFingerPosition>> buildCandidates(List<Note> pitched) {
    List<List<GuitarFingerPosition>> result = new ArrayList<>(pitched.size());
    for (Note note : pitched) {
      List<GuitarFingerPosition> cands = new ArrayList<>();
      for (int s = 0; s < OPEN_TUNING.length; s++) {
        int fret = note.pitch() - OPEN_TUNING[s];
        if (fret >= 0 && fret <= MAX_FRET) {
          cands.add(new GuitarFingerPosition(s + 1, fret));
        }
      }
      // Fallback: if no candidate (pitch out of range was already caught upstream),
      // use fret 0 string 1 as a placeholder so DP doesn't crash.
      if (cands.isEmpty()) {
        cands.add(new GuitarFingerPosition(1, 0));
      }
      result.add(cands);
    }
    return result;
  }
}
