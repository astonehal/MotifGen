package com.motifgen.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A musical motif: a short melodic idea spanning a number of bars.
 */
public class Motif {
    private final List<Note> notes;
    private final int bars;
    private final int ticksPerBar;
    private final int beatsPerBar;
    private final int ticksPerBeat;

    public Motif(List<Note> notes, int bars, int beatsPerBar, int ticksPerBeat) {
        this.notes = Collections.unmodifiableList(new ArrayList<>(notes));
        this.bars = bars;
        this.beatsPerBar = beatsPerBar;
        this.ticksPerBeat = ticksPerBeat;
        this.ticksPerBar = beatsPerBar * ticksPerBeat;
    }

    public List<Note> getNotes() { return notes; }
    public int getBars() { return bars; }
    public int getTicksPerBar() { return ticksPerBar; }
    public int getBeatsPerBar() { return beatsPerBar; }
    public int getTicksPerBeat() { return ticksPerBeat; }

    public long totalTicks() {
        return (long) bars * ticksPerBar;
    }

    /** Shift all notes so the motif starts at the given tick offset. */
    public Motif shiftTo(long offsetTick) {
        List<Note> shifted = notes.stream()
                .map(n -> n.withStartTick(n.startTick() + offsetTick))
                .toList();
        return new Motif(shifted, bars, beatsPerBar, ticksPerBeat);
    }

    /** Transpose all notes by the given number of semitones. */
    public Motif transpose(int semitones) {
        List<Note> transposed = notes.stream()
                .map(n -> n.transpose(semitones))
                .toList();
        return new Motif(transposed, bars, beatsPerBar, ticksPerBeat);
    }

    /** Return a new motif with adjusted bar count (truncating or repeating as needed). */
    public Motif withBars(int newBars) {
        if (newBars == bars) return this;
        long newTotalTicks = (long) newBars * ticksPerBar;
        List<Note> adjusted = new ArrayList<>();
        for (Note n : notes) {
            if (n.startTick() < newTotalTicks) {
                long dur = Math.min(n.durationTicks(), newTotalTicks - n.startTick());
                adjusted.add(n.withDuration(dur));
            }
        }
        return new Motif(adjusted, newBars, beatsPerBar, ticksPerBeat);
    }

    /** Get the pitch classes present in this motif (0-11). */
    public List<Integer> pitchClasses() {
        return notes.stream()
                .filter(n -> !n.isRest())
                .map(Note::pitchClass)
                .distinct()
                .sorted()
                .toList();
    }

    /** Get pitches present. */
    public List<Integer> pitches() {
        return notes.stream()
                .filter(n -> !n.isRest())
                .map(Note::pitch)
                .toList();
    }

    @Override
    public String toString() {
        return "Motif[bars=%d, notes=%d]".formatted(bars, notes.size());
    }
}
