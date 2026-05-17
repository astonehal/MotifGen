package com.motifgen.guitar.backing;

/**
 * Immutable record representing a single drum hit on the GM channel-10 kit.
 *
 * @param gmNote        General-MIDI percussion note number (e.g., 36 = kick)
 * @param startTick     absolute start position in MIDI ticks
 * @param durationTicks length in MIDI ticks
 * @param velocity      MIDI velocity (1-127)
 */
public record DrumEvent(int gmNote, long startTick, long durationTicks, int velocity) {}
