package com.motifgen.guitar.backing;

/**
 * Immutable record representing a single bass note on the fretboard.
 *
 * <p>The {@code midi} value is clamped to [28, 55] (E1–G3) at construction
 * time, ensuring all bass notes remain in the playable register.
 *
 * @param midi          MIDI pitch, clamped to [28, 55]
 * @param startTick     absolute start position in MIDI ticks
 * @param durationTicks length in MIDI ticks
 * @param velocity      MIDI velocity (1–127)
 * @param stringIdx     0-indexed bass string (0=E1/28, 1=A1/33, 2=D2/38, 3=G2/43)
 * @param fret          fret number (0–15)
 */
public record BassNote(int midi, long startTick, long durationTicks, int velocity,
    int stringIdx, int fret) {

  /** Minimum MIDI pitch for bass (E1). */
  public static final int MIDI_MIN = 28;

  /** Maximum MIDI pitch for bass (G3). */
  public static final int MIDI_MAX = 55;

  /** Upper bound of the primary register (G2). */
  public static final int PRIMARY_MAX = 43;

  /**
   * Compact canonical constructor — clamps {@code midi} to [28, 55].
   */
  public BassNote {
    midi = Math.max(MIDI_MIN, Math.min(MIDI_MAX, midi));
  }
}
