package com.motifgen.guitar.backing;

import java.util.List;

/**
 * Immutable record representing the complete bass guitar track.
 *
 * <p>All notes are on MIDI channel index 2 (= MIDI channel 3, 0-indexed)
 * using GM program 34 (Electric Bass – Finger).
 *
 * @param notes         channeled notes (channel=2, program=34)
 * @param program       GM program number (always 34)
 * @param combinedScore composite score in [0, 1] from {@link BassLineScorer}
 */
public record BassTrack(List<ChanneledNote> notes, int program, double combinedScore) {

  /** GM program number for Electric Bass – Finger. */
  public static final int BASS_PROGRAM = 34;

  /** MIDI channel index for the bass track (0-indexed = channel 3). */
  public static final int BASS_CHANNEL = 2;
}
