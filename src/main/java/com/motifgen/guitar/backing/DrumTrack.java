package com.motifgen.guitar.backing;

import java.util.List;

/**
 * Immutable record representing the complete drum track.
 *
 * <p>All events are emitted on MIDI channel index 9 (= MIDI channel 10,
 * 1-indexed) — the General-MIDI percussion channel.
 *
 * @param events ordered drum events (channel 9 percussion)
 */
public record DrumTrack(List<DrumEvent> events) {

  /** 0-indexed MIDI channel for General-MIDI percussion (channel 10). */
  public static final int DRUM_CHANNEL = 9;
}
