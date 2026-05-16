package com.motifgen.guitar.backing;

import com.motifgen.sentiment.SentimentProfile;

import java.util.Arrays;

/**
 * Factory for strum-pattern boolean arrays (true = strum, false = rest).
 *
 * <p>Seven built-in archetypes are provided. The {@link #forSentiment} factory
 * applies valence-driven up/down adjustments, tempo gating, voicing-type
 * filters, and POWER-mode bias.
 */
public final class StrumPattern {

  /** The seven named strum archetypes. */
  public enum Archetype {
    DRIVING,
    FOLK,
    FUNK,
    BALLAD,
    REGGAE,
    POWER,
    ARPEGGIO
  }

  // Eight-slot (one bar of eighth notes at 4/4) base patterns per archetype.
  // true = strum on that eighth-note slot, false = rest.
  private static final boolean[] PATTERN_DRIVING  = {true,  true,  true,  true,  true,  true,  true,  true};
  private static final boolean[] PATTERN_FOLK      = {true,  false, true,  false, true,  true,  false, true};
  private static final boolean[] PATTERN_FUNK      = {true,  false, false, true,  false, true,  false, false};
  private static final boolean[] PATTERN_BALLAD    = {true,  false, false, false, true,  false, false, false};
  private static final boolean[] PATTERN_REGGAE    = {false, false, true,  false, false, false, true,  false};
  private static final boolean[] PATTERN_POWER     = {true,  false, false, false, true,  false, false, false};
  private static final boolean[] PATTERN_ARPEGGIO  = {true,  false, true,  false, true,  false, true,  false};

  private static final double VALENCE_UPSTROKE_THRESHOLD = 0.6;
  private static final int    TEMPO_SIMPLIFY_THRESHOLD   = 160;

  private StrumPattern() {}

  /**
   * Returns a copy of the base pattern for the given archetype.
   *
   * @param archetype one of the seven named archetypes
   * @return copy of the eight-slot boolean array
   */
  public static boolean[] forArchetype(Archetype archetype) {
    boolean[] base = switch (archetype) {
      case DRIVING  -> PATTERN_DRIVING;
      case FOLK     -> PATTERN_FOLK;
      case FUNK     -> PATTERN_FUNK;
      case BALLAD   -> PATTERN_BALLAD;
      case REGGAE   -> PATTERN_REGGAE;
      case POWER    -> PATTERN_POWER;
      case ARPEGGIO -> PATTERN_ARPEGGIO;
    };
    return Arrays.copyOf(base, base.length);
  }

  /**
   * Chooses and modifies a strum pattern based on sentiment, voicing type, tempo,
   * and the rhythm density plan.
   *
   * <ul>
   *   <li>Archetype selected from {@link #pickArchetype(SentimentProfile, VoicingType)}</li>
   *   <li>Valence &gt; 0.6 flips some downstrokes (even slots) to upstrokes (odd slots on)</li>
   *   <li>Tempo &gt; 160 BPM + SIXTEENTH subdivision → simplified to EIGHTH (length ≤ 8)</li>
   *   <li>JAZZ voicing → remove every other strum (reduce density)</li>
   *   <li>POWER voicing → ensure beats 0 and 4 are always strummed</li>
   * </ul>
   *
   * @param profile   sentiment profile
   * @param voicing   voicing type
   * @param tempoBpm  tempo in BPM
   * @param plan      rhythm density plan from the planner
   * @return modified strum pattern array
   */
  public static boolean[] forSentiment(
      SentimentProfile profile, VoicingType voicing, int tempoBpm, RhythmDensityPlan plan) {

    Archetype archetype = pickArchetype(profile, voicing);
    boolean[] pattern = forArchetype(archetype);

    // Tempo gate: if tempo > 160 and subdivision is SIXTEENTH, simplify to EIGHTH length
    if (tempoBpm > TEMPO_SIMPLIFY_THRESHOLD && plan.subdivision() == Subdivision.SIXTEENTH) {
      pattern = toEighthLength(pattern);
    }

    // Valence adjustment: high valence flips some even-indexed downstrokes to upstrokes
    if (profile.valence() > VALENCE_UPSTROKE_THRESHOLD) {
      pattern = applyUpstrokes(pattern);
    }

    // JAZZ: remove every other active strum (mute + reduce density)
    if (voicing == VoicingType.JAZZ) {
      pattern = jazzFilter(pattern);
    }

    // POWER: ensure beats 0 and 4 are always strummed
    if (voicing == VoicingType.POWER) {
      if (pattern.length > 0) pattern[0] = true;
      if (pattern.length > 4) pattern[4] = true;
    }

    return pattern;
  }

  // -----------------------------------------------------------------------
  // Private helpers
  // -----------------------------------------------------------------------

  private static Archetype pickArchetype(SentimentProfile profile, VoicingType voicing) {
    if (voicing == VoicingType.JAZZ || voicing == VoicingType.SHELL) return Archetype.ARPEGGIO;
    if (voicing == VoicingType.POWER) return Archetype.POWER;

    double valence = profile.valence();
    double arousal = profile.arousal();

    if (arousal > 0.75) return valence > 0.5 ? Archetype.DRIVING : Archetype.FUNK;
    if (arousal > 0.5)  return valence > 0.5 ? Archetype.FOLK    : Archetype.REGGAE;
    return Archetype.BALLAD;
  }

  /** Collapses a 16-slot pattern to 8 slots by OR-ing each pair. */
  private static boolean[] toEighthLength(boolean[] src) {
    if (src.length <= 8) return Arrays.copyOf(src, src.length);
    boolean[] result = new boolean[8];
    for (int i = 0; i < 8; i++) {
      result[i] = src[i * 2] || src[i * 2 + 1];
    }
    return result;
  }

  /** Converts some downstrokes (even positions) into upstrokes (odd positions). */
  private static boolean[] applyUpstrokes(boolean[] pattern) {
    boolean[] result = Arrays.copyOf(pattern, pattern.length);
    for (int i = 0; i < result.length - 1; i += 2) {
      if (result[i]) {
        // Add an upstroke on the following odd slot
        result[i + 1] = true;
      }
    }
    return result;
  }

  /** Removes every other active strum slot (JAZZ density reduction). */
  private static boolean[] jazzFilter(boolean[] pattern) {
    boolean[] result = Arrays.copyOf(pattern, pattern.length);
    int activeCount = 0;
    for (int i = 0; i < result.length; i++) {
      if (result[i]) {
        if (activeCount % 2 == 1) {
          result[i] = false; // mute every second active strum
        }
        activeCount++;
      }
    }
    return result;
  }
}
