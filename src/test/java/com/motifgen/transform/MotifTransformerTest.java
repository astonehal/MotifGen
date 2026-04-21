package com.motifgen.transform;

import com.motifgen.model.Motif;
import com.motifgen.model.Note;
import com.motifgen.theory.KeySignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MotifTransformerTest {

  private static final int TPB = 480;
  private static final int BPB = 4;

  private MotifTransformer transformer;
  private KeySignature cMajor;

  @BeforeEach
  void setUp() {
    transformer = new MotifTransformer(42L); // deterministic
    cMajor = KeySignature.major(0);
  }

  private Motif motifOf(int bars, int... pitches) {
    List<Note> notes = new ArrayList<>();
    long tick = 0;
    for (int p : pitches) {
      notes.add(new Note(p, tick, TPB, 90));
      tick += TPB;
    }
    return new Motif(notes, bars, BPB, TPB);
  }

  private Motif withRestIn(Motif base, int restIndex) {
    List<Note> notes = new ArrayList<>(base.getNotes());
    Note n = notes.get(restIndex);
    notes.set(restIndex, new Note(Note.REST, n.startTick(), n.durationTicks(), 0));
    return new Motif(notes, base.getBars(), base.getBeatsPerBar(), base.getTicksPerBeat());
  }

  @Test
  void defaultConstructorProducesUsableTransformer() {
    MotifTransformer t = new MotifTransformer();
    Motif m = motifOf(1, 60, 62, 64, 65);
    // Just confirm all methods are callable without exception using seed-less transformer
    assertNotNull(t.vary(m, cMajor));
    assertNotNull(t.embellish(m, cMajor));
    assertNotNull(t.fragment(m, cMajor));
  }

  @Test
  void repeatReturnsSameMotif() {
    Motif m = motifOf(1, 60, 62);
    assertSame(m, transformer.repeat(m));
  }

  @Test
  void sequenceTransposesNotesInKeyPreservingRests() {
    Motif m = motifOf(1, 60, 62, 64, 65);
    Motif input = withRestIn(m, 2);
    Motif up = transformer.sequence(input, 2, cMajor); // up 2 scale steps
    assertEquals(input.getBars(), up.getBars());
    assertEquals(4, up.getNotes().size());
    // Rests preserved
    assertTrue(up.getNotes().get(2).isRest());
    // Non-rest notes transposed upward overall
    assertTrue(up.getNotes().get(0).pitch() > input.getNotes().get(0).pitch());
  }

  @Test
  void invertMirrorsAroundPivotAndPreservesRests() {
    Motif m = motifOf(1, 62, 64, 67);
    Motif input = withRestIn(m, 1);
    Motif inverted = transformer.invert(input, 60);
    // 62 -> 2 above pivot 60 -> 58 below = 58
    assertEquals(58, inverted.getNotes().get(0).pitch());
    assertTrue(inverted.getNotes().get(1).isRest());
    // 67 -> 7 above -> 53 below
    assertEquals(53, inverted.getNotes().get(2).pitch());
  }

  @Test
  void invertClampsToMidiRange() {
    Motif m = motifOf(1, 0); // extreme low
    Motif inverted = transformer.invert(m, 60);
    // pivot 60 + (60 - 0) = 120, still within 0..127
    assertEquals(120, inverted.getNotes().get(0).pitch());

    Motif high = motifOf(1, 127);
    Motif invertedHigh = high.transpose(0); // no-op
    Motif result = transformer.invert(invertedHigh, 0);
    assertTrue(result.getNotes().get(0).pitch() >= 0);
    assertTrue(result.getNotes().get(0).pitch() <= 127);
  }

  @Test
  void retrogradeReversesPitchesButKeepsTimings() {
    Motif m = motifOf(1, 60, 62, 64, 67);
    Motif retro = transformer.retrograde(m);
    List<Note> rn = retro.getNotes();
    assertEquals(67, rn.get(0).pitch());
    assertEquals(64, rn.get(1).pitch());
    assertEquals(62, rn.get(2).pitch());
    assertEquals(60, rn.get(3).pitch());
    // Timings unchanged
    for (int i = 0; i < rn.size(); i++) {
      assertEquals(m.getNotes().get(i).startTick(), rn.get(i).startTick());
      assertEquals(m.getNotes().get(i).durationTicks(), rn.get(i).durationTicks());
    }
  }

  @Test
  void retrogradeOnEmptyMotifReturnsSameInstance() {
    Motif empty = new Motif(List.of(), 1, BPB, TPB);
    assertSame(empty, transformer.retrograde(empty));
  }

  @Test
  void augmentDoublesDurationsStartsAndBars() {
    Motif m = motifOf(1, 60, 62);
    Motif aug = transformer.augment(m);
    assertEquals(2, aug.getBars());
    assertEquals(TPB * 2, aug.getNotes().get(0).durationTicks());
    assertEquals(TPB * 2, aug.getNotes().get(1).startTick());
  }

  @Test
  void diminishHalvesDurationsAndBarsWithFloor() {
    Motif m = motifOf(2, 60, 62);
    Motif dim = transformer.diminish(m);
    assertEquals(1, dim.getBars());
    assertEquals(TPB / 2, dim.getNotes().get(0).durationTicks());
    assertEquals(TPB / 2, dim.getNotes().get(1).startTick());

    // Bars never drop below 1
    Motif oneBar = motifOf(1, 60);
    assertEquals(1, transformer.diminish(oneBar).getBars());

    // Duration floor at 1 tick
    Motif tiny = new Motif(List.of(new Note(60, 0, 1, 90)), 1, BPB, TPB);
    assertEquals(1, transformer.diminish(tiny).getNotes().get(0).durationTicks());
  }

  @Test
  void embellishPreservesRestsAndProducesNotesForLongInputs() {
    // Use a deterministic seed and longer notes so neighbor-tone branch can fire
    MotifTransformer det = new MotifTransformer(1L);
    List<Note> notes = new ArrayList<>();
    for (int i = 0; i < 8; i++) {
      notes.add(new Note(60 + i, (long) i * TPB * 2, TPB * 2, 90));
    }
    notes.set(3, new Note(Note.REST, 3L * TPB * 2, TPB * 2, 0));
    Motif m = new Motif(notes, 4, BPB, TPB);

    Motif embellished = det.embellish(m, cMajor);
    assertTrue(embellished.getNotes().size() >= m.getNotes().size());
    // The rest at index 3 must still exist somewhere
    assertTrue(embellished.getNotes().stream().anyMatch(Note::isRest));
  }

  @Test
  void embellishWithShortNotesSkipsNeighborBranch() {
    // Short durations (< half of ticksPerBeat) bypass neighbor insertion, keeping count unchanged
    List<Note> notes = new ArrayList<>();
    for (int i = 0; i < 4; i++) {
      notes.add(new Note(60 + i, (long) i * 100, 100, 90));
    }
    Motif m = new Motif(notes, 1, BPB, TPB);
    Motif out = transformer.embellish(m, cMajor);
    assertEquals(m.getNotes().size(), out.getNotes().size());
  }

  @Test
  void fragmentReturnsMotifWithSameBarCount() {
    Motif m = motifOf(4, 60, 62, 64, 65, 67, 69, 71, 72);
    Motif fragmented = transformer.fragment(m, cMajor);
    assertEquals(m.getBars(), fragmented.getBars());
    assertFalse(fragmented.getNotes().isEmpty());
  }

@Test
  void varyPreservesShapeAndBarsAndRests() {
    Motif m = motifOf(1, 60, 62, 64, 65);
    Motif withRest = withRestIn(m, 1);
    Motif varied = transformer.vary(withRest, cMajor);
    assertEquals(withRest.getNotes().size(), varied.getNotes().size());
    assertEquals(withRest.getBars(), varied.getBars());
    assertTrue(varied.getNotes().get(1).isRest());
  }

  @Test
  void buildClimaxRaisesAllPitchesAndVelocitiesPreservingRests() {
    Motif base = motifOf(1, 60, 64, 67);
    Motif withRest = withRestIn(base, 1);
    Motif climax = transformer.buildClimax(withRest, cMajor);

    // Non-rest notes have higher pitch and velocity
    assertTrue(climax.getNotes().get(0).pitch() > 60);
    assertTrue(climax.getNotes().get(0).velocity() > 90);
    assertTrue(climax.getNotes().get(1).isRest());
    assertTrue(climax.getNotes().get(2).pitch() > 67);
  }

  @Test
  void buildClimaxVelocityIsCappedAt127() {
    Motif hot = new Motif(List.of(new Note(60, 0, TPB, 120)), 1, BPB, TPB);
    Motif climax = transformer.buildClimax(hot, cMajor);
    assertTrue(climax.getNotes().get(0).velocity() <= 127);
  }

  @Test
  void createCadenceReplacesFinalTwoNotesWithDominantThenTonic() {
    Motif m = motifOf(1, 60, 62, 64, 65);
    Motif cadence = transformer.createCadence(m, cMajor);
    assertEquals(m.getNotes().size(), cadence.getNotes().size());

    Note penult = cadence.getNotes().get(cadence.getNotes().size() - 2);
    Note last = cadence.getNotes().getLast();
    // penult should be on dominant pitch class (G = 7)
    assertEquals(7, penult.pitch() % 12);
    // last should be on tonic (C = 0)
    assertEquals(0, last.pitch() % 12);
    // last note duration doubled
    assertEquals(m.getNotes().getLast().durationTicks() * 2, last.durationTicks());
  }

  @Test
  void createCadenceReturnsInputWhenTooShort() {
    Motif tiny = motifOf(1, 60);
    assertSame(tiny, transformer.createCadence(tiny, cMajor));
  }
}
