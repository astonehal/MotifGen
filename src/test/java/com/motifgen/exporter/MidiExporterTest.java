package com.motifgen.exporter;

import com.motifgen.exporter.MidiExporter;
import com.motifgen.model.Motif;
import com.motifgen.model.Note;
import com.motifgen.model.Sentence;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.sound.midi.*;
import java.io.File;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MidiExporterTest {

  @TempDir
  Path tempDir;

  private static final int TPB = 480;
  private static final int BPB = 4;

  private Motif phraseOf(int... pitches) {
    java.util.List<Note> notes = new java.util.ArrayList<>();
    long tick = 0;
    for (int p : pitches) {
      notes.add(new Note(p, tick, TPB, 90));
      tick += TPB;
    }
    return new Motif(notes, 1, BPB, TPB);
  }

  private Sentence sentenceWithPhrases(Motif... phrases) {
    return new Sentence(List.of(phrases), "a a' b a''", "C major", 42.0);
  }

  @Test
  void exportWritesReadableMidiWithTempoAndNoteEvents() throws Exception {
    Motif a = phraseOf(60, 62, 64, 65);
    Motif b = phraseOf(67, 65, 64, 62);
    Sentence s = sentenceWithPhrases(a, b);

    File out = tempDir.resolve("out.mid").toFile();
    MidiExporter.export(s, out, 140);

    assertTrue(out.exists());
    assertTrue(out.length() > 0);

    // Read back and verify a note-on is present plus meta events
    Sequence seq = MidiSystem.getSequence(out);
    assertEquals(TPB, seq.getResolution());

    boolean foundNoteOn = false;
    boolean foundTempo = false;
    Track[] tracks = seq.getTracks();
    for (Track track : tracks) {
      for (int i = 0; i < track.size(); i++) {
        MidiMessage msg = track.get(i).getMessage();
        if (msg instanceof ShortMessage sm && sm.getCommand() == ShortMessage.NOTE_ON && sm.getData2() > 0) {
          foundNoteOn = true;
        }
        if (msg instanceof MetaMessage meta && meta.getType() == 0x51) {
          foundTempo = true;
        }
      }
    }
    assertTrue(foundNoteOn, "expected at least one NOTE_ON event");
    assertTrue(foundTempo, "expected tempo meta event");
  }

  @Test
  void exportClampsOutOfRangePitchesAndVelocities() throws Exception {
    Motif raw = new Motif(
        List.of(new Note(200, 0, TPB, 200), // both out of range
                new Note(-5, TPB, TPB, 0)),
        1, BPB, TPB);
    Sentence s = new Sentence(List.of(raw), "a", "C major", 0);

    File out = tempDir.resolve("clamp.mid").toFile();
    MidiExporter.export(s, out, 120);

    Sequence seq = MidiSystem.getSequence(out);
    for (Track track : seq.getTracks()) {
      for (int i = 0; i < track.size(); i++) {
        MidiMessage msg = track.get(i).getMessage();
        if (msg instanceof ShortMessage sm && sm.getCommand() == ShortMessage.NOTE_ON) {
          assertTrue(sm.getData1() >= 0 && sm.getData1() <= 127);
          assertTrue(sm.getData2() >= 1 && sm.getData2() <= 127);
        }
      }
    }
  }

  @Test
  void exportSkipsRestNotes() throws Exception {
    Motif withRest = new Motif(
        List.of(new Note(Note.REST, 0, TPB, 0),
                new Note(60, TPB, TPB, 90)),
        1, BPB, TPB);
    Sentence s = new Sentence(List.of(withRest), "a", "C major", 0);

    File out = tempDir.resolve("rest.mid").toFile();
    MidiExporter.export(s, out, 120);

    Sequence seq = MidiSystem.getSequence(out);
    int noteOnCount = 0;
    for (Track track : seq.getTracks()) {
      for (int i = 0; i < track.size(); i++) {
        MidiMessage msg = track.get(i).getMessage();
        if (msg instanceof ShortMessage sm && sm.getCommand() == ShortMessage.NOTE_ON && sm.getData2() > 0) {
          noteOnCount++;
        }
      }
    }
    assertEquals(1, noteOnCount);
  }

  @Test
  void defaultTempoOverloadSucceeds() throws Exception {
    Sentence s = sentenceWithPhrases(phraseOf(60, 64, 67));
    File out = tempDir.resolve("default_tempo.mid").toFile();
    MidiExporter.export(s, out);
    assertTrue(out.exists());
  }
}
