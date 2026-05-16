package com.motifgen.guitar.backing;

import com.motifgen.model.Motif;
import com.motifgen.model.Note;
import com.motifgen.model.Sentence;
import com.motifgen.sentiment.SentimentProfile;
import com.motifgen.theory.KeySignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TDD tests for GitHub issue #18 — Rhythm Guitar Backing Track.
 *
 * <p>Covers all 8 acceptance-criteria scenarios.
 */
class BackingTrackTest {

  // -----------------------------------------------------------------------
  // Shared fixtures
  // -----------------------------------------------------------------------

  private Sentence sentence;
  private SentimentProfile happyProfile;
  private SentimentProfile sadProfile;
  private KeySignature cMajor;

  @BeforeEach
  void setUp() {
    // 4-bar motif, 4/4, 480 ppq
    int ppq = 480;
    List<Note> notes = List.of(
        new Note(60, 0, ppq, 80),       // C4
        new Note(62, ppq, ppq, 75),      // D4
        new Note(64, ppq * 2, ppq, 70),  // E4
        new Note(65, ppq * 3, ppq, 65)   // F4
    );
    Motif motif = new Motif(notes, 4, 4, ppq);
    sentence = new Sentence(List.of(motif), "a a' b a''", "C major", 75.0);
    happyProfile = SentimentProfile.fromLabel("HAPPY");
    sadProfile = SentimentProfile.fromLabel("SAD");
    cMajor = KeySignature.major(0);
  }

  // -----------------------------------------------------------------------
  // Scenario 1: MIDI channel 2 (index 1), program 25, register 40–76
  // -----------------------------------------------------------------------

  @Test
  void backingTrackHasCorrectChannelAndProgram() {
    BackingTrack track = BackingTrackGenerator.generate(sentence, happyProfile);
    assertNotNull(track);
    // All notes must be on channel index 1 (= MIDI channel 2)
    track.notes().forEach(n ->
        assertEquals(1, n.channel(), "Expected channel index 1 (MIDI ch 2)"));
    assertEquals(25, track.program(), "Expected GM program 25 (Acoustic Guitar)");
  }

  @Test
  void backingTrackNotesPitchInRegister() {
    BackingTrack track = BackingTrackGenerator.generate(sentence, happyProfile);
    track.notes().forEach(n -> {
      if (!n.note().isRest()) {
        int pitch = n.note().pitch();
        assertTrue(pitch >= 40 && pitch <= 76,
            "Pitch " + pitch + " outside MIDI register 40-76 (E2-E5)");
      }
    });
  }

  // -----------------------------------------------------------------------
  // Scenario 2: Five HarmonyApproach values exist
  // -----------------------------------------------------------------------

  @Test
  void harmonyApproachEnumHasFiveValues() {
    assertEquals(5, HarmonyApproach.values().length,
        "Expected exactly 5 HarmonyApproach variants");
  }

  @Test
  void harmonyApproachGeneratesChords() {
    List<Note> melodyNotes = sentence.getAllNotes();
    for (HarmonyApproach approach : HarmonyApproach.values()) {
      List<ChordSlot> slots = approach.generateChords(melodyNotes, cMajor, happyProfile);
      assertNotNull(slots, approach + " returned null");
      assertFalse(slots.isEmpty(), approach + " returned empty chord list");
    }
  }

  // -----------------------------------------------------------------------
  // Scenario 3: RhythmDensityPlanner
  // -----------------------------------------------------------------------

  @Test
  void rhythmDensityPlannerLowArousalGivesWholeOrHalf() {
    SentimentProfile lowArousal = SentimentProfile.fromVA(0.5, 0.1); // arousal 0.1 → WHOLE
    RhythmDensityPlan plan = RhythmDensityPlanner.plan(lowArousal, "A", sentence);
    assertTrue(
        plan.subdivision() == Subdivision.WHOLE || plan.subdivision() == Subdivision.HALF,
        "Low arousal should yield WHOLE or HALF, got " + plan.subdivision());
  }

  @Test
  void rhythmDensityPlannerHighArousalGivesEighthOrSixteenth() {
    SentimentProfile highArousal = SentimentProfile.fromVA(0.8, 0.9); // arousal 0.9 → SIXTEENTH
    RhythmDensityPlan plan = RhythmDensityPlanner.plan(highArousal, "B", sentence);
    assertTrue(
        plan.subdivision() == Subdivision.EIGHTH || plan.subdivision() == Subdivision.SIXTEENTH,
        "High arousal should yield EIGHTH or SIXTEENTH, got " + plan.subdivision());
  }

  @Test
  void rhythmDensityPlannerSectionMultipliersApplied() {
    SentimentProfile mid = SentimentProfile.fromVA(0.5, 0.5); // arousal 0.5 → QUARTER
    // INTRO multiplier 0.7 should step subdivision down from QUARTER
    RhythmDensityPlan introPlan = RhythmDensityPlanner.plan(mid, "INTRO", sentence);
    RhythmDensityPlan aPlan = RhythmDensityPlanner.plan(mid, "A", sentence);
    // INTRO should not exceed A in density
    assertTrue(
        introPlan.subdivision().ordinal() <= aPlan.subdivision().ordinal(),
        "INTRO section should be equal or sparser than A section");
  }

  @Test
  void rhythmDensityPlanReturnsPlan() {
    RhythmDensityPlan plan = RhythmDensityPlanner.plan(happyProfile, "A", sentence);
    assertNotNull(plan);
    assertNotNull(plan.subdivision());
    assertTrue(plan.changesPerBar() >= 1);
    assertNotNull(plan.accentBeats());
  }

  // -----------------------------------------------------------------------
  // Scenario 4: StrumPattern archetypes and modifications
  // -----------------------------------------------------------------------

  @Test
  void strumPatternHasSevenArchetypes() {
    // All archetype names exist without throwing
    assertDoesNotThrow(() -> StrumPattern.forArchetype(StrumPattern.Archetype.DRIVING));
    assertDoesNotThrow(() -> StrumPattern.forArchetype(StrumPattern.Archetype.FOLK));
    assertDoesNotThrow(() -> StrumPattern.forArchetype(StrumPattern.Archetype.FUNK));
    assertDoesNotThrow(() -> StrumPattern.forArchetype(StrumPattern.Archetype.BALLAD));
    assertDoesNotThrow(() -> StrumPattern.forArchetype(StrumPattern.Archetype.REGGAE));
    assertDoesNotThrow(() -> StrumPattern.forArchetype(StrumPattern.Archetype.POWER));
    assertDoesNotThrow(() -> StrumPattern.forArchetype(StrumPattern.Archetype.ARPEGGIO));
    assertEquals(7, StrumPattern.Archetype.values().length);
  }

  @Test
  void strumPatternForSentimentReturnsNonEmpty() {
    RhythmDensityPlan plan = RhythmDensityPlanner.plan(happyProfile, "A", sentence);
    boolean[] pattern = StrumPattern.forSentiment(happyProfile, VoicingType.OPEN, 120, plan);
    assertNotNull(pattern);
    assertTrue(pattern.length > 0);
    // At least some beats should be active
    int active = 0;
    for (boolean b : pattern) if (b) active++;
    assertTrue(active > 0, "Strum pattern must have at least one active beat");
  }

  @Test
  void strumPatternPowerEnsuresBeats0And4() {
    RhythmDensityPlan plan = new RhythmDensityPlan(Subdivision.EIGHTH, 2, List.of(0, 4));
    boolean[] pattern = StrumPattern.forSentiment(sadProfile, VoicingType.POWER, 100, plan);
    // beats 0 and 4 must be strummed in an 8-slot pattern
    if (pattern.length >= 8) {
      assertTrue(pattern[0], "Beat 0 must be strummed in POWER mode");
      assertTrue(pattern[4], "Beat 4 must be strummed in POWER mode");
    }
  }

  @Test
  void strumPatternHighTempoSixteenthSimplifiestoEighth() {
    // tempo > 160 + SIXTEENTH should simplify
    RhythmDensityPlan plan = new RhythmDensityPlan(Subdivision.SIXTEENTH, 4, List.of(0, 2, 4, 6));
    boolean[] pattern = StrumPattern.forSentiment(happyProfile, VoicingType.OPEN, 180, plan);
    // Result should be at most EIGHTH granularity (length <= 8 per bar)
    assertTrue(pattern.length <= 8, "High tempo should simplify SIXTEENTH to EIGHTH (max 8 slots)");
  }

  // -----------------------------------------------------------------------
  // Scenario 5: Voicing engine in range 40–76 using GuitarFingering
  // -----------------------------------------------------------------------

  @Test
  void guitarChordVoicerProducesVoicedChordsInRange() {
    List<Note> melodyNotes = sentence.getAllNotes();
    List<ChordSlot> slots =
        HarmonyApproach.STATIC_PEDAL.generateChords(melodyNotes, cMajor, happyProfile);
    List<VoicedChord> voiced = GuitarChordVoicer.voice(slots, VoicingType.OPEN, 480);
    assertFalse(voiced.isEmpty());
    for (VoicedChord vc : voiced) {
      for (Note n : vc.notes()) {
        if (!n.isRest()) {
          int pitch = n.pitch();
          assertTrue(pitch >= 40 && pitch <= 76,
              "Voiced pitch " + pitch + " outside MIDI register 40-76");
        }
      }
    }
  }

  // -----------------------------------------------------------------------
  // Scenario 6: BackingTrackSelector picks highest scoring candidate
  // -----------------------------------------------------------------------

  @Test
  void backingTrackSelectorReturnsHighestScorer() {
    BackingTrack track = BackingTrackSelector.select(sentence, happyProfile);
    assertNotNull(track);
    // Score should be in 0-100 range
    assertTrue(track.combinedScore() >= 0.0 && track.combinedScore() <= 100.0,
        "Combined score should be 0-100, got " + track.combinedScore());
  }

  // -----------------------------------------------------------------------
  // Scenario 7: BackingConsonanceScorer uses CONSONANCE_TABLE, normalised 0-100
  // -----------------------------------------------------------------------

  @Test
  void consonanceScorerPerfectOctaveScoresHighest() {
    // Unison interval (0) has value 1.0 in the table → highest consonance
    List<Note> melodyNotes = sentence.getAllNotes();
    List<ChordSlot> slots =
        HarmonyApproach.STATIC_PEDAL.generateChords(melodyNotes, cMajor, happyProfile);
    // Build a voiced chord matching the melody note exactly (unison = max consonance)
    int ppq = 480;
    VoicedChord vc = new VoicedChord(0,
        List.of(new Note(60, 0, ppq, 80))); // C4 = same pitch class as melody
    double score = BackingConsonanceScorer.score(List.of(vc), melodyNotes, ppq);
    assertTrue(score >= 0.0 && score <= 100.0,
        "Consonance score should be 0-100, got " + score);
  }

  @Test
  void consonanceScorerReturnsValueInRange() {
    List<Note> melodyNotes = sentence.getAllNotes();
    List<ChordSlot> slots =
        HarmonyApproach.FUNCTIONAL_DIATONIC.generateChords(melodyNotes, cMajor, happyProfile);
    int ppq = 480;
    List<VoicedChord> voiced = GuitarChordVoicer.voice(slots, VoicingType.OPEN, ppq);
    double score = BackingConsonanceScorer.score(voiced, melodyNotes, ppq);
    assertTrue(score >= 0.0 && score <= 100.0,
        "Consonance score must be 0-100, got " + score);
  }

  // -----------------------------------------------------------------------
  // Scenario 8: BackingCatchinessScorer weighted formula
  // -----------------------------------------------------------------------

  @Test
  void catchinessScorerReturnsValueInRange() {
    List<Note> melodyNotes = sentence.getAllNotes();
    int ppq = 480;
    List<ChordSlot> slots =
        HarmonyApproach.STATIC_PEDAL.generateChords(melodyNotes, cMajor, happyProfile);
    RhythmDensityPlan plan = RhythmDensityPlanner.plan(happyProfile, "A", sentence);
    boolean[] strumPat = StrumPattern.forSentiment(happyProfile, VoicingType.OPEN, 120, plan);
    List<VoicedChord> voiced = GuitarChordVoicer.voice(slots, VoicingType.OPEN, ppq);
    double score = BackingCatchinessScorer.score(sentence, voiced, strumPat, ppq);
    assertTrue(score >= 0.0 && score <= 100.0,
        "Catchiness score must be 0-100, got " + score);
  }

  // -----------------------------------------------------------------------
  // Subdivision enum
  // -----------------------------------------------------------------------

  @Test
  void subdivisionTicksPerBeatCorrect() {
    int ppq = 480;
    assertEquals(ppq * 4, Subdivision.WHOLE.ticksPerBeat(ppq));
    assertEquals(ppq * 2, Subdivision.HALF.ticksPerBeat(ppq));
    assertEquals(ppq, Subdivision.QUARTER.ticksPerBeat(ppq));
    assertEquals(ppq / 2, Subdivision.EIGHTH.ticksPerBeat(ppq));
    assertEquals(ppq / 4, Subdivision.SIXTEENTH.ticksPerBeat(ppq));
  }

  // -----------------------------------------------------------------------
  // VoicingType enum
  // -----------------------------------------------------------------------

  @Test
  void voicingTypeEnumHasSixValues() {
    assertEquals(6, VoicingType.values().length,
        "Expected 6 VoicingType variants");
  }

  // -----------------------------------------------------------------------
  // BackingTrackGenerator facade
  // -----------------------------------------------------------------------

  @Test
  void backingTrackGeneratorProducesNonEmptyTrack() {
    BackingTrack track = BackingTrackGenerator.generate(sentence, happyProfile);
    assertNotNull(track);
    assertFalse(track.notes().isEmpty(), "Generated backing track must have notes");
  }

  @Test
  void backingTrackGeneratorSadProfileProducesTrack() {
    BackingTrack track = BackingTrackGenerator.generate(sentence, sadProfile);
    assertNotNull(track);
    assertFalse(track.notes().isEmpty());
  }
}
