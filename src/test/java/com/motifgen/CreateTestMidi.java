package com.motifgen;

import javax.sound.midi.*;
import java.io.File;

/**
 * Creates a simple 4-bar C major melody MIDI file for testing.
 */
public class CreateTestMidi {
    public static void main(String[] args) throws Exception {
        int ticksPerBeat = 480;
        Sequence seq = new Sequence(Sequence.PPQ, ticksPerBeat);
        Track track = seq.createTrack();

        // Time signature: 4/4
        MetaMessage timeSig = new MetaMessage();
        timeSig.setMessage(0x58, new byte[]{4, 2, 24, 8}, 4);
        track.add(new MidiEvent(timeSig, 0));

        // Tempo: 120 BPM (500000 microseconds per beat)
        MetaMessage tempo = new MetaMessage();
        int mpq = 500000;
        tempo.setMessage(0x51, new byte[]{
                (byte) (mpq >> 16), (byte) (mpq >> 8), (byte) mpq}, 3);
        track.add(new MidiEvent(tempo, 0));

        // 4-bar melody in C major: simple stepwise + leap pattern
        // Bar 1: C4 D4 E4 F4 (quarter notes)
        // Bar 2: G4 A4 G4 F4
        // Bar 3: E4 D4 C4 E4
        // Bar 4: G4 F4 E4 C4
        int[] pitches = {
                60, 62, 64, 65,  // Bar 1
                67, 69, 67, 65,  // Bar 2
                64, 62, 60, 64,  // Bar 3
                67, 65, 64, 60   // Bar 4
        };

        int velocity = 90;
        long tick = 0;
        for (int pitch : pitches) {
            ShortMessage noteOn = new ShortMessage();
            noteOn.setMessage(ShortMessage.NOTE_ON, 0, pitch, velocity);
            track.add(new MidiEvent(noteOn, tick));

            ShortMessage noteOff = new ShortMessage();
            noteOff.setMessage(ShortMessage.NOTE_OFF, 0, pitch, 0);
            track.add(new MidiEvent(noteOff, tick + ticksPerBeat));

            tick += ticksPerBeat;
        }

        String outPath = args.length > 0 ? args[0] : "test_motif.mid";
        MidiSystem.write(seq, 1, new File(outPath));
        System.out.println("Created test MIDI: " + outPath);
    }
}
