package com.motifgen.guitar.backing;

import com.motifgen.model.Sentence;
import com.motifgen.sentiment.SentimentProfile;

import java.util.List;

/**
 * Maps a {@link SentimentProfile} + section name to a {@link RhythmDensityPlan}.
 *
 * <h3>Arousal → subdivision</h3>
 * <pre>
 *   0.000 – 0.250  → WHOLE
 *   0.250 – 0.500  → HALF
 *   0.500 – 0.750  → QUARTER
 *   0.750 – 0.875  → EIGHTH
 *   0.875 – 1.000  → SIXTEENTH
 * </pre>
 *
 * <h3>Section multipliers</h3>
 * <pre>
 *   A      → 1.0  (baseline)
 *   B      → 1.3  (denser)
 *   INTRO  → 0.7  (sparser)
 *   OUTRO  → 0.6  (sparsest)
 * </pre>
 *
 * <h3>Melody-density complementarity</h3>
 * If melody density (non-rest notes / total bars) &gt; 0.7 and the chosen
 * subdivision is EIGHTH or finer, the planner steps it down one level.
 */
public final class RhythmDensityPlanner {

  private static final double THRESHOLD_HALF       = 0.25;
  private static final double THRESHOLD_QUARTER     = 0.50;
  private static final double THRESHOLD_EIGHTH      = 0.75;
  private static final double THRESHOLD_SIXTEENTH   = 0.875;

  private static final double MULTIPLIER_B     = 1.3;
  private static final double MULTIPLIER_A     = 1.0;
  private static final double MULTIPLIER_INTRO = 0.7;
  private static final double MULTIPLIER_OUTRO = 0.6;

  private static final double MELODY_DENSITY_THRESHOLD = 0.7;

  private RhythmDensityPlanner() {}

  /**
   * Produces a {@link RhythmDensityPlan} for the given inputs.
   *
   * @param profile  sentiment profile supplying arousal
   * @param section  section label (A, B, INTRO, OUTRO; anything else treated as A)
   * @param sentence the melody sentence (used to compute melody density)
   * @return computed plan
   */
  public static RhythmDensityPlan plan(
      SentimentProfile profile, String section, Sentence sentence) {

    double arousal = profile.arousal();
    double multiplier = sectionMultiplier(section);

    // Effective arousal adjusted by section multiplier (clamped to [0,1])
    double effectiveArousal = Math.min(1.0, Math.max(0.0, arousal * multiplier));

    Subdivision subdivision = arousalToSubdivision(effectiveArousal);

    // Melody-density complementarity
    double melodyDensity = computeMelodyDensity(sentence);
    if (melodyDensity > MELODY_DENSITY_THRESHOLD
        && subdivision.ordinal() >= Subdivision.EIGHTH.ordinal()) {
      subdivision = subdivision.stepDown();
    }

    int changesPerBar = changesPerBar(subdivision);
    List<Integer> accents = accentBeats(subdivision);

    return new RhythmDensityPlan(subdivision, changesPerBar, accents);
  }

  // -----------------------------------------------------------------------
  // Private helpers
  // -----------------------------------------------------------------------

  private static Subdivision arousalToSubdivision(double arousal) {
    if (arousal < THRESHOLD_HALF)     return Subdivision.WHOLE;
    if (arousal < THRESHOLD_QUARTER)  return Subdivision.HALF;
    if (arousal < THRESHOLD_EIGHTH)   return Subdivision.QUARTER;
    if (arousal < THRESHOLD_SIXTEENTH) return Subdivision.EIGHTH;
    return Subdivision.SIXTEENTH;
  }

  private static double sectionMultiplier(String section) {
    if (section == null) return MULTIPLIER_A;
    return switch (section.toUpperCase()) {
      case "B"     -> MULTIPLIER_B;
      case "INTRO" -> MULTIPLIER_INTRO;
      case "OUTRO" -> MULTIPLIER_OUTRO;
      default      -> MULTIPLIER_A;
    };
  }

  private static double computeMelodyDensity(Sentence sentence) {
    int totalBars = Math.max(1, sentence.totalBars());
    long noteCount = sentence.getAllNotes().stream()
        .filter(n -> !n.isRest())
        .count();
    return (double) noteCount / totalBars;
  }

  private static int changesPerBar(Subdivision subdivision) {
    return switch (subdivision) {
      case WHOLE     -> 1;
      case HALF      -> 1;
      case QUARTER   -> 2;
      case EIGHTH    -> 4;
      case SIXTEENTH -> 4;
    };
  }

  private static List<Integer> accentBeats(Subdivision subdivision) {
    return switch (subdivision) {
      case WHOLE     -> List.of(0);
      case HALF      -> List.of(0, 2);
      case QUARTER   -> List.of(0, 2);
      case EIGHTH    -> List.of(0, 2, 4, 6);
      case SIXTEENTH -> List.of(0, 4, 8, 12);
    };
  }
}
