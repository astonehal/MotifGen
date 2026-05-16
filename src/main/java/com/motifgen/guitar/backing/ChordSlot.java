package com.motifgen.guitar.backing;

import java.util.List;

/**
 * Holds the raw chord tones (MIDI pitch integers) for a single harmonic slot
 * before voicing is applied.
 *
 * @param startTick     start position in MIDI ticks
 * @param durationTicks length of this chord slot in ticks
 * @param pitches       chord-tone MIDI pitches (unvoiced, may span multiple octaves)
 */
public record ChordSlot(long startTick, long durationTicks, List<Integer> pitches) {}
