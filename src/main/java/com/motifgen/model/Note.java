package com.motifgen.model;

/**
 * Represents a single musical note with pitch, duration, velocity, and timing.
 * MIDI pitch 0-127, where 60 = middle C.
 * Duration and startTick are in ticks (typically 480 ticks per quarter note).
 */
public record Note(int pitch, long startTick, long durationTicks, int velocity) {

    public static final int REST = -1;

    public boolean isRest() {
        return pitch == REST;
    }

    public int pitchClass() {
        return isRest() ? -1 : pitch % 12;
    }

    public int octave() {
        return isRest() ? -1 : pitch / 12 - 1;
    }

    public Note transpose(int semitones) {
        if (isRest()) return this;
        int newPitch = Math.max(0, Math.min(127, pitch + semitones));
        return new Note(newPitch, startTick, durationTicks, velocity);
    }

    public Note withStartTick(long newStart) {
        return new Note(pitch, newStart, durationTicks, velocity);
    }

    public Note withDuration(long newDuration) {
        return new Note(pitch, startTick, newDuration, velocity);
    }

    public Note withVelocity(int newVelocity) {
        return new Note(pitch, startTick, durationTicks, newVelocity);
    }

    public long endTick() {
        return startTick + durationTicks;
    }
}
