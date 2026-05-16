package com.motifgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.motifgen.guitar.GuitarPlayabilityAnalyser;
import com.motifgen.guitar.GuitarPlayabilityResult;
import com.motifgen.guitar.PlayabilityGate;
import com.motifgen.model.Motif;
import com.motifgen.model.Note;
import com.motifgen.model.Sentence;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * End-to-end tests for issue #17 (Guitar Playability Validation).
 *
 * <p>These tests drive the full guitar analysis pipeline — range check, Viterbi fingering,
 * label classification, and the post-refinement gate — against realistic note sequences,
 * mirroring the Gherkin acceptance criteria.
 */
class GuitarPlayabilityE2ETest {

  private static final int TICKS_PER_BEAT = 480;
  private static final int BEATS_PER_BAR = 4;

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private Motif motifOf(int[] pitches) {
    List<Note> notes = new ArrayList<>();
    long tick = 0;
    for (int pitch : pitches) {
      notes.add(new Note(pitch, tick, TICKS_PER_BEAT, 90));
      tick += TICKS_PER_BEAT;
    }
    int bars = Math.max(1, (int) Math.ceil((double) pitches.length / BEATS_PER_BAR));
    return new Motif(notes, bars, BEATS_PER_BAR, TICKS_PER_BEAT);
  }

  private Sentence sentenceOf(Motif motif) {
    return new Sentence(List.of(motif), "a", "C major", 50.0);
  }

  // ---------------------------------------------------------------------------
  // Scenario 1: Out-of-range note is flagged as unplayable
  // ---------------------------------------------------------------------------

  /**
   * Given a melody containing a note with MIDI pitch outside [40, 88]
   * When guitar playability analysis is run
   * Then the result is "unplayable" with reason identifying the out-of-range note index.
   */
  @Test
  void outOfRangeNoteProducesUnplayableResult() {
    // Pitch 30 is below the guitar minimum of 40
    int[] pitches = {60, 62, 64, 30, 67};
    List<Note> notes = motifOf(pitches).getNotes();

    GuitarPlayabilityResult result = GuitarPlayabilityAnalyser.analyse(notes, TICKS_PER_BEAT);

    assertInstanceOf(GuitarPlayabilityResult.Unplayable.class, result,
        "Melody with pitch 30 (below 40) should be Unplayable");
    GuitarPlayabilityResult.Unplayable u = (GuitarPlayabilityResult.Unplayable) result;
    assertTrue(u.noteIndex() >= 0, "Note index must be non-negative");
    assertNotNull(u.reason(), "Reason must not be null");
    assertFalse(u.reason().isBlank(), "Reason must not be blank");
    assertTrue(u.reason().contains("30") || u.reason().contains("index"),
        "Reason should reference the problematic pitch or index: " + u.reason());
  }

  // ---------------------------------------------------------------------------
  // Scenario 2: In-range melody receives a playability classification
  // ---------------------------------------------------------------------------

  /**
   * Given a melody where all notes have MIDI pitch in [40, 88]
   * When guitar playability analysis is run
   * Then a playability label is returned
   * And an optimal (string, fret) fingering sequence is returned.
   */
  @Test
  void inRangeMelodyReceivesPlayabilityClassification() {
    // C major scale on guitar (all in range [40, 88])
    int[] pitches = {60, 62, 64, 65, 67, 69, 71, 72};
    List<Note> notes = motifOf(pitches).getNotes();

    GuitarPlayabilityResult result = GuitarPlayabilityAnalyser.analyse(notes, TICKS_PER_BEAT);

    assertInstanceOf(GuitarPlayabilityResult.Playable.class, result,
        "All notes in [40,88] should produce Playable");
    GuitarPlayabilityResult.Playable p = (GuitarPlayabilityResult.Playable) result;

    List<String> validLabels = List.of(
        "beginner-friendly", "intermediate", "advanced",
        "difficult but playable", "impractical");
    assertTrue(validLabels.contains(p.label()),
        "Label must be one of the defined values, got: " + p.label());

    assertFalse(p.fingering().isEmpty(),
        "Fingering sequence must not be empty for a non-empty melody");
    assertEquals(pitches.length, p.fingering().size(),
        "One fingering position per note");
  }

  // ---------------------------------------------------------------------------
  // Scenario 4: Impractical melodies are rejected by the gate
  // ---------------------------------------------------------------------------

  /**
   * Given a melody whose avg per-note cost exceeds 5.0
   * When the post-refinement gate runs
   * Then the melody is rejected and problem spots are recorded.
   */
  @Test
  void impracticalMelodyIsRejectedByGate() {
    // Rapidly alternate between extremes of the guitar range on very short notes
    // E2 (40) ↔ fret-22 on string 1 (86): each transition costs >> 5.0
    long shortTick = (long) (0.1 * TICKS_PER_BEAT);
    List<Note> notes = new ArrayList<>();
    long tick = 0;
    int[] altPitches = {40, 86, 40, 86, 40, 86, 40, 86};
    for (int p : altPitches) {
      notes.add(new Note(p, tick, shortTick, 90));
      tick += shortTick;
    }
    Motif motif = new Motif(notes, 1, BEATS_PER_BAR, TICKS_PER_BEAT);
    Sentence sentence = sentenceOf(motif);

    PlayabilityGate gate = new PlayabilityGate();
    PlayabilityGate.GateResult result = gate.evaluate(sentence, TICKS_PER_BEAT);

    assertFalse(result.passed(), "Impractical melody should be rejected");
    assertTrue(result.avgCost() > PlayabilityGate.REJECTION_THRESHOLD,
        "avgCost=%.2f should exceed threshold %.1f"
            .formatted(result.avgCost(), PlayabilityGate.REJECTION_THRESHOLD));
    assertFalse(result.problemSpots().isEmpty(),
        "Problem spots should be recorded on rejection");
  }

  // ---------------------------------------------------------------------------
  // Scenario 5: Playable melodies pass through the gate unmodified
  // ---------------------------------------------------------------------------

  /**
   * Given a melody whose avg per-note cost is below 5.0
   * When the post-refinement gate runs
   * Then the melody passes through with its playability label attached
   * And no notes are altered.
   */
  @Test
  void playableMelodyPassesThroughGateWithLabel() {
    // Stepwise C major melody — very easy to play on guitar
    int[] pitches = {60, 62, 64, 65, 67, 65, 64, 62};
    Motif motif = motifOf(pitches);
    Sentence original = sentenceOf(motif);

    PlayabilityGate gate = new PlayabilityGate();
    PlayabilityGate.GateResult result = gate.evaluate(original, TICKS_PER_BEAT);

    assertTrue(result.passed(), "Easy melody should pass the gate");
    String label = result.sentence().getMetadataValue(PlayabilityGate.METADATA_KEY_LABEL);
    assertNotNull(label, "Playability label must be attached to passing sentence");

    // Verify notes are unaltered
    List<Note> originalNotes = original.getAllNotes();
    List<Note> resultNotes = result.sentence().getAllNotes();
    assertEquals(originalNotes.size(), resultNotes.size(), "Note count must not change");
    for (int i = 0; i < originalNotes.size(); i++) {
      assertEquals(originalNotes.get(i).pitch(), resultNotes.get(i).pitch(),
          "Pitch at index " + i + " must not be altered");
    }
  }

  // ---------------------------------------------------------------------------
  // Full pipeline: analyse → gate integration
  // ---------------------------------------------------------------------------

  /**
   * Verifies the analyser and gate are consistent: a melody that analyses as Playable
   * with avgCost <= 5.0 should also pass the gate.
   */
  @Test
  void analyserAndGateAreConsistentForEasyMelody() {
    int[] pitches = {64, 65, 67, 69, 71, 69, 67, 65}; // in-range stepwise
    List<Note> notes = motifOf(pitches).getNotes();

    GuitarPlayabilityResult analyserResult =
        GuitarPlayabilityAnalyser.analyse(notes, TICKS_PER_BEAT);
    assertInstanceOf(GuitarPlayabilityResult.Playable.class, analyserResult);
    GuitarPlayabilityResult.Playable playable = (GuitarPlayabilityResult.Playable) analyserResult;

    Sentence sentence = sentenceOf(motifOf(pitches));
    PlayabilityGate gate = new PlayabilityGate();
    PlayabilityGate.GateResult gateResult = gate.evaluate(sentence, TICKS_PER_BEAT);

    boolean analyserSaysPass = playable.avgCost() <= PlayabilityGate.REJECTION_THRESHOLD;
    assertEquals(analyserSaysPass, gateResult.passed(),
        "Gate decision must be consistent with analyser avgCost");
  }

}
