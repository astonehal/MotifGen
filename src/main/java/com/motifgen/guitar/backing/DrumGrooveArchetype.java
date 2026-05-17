package com.motifgen.guitar.backing;

/**
 * Named groove archetypes for drum track generation.
 *
 * <p>Each archetype maps to a 16-slot sixteenth-note kick/snare/cymbal grid
 * via {@link DrumPattern}.
 */
public enum DrumGrooveArchetype {
  DRIVING,
  BALLAD,
  FOLK,
  FUNK,
  REGGAE,
  POWER;

  /**
   * Maps a {@link BassGrooveArchetype} to the closest drum groove archetype.
   *
   * @param bass the bass groove archetype
   * @return the corresponding drum groove archetype
   */
  public static DrumGrooveArchetype fromBassArchetype(BassGrooveArchetype bass) {
    return switch (bass) {
      case DRIVING -> DRIVING;
      case BALLAD  -> BALLAD;
      case FOLK    -> FOLK;
      case FUNK    -> FUNK;
      case REGGAE  -> REGGAE;
      case POWER   -> POWER;
    };
  }
}
