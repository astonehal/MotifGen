package com.motifgen.guitar.backing;

import java.util.List;

/**
 * Immutable record representing the complete drum track.
 *
 * <p>All events are emitted on MIDI channel index 9 (= MIDI channel 10,
 * 1-indexed) — the General-MIDI percussion channel.
 *
 * @param events     ordered drum events (channel 9 percussion)
 * @param violations playability violations found after the repair pass (empty if clean)
 * @param difficulty composite difficulty score and level; {@code null} before scoring
 */
public record DrumTrack(
    List<DrumEvent> events,
    List<DrumPlayabilityViolation> violations,
    DrumDifficulty.DifficultyScore difficulty) {

  /** 0-indexed MIDI channel for General-MIDI percussion (channel 10). */
  public static final int DRUM_CHANNEL = 9;

  /**
   * Convenience constructor for backward compatibility — creates a track with no
   * violations and no difficulty score. Existing callers (tests, exporters) that
   * supply only an event list continue to compile without change.
   *
   * @param events ordered drum events
   */
  public DrumTrack(List<DrumEvent> events) {
    this(events, List.of(), null);
  }
}
