package com.motifgen.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MotifTest {

  private static final int TICKS_PER_BEAT = 480;
  private static final int BEATS_PER_BAR = 4;

  private Motif cMajorScale() {
    List<Note> notes = List.of(
        new Note(60, 0, 480, 90),    // C
        new Note(62, 480, 480, 90),  // D
        new Note(64, 960, 480, 90),  // E
        new Note(65, 1440, 480, 90)  // F
    );
    return new Motif(notes, 1, BEATS_PER_BAR, TICKS_PER_BEAT);
  }

  @Test
  void constructorStoresMetadataAndImmutableNotes() {
    Motif m = cMajorScale();
    assertEquals(1, m.getBars());
    assertEquals(BEATS_PER_BAR, m.getBeatsPerBar());
    assertEquals(TICKS_PER_BEAT, m.getTicksPerBeat());
    assertEquals(BEATS_PER_BAR * TICKS_PER_BEAT, m.getTicksPerBar());
    assertEquals(4, m.getNotes().size());
    assertThrows(UnsupportedOperationException.class,
        () -> m.getNotes().add(new Note(60, 0, 100, 80)));
  }

  @Test
  void totalTicksMultipliesBarsByTicksPerBar() {
    Motif m = new Motif(List.of(), 4, 4, 480);
    assertEquals(4L * 4 * 480, m.totalTicks());
  }

  @Test
  void shiftToAddsOffsetToAllStartTicks() {
    Motif m = cMajorScale();
    Motif shifted = m.shiftTo(1920);
    assertEquals(1920, shifted.getNotes().get(0).startTick());
    assertEquals(1920 + 480, shifted.getNotes().get(1).startTick());
    assertEquals(m.getBars(), shifted.getBars());
  }

  @Test
  void transposeAppliesToEveryNote() {
    Motif m = cMajorScale();
    Motif up = m.transpose(12);
    assertEquals(72, up.getNotes().get(0).pitch());
    assertEquals(77, up.getNotes().get(3).pitch());
  }

  @Test
  void withBarsIdenticalReturnsSameInstance() {
    Motif m = cMajorScale();
    assertSame(m, m.withBars(1));
  }

  @Test
  void withBarsShrinkTruncatesAndClipsDurations() {
    List<Note> notes = List.of(
        new Note(60, 0, 1920, 90),     // spans a full 4-beat bar
        new Note(62, 1920, 1920, 90),  // starts at bar 2
        new Note(64, 3840, 1920, 90)   // starts at bar 3 — dropped when shrinking to 1 bar
    );
    Motif m = new Motif(notes, 3, BEATS_PER_BAR, TICKS_PER_BEAT);
    Motif shrunk = m.withBars(1);
    assertEquals(1, shrunk.getBars());
    assertEquals(1, shrunk.getNotes().size());
    assertEquals(1920, shrunk.getNotes().get(0).durationTicks());
  }

  @Test
  void withBarsShrinkClipsPartialNoteDuration() {
    List<Note> notes = List.of(new Note(60, 0, 3840, 90)); // 2 bars long
    Motif m = new Motif(notes, 2, BEATS_PER_BAR, TICKS_PER_BEAT);
    Motif shrunk = m.withBars(1);
    assertEquals(1920, shrunk.getNotes().get(0).durationTicks());
  }

  @Test
  void pitchClassesDistinctSortedExcludingRests() {
    List<Note> notes = List.of(
        new Note(60, 0, 480, 90),    // C  -> 0
        new Note(64, 480, 480, 90),  // E  -> 4
        new Note(Note.REST, 960, 480, 0),
        new Note(72, 1440, 480, 90)  // C5 -> 0 (deduped)
    );
    Motif m = new Motif(notes, 1, BEATS_PER_BAR, TICKS_PER_BEAT);
    assertEquals(List.of(0, 4), m.pitchClasses());
  }

  @Test
  void pitchesExcludesRests() {
    List<Note> notes = List.of(
        new Note(60, 0, 480, 90),
        new Note(Note.REST, 480, 480, 0),
        new Note(64, 960, 480, 90)
    );
    Motif m = new Motif(notes, 1, BEATS_PER_BAR, TICKS_PER_BEAT);
    assertEquals(List.of(60, 64), m.pitches());
  }

  @Test
  void toStringSummarizesBarsAndNoteCount() {
    Motif m = cMajorScale();
    assertEquals("Motif[bars=1, notes=4]", m.toString());
  }
}
