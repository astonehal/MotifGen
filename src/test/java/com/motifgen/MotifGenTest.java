package com.motifgen;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.sound.midi.*;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class MotifGenTest {

  @TempDir
  static Path tempDir;

  private static File testMidi;

  @BeforeAll
  static void createTestMidi() throws Exception {
    int tpb = 480;
    Sequence seq = new Sequence(Sequence.PPQ, tpb);
    Track track = seq.createTrack();

    MetaMessage timeSig = new MetaMessage();
    timeSig.setMessage(0x58, new byte[]{4, 2, 24, 8}, 4);
    track.add(new MidiEvent(timeSig, 0));

    MetaMessage tempo = new MetaMessage();
    tempo.setMessage(0x51, new byte[]{0x07, (byte) 0xA1, 0x20}, 3); // 120 BPM
    track.add(new MidiEvent(tempo, 0));

    int[] pitches = {60, 62, 64, 65, 67, 65, 64, 62,
                     60, 62, 64, 67, 65, 64, 62, 60};
    long tick = 0;
    for (int pitch : pitches) {
      ShortMessage on = new ShortMessage();
      on.setMessage(ShortMessage.NOTE_ON, 0, pitch, 90);
      track.add(new MidiEvent(on, tick));
      ShortMessage off = new ShortMessage();
      off.setMessage(ShortMessage.NOTE_OFF, 0, pitch, 0);
      track.add(new MidiEvent(off, tick + tpb));
      tick += tpb;
    }

    testMidi = tempDir.resolve("motif.mid").toFile();
    MidiSystem.write(seq, 1, testMidi);
  }

  @Test
  void runMidiFormatProducesMidOutputs() throws Exception {
    File out = tempDir.resolve("out_midi").toFile();
    MotifGen.run(testMidi.getAbsolutePath(), out.getAbsolutePath(), 120);

    File[] mids = out.listFiles((d, n) -> n.endsWith(".mid"));
    assertNotNull(mids);
    assertTrue(mids.length >= 1);
  }

  @Test
  void runMusicXmlFormatProducesOnlyXmlOutputs() throws Exception {
    File out = tempDir.resolve("out_xml").toFile();
    MotifGen.run(testMidi.getAbsolutePath(), out.getAbsolutePath(), 120,
        MotifGen.OutputFormat.MUSICXML);

    File[] xmls = out.listFiles((d, n) -> n.endsWith(".musicxml"));
    File[] mids = out.listFiles((d, n) -> n.endsWith(".mid"));
    assertNotNull(xmls);
    assertNotNull(mids);
    assertTrue(xmls.length >= 1);
    assertEquals(0, mids.length);
  }

  @Test
  void runBothFormatProducesMidAndXmlOutputs() throws Exception {
    File out = tempDir.resolve("out_both").toFile();
    MotifGen.run(testMidi.getAbsolutePath(), out.getAbsolutePath(), 100,
        MotifGen.OutputFormat.BOTH);

    assertTrue(out.listFiles((d, n) -> n.endsWith(".mid")).length >= 1);
    assertTrue(out.listFiles((d, n) -> n.endsWith(".musicxml")).length >= 1);
  }

  @Test
  void runCreatesOutputDirectoryIfMissing() throws Exception {
    File out = tempDir.resolve("not_yet_there").toFile();
    assertFalse(out.exists());
    MotifGen.run(testMidi.getAbsolutePath(), out.getAbsolutePath(), 120);
    assertTrue(out.exists());
    assertTrue(out.isDirectory());
  }

  @Test
  void mainWithValidArgsExecutesFullPipeline() throws Exception {
    File out = tempDir.resolve("main_out").toFile();
    Files.createDirectories(out.toPath());
    // Valid args: no System.exit path taken
    MotifGen.main(new String[]{
        testMidi.getAbsolutePath(),
        out.getAbsolutePath(),
        "96",
        "both"
    });
    assertTrue(out.listFiles((d, n) -> n.endsWith(".mid")).length >= 1);
    assertTrue(out.listFiles((d, n) -> n.endsWith(".musicxml")).length >= 1);
  }

  @Test
  void mainAcceptsFormatAliases() throws Exception {
    File out = tempDir.resolve("alias_out").toFile();
    Files.createDirectories(out.toPath());
    MotifGen.main(new String[]{
        testMidi.getAbsolutePath(),
        out.getAbsolutePath(),
        "120",
        "xml" // alias for musicxml
    });
    assertTrue(out.listFiles((d, n) -> n.endsWith(".musicxml")).length >= 1);
  }

  @Test
  void mainWithUnknownFormatDefaultsToMidi() throws Exception {
    File out = tempDir.resolve("bad_format_out").toFile();
    Files.createDirectories(out.toPath());
    MotifGen.main(new String[]{
        testMidi.getAbsolutePath(),
        out.getAbsolutePath(),
        "120",
        "bogus" // falls into default branch
    });
    assertTrue(out.listFiles((d, n) -> n.endsWith(".mid")).length >= 1);
  }

  @Test
  void outputFormatEnumHasExpectedValues() {
    assertEquals(3, MotifGen.OutputFormat.values().length);
    assertNotNull(MotifGen.OutputFormat.valueOf("MIDI"));
    assertNotNull(MotifGen.OutputFormat.valueOf("MUSICXML"));
    assertNotNull(MotifGen.OutputFormat.valueOf("BOTH"));
  }
}
