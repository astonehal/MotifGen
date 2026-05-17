package com.motifgen.exporter;

import com.motifgen.guitar.backing.BackingTrack;
import com.motifgen.guitar.backing.BassTrack;
import com.motifgen.guitar.backing.ChanneledNote;
import com.motifgen.guitar.backing.DrumEvent;
import com.motifgen.guitar.backing.DrumTrack;
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

    /**
     * Exports a Type-1 (multi-track) MIDI file containing the melody on track 0
     * (channel 0, 0-indexed) and the rhythm guitar backing on track 1
     * (channel 1, 0-indexed = MIDI channel 2; program-change to program 25
     * is written at tick 0 of track 1).
     *
     * <p>The existing single-track {@link #export(Sentence, File)} overload is
     * unchanged.
     *
     * @param melody     the melody sentence
     * @param backing    the backing track produced by BackingTrackGenerator
     * @param outputFile destination MIDI file
     * @throws Exception if MIDI I/O fails
     */
    public static void export(Sentence melody, BackingTrack backing, File outputFile)
            throws Exception {
        export(melody, backing, outputFile, DEFAULT_TEMPO_BPM);
    }

    /**
     * Exports a Type-1 MIDI file with melody and backing tracks at the specified tempo.
     *
     * @param melody     the melody sentence
     * @param backing    the backing track
     * @param outputFile destination MIDI file
     * @param tempoBpm   tempo in beats per minute
     * @throws Exception if MIDI I/O fails
     */
    public static void export(Sentence melody, BackingTrack backing, File outputFile,
            int tempoBpm) throws Exception {
        List<Note> melodyNotes = melody.getAllNotes();
        int ticksPerBeat = melody.getPhrases().getFirst().getTicksPerBeat();

        Sequence sequence = new Sequence(Sequence.PPQ, ticksPerBeat);

        // --- Track 0: melody (channel 0) ---
        Track melodyTrack = sequence.createTrack();

        int microsPerBeat = 60_000_000 / tempoBpm;
        byte[] tempoData = {
                (byte) ((microsPerBeat >> 16) & 0xFF),
                (byte) ((microsPerBeat >> 8)  & 0xFF),
                (byte) (microsPerBeat & 0xFF)
        };
        melodyTrack.add(new MidiEvent(new MetaMessage(0x51, tempoData, 3), 0));

        byte[] timeSigData = {4, 2, 24, 8};
        melodyTrack.add(new MidiEvent(new MetaMessage(0x58, timeSigData, 4), 0));

        String trackName = "MotifGen: " + melody.getKeyName() + " (" + melody.getStructure() + ")";
        melodyTrack.add(
                new MidiEvent(new MetaMessage(0x03, trackName.getBytes(), trackName.length()), 0));

        for (Note note : melodyNotes) {
            if (note.isRest()) continue;
            int pitch    = Math.max(0, Math.min(127, note.pitch()));
            int velocity = Math.max(1, Math.min(127, note.velocity()));
            melodyTrack.add(new MidiEvent(
                    new ShortMessage(ShortMessage.NOTE_ON, CHANNEL, pitch, velocity),
                    note.startTick()));
            melodyTrack.add(new MidiEvent(
                    new ShortMessage(ShortMessage.NOTE_OFF, CHANNEL, pitch, 0),
                    note.endTick()));
        }

        long melodyEnd = melodyNotes.stream().mapToLong(Note::endTick).max().orElse(0)
                + ticksPerBeat;
        melodyTrack.add(new MidiEvent(new MetaMessage(0x2F, new byte[0], 0), melodyEnd));

        // --- Track 1: backing guitar (channel 1, program 25) ---
        Track backingTrack = sequence.createTrack();

        String backingName = "Rhythm Guitar";
        backingTrack.add(
                new MidiEvent(new MetaMessage(0x03, backingName.getBytes(),
                        backingName.length()), 0));

        int backingChannel = BackingTrack.BACKING_CHANNEL;
        // Program change: select GM program 25 (Acoustic Guitar – Steel)
        backingTrack.add(new MidiEvent(
                new ShortMessage(ShortMessage.PROGRAM_CHANGE, backingChannel,
                        backing.program() - 1, 0),
                0));

        for (ChanneledNote cn : backing.notes()) {
            Note note = cn.note();
            if (note.isRest()) continue;
            int pitch    = Math.max(0, Math.min(127, note.pitch()));
            int velocity = Math.max(1, Math.min(127, note.velocity()));
            backingTrack.add(new MidiEvent(
                    new ShortMessage(ShortMessage.NOTE_ON, backingChannel, pitch, velocity),
                    note.startTick()));
            backingTrack.add(new MidiEvent(
                    new ShortMessage(ShortMessage.NOTE_OFF, backingChannel, pitch, 0),
                    note.endTick()));
        }

        long backingEnd = backing.notes().stream()
                .mapToLong(cn -> cn.note().endTick()).max().orElse(melodyEnd) + ticksPerBeat;
        backingTrack.add(new MidiEvent(new MetaMessage(0x2F, new byte[0], 0), backingEnd));

        MidiSystem.write(sequence, 1, outputFile);
    }

    /**
     * Exports a Type-1 (3-track) MIDI file: melody (ch 0), rhythm guitar (ch 1),
     * and bass guitar (ch 2, program 33 zero-indexed = GM 34).
     *
     * @param melody     the melody sentence
     * @param backing    the rhythm guitar backing track
     * @param bass       the bass guitar track
     * @param outputFile destination MIDI file
     * @param tempoBpm   tempo in beats per minute
     * @throws Exception if MIDI I/O fails
     */
    public static void export(Sentence melody, BackingTrack backing, BassTrack bass,
            File outputFile, int tempoBpm) throws Exception {
        List<Note> melodyNotes = melody.getAllNotes();
        int ticksPerBeat = melody.getPhrases().getFirst().getTicksPerBeat();

        Sequence sequence = new Sequence(Sequence.PPQ, ticksPerBeat);

        // --- Track 0: melody (channel 0) ---
        Track melodyTrack = sequence.createTrack();

        int microsPerBeat = 60_000_000 / tempoBpm;
        byte[] tempoData = {
                (byte) ((microsPerBeat >> 16) & 0xFF),
                (byte) ((microsPerBeat >> 8)  & 0xFF),
                (byte) (microsPerBeat & 0xFF)
        };
        melodyTrack.add(new MidiEvent(new MetaMessage(0x51, tempoData, 3), 0));

        byte[] timeSigData = {4, 2, 24, 8};
        melodyTrack.add(new MidiEvent(new MetaMessage(0x58, timeSigData, 4), 0));

        String trackName = "MotifGen: " + melody.getKeyName() + " (" + melody.getStructure() + ")";
        melodyTrack.add(
                new MidiEvent(new MetaMessage(0x03, trackName.getBytes(), trackName.length()), 0));

        for (Note note : melodyNotes) {
            if (note.isRest()) continue;
            int pitch    = Math.max(0, Math.min(127, note.pitch()));
            int velocity = Math.max(1, Math.min(127, note.velocity()));
            melodyTrack.add(new MidiEvent(
                    new ShortMessage(ShortMessage.NOTE_ON, CHANNEL, pitch, velocity),
                    note.startTick()));
            melodyTrack.add(new MidiEvent(
                    new ShortMessage(ShortMessage.NOTE_OFF, CHANNEL, pitch, 0),
                    note.endTick()));
        }

        long melodyEnd = melodyNotes.stream().mapToLong(Note::endTick).max().orElse(0)
                + ticksPerBeat;
        melodyTrack.add(new MidiEvent(new MetaMessage(0x2F, new byte[0], 0), melodyEnd));

        // --- Track 1: backing guitar (channel 1, program 25) ---
        Track backingTrack = sequence.createTrack();

        String backingName = "Rhythm Guitar";
        backingTrack.add(
                new MidiEvent(new MetaMessage(0x03, backingName.getBytes(),
                        backingName.length()), 0));

        int backingChannel = BackingTrack.BACKING_CHANNEL;
        backingTrack.add(new MidiEvent(
                new ShortMessage(ShortMessage.PROGRAM_CHANGE, backingChannel,
                        backing.program() - 1, 0),
                0));

        for (ChanneledNote cn : backing.notes()) {
            Note note = cn.note();
            if (note.isRest()) continue;
            int pitch    = Math.max(0, Math.min(127, note.pitch()));
            int velocity = Math.max(1, Math.min(127, note.velocity()));
            backingTrack.add(new MidiEvent(
                    new ShortMessage(ShortMessage.NOTE_ON, backingChannel, pitch, velocity),
                    note.startTick()));
            backingTrack.add(new MidiEvent(
                    new ShortMessage(ShortMessage.NOTE_OFF, backingChannel, pitch, 0),
                    note.endTick()));
        }

        long backingEnd = backing.notes().stream()
                .mapToLong(cn -> cn.note().endTick()).max().orElse(melodyEnd) + ticksPerBeat;
        backingTrack.add(new MidiEvent(new MetaMessage(0x2F, new byte[0], 0), backingEnd));

        // --- Track 2: bass guitar (channel 2, program 33 zero-indexed = GM 34) ---
        Track bassTrack = sequence.createTrack();

        String bassName = "Bass Guitar";
        bassTrack.add(
                new MidiEvent(new MetaMessage(0x03, bassName.getBytes(), bassName.length()), 0));

        int bassChannel = BassTrack.BASS_CHANNEL;
        bassTrack.add(new MidiEvent(
                new ShortMessage(ShortMessage.PROGRAM_CHANGE, bassChannel,
                        bass.program() - 1, 0),
                0));

        for (ChanneledNote cn : bass.notes()) {
            Note note = cn.note();
            if (note.isRest()) continue;
            int pitch    = Math.max(0, Math.min(127, note.pitch()));
            int velocity = Math.max(1, Math.min(127, note.velocity()));
            bassTrack.add(new MidiEvent(
                    new ShortMessage(ShortMessage.NOTE_ON, bassChannel, pitch, velocity),
                    note.startTick()));
            bassTrack.add(new MidiEvent(
                    new ShortMessage(ShortMessage.NOTE_OFF, bassChannel, pitch, 0),
                    note.endTick()));
        }

        long bassEnd = bass.notes().stream()
                .mapToLong(cn -> cn.note().endTick()).max().orElse(melodyEnd) + ticksPerBeat;
        bassTrack.add(new MidiEvent(new MetaMessage(0x2F, new byte[0], 0), bassEnd));

        MidiSystem.write(sequence, 1, outputFile);
    }

    /**
     * Exports a Type-1 (4-track) MIDI file: melody (ch 0), rhythm guitar (ch 1),
     * bass guitar (ch 2), and drums on channel 9 (= GM channel 10).
     *
     * <p>No program-change message is written for the drum track; channel 10 is
     * reserved for the General-MIDI percussion kit, which is selected implicitly.
     *
     * @param melody     the melody sentence
     * @param backing    the rhythm guitar backing track
     * @param bass       the bass guitar track
     * @param drums      the drum track
     * @param outputFile destination MIDI file
     * @param tempoBpm   tempo in beats per minute
     * @throws Exception if MIDI I/O fails
     */
    public static void export(Sentence melody, BackingTrack backing, BassTrack bass,
            DrumTrack drums, File outputFile, int tempoBpm) throws Exception {
        List<Note> melodyNotes = melody.getAllNotes();
        int ticksPerBeat = melody.getPhrases().getFirst().getTicksPerBeat();

        Sequence sequence = new Sequence(Sequence.PPQ, ticksPerBeat);

        // --- Track 0: melody (channel 0) ---
        Track melodyTrack = sequence.createTrack();

        int microsPerBeat = 60_000_000 / tempoBpm;
        byte[] tempoData = {
                (byte) ((microsPerBeat >> 16) & 0xFF),
                (byte) ((microsPerBeat >> 8)  & 0xFF),
                (byte) (microsPerBeat & 0xFF)
        };
        melodyTrack.add(new MidiEvent(new MetaMessage(0x51, tempoData, 3), 0));

        byte[] timeSigData = {4, 2, 24, 8};
        melodyTrack.add(new MidiEvent(new MetaMessage(0x58, timeSigData, 4), 0));

        String trackName = "MotifGen: " + melody.getKeyName() + " (" + melody.getStructure() + ")";
        melodyTrack.add(
                new MidiEvent(new MetaMessage(0x03, trackName.getBytes(), trackName.length()), 0));

        for (Note note : melodyNotes) {
            if (note.isRest()) continue;
            int pitch    = Math.max(0, Math.min(127, note.pitch()));
            int velocity = Math.max(1, Math.min(127, note.velocity()));
            melodyTrack.add(new MidiEvent(
                    new ShortMessage(ShortMessage.NOTE_ON, CHANNEL, pitch, velocity),
                    note.startTick()));
            melodyTrack.add(new MidiEvent(
                    new ShortMessage(ShortMessage.NOTE_OFF, CHANNEL, pitch, 0),
                    note.endTick()));
        }

        long melodyEnd = melodyNotes.stream().mapToLong(Note::endTick).max().orElse(0)
                + ticksPerBeat;
        melodyTrack.add(new MidiEvent(new MetaMessage(0x2F, new byte[0], 0), melodyEnd));

        // --- Track 1: backing guitar ---
        Track backingTrack = sequence.createTrack();
        String backingName = "Rhythm Guitar";
        backingTrack.add(new MidiEvent(
                new MetaMessage(0x03, backingName.getBytes(), backingName.length()), 0));
        int backingChannel = BackingTrack.BACKING_CHANNEL;
        backingTrack.add(new MidiEvent(
                new ShortMessage(ShortMessage.PROGRAM_CHANGE, backingChannel,
                        backing.program() - 1, 0),
                0));
        for (ChanneledNote cn : backing.notes()) {
            Note note = cn.note();
            if (note.isRest()) continue;
            int pitch    = Math.max(0, Math.min(127, note.pitch()));
            int velocity = Math.max(1, Math.min(127, note.velocity()));
            backingTrack.add(new MidiEvent(
                    new ShortMessage(ShortMessage.NOTE_ON, backingChannel, pitch, velocity),
                    note.startTick()));
            backingTrack.add(new MidiEvent(
                    new ShortMessage(ShortMessage.NOTE_OFF, backingChannel, pitch, 0),
                    note.endTick()));
        }
        long backingEnd = backing.notes().stream()
                .mapToLong(cn -> cn.note().endTick()).max().orElse(melodyEnd) + ticksPerBeat;
        backingTrack.add(new MidiEvent(new MetaMessage(0x2F, new byte[0], 0), backingEnd));

        // --- Track 2: bass guitar ---
        Track bassTrack = sequence.createTrack();
        String bassName = "Bass Guitar";
        bassTrack.add(new MidiEvent(
                new MetaMessage(0x03, bassName.getBytes(), bassName.length()), 0));
        int bassChannel = BassTrack.BASS_CHANNEL;
        bassTrack.add(new MidiEvent(
                new ShortMessage(ShortMessage.PROGRAM_CHANGE, bassChannel,
                        bass.program() - 1, 0),
                0));
        for (ChanneledNote cn : bass.notes()) {
            Note note = cn.note();
            if (note.isRest()) continue;
            int pitch    = Math.max(0, Math.min(127, note.pitch()));
            int velocity = Math.max(1, Math.min(127, note.velocity()));
            bassTrack.add(new MidiEvent(
                    new ShortMessage(ShortMessage.NOTE_ON, bassChannel, pitch, velocity),
                    note.startTick()));
            bassTrack.add(new MidiEvent(
                    new ShortMessage(ShortMessage.NOTE_OFF, bassChannel, pitch, 0),
                    note.endTick()));
        }
        long bassEnd = bass.notes().stream()
                .mapToLong(cn -> cn.note().endTick()).max().orElse(melodyEnd) + ticksPerBeat;
        bassTrack.add(new MidiEvent(new MetaMessage(0x2F, new byte[0], 0), bassEnd));

        // --- Track 3: drums (channel 9 = GM percussion) ---
        Track drumTrack = sequence.createTrack();
        String drumName = "Drums";
        drumTrack.add(new MidiEvent(
                new MetaMessage(0x03, drumName.getBytes(), drumName.length()), 0));
        int drumChannel = DrumTrack.DRUM_CHANNEL;
        for (DrumEvent ev : drums.events()) {
            int gm  = Math.max(0, Math.min(127, ev.gmNote()));
            int vel = Math.max(1, Math.min(127, ev.velocity()));
            drumTrack.add(new MidiEvent(
                    new ShortMessage(ShortMessage.NOTE_ON, drumChannel, gm, vel),
                    ev.startTick()));
            drumTrack.add(new MidiEvent(
                    new ShortMessage(ShortMessage.NOTE_OFF, drumChannel, gm, 0),
                    ev.startTick() + ev.durationTicks()));
        }
        long drumEnd = drums.events().stream()
                .mapToLong(e -> e.startTick() + e.durationTicks())
                .max().orElse(melodyEnd) + ticksPerBeat;
        drumTrack.add(new MidiEvent(new MetaMessage(0x2F, new byte[0], 0), drumEnd));

        MidiSystem.write(sequence, 1, outputFile);
    }
}
