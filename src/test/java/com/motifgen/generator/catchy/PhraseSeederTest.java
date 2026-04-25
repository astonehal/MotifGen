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

class PhraseSeederTest {

  private static final int TPB = 480;
  private static final int BPB = 4;

  private Motif cMajorMotif() {
    int[] pitches = {60, 62, 64, 65, 67, 65, 64, 62};
    long[] durs = {TPB, TPB / 2, TPB / 2, TPB, TPB, TPB / 2, TPB / 2, TPB};
    List<Note> notes = new ArrayList<>();
    long tick = 0;
    for (int i = 0; i < pitches.length; i++) {
      notes.add(new Note(pitches[i], tick, durs[i], 90));
      tick += durs[i];
    }
    return new Motif(notes, 4, BPB, TPB);
  }

  private List<Long> durations(Motif m) {
    return m.getNotes().stream().map(Note::durationTicks).toList();
  }

  private List<Integer> pitches(Motif m) {
    return m.getNotes().stream().map(Note::pitch).toList();
  }

  @Test
  void aPhraseIsExactIdentityAndImmutable() {
    PhraseSeeder seeder = new PhraseSeeder(42L);
    Motif motif = cMajorMotif();

    PhraseSeeder.SeededPhrase seeded =
        seeder.seed('A', false, motif, KeySignature.major(0));

    assertEquals(pitches(motif), pitches(seeded.phrase()));
    assertEquals(durations(motif), durations(seeded.phrase()));
    assertTrue(seeded.immutable(), "A phrases must be marked immutable");
  }

  @Test
  void bPhraseIsTransformedAndMutable() {
    PhraseSeeder seeder = new PhraseSeeder(7L);
    Motif motif = cMajorMotif();

    PhraseSeeder.SeededPhrase seeded =
        seeder.seed('B', false, motif, KeySignature.major(0));

    assertFalse(seeded.immutable(), "B phrases must be mutable");
    assertEquals(durations(motif), durations(seeded.phrase()),
        "B phrases must preserve seed rhythm before refinement");
    // Pitches must differ from the motif under at least one of the chosen transforms.
    // Across all 3 candidate transforms, every result differs from identity.
    assertFalse(pitches(motif).equals(pitches(seeded.phrase())),
        "B should not be identity-equal to the motif");
  }

  @Test
  void cPhraseIsTransformedAndMutable() {
    PhraseSeeder seeder = new PhraseSeeder(99L);
    Motif motif = cMajorMotif();

    PhraseSeeder.SeededPhrase seeded =
        seeder.seed('C', false, motif, KeySignature.major(0));

    assertFalse(seeded.immutable());
    assertEquals(durations(motif), durations(seeded.phrase()));
    assertFalse(pitches(motif).equals(pitches(seeded.phrase())),
        "C should not be identity-equal to the motif");
  }

  @Test
  void finalBPhraseEndsOnTonic() {
    PhraseSeeder seeder = new PhraseSeeder(13L);
    Motif motif = cMajorMotif();
    KeySignature key = KeySignature.major(0); // tonic = 0 (C)

    PhraseSeeder.SeededPhrase seeded = seeder.seed('B', true, motif, key);
    int lastPitch = lastSoundingPitch(seeded.phrase());

    assertEquals(0, lastPitch % 12, "final B phrase must resolve to tonic pc");
  }

  @Test
  void finalCPhraseEndsOnTonic() {
    PhraseSeeder seeder = new PhraseSeeder(31L);
    Motif motif = cMajorMotif();
    KeySignature key = KeySignature.major(0);

    PhraseSeeder.SeededPhrase seeded = seeder.seed('C', true, motif, key);
    int lastPitch = lastSoundingPitch(seeded.phrase());

    assertEquals(0, lastPitch % 12);
  }

  @Test
  void finalAPhraseIsStillIdentityAndImmutable() {
    PhraseSeeder seeder = new PhraseSeeder(0L);
    Motif motif = cMajorMotif();
    KeySignature key = KeySignature.major(0);

    PhraseSeeder.SeededPhrase seeded = seeder.seed('A', true, motif, key);

    // The motif itself ends on C (tonic) so identity is fine, but
    // the contract is: A is always identity and immutable, even when final.
    assertEquals(pitches(motif), pitches(seeded.phrase()));
    assertTrue(seeded.immutable());
  }

  @Test
  void deterministicForSameSeed() {
    Motif motif = cMajorMotif();
    KeySignature key = KeySignature.major(0);

    PhraseSeeder a = new PhraseSeeder(2024L);
    PhraseSeeder b = new PhraseSeeder(2024L);

    assertEquals(pitches(a.seed('B', false, motif, key).phrase()),
        pitches(b.seed('B', false, motif, key).phrase()));
    assertEquals(pitches(a.seed('C', false, motif, key).phrase()),
        pitches(b.seed('C', false, motif, key).phrase()));
  }

  @Test
  void differentSeedsExploreDifferentTransformsForB() {
    Motif motif = cMajorMotif();
    KeySignature key = KeySignature.major(0);

    java.util.Set<List<Integer>> seenB = new java.util.HashSet<>();
    for (long s = 0; s < 30; s++) {
      seenB.add(pitches(new PhraseSeeder(s).seed('B', false, motif, key).phrase()));
    }
    assertTrue(seenB.size() >= 2,
        "B seeder should produce more than one variant across many seeds, got "
            + seenB.size());
  }

  @Test
  void unknownRoleFallsBackToIdentity() {
    PhraseSeeder seeder = new PhraseSeeder(0L);
    Motif motif = cMajorMotif();
    PhraseSeeder.SeededPhrase seeded =
        seeder.seed('Z', false, motif, KeySignature.major(0));
    assertEquals(pitches(motif), pitches(seeded.phrase()));
  }

  private static int lastSoundingPitch(Motif m) {
    List<Note> notes = m.getNotes();
    for (int i = notes.size() - 1; i >= 0; i--) {
      if (!notes.get(i).isRest()) return notes.get(i).pitch();
    }
    throw new AssertionError("no sounding notes");
  }
}
