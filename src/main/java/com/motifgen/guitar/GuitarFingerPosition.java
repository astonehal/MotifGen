package com.motifgen.guitar;

/**
 * Represents a single finger placement on a guitar: which string (1=high e, 6=low E)
 * and which fret (0=open string, 1-22=fretted).
 *
 * <p>String numbering follows standard guitar convention:
 * string 1 = high E (MIDI 64), string 6 = low E (MIDI 40).
 */
public record GuitarFingerPosition(int string, int fret) {

  /** Number of strings on a standard guitar. */
  public static final int STRING_COUNT = 6;

  /** Maximum fret considered physically reachable on a standard guitar. */
  public static final int MAX_FRET = 22;

  /**
   * Validates the position on construction.
   *
   * @throws IllegalArgumentException if string or fret is out of physical range
   */
  public GuitarFingerPosition {
    if (string < 1 || string > STRING_COUNT) {
      throw new IllegalArgumentException(
          "String must be 1–" + STRING_COUNT + ", got: " + string);
    }
    if (fret < 0 || fret > MAX_FRET) {
      throw new IllegalArgumentException(
          "Fret must be 0–" + MAX_FRET + ", got: " + fret);
    }
  }
}
