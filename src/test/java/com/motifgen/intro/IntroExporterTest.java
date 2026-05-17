package com.motifgen.intro;

import com.motifgen.exporter.MidiExporter;
import com.motifgen.exporter.MusicXMLExporter;
import com.motifgen.guitar.backing.BackingTrack;
import com.motifgen.guitar.backing.BassTrack;
import com.motifgen.guitar.backing.ChanneledNote;
import com.motifgen.guitar.backing.DrumTrack;
import com.motifgen.model.Motif;
import com.motifgen.model.Note;
import com.motifgen.model.Sentence;
import com.motifgen.sentiment.SentimentProfile;
import com.motifgen.theory.KeySignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.sound.midi.MidiSystem;
import javax.sound.midi.Sequence;
import java.io.File;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the new {@link MidiExporter} and {@link MusicXMLExporter} overloads that prepend an
 * {@link IntroTrack} before the sentence.
 *
 * <p>Criteria covered:
 * <ul>
 *   <li>Intro prepended to MIDI export — sentence ticks shifted by offsetTicks.</li>
 *   <li>Intro prepended to MusicXML export — sentence starts at measure 5.</li>
 *   <li>Existing overloads remain callable (no regression).</li>
 * </ul>
 */
class IntroExporterTest {

  private static final int PPQ = 480;
  private static final int BPB = 4;
  private static final KeySignature C_MAJOR = KeySignature.major(0);

  @TempDir
  Path tempDir;

  private Sentence sentence;
  private IntroTrack intro;
  private BackingTrack backing;
  private BassTrack bass;
  private DrumTrack drums;

  @BeforeEach
  void setUp() {
    // Minimal 4-bar sentence.
    List<Note> notes = List.of(
        new Note(60, 0L,           PPQ, 80),
        new Note(62, (long) PPQ,   PPQ, 75),
        new Note(64, (long) PPQ*2, PPQ, 70),
        new Note(65, (long) PPQ*3, PPQ, 65));
    Motif motif = new Motif(notes, 4, BPB, PPQ);
    sentence = new Sentence(List.of(motif), "a a' b a''", "C major", 75.0);

    // Build a real intro via IntroGenerator.
    IntroContext ctx = IntroContext.of(
        SentimentProfile.fromVA(0.7, 0.8), C_MAJOR, "driving", PPQ, BPB);
    intro = new IntroGenerator().generate(ctx);

    // Stub backing / bass / drums (empty is fine for export round-trip tests).
    backing = new BackingTrack(List.of(), BackingTrack.GUITAR_PROGRAM, 0.0);
    bass    = new BassTrack(List.of(), BassTrack.BASS_PROGRAM, 0.0);
    drums   = new DrumTrack(List.of());
  }

  // -----------------------------------------------------------------------
  // MIDI export with intro
  // -----------------------------------------------------------------------

  @Test
  void midiExport_withIntro_fileCreated() throws Exception {
    File out = tempDir.resolve("intro_sentence.mid").toFile();
    MidiExporter.export(intro, sentence, out);
    assertTrue(out.exists() && out.length() > 0, "MIDI file should be created");
  }

  @Test
  void midiExport_withIntro_sequenceIsReadable() throws Exception {
    File out = tempDir.resolve("intro_sentence.mid").toFile();
    MidiExporter.export(intro, sentence, out);
    Sequence seq = MidiSystem.getSequence(out);
    assertNotNull(seq);
  }

  @Test
  void midiExport_withIntro_sentenceTicksShiftedByOffset() throws Exception {
    File out = tempDir.resolve("intro_sentence_shifted.mid").toFile();
    MidiExporter.export(intro, sentence, out);
    Sequence seq = MidiSystem.getSequence(out);

    // The last tick in the sequence should be > offsetTicks (intro + sentence).
    long totalTicks = seq.getTickLength();
    long offsetTicks = intro.offsetTicks(); // 4 * 4 * 480 = 7680
    assertTrue(totalTicks > offsetTicks,
        "Sequence length should exceed the intro offset (sentence was shifted)");
  }

  @Test
  void midiExport_fullOverload_withIntro_fileCreated() throws Exception {
    File out = tempDir.resolve("full_intro.mid").toFile();
    MidiExporter.export(intro, sentence, backing, bass, drums, out);
    assertTrue(out.exists() && out.length() > 0);
  }

  // -----------------------------------------------------------------------
  // MusicXML export with intro
  // -----------------------------------------------------------------------

  @Test
  void musicXmlExport_withIntro_fileCreated() throws Exception {
    File out = tempDir.resolve("intro_sentence.musicxml").toFile();
    MusicXMLExporter.export(intro, sentence, out);
    assertTrue(out.exists() && out.length() > 0, "MusicXML file should be created");
  }

  @Test
  void musicXmlExport_withIntro_containsMeasure5() throws Exception {
    File out = tempDir.resolve("intro_m5.musicxml").toFile();
    MusicXMLExporter.export(intro, sentence, out);
    String xml = java.nio.file.Files.readString(out.toPath());
    assertTrue(xml.contains("number=\"5\""),
        "MusicXML should contain measure 5 (sentence starts after 4 intro bars)");
  }

  @Test
  void musicXmlExport_withIntro_containsMeasures1to4() throws Exception {
    File out = tempDir.resolve("intro_m1to4.musicxml").toFile();
    MusicXMLExporter.export(intro, sentence, out);
    String xml = java.nio.file.Files.readString(out.toPath());
    assertTrue(xml.contains("number=\"1\""), "MusicXML should contain intro measure 1");
    assertTrue(xml.contains("number=\"4\""), "MusicXML should contain intro measure 4");
  }

  @Test
  void musicXmlExport_fullOverload_withIntro_fileCreated() throws Exception {
    File out = tempDir.resolve("full_intro.musicxml").toFile();
    MusicXMLExporter.export(intro, sentence, backing, bass, drums, out);
    assertTrue(out.exists() && out.length() > 0);
  }

  // -----------------------------------------------------------------------
  // Existing overloads unchanged (regression guard)
  // -----------------------------------------------------------------------

  @Test
  void midiExport_existingOverload_stillWorks() throws Exception {
    File out = tempDir.resolve("no_intro.mid").toFile();
    MidiExporter.export(sentence, out);
    assertTrue(out.exists() && out.length() > 0);
  }

  @Test
  void musicXmlExport_existingOverload_stillWorks() throws Exception {
    File out = tempDir.resolve("no_intro.musicxml").toFile();
    MusicXMLExporter.export(sentence, out);
    assertTrue(out.exists() && out.length() > 0);
  }
}
