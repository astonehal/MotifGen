package com.motifgen.sentiment;

/**
 * Russell Circumplex eight-entry valence/arousal lookup table.
 *
 * <p>Each entry is stored as {@code {valence, arousal}}. The table is the
 * single source of truth consumed by {@link SentimentProfile}.
 */
public final class SentimentVATable {

  /** Labels in the same order as {@link #VA}. */
  static final String[] LABELS = {
      "HAPPY", "EXCITED", "RELAXED", "CONTENT", "SAD", "GLOOMY", "TENSE", "ANGRY"
  };

  /** Valence/arousal coordinates for each label (same order as {@link #LABELS}). */
  static final double[][] VA = {
      {0.75, 0.70}, // HAPPY
      {0.65, 0.85}, // EXCITED
      {0.70, 0.25}, // RELAXED
      {0.65, 0.40}, // CONTENT
      {0.20, 0.30}, // SAD
      {0.20, 0.20}, // GLOOMY
      {0.25, 0.75}, // TENSE
      {0.15, 0.80}, // ANGRY
  };

  private SentimentVATable() {}

  /**
   * Returns the label whose V/A coordinates are closest to {@code (v, a)} by
   * Euclidean distance.
   *
   * @param v valence (0–1)
   * @param a arousal (0–1)
   * @return the closest label name in UPPER_CASE
   */
  public static String closestLabel(double v, double a) {
    String best = LABELS[0];
    double bestDist = Double.MAX_VALUE;
    for (int i = 0; i < LABELS.length; i++) {
      double dv = VA[i][0] - v;
      double da = VA[i][1] - a;
      double dist = Math.sqrt(dv * dv + da * da);
      if (dist < bestDist) {
        bestDist = dist;
        best = LABELS[i];
      }
    }
    return best;
  }

  /**
   * Returns {@code {valence, arousal}} for the named label (case-insensitive).
   *
   * @param label one of the eight sentiment names
   * @return two-element array {@code [valence, arousal]}
   * @throws IllegalArgumentException if the label is not in the table
   */
  public static double[] vaForLabel(String label) {
    String upper = label.toUpperCase();
    for (int i = 0; i < LABELS.length; i++) {
      if (LABELS[i].equals(upper)) {
        return new double[]{VA[i][0], VA[i][1]};
      }
    }
    throw new IllegalArgumentException("Unknown sentiment label: " + label);
  }
}
