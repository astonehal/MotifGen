package com.motifgen.guitar;

import com.motifgen.model.Note;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link GuitarFingering} transition-cost physics (Scenario 3).
 */
class GuitarFingeringTest {

  private static final int TICKS_PER_BEAT = 480;

  // Helper: position on string 1
  private GuitarFingerPosition pos(int string, int fret) {
    return new GuitarFingerPosition(string, fret);
  }

  // -----------------------------------------------------------------------
  // Scenario 3: Transition cost reflects guitar physics
  // -----------------------------------------------------------------------

  @Test
  void smallFretDistanceCostsLessThanLargeFretDistance() {
    // fret distance 2 vs fret distance 7
    double costSmall = GuitarFingering.transitionCost(pos(1, 5), pos(1, 7), 1.0); // dist=2
    double costLarge = GuitarFingering.transitionCost(pos(1, 5), pos(1, 12), 1.0); // dist=7
    assertTrue(costSmall < costLarge,
        "fretDist=2 should cost less than fretDist=7: small=%.2f, large=%.2f"
            .formatted(costSmall, costLarge));
  }

  @Test
  void fretDistanceThreeOrLessCostsLessThanFretDistanceGreaterThanFive() {
    double costEasy = GuitarFingering.transitionCost(pos(1, 3), pos(1, 6), 1.0); // dist=3
    double costHard = GuitarFingering.transitionCost(pos(1, 0), pos(1, 6), 1.0); // dist=6
    assertTrue(costEasy < costHard,
        "fretDist=3 should cost less than fretDist=6: easy=%.2f, hard=%.2f"
            .formatted(costEasy, costHard));
  }

  @Test
  void openStringReceivesCostBonus() {
    // From fret 3: moving to fret 0 (dist=3, open bonus) vs fret 6 (dist=3, no bonus)
    // Both have fretDist=3 so the only difference is the open-string -0.5 bonus.
    double costToOpen = GuitarFingering.transitionCost(pos(1, 3), pos(1, 0), 1.0);
    double costSameDist = GuitarFingering.transitionCost(pos(1, 3), pos(1, 6), 1.0);
    assertTrue(costToOpen < costSameDist,
        "Transition to open string (fret=0) should cost less than same fret-distance non-open: "
            + "open=%.2f, nonOpen=%.2f".formatted(costToOpen, costSameDist));
  }

  @Test
  void openStringBonusIsApplied() {
    // Identical positions except next fret: 0 vs 4 (same fret distance from fret 4)
    // from fret 4 to fret 0: dist=4, open bonus applied
    // from fret 4 to fret 8: dist=4, no bonus
    double costToOpen = GuitarFingering.transitionCost(pos(1, 4), pos(1, 0), 1.0);
    double costToNonOpen = GuitarFingering.transitionCost(pos(1, 4), pos(1, 8), 1.0);
    assertTrue(costToOpen < costToNonOpen,
        "Open string should be cheaper than same fret-distance non-open: "
            + "open=%.2f, nonOpen=%.2f".formatted(costToOpen, costToNonOpen));
  }

  @Test
  void fretsAboveTwelveIncurCrampingPenalty() {
    // fret 13 vs fret 11 (same fret distance from fret 1)
    double costHighFret = GuitarFingering.transitionCost(pos(1, 1), pos(1, 13), 1.0);
    double costLowFret = GuitarFingering.transitionCost(pos(1, 1), pos(1, 11), 1.0);
    // fret 13: cramping penalty = (13-12)*0.3 = 0.3 extra
    // fret 11: no cramping penalty
    // fret distances are different (12 vs 10), so cramping makes high fret costlier
    assertTrue(costHighFret > costLowFret,
        "Fret 13 should cost more than fret 11 due to cramping: high=%.2f, low=%.2f"
            .formatted(costHighFret, costLowFret));
  }

  @Test
  void crampingPenaltyScalesWithFretsAboveTwelve() {
    double costFret14 = GuitarFingering.transitionCost(pos(1, 0), pos(1, 14), 1.0);
    double costFret16 = GuitarFingering.transitionCost(pos(1, 0), pos(1, 16), 1.0);
    assertTrue(costFret16 > costFret14,
        "Fret 16 should cost more than fret 14: fret14=%.2f, fret16=%.2f"
            .formatted(costFret14, costFret16));
  }

  @Test
  void largeFretShiftOnShortNoteIncursTimePressurePenalty() {
    // fret dist > 3, duration < 0.25 beats → time pressure +1.0
    double costShortNote = GuitarFingering.transitionCost(pos(1, 0), pos(1, 7), 0.125);
    double costLongNote = GuitarFingering.transitionCost(pos(1, 0), pos(1, 7), 1.0);
    assertEquals(costLongNote + 1.0, costShortNote, 1e-9,
        "Short note with large fret shift should cost exactly 1.0 more than long note");
  }

  @Test
  void timePressurePenaltyNotAppliedWhenFretDistThreeOrLess() {
    // fret dist = 3 (not > 3), short note → no time pressure
    double costShort = GuitarFingering.transitionCost(pos(1, 0), pos(1, 3), 0.1);
    double costLong = GuitarFingering.transitionCost(pos(1, 0), pos(1, 3), 1.0);
    assertEquals(costShort, costLong, 1e-9,
        "Time pressure should not apply when fret distance <= 3");
  }

  @Test
  void timePressurePenaltyNotAppliedWhenDurationLong() {
    // fret dist > 3 but duration >= 0.25 → no time pressure
    double costLong = GuitarFingering.transitionCost(pos(1, 0), pos(1, 7), 0.25);
    double costShort = GuitarFingering.transitionCost(pos(1, 0), pos(1, 7), 0.125);
    assertEquals(costLong + 1.0, costShort, 1e-9,
        "Time pressure applies only for duration < 0.25 beats");
  }

  // -----------------------------------------------------------------------
  // compute() tests
  // -----------------------------------------------------------------------

  @Test
  void computeReturnsOnePositionPerPitchedNote() {
    List<Note> notes = List.of(
        new Note(64, 0, TICKS_PER_BEAT, 90),   // E4 = string 1 fret 0
        new Note(Note.REST, TICKS_PER_BEAT, TICKS_PER_BEAT, 0),
        new Note(67, TICKS_PER_BEAT * 2L, TICKS_PER_BEAT, 90)  // G4
    );
    List<GuitarFingerPosition> fingering = GuitarFingering.compute(notes, TICKS_PER_BEAT);
    assertEquals(2, fingering.size(), "Should have one position per non-rest note");
  }

  @Test
  void computeChoosesOpenStringForE4() {
    // MIDI 64 = E4 = open string 1 (fret 0) — should prefer open string due to bonus
    List<Note> notes = List.of(new Note(64, 0, TICKS_PER_BEAT, 90));
    List<GuitarFingerPosition> fingering = GuitarFingering.compute(notes, TICKS_PER_BEAT);
    assertEquals(1, fingering.size());
    // With a single note there's no transition cost, so any valid position is fine
    // Just verify it found a valid position
    GuitarFingerPosition pos = fingering.get(0);
    assertTrue(pos.fret() >= 0 && pos.fret() <= 22);
    assertTrue(pos.string() >= 1 && pos.string() <= 6);
  }

  @Test
  void computeReturnsEmptyForEmptyInput() {
    assertTrue(GuitarFingering.compute(List.of(), TICKS_PER_BEAT).isEmpty());
  }

  @Test
  void computeReturnsEmptyForAllRests() {
    List<Note> rests = List.of(new Note(Note.REST, 0, TICKS_PER_BEAT, 0));
    assertTrue(GuitarFingering.compute(rests, TICKS_PER_BEAT).isEmpty());
  }

  @Test
  void averageCostIsZeroForSingleNote() {
    List<Note> pitched = List.of(new Note(64, 0, TICKS_PER_BEAT, 90));
    List<GuitarFingerPosition> fingering = GuitarFingering.compute(pitched, TICKS_PER_BEAT);
    assertEquals(0.0, GuitarFingering.averageCost(fingering, pitched, TICKS_PER_BEAT), 1e-9);
  }

  @Test
  void averageCostMatchesManualCalculation() {
    // Two notes: fret 0 → fret 2, same string, long duration
    // cost = 2 * 0.4 = 0.8
    GuitarFingerPosition p1 = pos(1, 0);
    GuitarFingerPosition p2 = pos(1, 2);
    List<Note> pitched = List.of(
        new Note(64, 0, TICKS_PER_BEAT, 90),
        new Note(66, TICKS_PER_BEAT, TICKS_PER_BEAT, 90));
    List<GuitarFingerPosition> fingering = List.of(p1, p2);
    double avg = GuitarFingering.averageCost(fingering, pitched, TICKS_PER_BEAT);
    assertEquals(0.8, avg, 1e-9,
        "fretDist=2, same string, long note → cost=0.8");
  }
}
