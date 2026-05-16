package com.motifgen.guitar.backing;

import java.util.List;

/**
 * Immutable record representing the complete rhythm guitar backing track.
 *
 * <p>All notes are on MIDI channel index 1 (= MIDI channel 2, 0-indexed)
 * using GM program 25 (Acoustic Guitar – Steel).
 *
 * @param notes         channeled notes (channel=1, program=25)
 * @param program       GM program number (always 25)
 * @param combinedScore 0.5 × consonance + 0.5 × catchiness, normalised 0–100
 */
public record BackingTrack(List<ChanneledNote> notes, int program, double combinedScore) {

  /** GM program number for Acoustic Guitar – Steel. */
  public static final int GUITAR_PROGRAM = 25;

  /** MIDI channel index for the backing track (0-indexed = channel 2). */
  public static final int BACKING_CHANNEL = 1;
}
