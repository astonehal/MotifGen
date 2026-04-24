package com.motifgen.generator.catchy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.motifgen.model.Motif;
import com.motifgen.model.Note;
import com.motifgen.theory.KeySignature;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class BeamSearcherTest {

  private static final int TPB = 480;
  private static final int BPB = 4;

  private Motif seedMotif() {
    int[] pitches = {60, 62, 64, 65, 67, 65, 64, 62};
    List<Note> notes = new ArrayList<>();
    long tick = 0;
    for (int p : pitches) {
      notes.add(new Note(p, tick, TPB, 90));
      tick += TPB;
    }
    return new Motif(notes, 4, BPB, TPB);
  }

  @Test
  void returnsPhraseOfRequestedLength() {
    BeamSearcher searcher = new BeamSearcher(42L, 16);
    KeySignature cMajor = KeySignature.major(0);

    Motif phrase = searcher.search(seedMotif(), SectionGoal.REINFORCE_MOTIF,
        List.of(), cMajor, 8, TPB, BPB);

    long sounding = phrase.getNotes().stream().filter(n -> !n.isRest()).count();
    assertEquals(8, sounding,
        "phrase should contain exactly the requested number of sounding notes");
  }

  @Test
  void consecutivePitchesRespectLeapConstraint() {
    BeamSearcher searcher = new BeamSearcher(7L, 16);
    KeySignature cMajor = KeySignature.major(0);

    Motif phrase = searcher.search(seedMotif(), SectionGoal.REINFORCE_MOTIF,
        List.of(), cMajor, 10, TPB, BPB);

    List<Note> pitched = phrase.getNotes().stream().filter(n -> !n.isRest()).toList();
    for (int i = 1; i < pitched.size(); i++) {
      int interval = Math.abs(pitched.get(i).pitch() - pitched.get(i - 1).pitch());
      assertTrue(interval <= 14,
          "adjacent interval exceeds 14 semitones at index " + i + ": " + interval);
    }
  }

  @Test
  void phraseHonoursTargetTotalTicks() {
    BeamSearcher searcher = new BeamSearcher(1L, 16);
    KeySignature cMajor = KeySignature.major(0);

    Motif phrase = searcher.search(seedMotif(), SectionGoal.REINFORCE_MOTIF,
        List.of(), cMajor, 8, TPB, BPB);

    assertEquals((long) BPB * TPB * phrase.getBars(), phrase.totalTicks());
    assertFalse(phrase.getNotes().isEmpty());
  }

  @Test
  void deterministicWithSameSeed() {
    BeamSearcher a = new BeamSearcher(2025L, 16);
    BeamSearcher b = new BeamSearcher(2025L, 16);
    KeySignature cMajor = KeySignature.major(0);

    Motif pa = a.search(seedMotif(), SectionGoal.PROVIDE_CONTRAST,
        List.of(), cMajor, 8, TPB, BPB);
    Motif pb = b.search(seedMotif(), SectionGoal.PROVIDE_CONTRAST,
        List.of(), cMajor, 8, TPB, BPB);

    List<Integer> pitchesA = pa.getNotes().stream()
        .filter(n -> !n.isRest()).map(Note::pitch).toList();
    List<Integer> pitchesB = pb.getNotes().stream()
        .filter(n -> !n.isRest()).map(Note::pitch).toList();
    assertEquals(pitchesA, pitchesB);
  }

  @Test
  void resolveToTonicGoalEndsOnTonicPitchClass() {
    BeamSearcher searcher = new BeamSearcher(99L, 16);
    KeySignature cMajor = KeySignature.major(0); // tonic pitch class = 0

    Motif phrase = searcher.search(seedMotif(), SectionGoal.RESOLVE_TO_TONIC,
        List.of(), cMajor, 8, TPB, BPB);

    List<Note> pitched = phrase.getNotes().stream().filter(n -> !n.isRest()).toList();
    assertEquals(0, pitched.getLast().pitchClass(),
        "RESOLVE_TO_TONIC should end on the tonic pitch class");
  }
}
