package com.motifgen.guitar.backing;

/**
 * Named groove archetypes for bass line generation.
 *
 * <p>Each archetype maps to a distinct 8-slot rhythmic pattern via
 * {@link BassRhythmPattern}.
 */
public enum BassGrooveArchetype {
  DRIVING,
  BALLAD,
  FOLK,
  FUNK,
  REGGAE,
  POWER;

  /**
   * Maps a {@link StrumPattern.Archetype} (rhythm guitar) to the closest
   * bass groove archetype.
   *
   * @param strumArchetype the rhythm-guitar strum archetype
   * @return the corresponding bass groove archetype
   */
  public static BassGrooveArchetype fromStrumArchetype(StrumPattern.Archetype strumArchetype) {
    return switch (strumArchetype) {
      case DRIVING -> DRIVING;
      case POWER -> POWER;
      case BALLAD, ARPEGGIO -> BALLAD;
      case FOLK -> FOLK;
      case FUNK -> FUNK;
      case REGGAE -> REGGAE;
    };
  }
}
