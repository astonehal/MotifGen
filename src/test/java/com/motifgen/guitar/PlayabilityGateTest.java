package com.motifgen.guitar;

import com.motifgen.model.Motif;
import com.motifgen.model.Note;
import com.motifgen.model.Sentence;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link PlayabilityGate} covering Scenarios 4 and 5.
 */
class PlayabilityGateTest {

  private static final int TICKS_PER_BEAT = 480;
  private static final int BEATS_PER_BAR = 4;

  private PlayabilityGate gate;

  @BeforeEach
  void setUp() {
    gate = new PlayabilityGate();
  }

  // -----------------------------------------------------------------------
  // Helpers
  // -----------------------------------------------------------------------

  /** Build a one-bar motif from a list of (pitch, durationTicks) pairs. */
  private Motif motifOf(int[][] pitchAndTicks) {
    List<Note> notes = new ArrayList<>();
    long tick = 0;
    for (int[] pt : pitchAndTicks) {
      notes.add(new Note(pt[0], tick, pt[1], 90));
      tick += pt[1];
    }
    return new Motif(notes, 1, BEATS_PER_BAR, TICKS_PER_BEAT);
  }

  /** Wrap a single motif in a sentence. */
  private Sentence sentenceOf(Motif motif) {
    return new Sentence(List.of(motif), "a", "C major", 50.0);
  }

  /**
   * Build a sentence whose notes produce an average cost > 5.0.
   *
   * <p>Strategy: rapidly alternate between fret 0 and fret 22 on short (sixteenth) notes.
   * Each transition has fretDist=22: cost = 4.0 + (22-5)*1.5 = 4.0 + 25.5 = 29.5,
   * plus time-pressure penalty of 1.0 (fretDist > 3 and 0.25 beats < 0.25? — use 0.1 beats).
   * Adjusted: duration = 0.1 beats < 0.25, so penalty applies → cost = 30.5 >> 5.0.
   */
  private Sentence impracticalSentence() {
    long shortTick = (long) (0.1 * TICKS_PER_BEAT); // 48 ticks < 0.25 * 480 = 120
    // E2 (MIDI 40) = string 6 fret 0; high note on string 1 fret 22 = 64+22=86
    int[][] notes = {
        {40, (int) shortTick},
        {86, (int) shortTick},
        {40, (int) shortTick},
        {86, (int) shortTick},
        {40, (int) shortTick},
        {86, (int) shortTick},
        {40, (int) shortTick},
        {86, (int) shortTick},
    };
    return sentenceOf(motifOf(notes));
  }

  /**
   * Build a sentence with cheap, beginner-friendly transitions (avgCost well below 5.0).
   * Same pitch repeated → fretDist=0, stringDist=0, open string bonus → cost close to 0.
   */
  private Sentence easyBeginnerSentence() {
    // E4 (MIDI 64) repeated — string 1 fret 0, no transitions to speak of
    int[][] notes = {
        {64, TICKS_PER_BEAT},
        {64, TICKS_PER_BEAT},
        {64, TICKS_PER_BEAT},
        {64, TICKS_PER_BEAT},
    };
    return sentenceOf(motifOf(notes));
  }

  // -----------------------------------------------------------------------
  // Scenario 4: Impractical melodies are rejected
  // -----------------------------------------------------------------------

  @Test
  void impracticalMelodyIsRejected() {
    Sentence sentence = impracticalSentence();
    PlayabilityGate.GateResult result = gate.evaluate(sentence, TICKS_PER_BEAT);
    assertFalse(result.passed(), "High-cost melody should be rejected");
  }

  @Test
  void rejectedMelodyAvgCostExceedsThreshold() {
    Sentence sentence = impracticalSentence();
    PlayabilityGate.GateResult result = gate.evaluate(sentence, TICKS_PER_BEAT);
    assertTrue(result.avgCost() > PlayabilityGate.REJECTION_THRESHOLD,
        "Rejected melody avgCost should exceed %.1f, got %.2f"
            .formatted(PlayabilityGate.REJECTION_THRESHOLD, result.avgCost()));
  }

  @Test
  void rejectedMelodyHasProblemSpots() {
    Sentence sentence = impracticalSentence();
    PlayabilityGate.GateResult result = gate.evaluate(sentence, TICKS_PER_BEAT);
    assertFalse(result.problemSpots().isEmpty(),
        "Rejected melody should have at least one problem spot recorded");
  }

  @Test
  void problemSpotsHaveDescriptions() {
    Sentence sentence = impracticalSentence();
    PlayabilityGate.GateResult result = gate.evaluate(sentence, TICKS_PER_BEAT);
    for (String spot : result.problemSpots()) {
      assertNotNull(spot, "Problem spot description must not be null");
      assertFalse(spot.isBlank(), "Problem spot description must not be blank");
    }
  }

  @Test
  void rejectedSentenceIsReturnedUnmodified() {
    Sentence sentence = impracticalSentence();
    PlayabilityGate.GateResult result = gate.evaluate(sentence, TICKS_PER_BEAT);
    // Original sentence object is returned on rejection (no labelling)
    assertSame(sentence, result.sentence(),
        "Rejected sentence should be the same object (unmodified)");
  }

  // -----------------------------------------------------------------------
  // Scenario 5: Playable melodies pass through unmodified
  // -----------------------------------------------------------------------

  @Test
  void easyMelodyPasses() {
    Sentence sentence = easyBeginnerSentence();
    PlayabilityGate.GateResult result = gate.evaluate(sentence, TICKS_PER_BEAT);
    assertTrue(result.passed(), "Easy melody should pass the gate");
  }

  @Test
  void passingMelodyAvgCostBelowThreshold() {
    Sentence sentence = easyBeginnerSentence();
    PlayabilityGate.GateResult result = gate.evaluate(sentence, TICKS_PER_BEAT);
    assertTrue(result.avgCost() <= PlayabilityGate.REJECTION_THRESHOLD,
        "Passing melody avgCost should be <= %.1f, got %.2f"
            .formatted(PlayabilityGate.REJECTION_THRESHOLD, result.avgCost()));
  }

  @Test
  void passingMelodyHasPlayabilityLabelInMetadata() {
    Sentence sentence = easyBeginnerSentence();
    PlayabilityGate.GateResult result = gate.evaluate(sentence, TICKS_PER_BEAT);
    String label = result.sentence().getMetadataValue(PlayabilityGate.METADATA_KEY_LABEL);
    assertNotNull(label, "Passing sentence should have playability label in metadata");
    List<String> validLabels = List.of(
        "beginner-friendly", "intermediate", "advanced",
        "difficult but playable", "impractical");
    assertTrue(validLabels.contains(label), "Label should be a valid value, got: " + label);
  }

  @Test
  void passingMelodyHasNoProblemSpots() {
    Sentence sentence = easyBeginnerSentence();
    PlayabilityGate.GateResult result = gate.evaluate(sentence, TICKS_PER_BEAT);
    assertTrue(result.problemSpots().isEmpty(),
        "Passing melody should have no problem spots");
  }

  @Test
  void passingMelodyNotesAreUnaltered() {
    Sentence original = easyBeginnerSentence();
    PlayabilityGate.GateResult result = gate.evaluate(original, TICKS_PER_BEAT);
    List<Note> originalNotes = original.getAllNotes();
    List<Note> resultNotes = result.sentence().getAllNotes();
    assertEquals(originalNotes.size(), resultNotes.size(),
        "Note count must not change");
    for (int i = 0; i < originalNotes.size(); i++) {
      assertEquals(originalNotes.get(i).pitch(), resultNotes.get(i).pitch(),
          "Note pitch at index " + i + " must not change");
      assertEquals(originalNotes.get(i).durationTicks(), resultNotes.get(i).durationTicks(),
          "Note duration at index " + i + " must not change");
    }
  }

  // -----------------------------------------------------------------------
  // Guard clause tests
  // -----------------------------------------------------------------------

  @Test
  void nullSentenceThrowsIllegalArgument() {
    assertThrows(IllegalArgumentException.class,
        () -> gate.evaluate(null, TICKS_PER_BEAT));
  }

  @Test
  void nonPositiveTicksPerBeatThrowsIllegalArgument() {
    assertThrows(IllegalArgumentException.class,
        () -> gate.evaluate(easyBeginnerSentence(), 0));
  }
}
