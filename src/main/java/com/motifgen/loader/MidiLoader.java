package com.motifgen.loader;

import com.motifgen.model.Motif;
import com.motifgen.model.Note;

import javax.sound.midi.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads a motif from a Standard MIDI file.
 * Extracts notes from the first melodic track and interprets them as a 4-bar motif.
 */
public class MidiLoader {

    private static final int DEFAULT_TICKS_PER_BEAT = 480;

    public static Motif load(File file, int bars) throws Exception {
        Sequence sequence = MidiSystem.getSequence(file);
        int ticksPerBeat = sequence.getResolution();
        if (sequence.getDivisionType() != Sequence.PPQ) {
            ticksPerBeat = DEFAULT_TICKS_PER_BEAT;
        }

        List<Note> notes = extractNotes(sequence, ticksPerBeat);
        if (notes.isEmpty()) {
            throw new IllegalArgumentException("No melodic notes found in MIDI file: " + file.getName());
        }

        int beatsPerBar = detectBeatsPerBar(sequence, ticksPerBeat);
        int ticksPerBar = beatsPerBar * ticksPerBeat;
        long maxTick = (long) bars * ticksPerBar;

        // Trim notes to requested bar count
        List<Note> trimmed = notes.stream()
                .filter(n -> n.startTick() < maxTick)
                .map(n -> {
                    long end = Math.min(n.endTick(), maxTick);
                    return n.withDuration(end - n.startTick());
                })
                .toList();

        // Use the actual note content to determine bar count so that a short file
        // (e.g. 1-bar motif) is loaded with bars=1 rather than the caller's requested
        // bars=4. This lets MotifLengthMatcher tile the motif up to phrase length.
        long lastNoteTick = trimmed.stream().mapToLong(Note::endTick).max().orElse(0L);
        int actualBars = (int) Math.max(1,
                (lastNoteTick + ticksPerBar - 1) / ticksPerBar);
        int useBars = Math.min(bars, actualBars);

        return new Motif(trimmed, useBars, beatsPerBar, ticksPerBeat);
    }

    private static List<Note> extractNotes(Sequence sequence, int ticksPerBeat) {
        List<Note> allNotes = new ArrayList<>();

        for (Track track : sequence.getTracks()) {
            Map<Integer, Long> noteOnTimes = new HashMap<>();
            Map<Integer, Integer> noteOnVelocities = new HashMap<>();
            List<Note> trackNotes = new ArrayList<>();

            for (int i = 0; i < track.size(); i++) {
                MidiEvent event = track.get(i);
                MidiMessage msg = event.getMessage();

                if (msg instanceof ShortMessage sm) {
                    int channel = sm.getChannel();
                    if (channel == 9) continue; // Skip percussion

                    int command = sm.getCommand();
                    int pitch = sm.getData1();
                    int velocity = sm.getData2();

                    if (command == ShortMessage.NOTE_ON && velocity > 0) {
                        noteOnTimes.put(pitch, event.getTick());
                        noteOnVelocities.put(pitch, velocity);
                    } else if (command == ShortMessage.NOTE_OFF ||
                            (command == ShortMessage.NOTE_ON && velocity == 0)) {
                        Long startTick = noteOnTimes.remove(pitch);
                        Integer vel = noteOnVelocities.remove(pitch);
                        if (startTick != null) {
                            long duration = event.getTick() - startTick;
                            trackNotes.add(new Note(pitch, startTick, duration, vel != null ? vel : 80));
                        }
                    }
                }
            }

            // Use the track with the most notes (likely the melody)
            if (trackNotes.size() > allNotes.size()) {
                allNotes = trackNotes;
            }
        }

        allNotes.sort(Comparator.comparingLong(Note::startTick).thenComparingInt(Note::pitch));
        return allNotes;
    }

    private static int detectBeatsPerBar(Sequence sequence, int ticksPerBeat) {
        for (Track track : sequence.getTracks()) {
            for (int i = 0; i < track.size(); i++) {
                MidiEvent event = track.get(i);
                MidiMessage msg = event.getMessage();
                if (msg instanceof MetaMessage meta && meta.getType() == 0x58) {
                    byte[] data = meta.getData();
                    if (data.length >= 2) {
                        return data[0]; // numerator of time signature
                    }
                }
            }
        }
        return 4; // default 4/4
    }
}
