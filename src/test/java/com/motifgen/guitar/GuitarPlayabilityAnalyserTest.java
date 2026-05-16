package com.motifgen.guitar;

import com.motifgen.model.Note;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link GuitarPlayabilityAnalyser} covering Scenarios 1 and 2.
 */
class GuitarPlayabilityAnalyserTest {

  private static final int TICKS_PER_BEAT = 480;

  // Helper: single pitched note at given MIDI pitch
  private Note note(int pitch) {
    return new Note(pitch, 0, TICKS_PER_BEAT, 90);
  }

  // Helper: build a simple in-range melody of n identical notes
  private List<Note> inRangeMelody(int pitch, int count) {
    List<Note> notes = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      notes.add(new Note(pitch, (long) i * TICKS_PER_BEAT, TICKS_PER_BEAT, 90));
    }
    return notes;
  }

  // -----------------------------------------------------------------------
  // Scenario 1: Out-of-range note → Unplayable
  // -----------------------------------------------------------------------

  @Test
  void pitchBelowRangeIsUnplayable() {
    List<Note> notes = List.of(note(39)); // below 40
    GuitarPlayabilityResult result = GuitarPlayabilityAnalyser.analyse(notes, TICKS_PER_BEAT);
    assertInstanceOf(GuitarPlayabilityResult.Unplayable.class, result,
        "Pitch 39 is below range [40,88] — should be Unplayable");
  }

  @Test
  void pitchAboveRangeIsUnplayable() {
    List<Note> notes = List.of(note(89)); // above 88
    GuitarPlayabilityResult result = GuitarPlayabilityAnalyser.analyse(notes, TICKS_PER_BEAT);
    assertInstanceOf(GuitarPlayabilityResult.Unplayable.class, result,
        "Pitch 89 is above range [40,88] — should be Unplayable");
  }

  @Test
  void unplayableResultIdentifiesOutOfRangeNoteIndex() {
    // In-range note first, then out-of-range at index 1
    List<Note> notes = List.of(
        new Note(60, 0, TICKS_PER_BEAT, 90),
        new Note(30, TICKS_PER_BEAT, TICKS_PER_BEAT, 90)); // pitch 30 < 40
    GuitarPlayabilityResult result = GuitarPlayabilityAnalyser.analyse(notes, TICKS_PER_BEAT);
    assertInstanceOf(GuitarPlayabilityResult.Unplayable.class, result);
    GuitarPlayabilityResult.Unplayable u = (GuitarPlayabilityResult.Unplayable) result;
    assertEquals(1, u.noteIndex(), "Out-of-range note is at index 1");
    assertTrue(u.reason().contains("index 1"),
        "Reason should mention note index 1, got: " + u.reason());
  }

  @Test
  void restNotesAreIgnoredInRangeCheck() {
    // Rest followed by valid note — should pass
    List<Note> notes = List.of(
        new Note(Note.REST, 0, TICKS_PER_BEAT, 0),
        new Note(60, TICKS_PER_BEAT, TICKS_PER_BEAT, 90));
    GuitarPlayabilityResult result = GuitarPlayabilityAnalyser.analyse(notes, TICKS_PER_BEAT);
    assertInstanceOf(GuitarPlayabilityResult.Playable.class, result,
        "Rests should be ignored in range check");
  }

  @ParameterizedTest
  @ValueSource(ints = {40, 60, 88})
  void boundaryPitchesAreInRange(int pitch) {
    List<Note> notes = List.of(note(pitch));
    GuitarPlayabilityResult result = GuitarPlayabilityAnalyser.analyse(notes, TICKS_PER_BEAT);
    assertInstanceOf(GuitarPlayabilityResult.Playable.class, result,
        "Pitch " + pitch + " is within range [40,88] — should be Playable");
  }

  // -----------------------------------------------------------------------
  // Scenario 2: In-range melody → Playable with label and fingering
  // -----------------------------------------------------------------------

  @Test
  void inRangeMelodyReturnsPlayable() {
    List<Note> notes = inRangeMelody(60, 4);
    GuitarPlayabilityResult result = GuitarPlayabilityAnalyser.analyse(notes, TICKS_PER_BEAT);
    assertInstanceOf(GuitarPlayabilityResult.Playable.class, result);
  }

  @Test
  void playableResultHasValidLabel() {
    List<String> validLabels = List.of(
        "beginner-friendly", "intermediate", "advanced",
        "difficult but playable", "impractical");
    List<Note> notes = inRangeMelody(60, 4);
    GuitarPlayabilityResult result = GuitarPlayabilityAnalyser.analyse(notes, TICKS_PER_BEAT);
    GuitarPlayabilityResult.Playable p = (GuitarPlayabilityResult.Playable) result;
    assertTrue(validLabels.contains(p.label()),
        "Label should be one of the defined values, got: " + p.label());
  }

  @Test
  void playableResultHasFingeringWithOnePositionPerPitchedNote() {
    List<Note> notes = List.of(
        new Note(60, 0, TICKS_PER_BEAT, 90),
        new Note(Note.REST, TICKS_PER_BEAT, TICKS_PER_BEAT, 0),
        new Note(64, TICKS_PER_BEAT * 2L, TICKS_PER_BEAT, 90));
    GuitarPlayabilityResult result = GuitarPlayabilityAnalyser.analyse(notes, TICKS_PER_BEAT);
    GuitarPlayabilityResult.Playable p = (GuitarPlayabilityResult.Playable) result;
    assertEquals(2, p.fingering().size(),
        "Fingering should have one position per non-rest note");
  }

  @Test
  void playableFingeringPositionsAreWithinPhysicalBounds() {
    List<Note> notes = inRangeMelody(60, 6);
    GuitarPlayabilityResult result = GuitarPlayabilityAnalyser.analyse(notes, TICKS_PER_BEAT);
    GuitarPlayabilityResult.Playable p = (GuitarPlayabilityResult.Playable) result;
    for (GuitarFingerPosition pos : p.fingering()) {
      assertTrue(pos.string() >= 1 && pos.string() <= 6,
          "String out of range: " + pos.string());
      assertTrue(pos.fret() >= 0 && pos.fret() <= 22,
          "Fret out of range: " + pos.fret());
    }
  }

  @Test
  void labelClassificationBeginnerFriendly() {
    assertEquals("beginner-friendly", GuitarPlayabilityAnalyser.labelFor(0.0));
    assertEquals("beginner-friendly", GuitarPlayabilityAnalyser.labelFor(1.0));
  }

  @Test
  void labelClassificationIntermediate() {
    assertEquals("intermediate", GuitarPlayabilityAnalyser.labelFor(1.1));
    assertEquals("intermediate", GuitarPlayabilityAnalyser.labelFor(2.0));
  }

  @Test
  void labelClassificationAdvanced() {
    assertEquals("advanced", GuitarPlayabilityAnalyser.labelFor(2.1));
    assertEquals("advanced", GuitarPlayabilityAnalyser.labelFor(3.5));
  }

  @Test
  void labelClassificationDifficultButPlayable() {
    assertEquals("difficult but playable", GuitarPlayabilityAnalyser.labelFor(3.6));
    assertEquals("difficult but playable", GuitarPlayabilityAnalyser.labelFor(5.0));
  }

  @Test
  void labelClassificationImpractical() {
    assertEquals("impractical", GuitarPlayabilityAnalyser.labelFor(5.1));
    assertEquals("impractical", GuitarPlayabilityAnalyser.labelFor(100.0));
  }

  @Test
  void nullNotesThrowsIllegalArgument() {
    assertThrows(IllegalArgumentException.class,
        () -> GuitarPlayabilityAnalyser.analyse(null, TICKS_PER_BEAT));
  }

  @Test
  void nonPositiveTicksPerBeatThrowsIllegalArgument() {
    assertThrows(IllegalArgumentException.class,
        () -> GuitarPlayabilityAnalyser.analyse(inRangeMelody(60, 2), 0));
  }
}
