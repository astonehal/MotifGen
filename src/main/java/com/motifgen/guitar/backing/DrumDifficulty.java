package com.motifgen.guitar.backing;

/**
 * Difficulty levels for a drum track, ordered from easiest to hardest.
 *
 * <p>Score ranges are defined over [0.0, 1.0] and are contiguous with no gaps.
 */
public enum DrumDifficulty {
  BEGINNER(0.0, 0.35),
  INTERMEDIATE(0.35, 0.60),
  ADVANCED(0.60, 0.80),
  EXPERT(0.80, 1.0);

  private final double minScore;
  private final double maxScore;

  DrumDifficulty(double minScore, double maxScore) {
    this.minScore = minScore;
    this.maxScore = maxScore;
  }

  /** Lower bound (inclusive) of this level's composite score range. */
  public double minScore() {
    return minScore;
  }

  /** Upper bound (exclusive, except EXPERT which is inclusive at 1.0) of the range. */
  public double maxScore() {
    return maxScore;
  }

  /**
   * Returns the {@link DrumDifficulty} level that contains {@code score}.
   *
   * @param score composite difficulty score in [0.0, 1.0]
   * @return matching level; {@code EXPERT} if score equals 1.0 exactly
   */
  public static DrumDifficulty fromScore(double score) {
    for (DrumDifficulty level : values()) {
      if (score < level.maxScore) {
        return level;
      }
    }
    return EXPERT;
  }

  /**
   * Composite difficulty score and level returned by {@link DrumDifficultyScorer}.
   *
   * @param numericScore composite score in [0.0, 1.0]
   * @param level        corresponding difficulty level
   * @param independenceScore  sub-score: limb-independence demand (weight 35 %)
   * @param densityScore       sub-score: subdivision density (weight 25 %)
   * @param kickScore          sub-score: kick complexity (weight 20 %)
   * @param tempoPenalty       sub-score: tempo penalty (weight 20 %)
   */
  public record DifficultyScore(
      double numericScore,
      DrumDifficulty level,
      double independenceScore,
      double densityScore,
      double kickScore,
      double tempoPenalty) {}
}
