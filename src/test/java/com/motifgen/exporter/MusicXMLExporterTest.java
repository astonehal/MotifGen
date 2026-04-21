package com.motifgen.exporter;

import com.motifgen.exporter.MusicXMLExporter;
import com.motifgen.model.Motif;
import com.motifgen.model.Note;
import com.motifgen.model.Sentence;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MusicXMLExporterTest {

  @TempDir
  Path tempDir;

  private static final int TPB = 480;
  private static final int BPB = 4;

  private Motif quarterNotePhrase(int... pitches) {
    List<Note> notes = new ArrayList<>();
    long tick = 0;
    for (int p : pitches) {
      notes.add(new Note(p, tick, TPB, 90));
      tick += TPB;
    }
    return new Motif(notes, 1, BPB, TPB);
  }

  private Sentence sentenceIn(String keyName, Motif... phrases) {
    return new Sentence(List.of(phrases), "a a' b a''", keyName, 0.0);
  }

  @Test
  void exportWritesMusicXmlWithPitchAndKeyMetadata() throws Exception {
    Sentence s = sentenceIn("C major",
        quarterNotePhrase(60, 62, 64, 65),
        quarterNotePhrase(67, 65, 64, 62));

    File out = tempDir.resolve("out.musicxml").toFile();
    MusicXMLExporter.export(s, out, 140);
    String xml = Files.readString(out.toPath());

    assertTrue(xml.contains("<score-partwise"));
    assertTrue(xml.contains("<step>C</step>"));
    assertTrue(xml.contains("<mode>major</mode>"));
    assertTrue(xml.contains("<per-minute>140</per-minute>"));
  }

  @Test
  void exportRendersAccidentalsViaAlter() throws Exception {
    // F#4 = 66, pitch class 6 -> step F, alter 1
    Sentence s = sentenceIn("G major", quarterNotePhrase(66, 66, 66, 66));
    File out = tempDir.resolve("sharp.musicxml").toFile();
    MusicXMLExporter.export(s, out, 120);
    String xml = Files.readString(out.toPath());
    assertTrue(xml.contains("<step>F</step>"));
    assertTrue(xml.contains("<alter>1</alter>"));
    assertTrue(xml.contains("<fifths>1</fifths>"));
  }

  @Test
  void exportMinorKeyUsesCorrectFifthsAndMode() throws Exception {
    Sentence s = sentenceIn("A minor", quarterNotePhrase(69, 71, 72, 74));
    File out = tempDir.resolve("aminor.musicxml").toFile();
    MusicXMLExporter.export(s, out, 120);
    String xml = Files.readString(out.toPath());
    assertTrue(xml.contains("<mode>minor</mode>"));
    // A minor has 0 fifths (relative to C major, adjusted by -3 in the exporter)
    assertTrue(xml.contains("<fifths>0</fifths>"));
  }

  @Test
  void exportExoticKeyNamesFallBackToDefaults() throws Exception {
    // Unknown root triggers the default switch branch (fifths=0)
    Sentence s = sentenceIn("Q major", quarterNotePhrase(60, 62, 64, 65));
    File out = tempDir.resolve("unknown_key.musicxml").toFile();
    MusicXMLExporter.export(s, out, 120);
    assertTrue(Files.readString(out.toPath()).contains("<fifths>0</fifths>"));
  }

  @Test
  void exportHandlesSentenceWithRestNote() throws Exception {
    List<Note> mixed = List.of(
        new Note(60, 0, TPB, 90),
        new Note(Note.REST, TPB, TPB, 0),
        new Note(64, TPB * 2, TPB, 90),
        new Note(65, TPB * 3, TPB, 90));
    Motif phrase = new Motif(mixed, 1, BPB, TPB);
    Sentence s = sentenceIn("C major", phrase);

    File out = tempDir.resolve("rest.musicxml").toFile();
    MusicXMLExporter.export(s, out, 120);
    String xml = Files.readString(out.toPath());
    assertTrue(xml.contains("<rest"));
  }

  @Test
  void defaultTempoOverloadSucceeds() throws Exception {
    Sentence s = sentenceIn("C major", quarterNotePhrase(60, 62, 64, 65));
    File out = tempDir.resolve("default.musicxml").toFile();
    MusicXMLExporter.export(s, out);
    assertTrue(out.exists());
  }

  @Test
  void notesWithVariousDurationsProduceDifferentTypes() throws Exception {
    // Mix of whole (4 beats), half (2), quarter (1), eighth (0.5)
    List<Note> notes = List.of(
        new Note(60, 0, TPB * 4, 90),
        new Note(62, TPB * 4, TPB * 2, 90),
        new Note(64, TPB * 6, TPB, 90),
        new Note(65, TPB * 7, TPB / 2, 90));
    Motif phrase = new Motif(notes, 2, BPB, TPB);
    Sentence s = new Sentence(List.of(phrase), "a", "C major", 0);

    File out = tempDir.resolve("types.musicxml").toFile();
    MusicXMLExporter.export(s, out, 120);
    String xml = Files.readString(out.toPath());

    assertTrue(xml.contains("<type>whole</type>"));
    assertTrue(xml.contains("<type>half</type>"));
    assertTrue(xml.contains("<type>quarter</type>"));
    assertTrue(xml.contains("<type>eighth</type>"));
  }
}
