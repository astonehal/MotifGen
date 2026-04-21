package com.motifgen.loader;

import com.motifgen.model.Motif;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.sound.midi.*;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class MotifLoaderTest {

  @TempDir
  Path tempDir;

  private File writeMidi(String name) throws Exception {
    Sequence seq = new Sequence(Sequence.PPQ, 480);
    Track t = seq.createTrack();
    ShortMessage on = new ShortMessage();
    on.setMessage(ShortMessage.NOTE_ON, 0, 60, 90);
    t.add(new MidiEvent(on, 0));
    ShortMessage off = new ShortMessage();
    off.setMessage(ShortMessage.NOTE_OFF, 0, 60, 0);
    t.add(new MidiEvent(off, 480));

    File f = tempDir.resolve(name).toFile();
    MidiSystem.write(seq, 1, f);
    return f;
  }

  private File writeXml(String name, String ext) throws Exception {
    String body = """
        <?xml version="1.0" encoding="UTF-8"?>
        <score-partwise version="4.0">
          <part-list>
            <score-part id="P1"><part-name>Melody</part-name></score-part>
          </part-list>
          <part id="P1">
            <measure number="1">
              <attributes><divisions>1</divisions></attributes>
              <note><pitch><step>C</step><octave>4</octave></pitch><duration>1</duration></note>
            </measure>
          </part>
        </score-partwise>
        """;
    File f = tempDir.resolve(name + "." + ext).toFile();
    Files.writeString(f.toPath(), body);
    return f;
  }

  @Test
  void dispatchesMidExtensionToMidiLoader() throws Exception {
    Motif m = MotifLoader.load(writeMidi("song.mid").getAbsolutePath(), 4);
    assertNotNull(m);
    assertFalse(m.getNotes().isEmpty());
  }

  @Test
  void dispatchesMidiExtensionToMidiLoader() throws Exception {
    Motif m = MotifLoader.load(writeMidi("song.midi").getAbsolutePath(), 4);
    assertNotNull(m);
  }

  @Test
  void dispatchesXmlExtensionToMusicXMLLoader() throws Exception {
    Motif m = MotifLoader.load(writeXml("piece", "xml").getAbsolutePath(), 1);
    assertEquals(60, m.getNotes().get(0).pitch());
  }

  @Test
  void dispatchesMusicXmlExtensionToMusicXMLLoader() throws Exception {
    Motif m = MotifLoader.load(writeXml("piece", "musicxml").getAbsolutePath(), 1);
    assertEquals(60, m.getNotes().get(0).pitch());
  }

  @Test
  void throwsWhenFileMissing() {
    File missing = tempDir.resolve("nope.mid").toFile();
    IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
        () -> MotifLoader.load(missing.getAbsolutePath(), 4));
    assertTrue(ex.getMessage().contains("File not found"));
  }

  @Test
  void throwsOnUnknownExtension() throws Exception {
    File f = tempDir.resolve("unknown.txt").toFile();
    Files.writeString(f.toPath(), "hello");
    IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
        () -> MotifLoader.load(f.getAbsolutePath(), 4));
    assertTrue(ex.getMessage().contains("Unsupported file format"));
  }
}
