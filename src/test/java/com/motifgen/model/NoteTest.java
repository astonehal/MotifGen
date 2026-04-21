package com.motifgen.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NoteTest {

  @Test
  void recordAccessorsReturnFields() {
    Note n = new Note(60, 100, 480, 90);
    assertEquals(60, n.pitch());
    assertEquals(100, n.startTick());
    assertEquals(480, n.durationTicks());
    assertEquals(90, n.velocity());
  }

  @Test
  void restIsIdentifiedByPitchSentinel() {
    Note rest = new Note(Note.REST, 0, 100, 0);
    assertTrue(rest.isRest());
    assertEquals(-1, rest.pitchClass());
    assertEquals(-1, rest.octave());
  }

  @Test
  void nonRestNoteReportsPitchClassAndOctave() {
    Note middleC = new Note(60, 0, 480, 90);
    assertFalse(middleC.isRest());
    assertEquals(0, middleC.pitchClass());
    assertEquals(4, middleC.octave());

    Note gSharp5 = new Note(80, 0, 480, 90);
    assertEquals(8, gSharp5.pitchClass());
    assertEquals(5, gSharp5.octave());
  }

  @Test
  void transposeShiftsPitch() {
    Note n = new Note(60, 0, 480, 90);
    assertEquals(67, n.transpose(7).pitch());
    assertEquals(48, n.transpose(-12).pitch());
  }

  @Test
  void transposeClampsToMidiRange() {
    assertEquals(0, new Note(5, 0, 100, 80).transpose(-100).pitch());
    assertEquals(127, new Note(120, 0, 100, 80).transpose(100).pitch());
  }

  @Test
  void transposeOnRestReturnsSameInstance() {
    Note rest = new Note(Note.REST, 0, 100, 0);
    assertSame(rest, rest.transpose(5));
  }

  @Test
  void withersReturnModifiedCopies() {
    Note n = new Note(60, 0, 480, 90);

    Note shifted = n.withStartTick(200);
    assertEquals(200, shifted.startTick());
    assertEquals(60, shifted.pitch());

    Note longer = n.withDuration(960);
    assertEquals(960, longer.durationTicks());

    Note louder = n.withVelocity(120);
    assertEquals(120, louder.velocity());
  }

  @Test
  void endTickAddsDurationToStart() {
    Note n = new Note(60, 100, 480, 90);
    assertEquals(580, n.endTick());
  }
}
