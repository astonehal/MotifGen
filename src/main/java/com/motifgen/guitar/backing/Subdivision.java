package com.motifgen.guitar.backing;

/**
 * Rhythmic subdivision levels used by the {@link RhythmDensityPlanner}.
 *
 * <p>Ordinal order runs from coarsest (WHOLE) to finest (SIXTEENTH).
 */
public enum Subdivision {
  WHOLE,
  HALF,
  QUARTER,
  EIGHTH,
  SIXTEENTH;

  /**
   * Returns the number of ticks this subdivision spans given a quarter-note resolution.
   *
   * @param ppq ticks per quarter note (pulses per quarter)
   * @return ticks for this subdivision
   */
  public int ticksPerBeat(int ppq) {
    return switch (this) {
      case WHOLE      -> ppq * 4;
      case HALF       -> ppq * 2;
      case QUARTER    -> ppq;
      case EIGHTH     -> ppq / 2;
      case SIXTEENTH  -> ppq / 4;
    };
  }

  /** Steps down one level (coarser); returns this if already at WHOLE. */
  public Subdivision stepDown() {
    return ordinal() == 0 ? this : values()[ordinal() - 1];
  }

  /** Steps up one level (finer); returns this if already at SIXTEENTH. */
  public Subdivision stepUp() {
    return ordinal() == values().length - 1 ? this : values()[ordinal() + 1];
  }
}
