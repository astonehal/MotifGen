package com.motifgen.guitar.backing;

/**
 * Section variants A/B/C describing how the drum kit is voiced for that
 * 8-bar phrase. Differentiation gives the ear forward motion across sections:
 *
 * <ul>
 *   <li>A: closed hi-hat, no ghost snares, no extra kick</li>
 *   <li>B: ride cymbal with ghost snares</li>
 *   <li>C: ride bell with ghost snares and extra kick (climax)</li>
 * </ul>
 */
public enum DrumSection {
  A(DrumPattern.CLOSED_HIHAT, false, false, 1.0),
  B(DrumPattern.RIDE,         true,  false, 1.0),
  C(DrumPattern.RIDE_BELL,    true,  true,  1.1);

  private final int cymbalNote;
  private final boolean ghostSnares;
  private final boolean extraKick;
  private final double velocityMod;

  DrumSection(int cymbalNote, boolean ghostSnares, boolean extraKick, double velocityMod) {
    this.cymbalNote = cymbalNote;
    this.ghostSnares = ghostSnares;
    this.extraKick = extraKick;
    this.velocityMod = velocityMod;
  }

  /** GM note number used for the cymbal voice in this section. */
  public int cymbalNote() { return cymbalNote; }
  /** Whether soft "ghost" snare hits are added on offbeats. */
  public boolean ghostSnares() { return ghostSnares; }
  /** Whether an extra kick is added on beat 2.5. */
  public boolean extraKick() { return extraKick; }
  /** Velocity scaling factor for hits within this section. */
  public double velocityMod() { return velocityMod; }

  /**
   * Maps a label character ('A','B','C'; case-insensitive) to a section.
   * Unknown letters default to {@link #A}.
   *
   * @param label the section label character
   * @return the matching {@link DrumSection}
   */
  public static DrumSection fromLabel(char label) {
    return switch (Character.toUpperCase(label)) {
      case 'B' -> B;
      case 'C' -> C;
      default  -> A;
    };
  }
}
