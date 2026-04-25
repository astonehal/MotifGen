package com.motifgen.generator.catchy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.motifgen.model.Motif;
import com.motifgen.model.Note;
import com.motifgen.theory.KeySignature;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class MotifTransformerTest {

  private static final int TPB = 480;
  private static final int BPB = 4;

  /** C D E F-half | each note has a deliberately distinct duration. */
  private Motif rhythmicallyVariedMotif() {
    List<Note> notes = new ArrayList<>();
    notes.add(new Note(60, 0, TPB, 90));            // C quarter
    notes.add(new Note(62, TPB, TPB / 2, 90));      // D eighth
    notes.add(new Note(64, TPB + TPB / 2, TPB, 90));// E quarter
    notes.add(new Note(65, 2 * TPB + TPB / 2, 2L * TPB, 90)); // F half
    return new Motif(notes, 4, BPB, TPB);
  }

  private List<Long> durations(Motif m) {
    return m.getNotes().stream().map(Note::durationTicks).toList();
  }

  private List<Integer> pitches(Motif m) {
    return m.getNotes().stream().map(Note::pitch).toList();
  }

  @Test
  void identityReturnsNoteForNoteEqualMotif() {
    MotifTransformer t = new MotifTransformer();
    Motif input = rhythmicallyVariedMotif();
    Motif out = t.identity(input);

    assertEquals(pitches(input), pitches(out));
    assertEquals(durations(input), durations(out));
    assertEquals(input.getBars(), out.getBars());
  }

  @Test
  void diatonicTransposeUpOneStepInCMajor() {
    MotifTransformer t = new MotifTransformer();
    Motif out = t.diatonicTranspose(rhythmicallyVariedMotif(), 1, KeySignature.major(0));

    // C D E F  ->  D E F G  (in C major)
    assertEquals(List.of(62, 64, 65, 67), pitches(out));
  }

  @Test
  void diatonicTransposePreservesEveryDuration() {
    MotifTransformer t = new MotifTransformer();
    Motif input = rhythmicallyVariedMotif();
    Motif out = t.diatonicTranspose(input, 4, KeySignature.major(0));

    assertEquals(durations(input), durations(out));
  }

  @Test
  void diatonicTransposeAcrossOctaveBoundaryWrapsCorrectly() {
    MotifTransformer t = new MotifTransformer();
    // Start on B4 (pitch 71). +1 step in C major -> C5 (pitch 72).
    Motif input = new Motif(List.of(new Note(71, 0, TPB, 90)), 1, BPB, TPB);
    Motif out = t.diatonicTranspose(input, 1, KeySignature.major(0));

    assertEquals(72, out.getNotes().get(0).pitch());
  }

  @Test
  void diatonicTransposeNegativeStepsGoesDown() {
    MotifTransformer t = new MotifTransformer();
    // C4 (60) -1 step in C major -> B3 (pitch 59).
    Motif input = new Motif(List.of(new Note(60, 0, TPB, 90)), 1, BPB, TPB);
    Motif out = t.diatonicTranspose(input, -1, KeySignature.major(0));

    assertEquals(59, out.getNotes().get(0).pitch());
  }

  @Test
  void diatonicTransposeSnapsOutOfKeyPitches() {
    MotifTransformer t = new MotifTransformer();
    // C# (61) is not in C major. With 0 steps we still expect the class to
    // snap it to the nearest in-key pitch, then return that snapped pitch.
    Motif input = new Motif(List.of(new Note(61, 0, TPB, 90)), 1, BPB, TPB);
    Motif out = t.diatonicTranspose(input, 0, KeySignature.major(0));
    int outPitch = out.getNotes().get(0).pitch();

    assertEquals(true, KeySignature.major(0).containsPitchClass(outPitch % 12),
        "snapped pitch should be in C major, got " + outPitch);
  }

  @Test
  void invertMirrorsAroundPivot() {
    MotifTransformer t = new MotifTransformer();
    // pivot 60 (C4); 60->60, 62->58, 64->56, 65->55
    Motif out = t.invert(rhythmicallyVariedMotif(), 60);

    assertEquals(List.of(60, 58, 56, 55), pitches(out));
  }

  @Test
  void invertPreservesEveryDuration() {
    MotifTransformer t = new MotifTransformer();
    Motif input = rhythmicallyVariedMotif();
    Motif out = t.invert(input, 60);

    assertEquals(durations(input), durations(out));
  }

  @Test
  void retrogradeReversesPitchSequence() {
    MotifTransformer t = new MotifTransformer();
    Motif out = t.retrograde(rhythmicallyVariedMotif());

    // Pitches reversed; rhythm slots preserved.
    assertEquals(List.of(65, 64, 62, 60), pitches(out));
  }

  @Test
  void retrogradePreservesRhythmSlots() {
    MotifTransformer t = new MotifTransformer();
    Motif input = rhythmicallyVariedMotif();
    Motif out = t.retrograde(input);

    assertEquals(durations(input), durations(out));
  }

  @Test
  void allTransformsLeaveRestsUntouched() {
    MotifTransformer t = new MotifTransformer();
    List<Note> notes = List.of(
        new Note(60, 0, TPB, 90),
        new Note(Note.REST, TPB, TPB, 0),
        new Note(64, 2L * TPB, TPB, 90));
    Motif input = new Motif(notes, 2, BPB, TPB);

    assertEquals(true, t.identity(input).getNotes().get(1).isRest());
    assertEquals(true, t.diatonicTranspose(input, 2, KeySignature.major(0))
        .getNotes().get(1).isRest());
    assertEquals(true, t.invert(input, 60).getNotes().get(1).isRest());
    assertEquals(true, t.retrograde(input).getNotes().get(1).isRest());
  }

  @Test
  void diatonicTransposeUpFiveStepsIsAFifth() {
    MotifTransformer t = new MotifTransformer();
    // C major scale degrees: C D E F G A B. +4 steps from C is G (a 5th).
    Motif input = new Motif(List.of(new Note(60, 0, TPB, 90)), 1, BPB, TPB);
    Motif out = t.diatonicTranspose(input, 4, KeySignature.major(0));

    assertEquals(67, out.getNotes().get(0).pitch());
  }

  @Test
  void differentTransformsProduceDifferentPitches() {
    MotifTransformer t = new MotifTransformer();
    Motif input = rhythmicallyVariedMotif();
    KeySignature key = KeySignature.major(0);

    assertNotEquals(pitches(t.invert(input, 60)),
        pitches(t.diatonicTranspose(input, 4, key)));
    assertNotEquals(pitches(t.retrograde(input)),
        pitches(t.invert(input, 60)));
  }
}
