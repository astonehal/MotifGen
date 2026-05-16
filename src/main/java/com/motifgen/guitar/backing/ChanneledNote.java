package com.motifgen.guitar.backing;

import com.motifgen.model.Note;

/**
 * A {@link Note} decorated with a MIDI channel index (0-indexed).
 *
 * <p>The backing track uses channel index 1 (= MIDI channel 2).
 *
 * @param note    the underlying note
 * @param channel 0-indexed MIDI channel (0–15)
 */
public record ChanneledNote(Note note, int channel) {}
