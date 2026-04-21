package com.motifgen.exporter;

import com.motifgen.model.Note;
import com.motifgen.model.Sentence;

import javax.sound.midi.*;
import java.io.File;
import java.util.List;

/**
 * Exports a Sentence to a Standard MIDI file.
 */
public class MidiExporter {

    private static final int CHANNEL = 0;
    private static final int DEFAULT_TEMPO_BPM = 120;

    public static void export(Sentence sentence, File outputFile, int tempoBpm) throws Exception {
        List<Note> notes = sentence.getAllNotes();
        int ticksPerBeat = sentence.getPhrases().getFirst().getTicksPerBeat();

        Sequence sequence = new Sequence(Sequence.PPQ, ticksPerBeat);
        Track track = sequence.createTrack();

        // Set tempo
        int microsPerBeat = 60_000_000 / tempoBpm;
        byte[] tempoData = {
                (byte) ((microsPerBeat >> 16) & 0xFF),
                (byte) ((microsPerBeat >> 8) & 0xFF),
                (byte) (microsPerBeat & 0xFF)
        };
        track.add(new MidiEvent(new MetaMessage(0x51, tempoData, 3), 0));

        // Set time signature (4/4)
        byte[] timeSigData = {4, 2, 24, 8}; // 4/4, 24 MIDI clocks per metronome, 8 32nds per beat
        track.add(new MidiEvent(new MetaMessage(0x58, timeSigData, 4), 0));

        // Add track name
        String trackName = "MotifGen: " + sentence.getKeyName() + " (" + sentence.getStructure() + ")";
        track.add(new MidiEvent(new MetaMessage(0x03, trackName.getBytes(), trackName.length()), 0));

        // Add notes
        for (Note note : notes) {
            if (note.isRest()) continue;

            int pitch = Math.max(0, Math.min(127, note.pitch()));
            int velocity = Math.max(1, Math.min(127, note.velocity()));

            ShortMessage noteOn = new ShortMessage(ShortMessage.NOTE_ON, CHANNEL, pitch, velocity);
            track.add(new MidiEvent(noteOn, note.startTick()));

            ShortMessage noteOff = new ShortMessage(ShortMessage.NOTE_OFF, CHANNEL, pitch, 0);
            track.add(new MidiEvent(noteOff, note.endTick()));
        }

        // End of track
        long lastTick = notes.stream()
                .mapToLong(Note::endTick)
                .max()
                .orElse(0) + ticksPerBeat;
        track.add(new MidiEvent(new MetaMessage(0x2F, new byte[0], 0), lastTick));

        MidiSystem.write(sequence, 1, outputFile);
    }

    public static void export(Sentence sentence, File outputFile) throws Exception {
        export(sentence, outputFile, DEFAULT_TEMPO_BPM);
    }
}
