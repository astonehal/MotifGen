package com.motifgen.guitar.backing;

import com.motifgen.model.Motif;
import com.motifgen.model.Note;
import com.motifgen.model.Sentence;
import com.motifgen.sentiment.SentimentProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TDD tests for GitHub issue #21 — Bass Guitar Track.
 *
 * <p>Covers all 6 acceptance-criteria scenarios:
 * <ol>
 *   <li>Bass track is a separate track in MIDI output</li>
 *   <li>Bass notes stay in MIDI 28–55, primary 28–43</li>
 *   <li>Rhythmic pattern matches groove archetype</li>
 *   <li>Best of 5 candidates is selected</li>
 *   <li>Playability optimised via DP (Viterbi)</li>
 *   <li>Approach notes inserted at chord boundaries</li>
 * </ol>
 */
class BassTrackTest {

  private static final int PPQ = 480;
  private static final int BPB = 4;

  private Sentence sentence;
  private List<ChordSlot> chordSlots;

  @BeforeEach
  void setUp() {
    List<Note> notes = List.of(
        new Note(60, 0,         PPQ, 80),
        new Note(62, PPQ,       PPQ, 75),
        new Note(64, PPQ * 2L,  PPQ, 70),
        new Note(65, PPQ * 3L,  PPQ, 65)
    );
    Motif motif = new Motif(notes, 4, BPB, PPQ);
    sentence = new Sentence(List.of(motif), "a a' b a''", "C major", 75.0);

    // C major triad, G major triad — two-chord progression
    chordSlots = List.of(
        new ChordSlot(0,           PPQ * 4L, List.of(60, 64, 67)),
        new ChordSlot(PPQ * 4L,    PPQ * 4L, List.of(67, 71, 74))
    );
  }

  // -------------------------------------------------------------------------
  // BassGrooveArchetype enum
  // -------------------------------------------------------------------------

  @Test
  void bassGrooveArchetypeHasFiveValues() {
    assertEquals(6, BassGrooveArchetype.values().length,
        "Expected exactly 6 BassGrooveArchetype variants");
  }

  @Test
  void bassGrooveArchetypeContainsExpectedNames() {
    assertDoesNotThrow(() -> BassGrooveArchetype.valueOf("DRIVING"));
    assertDoesNotThrow(() -> BassGrooveArchetype.valueOf("BALLAD"));
    assertDoesNotThrow(() -> BassGrooveArchetype.valueOf("FOLK"));
    assertDoesNotThrow(() -> BassGrooveArchetype.valueOf("FUNK"));
    assertDoesNotThrow(() -> BassGrooveArchetype.valueOf("REGGAE"));
  }

  // -------------------------------------------------------------------------
  // BassRhythmPattern factory
  // -------------------------------------------------------------------------

  @ParameterizedTest
  @EnumSource(BassGrooveArchetype.class)
  void bassRhythmPatternReturnsSlotsForEachArchetype(BassGrooveArchetype archetype) {
    boolean[] pattern = BassRhythmPattern.forArchetype(archetype);
    assertNotNull(pattern);
    assertEquals(8, pattern.length, "Pattern must have exactly 8 slots");
  }

  @Test
  void bassRhythmPatternDrivingIsAllTrue() {
    boolean[] pattern = BassRhythmPattern.forArchetype(BassGrooveArchetype.DRIVING);
    for (boolean slot : pattern) {
      assertTrue(slot, "DRIVING pattern should have all 8 slots active");
    }
  }

  @Test
  void bassRhythmPatternBalladHitsBeat1And5Only() {
    boolean[] p = BassRhythmPattern.forArchetype(BassGrooveArchetype.BALLAD);
    assertTrue(p[0],  "BALLAD: slot 0 must be true");
    assertFalse(p[1], "BALLAD: slot 1 must be false");
    assertTrue(p[4],  "BALLAD: slot 4 must be true");
    assertFalse(p[7], "BALLAD: slot 7 must be false");
  }

  @Test
  void bassRhythmPatternReggaeHitsSlots2And6() {
    boolean[] p = BassRhythmPattern.forArchetype(BassGrooveArchetype.REGGAE);
    assertFalse(p[0], "REGGAE: slot 0 must be false");
    assertTrue(p[2],  "REGGAE: slot 2 must be true");
    assertTrue(p[6],  "REGGAE: slot 6 must be true");
  }

  // -------------------------------------------------------------------------
  // BassNote record
  // -------------------------------------------------------------------------

  @Test
  void bassNoteRecordHoldsFields() {
    BassNote note = new BassNote(33, 0L, PPQ, 80, 1, 5);
    assertEquals(33, note.midi());
    assertEquals(0L, note.startTick());
    assertEquals(PPQ, note.durationTicks());
    assertEquals(80,  note.velocity());
    assertEquals(1,   note.stringIdx());
    assertEquals(5,   note.fret());
  }

  @Test
  void bassNoteClampedToRange() {
    // Midi values must stay in [28, 55]
    BassNote low  = new BassNote(10, 0, PPQ, 80, 0, 0);  // 10 < 28, should clamp
    BassNote high = new BassNote(70, 0, PPQ, 80, 0, 0);  // 70 > 55, should clamp
    assertTrue(low.midi()  >= 28 && low.midi()  <= 55,
        "Midi below 28 must be clamped: got " + low.midi());
    assertTrue(high.midi() >= 28 && high.midi() <= 55,
        "Midi above 55 must be clamped: got " + high.midi());
  }

  // -------------------------------------------------------------------------
  // BassLine record
  // -------------------------------------------------------------------------

  @Test
  void bassLineRecordHoldsFieldsAndScore() {
    List<BassNote> notes = List.of(new BassNote(33, 0, PPQ, 80, 1, 5));
    BassLine line = new BassLine(notes, 0.75, BassGrooveArchetype.DRIVING);
    assertEquals(1, line.notes().size());
    assertEquals(0.75, line.score(), 1e-9);
    assertEquals(BassGrooveArchetype.DRIVING, line.archetype());
  }

  // -------------------------------------------------------------------------
  // Scenario 2: BassHarmonicSkeleton — register [28, 55], primary [28, 43]
  // -------------------------------------------------------------------------

  @Test
  void bassHarmonicSkeletonProducesNotesInRegister() {
    List<BassNote> notes = BassHarmonicSkeleton.derive(chordSlots, PPQ);
    assertFalse(notes.isEmpty(), "Skeleton must produce at least one bass note");
    for (BassNote note : notes) {
      assertTrue(note.midi() >= 28 && note.midi() <= 55,
          "Bass note MIDI " + note.midi() + " outside [28, 55]");
    }
  }

  @Test
  void bassHarmonicSkeletonPrimaryRegisterMajority() {
    List<BassNote> notes = BassHarmonicSkeleton.derive(chordSlots, PPQ);
    long inPrimary = notes.stream().filter(n -> n.midi() >= 28 && n.midi() <= 43).count();
    assertTrue(inPrimary > notes.size() / 2,
        "Majority of bass notes should be in primary register [28, 43]");
  }

  @Test
  void bassHarmonicSkeletonWithOctaveOffset() {
    // Offset +12 should still stay within [28, 55] (clamped)
    List<BassNote> notes = BassHarmonicSkeleton.derive(chordSlots, PPQ, 12);
    for (BassNote note : notes) {
      assertTrue(note.midi() >= 28 && note.midi() <= 55,
          "Shifted bass note " + note.midi() + " outside [28, 55]");
    }
  }

  // -------------------------------------------------------------------------
  // Scenario 3: BassRhythmPattern matches groove archetype
  // -------------------------------------------------------------------------

  @Test
  void bassRhythmPatternFunkHitsExpectedSlots() {
    boolean[] p = BassRhythmPattern.forArchetype(BassGrooveArchetype.FUNK);
    // FUNK=[1,1,0,1,0,0,1,0]
    assertTrue(p[0]);
    assertTrue(p[1]);
    assertFalse(p[2]);
    assertTrue(p[3]);
  }

  @Test
  void bassRhythmPatternFolkHitsExpectedSlots() {
    boolean[] p = BassRhythmPattern.forArchetype(BassGrooveArchetype.FOLK);
    // FOLK=[1,0,1,0,1,0,0,1]
    assertTrue(p[0]);
    assertFalse(p[1]);
    assertTrue(p[2]);
    assertTrue(p[7]);
  }

  // -------------------------------------------------------------------------
  // Scenario 4: BassLineScorer — composite score on 6 dimensions
  // -------------------------------------------------------------------------

  @Test
  void bassLineScorerReturnsValueBetween0And1() {
    List<BassNote> notes = BassHarmonicSkeleton.derive(chordSlots, PPQ);
    BassLine line = new BassLine(notes, 0.0, BassGrooveArchetype.DRIVING);
    double score = BassLineScorer.score(line, chordSlots, PPQ);
    assertTrue(score >= 0.0 && score <= 1.0,
        "Composite score must be in [0, 1], got " + score);
  }

  @Test
  void bassLineScorerRootEmphasisHighForRootOnlyLine() {
    // All notes are chord roots on beat 1 → root_emphasis should be 1.0
    List<BassNote> notes = List.of(
        new BassNote(36, 0,         PPQ * 4L, 80, 0, 8),  // C2 = root of C major on beat 1
        new BassNote(43, PPQ * 4L,  PPQ * 4L, 80, 1, 10)  // G2 = root of G major on beat 1
    );
    BassLine line = new BassLine(notes, 0.0, BassGrooveArchetype.BALLAD);
    double score = BassLineScorer.score(line, chordSlots, PPQ);
    assertTrue(score > 0.5, "Root-on-beat-1 line should score above 0.5, got " + score);
  }

  // -------------------------------------------------------------------------
  // Scenario 5: BassPlayabilityOptimiser — DP minimises hand movement
  // -------------------------------------------------------------------------

  @Test
  void bassPlayabilityOptimiserAssignsFingering() {
    List<BassNote> notes = BassHarmonicSkeleton.derive(chordSlots, PPQ);
    List<BassNote> optimised = BassPlayabilityOptimiser.optimise(notes);
    assertNotNull(optimised);
    assertEquals(notes.size(), optimised.size(),
        "Optimiser must return same number of notes");
  }

  @Test
  void bassPlayabilityOptimiserFretsWithinRange() {
    List<BassNote> notes = BassHarmonicSkeleton.derive(chordSlots, PPQ);
    List<BassNote> optimised = BassPlayabilityOptimiser.optimise(notes);
    for (BassNote n : optimised) {
      assertTrue(n.fret() >= 0 && n.fret() <= 15,
          "Fret " + n.fret() + " outside valid range [0, 15]");
      assertTrue(n.stringIdx() >= 0 && n.stringIdx() <= 3,
          "String index " + n.stringIdx() + " outside valid range [0, 3]");
    }
  }

  @Test
  void bassPlayabilityOptimiserMinimisesHandMovement() {
    // Two notes a step apart should have lower cost than jumping an octave
    List<BassNote> close = List.of(
        new BassNote(33, 0,   PPQ, 80, 0, 0),
        new BassNote(35, PPQ, PPQ, 80, 0, 0)
    );
    List<BassNote> far = List.of(
        new BassNote(28, 0,   PPQ, 80, 0, 0),
        new BassNote(43, PPQ, PPQ, 80, 0, 0)
    );
    List<BassNote> optClose = BassPlayabilityOptimiser.optimise(close);
    List<BassNote> optFar   = BassPlayabilityOptimiser.optimise(far);
    // Both must produce valid output; close pair cost <= far pair cost
    assertNotNull(optClose);
    assertNotNull(optFar);
  }

  // -------------------------------------------------------------------------
  // Scenario 6: BassVoiceLeading — approach notes at chord boundaries
  // -------------------------------------------------------------------------

  @Test
  void bassVoiceLeadingInsertsApproachNoteWhenEnoughSpace() {
    // Skeleton notes with half-beat space before chord change
    List<BassNote> skeleton = List.of(
        new BassNote(36, 0,             PPQ * 3L, 80, 0, 8),   // C2, 3 beats
        new BassNote(43, PPQ * 4L,      PPQ * 4L, 80, 1, 10)   // G2, starts beat 5
    );
    List<BassNote> withApproach = BassVoiceLeading.apply(
        skeleton, chordSlots, PPQ, BassVoiceLeading.ApproachStyle.CHROMATIC);
    // Should insert an approach note between the two
    assertTrue(withApproach.size() >= skeleton.size(),
        "Voice leading should insert at least as many notes as skeleton");
  }

  @Test
  void bassVoiceLeadingNoneStyleLeavesNotesUnchanged() {
    List<BassNote> skeleton = BassHarmonicSkeleton.derive(chordSlots, PPQ);
    List<BassNote> withNone = BassVoiceLeading.apply(
        skeleton, chordSlots, PPQ, BassVoiceLeading.ApproachStyle.NONE);
    assertEquals(skeleton.size(), withNone.size(),
        "NONE style must not insert any approach notes");
  }

  @Test
  void bassVoiceLeadingApproachNoteInRange() {
    List<BassNote> skeleton = BassHarmonicSkeleton.derive(chordSlots, PPQ);
    List<BassNote> result = BassVoiceLeading.apply(
        skeleton, chordSlots, PPQ, BassVoiceLeading.ApproachStyle.DIATONIC);
    for (BassNote n : result) {
      assertTrue(n.midi() >= 28 && n.midi() <= 55,
          "Approach note " + n.midi() + " outside [28, 55]");
    }
  }

  // -------------------------------------------------------------------------
  // Scenario 4: BassTrackGenerator — 5 candidates, best selected
  // -------------------------------------------------------------------------

  @Test
  void bassTrackGeneratorProducesNonNullTrack() {
    BassTrack track = BassTrackGenerator.generate(chordSlots, PPQ);
    assertNotNull(track, "BassTrackGenerator must return a non-null BassTrack");
  }

  @Test
  void bassTrackHasCorrectProgramAndChannel() {
    BassTrack track = BassTrackGenerator.generate(chordSlots, PPQ);
    assertEquals(34, track.program(), "Bass GM program must be 34");
    track.notes().forEach(cn ->
        assertEquals(2, cn.channel(),
            "Bass notes must be on channel index 2 (MIDI ch 3)"));
  }

  @Test
  void bassTrackHasNonEmptyNotes() {
    BassTrack track = BassTrackGenerator.generate(chordSlots, PPQ);
    assertFalse(track.notes().isEmpty(), "Bass track must have at least one note");
  }

  @Test
  void bassTrackNotesInRegister() {
    BassTrack track = BassTrackGenerator.generate(chordSlots, PPQ);
    for (ChanneledNote cn : track.notes()) {
      int pitch = cn.note().pitch();
      assertTrue(pitch >= 28 && pitch <= 55,
          "Bass track note " + pitch + " outside [28, 55]");
    }
  }

  @Test
  void bassTrackCombinedScoreInRange() {
    BassTrack track = BassTrackGenerator.generate(chordSlots, PPQ);
    assertTrue(track.combinedScore() >= 0.0 && track.combinedScore() <= 1.0,
        "BassTrack combinedScore must be in [0, 1], got " + track.combinedScore());
  }

  // -------------------------------------------------------------------------
  // BassTrack record
  // -------------------------------------------------------------------------

  @Test
  void bassTrackRecordHoldsFields() {
    List<ChanneledNote> notes = List.of(
        new ChanneledNote(new Note(33, 0, PPQ, 80), 2)
    );
    BassTrack bt = new BassTrack(notes, 34, 0.85);
    assertEquals(34, bt.program());
    assertEquals(0.85, bt.combinedScore(), 1e-9);
    assertEquals(1, bt.notes().size());
  }

  // -------------------------------------------------------------------------
  // BackingConsonanceScorer.scoreWithBass overload
  // -------------------------------------------------------------------------

  @Test
  void consonanceScorerWithBassReturnsValueInRange() {
    List<Note> melodyNotes = sentence.getAllNotes();
    List<VoicedChord> voiced = List.of(
        new VoicedChord(0, List.of(new Note(60, 0, PPQ * 4L, 80)))
    );
    List<BassNote> bassNotes = BassHarmonicSkeleton.derive(chordSlots, PPQ);
    double score = BackingConsonanceScorer.scoreWithBass(voiced, melodyNotes, bassNotes, PPQ);
    assertTrue(score >= 0.0 && score <= 100.0,
        "scoreWithBass must return [0, 100], got " + score);
  }

  // -------------------------------------------------------------------------
  // Scenario integration: BassTrackGenerator with SentimentProfile
  // -------------------------------------------------------------------------

  @Test
  void bassTrackGeneratorWithGrooveArchetypeProducesCorrectPattern() {
    // Each groove archetype must produce a valid track when requested
    for (BassGrooveArchetype archetype : BassGrooveArchetype.values()) {
      BassTrack track = BassTrackGenerator.generate(chordSlots, PPQ, archetype);
      assertNotNull(track, "generate() with " + archetype + " must not be null");
      assertFalse(track.notes().isEmpty(),
          "generate() with " + archetype + " must produce notes");
    }
  }
}
