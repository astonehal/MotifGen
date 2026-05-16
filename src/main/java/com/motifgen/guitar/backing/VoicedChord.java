package com.motifgen.guitar.backing;

import com.motifgen.model.Note;

import java.util.List;

/**
 * A chord slot after voicing and strum-pattern application: a start tick plus
 * the concrete {@link Note} objects (with actual MIDI pitches, velocities, and
 * durations derived from the strum grid).
 *
 * @param startTick absolute start tick of this chord event
 * @param notes     concrete note events; may be empty if the strum pattern is all-rest
 *                  at this position
 */
public record VoicedChord(long startTick, List<Note> notes) {}
