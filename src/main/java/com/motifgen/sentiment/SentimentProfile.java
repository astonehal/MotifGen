package com.motifgen.sentiment;

import java.util.Random;

/**
 * Immutable value object capturing a sentiment as a valence/arousal pair plus
 * the closest named label from the Russell Circumplex table.
 *
 * <p>Three factory methods cover the three input scenarios:
 * <ul>
 *   <li>{@link #fromLabel(String)} — named sentiment (case-insensitive)</li>
 *   <li>{@link #fromVA(double, double)} — direct V/A values</li>
 *   <li>{@link #random(Random)} — randomly selected V/A</li>
 * </ul>
 */
public record SentimentProfile(double valence, double arousal, String closestLabel) {

  /**
   * Creates a profile from a named sentiment label (case-insensitive).
   *
   * @param label one of the eight labels in {@link SentimentVATable}
   * @return profile whose V/A match the table entry exactly
   * @throws IllegalArgumentException if the label is unknown
   */
  public static SentimentProfile fromLabel(String label) {
    double[] va = SentimentVATable.vaForLabel(label); // throws if unknown
    return new SentimentProfile(va[0], va[1], label.toUpperCase());
  }

  /**
   * Creates a profile from explicit valence and arousal values. Both are
   * clamped to [0, 1]. The closest label is derived by Euclidean distance.
   *
   * @param valence 0 (negative) – 1 (positive)
   * @param arousal 0 (calm) – 1 (energetic)
   * @return profile with the supplied V/A and auto-resolved label
   */
  public static SentimentProfile fromVA(double valence, double arousal) {
    double v = Math.max(0.0, Math.min(1.0, valence));
    double a = Math.max(0.0, Math.min(1.0, arousal));
    return new SentimentProfile(v, a, SentimentVATable.closestLabel(v, a));
  }

  /**
   * Selects V/A uniformly at random from [0, 1] × [0, 1] and resolves the
   * closest label.
   *
   * @param rng source of randomness
   * @return randomly constructed profile
   */
  public static SentimentProfile random(Random rng) {
    return fromVA(rng.nextDouble(), rng.nextDouble());
  }
}
