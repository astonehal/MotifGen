package com.motifgen.guitar.backing;

import java.util.ArrayList;
import java.util.List;

/**
 * Viterbi DP optimiser that assigns each bass note to a string/fret position
 * minimising total hand movement.
 *
 * <p>Bass tuning (standard 4-string, low to high):
 * <ul>
 *   <li>String 0 (E1) — open = MIDI 28</li>
 *   <li>String 1 (A1) — open = MIDI 33</li>
 *   <li>String 2 (D2) — open = MIDI 38</li>
 *   <li>String 3 (G2) — open = MIDI 43</li>
 * </ul>
 *
 * <p>Maximum fret: 15.
 * Transition cost = fret_shift × 1.0 + string_crossing × 0.5.
 */
public final class BassPlayabilityOptimiser {

  /** Open-string MIDI pitches for a standard 4-string bass. */
  private static final int[] OPEN = {28, 33, 38, 43};

  /** Maximum fret number allowed. */
  private static final int MAX_FRET = 15;

  private static final double FRET_SHIFT_WEIGHT   = 1.0;
  private static final double STRING_CROSS_WEIGHT = 0.5;

  private BassPlayabilityOptimiser() {}

  /**
   * Assigns optimal string/fret positions to each bass note using Viterbi DP.
   *
   * @param notes input bass notes (midi pitch already clamped)
   * @return new list of bass notes with {@code stringIdx} and {@code fret} set
   */
  public static List<BassNote> optimise(List<BassNote> notes) {
    if (notes.isEmpty()) return List.of();

    int n = notes.size();

    // For each note, enumerate valid (string, fret) positions
    List<int[][]> positions = new ArrayList<>(); // each entry: [[stringIdx, fret], ...]
    for (BassNote note : notes) {
      List<int[]> valid = new ArrayList<>();
      for (int s = 0; s < OPEN.length; s++) {
        int fret = note.midi() - OPEN[s];
        if (fret >= 0 && fret <= MAX_FRET) {
          valid.add(new int[]{s, fret});
        }
      }
      if (valid.isEmpty()) {
        // Fallback: find closest string+fret
        int bestS = 0;
        int bestFret = 0;
        int minDiff = Integer.MAX_VALUE;
        for (int s = 0; s < OPEN.length; s++) {
          int fret = note.midi() - OPEN[s];
          int clampedFret = Math.max(0, Math.min(MAX_FRET, fret));
          int diff = Math.abs(fret - clampedFret);
          if (diff < minDiff) {
            minDiff = diff;
            bestS = s;
            bestFret = clampedFret;
          }
        }
        valid.add(new int[]{bestS, bestFret});
      }
      positions.add(valid.toArray(new int[0][]));
    }

    // DP: dp[i][j] = min cost to reach position j of note i
    int maxPositions = positions.stream().mapToInt(p -> p.length).max().orElse(1);
    double[][] dp = new double[n][maxPositions];
    int[][] parent = new int[n][maxPositions]; // predecessor index

    // Init first note
    for (int j = 0; j < positions.get(0).length; j++) {
      dp[0][j] = 0.0;
      parent[0][j] = -1;
    }

    // Fill DP
    for (int i = 1; i < n; i++) {
      int[][] curPos = positions.get(i);
      int[][] prevPos = positions.get(i - 1);
      for (int j = 0; j < curPos.length; j++) {
        dp[i][j] = Double.MAX_VALUE;
        for (int k = 0; k < prevPos.length; k++) {
          double cost = dp[i - 1][k] + transitionCost(prevPos[k], curPos[j]);
          if (cost < dp[i][j]) {
            dp[i][j] = cost;
            parent[i][j] = k;
          }
        }
      }
    }

    // Traceback
    int[] chosen = new int[n];
    // Find best last position
    int lastBest = 0;
    double bestCost = dp[n - 1][0];
    for (int j = 1; j < positions.get(n - 1).length; j++) {
      if (dp[n - 1][j] < bestCost) {
        bestCost = dp[n - 1][j];
        lastBest = j;
      }
    }
    chosen[n - 1] = lastBest;
    for (int i = n - 1; i > 0; i--) {
      chosen[i - 1] = parent[i][chosen[i]];
    }

    // Build result
    List<BassNote> result = new ArrayList<>(n);
    for (int i = 0; i < n; i++) {
      BassNote orig = notes.get(i);
      int[] pos = positions.get(i)[chosen[i]];
      result.add(new BassNote(orig.midi(), orig.startTick(), orig.durationTicks(),
          orig.velocity(), pos[0], pos[1]));
    }
    return result;
  }

  private static double transitionCost(int[] from, int[] to) {
    int fretShift   = Math.abs(to[1] - from[1]);
    int stringCross = Math.abs(to[0] - from[0]);
    return fretShift * FRET_SHIFT_WEIGHT + stringCross * STRING_CROSS_WEIGHT;
  }
}
