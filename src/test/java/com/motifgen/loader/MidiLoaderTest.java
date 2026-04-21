package com.motifgen.loader;

import com.motifgen.model.Motif;
import com.motifgen.model.Note;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.sound.midi.*;
import java.io.File;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MidiLoaderTest {

  @TempDir
  Path tempDir;

  private static void addNote(Track track, int pitch, long onTick, long offTick, int channel, int velocity) throws Exception {
    ShortMessage on = new ShortMessage();
    on.setMessage(ShortMessage.NOTE_ON, channel, pitch, velocity);
    track.add(new MidiEvent(on, onTick));
    ShortMessage off = new ShortMessage();
    off.setMessage(ShortMessage.NOTE_OFF, channel, pitch, 0);
    track.add(new MidiEvent(off, offTick));
  }

  private File writeSequence(Sequence seq, String name) throws Exception {
    File f = tempDir.resolve(name).toFile();
    MidiSystem.write(seq, 1, f);
    return f;
  }

  @Test
  void loadsNotesFromSingleTrackWithDefaultTimeSignature() throws Exception {
    int tpb = 480;
    Sequence seq = new Sequence(Sequence.PPQ, tpb);
    Track t = seq.createTrack();
    addNote(t, 60, 0, tpb, 0, 90);
    addNote(t, 62, tpb, tpb * 2, 0, 90);

    File f = writeSequence(seq, "simple.mid");
    Motif m = MidiLoader.load(f, 4);

    assertEquals(4, m.getBeatsPerBar());
    assertEquals(tpb, m.getTicksPerBeat());
    List<Note> notes = m.getNotes();
    assertEquals(2, notes.size());
    assertEquals(60, notes.get(0).pitch());
    assertEquals(90, notes.get(0).velocity());
    assertEquals(tpb, notes.get(0).durationTicks());
  }

  @Test
  void readsExplicitTimeSignatureMeta() throws Exception {
    int tpb = 480;
    Sequence seq = new Sequence(Sequence.PPQ, tpb);
    Track t = seq.createTrack();
    // 3/4 time signature
    MetaMessage ts = new MetaMessage();
    ts.setMessage(0x58, new byte[]{3, 2, 24, 8}, 4);
    t.add(new MidiEvent(ts, 0));
    addNote(t, 60, 0, tpb, 0, 90);

    File f = writeSequence(seq, "threefour.mid");
    Motif m = MidiLoader.load(f, 4);
    assertEquals(3, m.getBeatsPerBar());
  }

  @Test
  void skipsPercussionChannel9() throws Exception {
    int tpb = 480;
    Sequence seq = new Sequence(Sequence.PPQ, tpb);
    // Track with many drum hits on channel 9 — should NOT be picked
    Track drums = seq.createTrack();
    for (int i = 0; i < 8; i++) {
      addNote(drums, 36, i * 100L, i * 100L + 50, 9, 100);
    }
    // Melody track with fewer notes on channel 0 — should be picked
    Track melody = seq.createTrack();
    addNote(melody, 60, 0, tpb, 0, 90);
    addNote(melody, 64, tpb, tpb * 2, 0, 90);

    File f = writeSequence(seq, "drums_and_melody.mid");
    Motif m = MidiLoader.load(f, 4);
    // Melody track wins because drum track contributed zero notes (channel skipped)
    assertEquals(2, m.getNotes().size());
    assertEquals(60, m.getNotes().get(0).pitch());
  }

  @Test
  void chooseTrackWithMostNotes() throws Exception {
    int tpb = 480;
    Sequence seq = new Sequence(Sequence.PPQ, tpb);
    Track small = seq.createTrack();
    addNote(small, 48, 0, tpb, 0, 80);
    Track big = seq.createTrack();
    for (int i = 0; i < 4; i++) {
      addNote(big, 60 + i, i * (long) tpb, (i + 1L) * tpb, 0, 90);
    }

    File f = writeSequence(seq, "pick_biggest.mid");
    Motif m = MidiLoader.load(f, 4);
    assertEquals(4, m.getNotes().size());
    assertEquals(60, m.getNotes().get(0).pitch());
  }

  @Test
  void noteOnWithZeroVelocityIsTreatedAsNoteOff() throws Exception {
    int tpb = 480;
    Sequence seq = new Sequence(Sequence.PPQ, tpb);
    Track t = seq.createTrack();
    ShortMessage on = new ShortMessage();
    on.setMessage(ShortMessage.NOTE_ON, 0, 60, 90);
    t.add(new MidiEvent(on, 0));
    // NOTE_ON with velocity 0 acts as NOTE_OFF
    ShortMessage offViaOn = new ShortMessage();
    offViaOn.setMessage(ShortMessage.NOTE_ON, 0, 60, 0);
    t.add(new MidiEvent(offViaOn, tpb));

    File f = writeSequence(seq, "zero_vel_off.mid");
    Motif m = MidiLoader.load(f, 4);
    assertEquals(1, m.getNotes().size());
    assertEquals(tpb, m.getNotes().get(0).durationTicks());
  }

  @Test
  void trimsNotesPastRequestedBarCount() throws Exception {
    int tpb = 480;
    Sequence seq = new Sequence(Sequence.PPQ, tpb);
    Track t = seq.createTrack();
    // 4/4 time, 1 bar = 4 * 480 = 1920 ticks
    addNote(t, 60, 0, tpb, 0, 90);              // within 1 bar
    addNote(t, 62, 3000, 3480, 0, 90);          // outside 1 bar — dropped
    addNote(t, 64, 1800, 2400, 0, 90);          // straddles boundary — clipped

    File f = writeSequence(seq, "trim.mid");
    Motif m = MidiLoader.load(f, 1);
    // Notes past 1920 dropped; straddling note kept but clipped
    assertEquals(2, m.getNotes().size());
    Note clipped = m.getNotes().stream()
        .filter(n -> n.pitch() == 64)
        .findFirst()
        .orElseThrow();
    assertTrue(clipped.endTick() <= 1920);
  }

  @Test
  void throwsWhenNoMelodicNotesPresent() throws Exception {
    int tpb = 480;
    Sequence seq = new Sequence(Sequence.PPQ, tpb);
    seq.createTrack();
    File f = writeSequence(seq, "empty.mid");
    assertThrows(IllegalArgumentException.class, () -> MidiLoader.load(f, 4));
  }
}
