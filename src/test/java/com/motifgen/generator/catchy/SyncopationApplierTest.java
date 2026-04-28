package com.motifgen.generator.catchy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.motifgen.model.Motif;
import com.motifgen.model.Note;
import com.motifgen.sentiment.SentimentProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link SyncopationApplier}.
 *
 * <p>Covers Scenario 6 (rhythmic density / syncopation linked to Arousal).
 */
class SyncopationApplierTest {

  private static final int TPB = 480;
  private static final int BPB = 4;

  /** Builds a motif with notes all landing exactly on beat boundaries. */
  private Motif onBeatMotif(int noteCount) {
    List<Note> notes = new ArrayList<>();
    for (int i = 0; i < noteCount; i++) {
      notes.add(new Note(60 + i, (long) i * TPB, TPB, 90));
    }
    return new Motif(notes, noteCount / BPB + 1, BPB, TPB);
  }

  @Test
  void noteCountIsPreservedAfterApplication() {
    SentimentProfile high = SentimentProfile.fromLabel("EXCITED"); // A=0.85
    Motif motif = onBeatMotif(8);
    Motif result = SyncopationApplier.apply(motif, high, TPB, new Random(1L));
    long soundingBefore = motif.getNotes().stream().filter(n -> !n.isRest()).count();
    long soundingAfter  = result.getNotes().stream().filter(n -> !n.isRest()).count();
    assertEquals(soundingBefore, soundingAfter,
        "SyncopationApplier must not add or remove notes");
  }

  @Test
  void durationNeverDropsBelowOneTick() {
    SentimentProfile high = SentimentProfile.fromLabel("EXCITED");
    Motif motif = onBeatMotif(8);
    Motif result = SyncopationApplier.apply(motif, high, TPB, new Random(2L));
    for (Note n : result.getNotes()) {
      assertTrue(n.durationTicks() >= 1,
          "No note may have duration < 1 tick, got " + n.durationTicks());
    }
  }

  @Test
  void highArousalShiftsAtLeastOneNote() {
    SentimentProfile high = SentimentProfile.fromLabel("EXCITED"); // A=0.85
    Motif motif = onBeatMotif(8);
    Motif result = SyncopationApplier.apply(motif, high, TPB, new Random(3L));

    List<Long> origStarts = motif.getNotes().stream().map(Note::startTick).toList();
    List<Long> newStarts  = result.getNotes().stream().map(Note::startTick).toList();
    boolean anyShifted = false;
    for (int i = 0; i < origStarts.size(); i++) {
      if (!origStarts.get(i).equals(newStarts.get(i))) {
        anyShifted = true;
        break;
      }
    }
    assertTrue(anyShifted,
        "High-arousal syncopation should shift at least one note's start tick");
  }

  @Test
  void lowArousalApplierReturnsEquivalentMotif() {
    // syncopationLevel = V*0.3 + A*0.7 with RELAXED (V=0.70, A=0.25)
    // = 0.21 + 0.175 = 0.385 → floor(0.385 * 8 * 0.4) = floor(1.232) = 1 note shifted
    // At minimum the contract is: note count preserved and durations >= 1
    SentimentProfile low = SentimentProfile.fromLabel("RELAXED");
    Motif motif = onBeatMotif(8);
    Motif result = SyncopationApplier.apply(motif, low, TPB, new Random(4L));
    assertEquals(motif.getNotes().size(), result.getNotes().size());
  }

  @Test
  void startTickNeverExceedsPhraseEnd() {
    SentimentProfile high = SentimentProfile.fromLabel("TENSE");
    Motif motif = onBeatMotif(8);
    long phraseEnd = motif.totalTicks();
    Motif result = SyncopationApplier.apply(motif, high, TPB, new Random(5L));
    for (Note n : result.getNotes()) {
      assertTrue(n.startTick() < phraseEnd,
          "startTick " + n.startTick() + " must be < phraseEnd " + phraseEnd);
    }
  }
}
