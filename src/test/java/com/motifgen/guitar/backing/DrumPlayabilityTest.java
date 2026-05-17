package com.motifgen.guitar.backing;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TDD tests for GitHub issue #25 — Drum Track Playability.
 *
 * <p>Covers all 8 acceptance-criteria scenarios:
 * <ol>
 *   <li>Drum output has no humanization</li>
 *   <li>Limb collision detection</li>
 *   <li>Right-hand conflict detection</li>
 *   <li>Auto-repair removes lower-priority conflicting hits</li>
 *   <li>Auto-repair fixes limb collision by nudging or removing</li>
 *   <li>Difficulty scoring produces a valid level</li>
 *   <li>Difficulty simplification targets intermediate</li>
 *   <li>Full pipeline output is playable</li>
 * </ol>
 */
class DrumPlayabilityTest {

  private static final int PPQ = 480;
  private static final int SIXTEENTH = PPQ / 4;

  // -------------------------------------------------------------------------
  // Scenario 1: No humanization — events lie on clean 16th-note grid
  // -------------------------------------------------------------------------

  @Test
  void generatedEventsLieOnSixteenthGrid() {
    DrumTrack track = buildSimpleTrack(DrumGrooveArchetype.DRIVING, 4);
    for (DrumEvent e : track.events()) {
      assertEquals(0, e.startTick() % SIXTEENTH,
          "Event " + e.gmNote() + " at tick " + e.startTick() + " is off the 16th grid");
    }
  }

  @Test
  void generatedEventDurationsAreOnSixteenthGrid() {
    DrumTrack track = buildSimpleTrack(DrumGrooveArchetype.DRIVING, 4);
    for (DrumEvent e : track.events()) {
      assertEquals(0, e.durationTicks() % SIXTEENTH,
          "Event " + e.gmNote() + " duration " + e.durationTicks() + " not a 16th multiple");
    }
  }

  // -------------------------------------------------------------------------
  // Scenario 2: Limb collision detection
  // -------------------------------------------------------------------------

  @Test
  void limbCollisionDetectedWhenRightFootTooFast() {
    // Two kicks 120 ticks apart — less than 240-tick minimum for RIGHT_FOOT.
    List<DrumEvent> events = List.of(
        new DrumEvent(DrumPattern.KICK, 0L, SIXTEENTH, 100),
        new DrumEvent(DrumPattern.KICK, 120L, SIXTEENTH, 100)
    );
    List<DrumPlayabilityViolation> violations =
        DrumPlayabilityValidator.validate(events, 120);
    assertTrue(violations.stream()
        .anyMatch(v -> v.type() == DrumPlayabilityViolation.Type.LIMB_COLLISION
            && v.limb() == DrumPlayabilityViolation.Limb.RIGHT_FOOT),
        "Expected LIMB_COLLISION on RIGHT_FOOT");
  }

  @Test
  void limbCollisionDetectedWhenRightHandTooFast() {
    // Two closed hihats 30 ticks apart — less than 60-tick minimum for RIGHT_HAND.
    List<DrumEvent> events = List.of(
        new DrumEvent(DrumPattern.CLOSED_HIHAT, 0L, SIXTEENTH, 80),
        new DrumEvent(DrumPattern.CLOSED_HIHAT, 30L, SIXTEENTH, 80)
    );
    List<DrumPlayabilityViolation> violations =
        DrumPlayabilityValidator.validate(events, 120);
    assertTrue(violations.stream()
        .anyMatch(v -> v.type() == DrumPlayabilityViolation.Type.LIMB_COLLISION
            && v.limb() == DrumPlayabilityViolation.Limb.RIGHT_HAND),
        "Expected LIMB_COLLISION on RIGHT_HAND");
  }

  @Test
  void noLimbCollisionWhenHitsAreWidelySpaced() {
    // Kicks on every quarter note — 480 ticks apart, well above 240-tick minimum.
    List<DrumEvent> events = List.of(
        new DrumEvent(DrumPattern.KICK, 0L, SIXTEENTH, 100),
        new DrumEvent(DrumPattern.KICK, 480L, SIXTEENTH, 100),
        new DrumEvent(DrumPattern.KICK, 960L, SIXTEENTH, 100)
    );
    List<DrumPlayabilityViolation> violations =
        DrumPlayabilityValidator.validate(events, 120);
    assertTrue(violations.stream()
        .noneMatch(v -> v.type() == DrumPlayabilityViolation.Type.LIMB_COLLISION),
        "Expected no LIMB_COLLISION for widely spaced kicks");
  }

  @Test
  void rightHandMinGapIncreasesAbove160Bpm() {
    // At 180 BPM, right-hand minimum gap is 120 ticks.
    // Two hihats 90 ticks apart — below 120, should be a collision.
    List<DrumEvent> events = List.of(
        new DrumEvent(DrumPattern.CLOSED_HIHAT, 0L, SIXTEENTH, 80),
        new DrumEvent(DrumPattern.CLOSED_HIHAT, 90L, SIXTEENTH, 80)
    );
    List<DrumPlayabilityViolation> violations =
        DrumPlayabilityValidator.validate(events, 180);
    assertTrue(violations.stream()
        .anyMatch(v -> v.type() == DrumPlayabilityViolation.Type.LIMB_COLLISION
            && v.limb() == DrumPlayabilityViolation.Limb.RIGHT_HAND),
        "Expected LIMB_COLLISION at 180 BPM for 90-tick gap");
  }

  @Test
  void rightHandNoCollisionAt61TickGapBelow160Bpm() {
    // At 120 BPM, minimum gap is 60 ticks. 61 ticks should be fine.
    List<DrumEvent> events = List.of(
        new DrumEvent(DrumPattern.CLOSED_HIHAT, 0L, SIXTEENTH, 80),
        new DrumEvent(DrumPattern.CLOSED_HIHAT, 61L, SIXTEENTH, 80)
    );
    List<DrumPlayabilityViolation> violations =
        DrumPlayabilityValidator.validate(events, 120);
    assertTrue(violations.stream()
        .noneMatch(v -> v.type() == DrumPlayabilityViolation.Type.LIMB_COLLISION),
        "No collision expected for 61-tick gap at 120 BPM");
  }

  // -------------------------------------------------------------------------
  // Scenario 3: Right-hand conflict detection
  // -------------------------------------------------------------------------

  @Test
  void rightHandConflictDetectedWhenHihatAndRideOnSameTick() {
    List<DrumEvent> events = List.of(
        new DrumEvent(DrumPattern.CLOSED_HIHAT, 0L, SIXTEENTH, 80),
        new DrumEvent(DrumPattern.RIDE, 0L, SIXTEENTH, 80)
    );
    List<DrumPlayabilityViolation> violations =
        DrumPlayabilityValidator.validate(events, 120);
    assertTrue(violations.stream()
        .anyMatch(v -> v.type() == DrumPlayabilityViolation.Type.RIGHT_HAND_CONFLICT),
        "Expected RIGHT_HAND_CONFLICT for hihat_closed + ride on same tick");
  }

  @Test
  void rightHandConflictDetectedWithinTenTickTolerance() {
    // Hihat at 0, ride at 8 — within 10-tick tolerance.
    List<DrumEvent> events = List.of(
        new DrumEvent(DrumPattern.CLOSED_HIHAT, 0L, SIXTEENTH, 80),
        new DrumEvent(DrumPattern.RIDE, 8L, SIXTEENTH, 80)
    );
    List<DrumPlayabilityViolation> violations =
        DrumPlayabilityValidator.validate(events, 120);
    assertTrue(violations.stream()
        .anyMatch(v -> v.type() == DrumPlayabilityViolation.Type.RIGHT_HAND_CONFLICT),
        "Expected RIGHT_HAND_CONFLICT within 10-tick tolerance");
  }

  @Test
  void rightHandConflictDetectedOpenHihatAndRideBell() {
    List<DrumEvent> events = List.of(
        new DrumEvent(DrumPattern.OPEN_HIHAT, 0L, SIXTEENTH, 80),
        new DrumEvent(DrumPattern.RIDE_BELL, 0L, SIXTEENTH, 80)
    );
    List<DrumPlayabilityViolation> violations =
        DrumPlayabilityValidator.validate(events, 120);
    assertTrue(violations.stream()
        .anyMatch(v -> v.type() == DrumPlayabilityViolation.Type.RIGHT_HAND_CONFLICT),
        "Expected RIGHT_HAND_CONFLICT for open_hihat + ride_bell on same tick");
  }

  @Test
  void noRightHandConflictWhenHihatAndRideAreFarApart() {
    List<DrumEvent> events = List.of(
        new DrumEvent(DrumPattern.CLOSED_HIHAT, 0L, SIXTEENTH, 80),
        new DrumEvent(DrumPattern.RIDE, 240L, SIXTEENTH, 80)
    );
    List<DrumPlayabilityViolation> violations =
        DrumPlayabilityValidator.validate(events, 120);
    assertTrue(violations.stream()
        .noneMatch(v -> v.type() == DrumPlayabilityViolation.Type.RIGHT_HAND_CONFLICT),
        "No conflict expected when hihat and ride are 240 ticks apart");
  }

  // -------------------------------------------------------------------------
  // Scenario 4: Auto-repair removes lower-priority conflicting hits
  // -------------------------------------------------------------------------

  @Test
  void repairRemovesLowerPriorityOnRightHandConflict() {
    // Ride (higher priority) vs crash (lower priority) at same tick.
    // Per priority list: ride(51) index 4, crash(49) index 10 → crash is lower priority.
    List<DrumEvent> events = new ArrayList<>(List.of(
        new DrumEvent(DrumPattern.RIDE, 0L, SIXTEENTH, 80),
        new DrumEvent(DrumPattern.CRASH, 0L, SIXTEENTH, 90)   // crash = lower priority
    ));
    List<DrumEvent> repaired = DrumPlayabilityRepair.repair(events, 120);
    // After repair, no RIGHT_HAND_CONFLICT should remain.
    List<DrumPlayabilityViolation> violations =
        DrumPlayabilityValidator.validate(repaired, 120);
    assertTrue(violations.stream()
        .noneMatch(v -> v.type() == DrumPlayabilityViolation.Type.RIGHT_HAND_CONFLICT),
        "Expected no RIGHT_HAND_CONFLICT after repair");
    // Ride should be retained.
    assertTrue(repaired.stream().anyMatch(e -> e.gmNote() == DrumPattern.RIDE),
        "Ride (higher priority) should be retained after repair");
  }

  @Test
  void repairRemovesHihatWhenRideIsHigherPriority() {
    // ride(51) priority 4, hihat_open(46) priority 9 → open hihat is lower priority.
    List<DrumEvent> events = new ArrayList<>(List.of(
        new DrumEvent(DrumPattern.RIDE, 0L, SIXTEENTH, 80),
        new DrumEvent(DrumPattern.OPEN_HIHAT, 0L, SIXTEENTH, 80)
    ));
    List<DrumEvent> repaired = DrumPlayabilityRepair.repair(events, 120);
    List<DrumPlayabilityViolation> violations =
        DrumPlayabilityValidator.validate(repaired, 120);
    assertTrue(violations.stream()
        .noneMatch(v -> v.type() == DrumPlayabilityViolation.Type.RIGHT_HAND_CONFLICT),
        "Expected no RIGHT_HAND_CONFLICT after repair");
  }

  // -------------------------------------------------------------------------
  // Scenario 5: Auto-repair fixes limb collision by nudging or removing
  // -------------------------------------------------------------------------

  @Test
  void repairFixesLimbCollisionOnRightFoot() {
    // Two kicks 120 ticks apart (< 240-tick min for RIGHT_FOOT).
    List<DrumEvent> events = new ArrayList<>(List.of(
        new DrumEvent(DrumPattern.KICK, 0L, SIXTEENTH, 100),
        new DrumEvent(DrumPattern.KICK, 120L, SIXTEENTH, 100)
    ));
    List<DrumEvent> repaired = DrumPlayabilityRepair.repair(events, 120);
    List<DrumPlayabilityViolation> violations =
        DrumPlayabilityValidator.validate(repaired, 120);
    assertTrue(violations.stream()
        .noneMatch(v -> v.type() == DrumPlayabilityViolation.Type.LIMB_COLLISION
            && v.limb() == DrumPlayabilityViolation.Limb.RIGHT_FOOT),
        "Expected no RIGHT_FOOT LIMB_COLLISION after repair");
  }

  @Test
  void repairFixesLimbCollisionOnRightHand() {
    List<DrumEvent> events = new ArrayList<>(List.of(
        new DrumEvent(DrumPattern.CLOSED_HIHAT, 0L, SIXTEENTH, 80),
        new DrumEvent(DrumPattern.CLOSED_HIHAT, 30L, SIXTEENTH, 80)
    ));
    List<DrumEvent> repaired = DrumPlayabilityRepair.repair(events, 120);
    List<DrumPlayabilityViolation> violations =
        DrumPlayabilityValidator.validate(repaired, 120);
    assertTrue(violations.stream()
        .noneMatch(v -> v.type() == DrumPlayabilityViolation.Type.LIMB_COLLISION
            && v.limb() == DrumPlayabilityViolation.Limb.RIGHT_HAND),
        "Expected no RIGHT_HAND LIMB_COLLISION after repair");
  }

  // -------------------------------------------------------------------------
  // Scenario 6: Difficulty scoring produces a valid level
  // -------------------------------------------------------------------------

  @Test
  void difficultyScoringProducesValidLevel() {
    List<DrumEvent> events = buildGrooveEvents(4, 120);
    DrumDifficulty.DifficultyScore score = DrumDifficultyScorer.score(events, 120);
    assertNotNull(score, "Score must not be null");
    assertNotNull(score.level(), "Level must not be null");
    assertTrue(score.numericScore() >= 0.0 && score.numericScore() <= 1.0,
        "Numeric score must be in [0, 1]");
  }

  @Test
  void difficultyScoringLevelIsOneOfFourValues() {
    List<DrumEvent> events = buildGrooveEvents(4, 120);
    DrumDifficulty.DifficultyScore score = DrumDifficultyScorer.score(events, 120);
    DrumDifficulty level = score.level();
    assertTrue(
        level == DrumDifficulty.BEGINNER
            || level == DrumDifficulty.INTERMEDIATE
            || level == DrumDifficulty.ADVANCED
            || level == DrumDifficulty.EXPERT,
        "Level must be BEGINNER, INTERMEDIATE, ADVANCED, or EXPERT");
  }

  @Test
  void emptyTrackScoredAsBeginner() {
    DrumDifficulty.DifficultyScore score = DrumDifficultyScorer.score(List.of(), 120);
    assertEquals(DrumDifficulty.BEGINNER, score.level(),
        "Empty track should be BEGINNER");
  }

  @Test
  void highTempoIncreasesScore() {
    List<DrumEvent> events = buildGrooveEvents(4, 200);
    DrumDifficulty.DifficultyScore slow = DrumDifficultyScorer.score(events, 120);
    DrumDifficulty.DifficultyScore fast = DrumDifficultyScorer.score(events, 200);
    assertTrue(fast.numericScore() >= slow.numericScore(),
        "Higher tempo should produce equal or higher difficulty score");
  }

  // -------------------------------------------------------------------------
  // Scenario 7: Difficulty simplification targets intermediate
  // -------------------------------------------------------------------------

  @Test
  void finaliseTrackSimplifiesToTargetLevel() {
    // Build a complex track likely to score ADVANCED or higher.
    List<DrumEvent> complexEvents = buildComplexEvents();
    DrumTrack track = new DrumTrack(complexEvents);
    DrumTrack simplified = DrumTrackGenerator.finaliseTrack(track, 180, DrumDifficulty.INTERMEDIATE);
    DrumDifficulty.DifficultyScore score =
        DrumDifficultyScorer.score(simplified.events(), 180);
    assertTrue(
        score.level() == DrumDifficulty.BEGINNER
            || score.level() == DrumDifficulty.INTERMEDIATE,
        "After finalisation, track should be INTERMEDIATE or lower, got: " + score.level());
  }

  @Test
  void finaliseTrackHasNoViolations() {
    List<DrumEvent> complexEvents = buildComplexEvents();
    DrumTrack track = new DrumTrack(complexEvents);
    DrumTrack finalised = DrumTrackGenerator.finaliseTrack(track, 120, DrumDifficulty.INTERMEDIATE);
    List<DrumPlayabilityViolation> violations =
        DrumPlayabilityValidator.validate(finalised.events(), 120);
    assertTrue(violations.isEmpty(),
        "Finalised track must have no violations; found: " + violations);
  }

  // -------------------------------------------------------------------------
  // Scenario 8: Full pipeline output is playable
  // -------------------------------------------------------------------------

  @Test
  void generateProducesNoViolations() {
    DrumTrack track = buildSimpleTrack(DrumGrooveArchetype.DRIVING, 4);
    List<DrumPlayabilityViolation> violations =
        DrumPlayabilityValidator.validate(track.events(), 120);
    assertTrue(violations.isEmpty(),
        "Generated track must have no violations; found: " + violations);
  }

  @Test
  void generateViolationsAndDifficultyStoredOnTrack() {
    DrumTrack track = buildSimpleTrack(DrumGrooveArchetype.DRIVING, 4);
    assertNotNull(track.violations(), "Track violations field must not be null");
    assertNotNull(track.difficulty(), "Track difficulty field must not be null");
    assertTrue(track.violations().isEmpty(), "Generated track violations should be empty");
  }

  @Test
  void generateEventsOnCleanGridWithDrumChannel() {
    DrumTrack track = buildSimpleTrack(DrumGrooveArchetype.FUNK, 4);
    for (DrumEvent e : track.events()) {
      assertEquals(0, e.startTick() % SIXTEENTH,
          "Funk track event off the 16th grid at tick " + e.startTick());
    }
  }

  // -------------------------------------------------------------------------
  // DrumDifficulty enum
  // -------------------------------------------------------------------------

  @Test
  void drumDifficultyHasFourLevels() {
    assertEquals(4, DrumDifficulty.values().length);
  }

  @Test
  void drumDifficultyLevelsHaveScoreRanges() {
    for (DrumDifficulty level : DrumDifficulty.values()) {
      assertTrue(level.minScore() >= 0.0 && level.maxScore() <= 1.0,
          level + " score range out of bounds");
      assertTrue(level.minScore() < level.maxScore(),
          level + " min must be < max");
    }
  }

  // -------------------------------------------------------------------------
  // DrumPlayabilityViolation
  // -------------------------------------------------------------------------

  @Test
  void violationRecordHoldsAllFields() {
    DrumEvent ev = new DrumEvent(DrumPattern.KICK, 0L, SIXTEENTH, 100);
    DrumPlayabilityViolation v = new DrumPlayabilityViolation(
        DrumPlayabilityViolation.Type.LIMB_COLLISION,
        DrumPlayabilityViolation.Limb.RIGHT_FOOT,
        0L,
        List.of(ev));
    assertEquals(DrumPlayabilityViolation.Type.LIMB_COLLISION, v.type());
    assertEquals(DrumPlayabilityViolation.Limb.RIGHT_FOOT, v.limb());
    assertEquals(0L, v.beatTick());
    assertEquals(1, v.drumNotes().size());
  }

  // -------------------------------------------------------------------------
  // Helpers
  // -------------------------------------------------------------------------

  private DrumTrack buildSimpleTrack(DrumGrooveArchetype archetype, int bars) {
    var notes = new java.util.ArrayList<com.motifgen.model.Note>();
    for (int bar = 0; bar < bars; bar++) {
      notes.add(new com.motifgen.model.Note(60, (long) bar * 4 * PPQ, PPQ, 80));
    }
    var motif = new com.motifgen.model.Motif(notes, bars, 4, PPQ);
    var sentence = new com.motifgen.model.Sentence(List.of(motif), "a", "C major", 75.0);
    var slots = new java.util.ArrayList<ChordSlot>();
    for (int bar = 0; bar < bars; bar++) {
      slots.add(new ChordSlot((long) bar * 4 * PPQ, 4 * PPQ, List.of(60, 64, 67)));
    }
    return DrumTrackGenerator.generate(sentence, slots, null, archetype);
  }

  /** Builds a basic 4-bar groove event list at a given tempo (for scorer tests). */
  private List<DrumEvent> buildGrooveEvents(int bars, int tempoBpm) {
    List<DrumEvent> events = new ArrayList<>();
    for (int bar = 0; bar < bars; bar++) {
      long barStart = (long) bar * 4 * PPQ;
      for (int slot = 0; slot < 16; slot++) {
        long tick = barStart + (long) slot * SIXTEENTH;
        if (slot % 4 == 0) events.add(new DrumEvent(DrumPattern.KICK, tick, SIXTEENTH, 100));
        if (slot == 4 || slot == 12) events.add(new DrumEvent(DrumPattern.SNARE, tick, SIXTEENTH, 95));
        if (slot % 2 == 0) events.add(new DrumEvent(DrumPattern.CLOSED_HIHAT, tick, SIXTEENTH, 80));
      }
    }
    return events;
  }

  /** Builds a complex event list with ghost snares, syncopated kicks, and toms. */
  private List<DrumEvent> buildComplexEvents() {
    List<DrumEvent> events = new ArrayList<>();
    for (int bar = 0; bar < 4; bar++) {
      long barStart = (long) bar * 4 * PPQ;
      for (int slot = 0; slot < 16; slot++) {
        long tick = barStart + (long) slot * SIXTEENTH;
        // Dense hihat (every 16th).
        events.add(new DrumEvent(DrumPattern.CLOSED_HIHAT, tick, SIXTEENTH, 80));
        // Syncopated kick.
        if (slot % 3 == 0) events.add(new DrumEvent(DrumPattern.KICK, tick, SIXTEENTH, 100));
        // Snare on back-beats + ghost notes.
        if (slot == 4 || slot == 12) events.add(new DrumEvent(DrumPattern.SNARE, tick, SIXTEENTH, 95));
        if (slot % 4 == 1) events.add(new DrumEvent(DrumPattern.SNARE, tick, SIXTEENTH, 35));
        // Toms scattered.
        if (slot == 6 || slot == 10) events.add(new DrumEvent(DrumPattern.HIGH_TOM, tick, SIXTEENTH, 90));
      }
    }
    return events;
  }
}
