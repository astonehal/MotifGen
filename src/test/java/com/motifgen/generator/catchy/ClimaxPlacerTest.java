package com.motifgen.generator.catchy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.motifgen.model.Motif;
import com.motifgen.model.Note;
import com.motifgen.theory.KeySignature;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ClimaxPlacerTest {

  private static final int TPB = 480;
  private static final int BPB = 4;

  private Motif motifFromPitches(int[] pitches) {
    List<Note> notes = new ArrayList<>();
    long tick = 0;
    for (int p : pitches) {
      notes.add(new Note(p, tick, TPB, 90));
      tick += TPB;
    }
    return new Motif(notes, 4, BPB, TPB);
  }

  @Test
  void peakSitsAtPlannedPositionAfterPlacement() {
    ClimaxPlacer placer = new ClimaxPlacer();
    KeySignature cMajor = KeySignature.major(0);

    Motif flat = motifFromPitches(new int[] {60, 62, 64, 62, 60, 62, 64, 62});
    int climaxIdx = 4;

    Motif shaped = placer.enforceClimax(flat, climaxIdx, cMajor);

    List<Note> pitched = shaped.getNotes().stream().filter(n -> !n.isRest()).toList();
    int maxPitch = pitched.stream().mapToInt(Note::pitch).max().orElseThrow();
    assertEquals(maxPitch, pitched.get(climaxIdx).pitch(),
        "pitch at climax position should be the global maximum");
  }

  @Test
  void alreadyCorrectMelodyIsLeftStructurallyIntact() {
    ClimaxPlacer placer = new ClimaxPlacer();
    KeySignature cMajor = KeySignature.major(0);

    Motif good = motifFromPitches(new int[] {60, 62, 64, 65, 79, 65, 64, 62});
    Motif shaped = placer.enforceClimax(good, 4, cMajor);

    List<Note> before = good.getNotes().stream().filter(n -> !n.isRest()).toList();
    List<Note> after = shaped.getNotes().stream().filter(n -> !n.isRest()).toList();
    assertEquals(before.size(), after.size());

    int maxPitch = after.stream().mapToInt(Note::pitch).max().orElseThrow();
    assertEquals(maxPitch, after.get(4).pitch());
  }

  @Test
  void outOfRangeClimaxIndexIsHandledGracefully() {
    ClimaxPlacer placer = new ClimaxPlacer();
    KeySignature cMajor = KeySignature.major(0);
    Motif m = motifFromPitches(new int[] {60, 62, 64, 65});

    Motif shapedLow = placer.enforceClimax(m, -1, cMajor);
    Motif shapedHigh = placer.enforceClimax(m, 99, cMajor);

    assertEquals(m.getNotes().size(), shapedLow.getNotes().size());
    assertEquals(m.getNotes().size(), shapedHigh.getNotes().size());
  }

  @Test
  void climaxPitchClassRemainsInKey() {
    ClimaxPlacer placer = new ClimaxPlacer();
    KeySignature gMajor = KeySignature.major(7);

    Motif flat = motifFromPitches(new int[] {67, 69, 71, 72, 67, 69, 71, 72});
    Motif shaped = placer.enforceClimax(flat, 3, gMajor);

    List<Note> pitched = shaped.getNotes().stream().filter(n -> !n.isRest()).toList();
    int climaxPc = pitched.get(3).pitch() % 12;
    assertTrue(gMajor.containsPitchClass(climaxPc),
        "climax pitch class " + climaxPc + " should be in key " + gMajor.name());
  }
}
