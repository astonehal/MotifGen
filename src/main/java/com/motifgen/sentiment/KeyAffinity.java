package com.motifgen.sentiment;

import com.motifgen.theory.KeySignature;

/**
 * Scores a {@link KeySignature} against a {@link SentimentProfile} so that
 * the {@code SentenceGenerator} can weight key candidates by valence.
 *
 * <p>Scoring rules (Scenario 4):
 * <ul>
 *   <li>Valence &gt;= 0.6 → major keys score higher than minor keys.</li>
 *   <li>Valence &lt;= 0.4 → minor keys score higher than major keys.</li>
 *   <li>Middle range → both score equally (base score of 1.0).</li>
 * </ul>
 *
 * <p>All scores are strictly positive so they can be used as weights.
 */
public final class KeyAffinity {

  private static final double HIGH_VALENCE_THRESHOLD = 0.6;
  private static final double LOW_VALENCE_THRESHOLD  = 0.4;
  private static final double BASE_SCORE             = 1.0;
  private static final double BONUS                  = 0.5;

  private KeyAffinity() {}

  /**
   * Returns a positive score reflecting how well the key matches the sentiment.
   *
   * @param key     key signature to evaluate
   * @param profile sentiment profile containing valence
   * @return score &gt; 0; higher means a better match
   */
  public static double sentimentScore(KeySignature key, SentimentProfile profile) {
    double v = profile.valence();
    if (v >= HIGH_VALENCE_THRESHOLD && !key.minor()) {
      return BASE_SCORE + BONUS;
    }
    if (v <= LOW_VALENCE_THRESHOLD && key.minor()) {
      return BASE_SCORE + BONUS;
    }
    return BASE_SCORE;
  }
}
