package com.motifgen.loader;

import com.motifgen.model.Motif;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class MusicXMLLoaderTest {

  @TempDir
  Path tempDir;

  private File writeXml(String name, String body) throws Exception {
    File f = tempDir.resolve(name).toFile();
    Files.writeString(f.toPath(), body);
    return f;
  }

  private static final String HEADER = """
      <?xml version="1.0" encoding="UTF-8"?>
      <score-partwise version="4.0">
        <part-list>
          <score-part id="P1"><part-name>Melody</part-name></score-part>
        </part-list>
        <part id="P1">
      """;

  private static final String FOOTER = """
        </part>
      </score-partwise>
      """;

  @Test
  void loadsSingleMeasureMelodyAndParsesKeySignature() throws Exception {
    String body = HEADER + """
        <measure number="1">
          <attributes>
            <divisions>1</divisions>
            <key><fifths>0</fifths><mode>major</mode></key>
            <time><beats>4</beats><beat-type>4</beat-type></time>
          </attributes>
          <note><pitch><step>C</step><octave>4</octave></pitch><duration>1</duration></note>
          <note><pitch><step>D</step><octave>4</octave></pitch><duration>1</duration></note>
          <note><pitch><step>E</step><octave>4</octave></pitch><duration>1</duration></note>
          <note><pitch><step>F</step><octave>4</octave></pitch><duration>1</duration></note>
        </measure>
        """ + FOOTER;

    File f = writeXml("simple.musicxml", body);
    Motif m = MusicXMLLoader.load(f, 1);

    assertEquals(4, m.getBeatsPerBar());
    assertEquals(4, m.getNotes().size());
    assertEquals(60, m.getNotes().get(0).pitch()); // C4
    assertEquals(62, m.getNotes().get(1).pitch()); // D4
    assertEquals(64, m.getNotes().get(2).pitch()); // E4
    assertEquals(65, m.getNotes().get(3).pitch()); // F4
    // Default velocity of 80
    assertEquals(80, m.getNotes().get(0).velocity());
  }

  @Test
  void parsesAccidentalsRestsAndExplicitDynamics() throws Exception {
    String body = HEADER + """
        <measure number="1">
          <attributes><divisions>2</divisions></attributes>
          <note>
            <pitch><step>F</step><alter>1</alter><octave>4</octave></pitch>
            <duration>2</duration>
            <dynamics>100</dynamics>
          </note>
          <note><rest/><duration>2</duration></note>
          <note>
            <pitch><step>B</step><alter>-1</alter><octave>4</octave></pitch>
            <duration>2</duration>
          </note>
        </measure>
        """ + FOOTER;

    File f = writeXml("accidentals.musicxml", body);
    Motif m = MusicXMLLoader.load(f, 1);

    // F# = 66, rest filtered out of final motif, Bb = 70
    assertEquals(2, m.getNotes().size());
    assertEquals(66, m.getNotes().get(0).pitch());
    assertEquals(100, m.getNotes().get(0).velocity());
    assertEquals(70, m.getNotes().get(1).pitch());
  }

  @Test
  void parsesChordsAsSimultaneousNotes() throws Exception {
    String body = HEADER + """
        <measure number="1">
          <attributes><divisions>1</divisions></attributes>
          <note><pitch><step>C</step><octave>4</octave></pitch><duration>1</duration></note>
          <note><chord/><pitch><step>E</step><octave>4</octave></pitch><duration>1</duration></note>
          <note><chord/><pitch><step>G</step><octave>4</octave></pitch><duration>1</duration></note>
        </measure>
        """ + FOOTER;

    File f = writeXml("chord.musicxml", body);
    Motif m = MusicXMLLoader.load(f, 1);

    // All three notes should share the same start tick
    assertEquals(3, m.getNotes().size());
    long start = m.getNotes().get(0).startTick();
    assertEquals(start, m.getNotes().get(1).startTick());
    assertEquals(start, m.getNotes().get(2).startTick());
  }

  @Test
  void respectsBarLimit() throws Exception {
    String body = HEADER + """
        <measure number="1">
          <attributes><divisions>1</divisions></attributes>
          <note><pitch><step>C</step><octave>4</octave></pitch><duration>1</duration></note>
        </measure>
        <measure number="2">
          <note><pitch><step>D</step><octave>4</octave></pitch><duration>1</duration></note>
        </measure>
        """ + FOOTER;

    File f = writeXml("two_bars.musicxml", body);
    Motif m = MusicXMLLoader.load(f, 1);
    assertEquals(1, m.getNotes().size());
    assertEquals(60, m.getNotes().get(0).pitch());
  }

  @Test
  void throwsWhenNoNotesArePresent() throws Exception {
    String body = HEADER + """
        <measure number="1">
          <attributes><divisions>1</divisions></attributes>
        </measure>
        """ + FOOTER;

    File f = writeXml("empty.musicxml", body);
    assertThrows(IllegalArgumentException.class, () -> MusicXMLLoader.load(f, 1));
  }

  @Test
  void nonNumericBeatsFallsBackToFourFour() throws Exception {
    String body = HEADER + """
        <measure number="1">
          <attributes>
            <divisions>1</divisions>
            <time><beats>abc</beats><beat-type>4</beat-type></time>
          </attributes>
          <note><pitch><step>C</step><octave>4</octave></pitch><duration>1</duration></note>
        </measure>
        """ + FOOTER;

    File f = writeXml("bad_beats.musicxml", body);
    Motif m = MusicXMLLoader.load(f, 1);
    assertEquals(4, m.getBeatsPerBar());
  }
}
