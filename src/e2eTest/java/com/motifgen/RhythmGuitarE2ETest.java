package com.motifgen;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.motifgen.exporter.MidiExporter;
import com.motifgen.guitar.backing.BackingCatchinessScorer;
import com.motifgen.guitar.backing.BackingConsonanceScorer;
import com.motifgen.guitar.backing.BackingTrack;
import com.motifgen.guitar.backing.BackingTrackGenerator;
import com.motifgen.guitar.backing.BackingTrackSelector;
import com.motifgen.guitar.backing.GuitarChordVoicer;
import com.motifgen.guitar.backing.HarmonyApproach;
import com.motifgen.guitar.backing.RhythmDensityPlan;
import com.motifgen.guitar.backing.RhythmDensityPlanner;
import com.motifgen.guitar.backing.StrumPattern;
import com.motifgen.guitar.backing.VoicingType;
import com.motifgen.model.Motif;
import com.motifgen.model.Note;
import com.motifgen.model.Sentence;
import com.motifgen.sentiment.SentimentProfile;
import com.motifgen.theory.KeySignature;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import javax.sound.midi.MidiSystem;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Track;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * End-to-end tests for issue #18 (Add Rhythm Guitar Backing Track).
 *
 * <p>These tests drive the full backing track pipeline — harmony generation,
 * rhythm density planning, strum pattern selection, chord voicing, scoring,
 * and MIDI export — against realistic note sequences, mirroring the Gherkin
 * acceptance criteria.
 */
class RhythmGuitarE2ETest {

  private static final int TICKS_PER_BEAT = 480;
  private static final int BEATS_PER_BAR  = 4;
  private static final int DEFAULT_TEMPO  = 120;

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private Motif motifOf(int[] pitches) {
    List<Note> notes = new ArrayList<>();
    long tick = 0;
    for (int pitch : pitches) {
      notes.add(new Note(pitch, tick, TICKS_PER_BEAT, 80));
      tick += TICKS_PER_BEAT;
    }
    int bars = Math.max(1, (int) Math.ceil((double) pitches.length / BEATS_PER_BAR));
    return new Motif(notes, bars, BEATS_PER_BAR, TICKS_PER_BEAT);
  }

  /** Build a 4-bar C-major sentence from the given pitches. */
  private Sentence sentenceOf(int[] pitches) {
    Motif motif = motifOf(pitches);
    return new Sentence(List.of(motif), "a", "C major", 50.0);
  }

  /** A simple 8-note in-range C-major scale melody. */
  private Sentence cMajorSentence() {
    return sentenceOf(new int[]{60, 62, 64, 65, 67, 69, 71, 72});
  }

  /** Counts active (true) slots in a strum pattern array. */
  private int activeStrums(boolean[] pattern) {
    int count = 0;
    for (boolean b : pattern) if (b) count++;
    return count;
  }

  // ---------------------------------------------------------------------------
  // Scenario 1: Second MIDI track on channel 2, program 25, pitches in [40, 76]
  // ---------------------------------------------------------------------------

  /**
   * Scenario 1.
   * Given a melody sentence and a generated backing track
   * When the two-track MIDI export is performed
   * Then the MIDI file has exactly 2 tracks (Type-1 SMF)
   * And track 1 (the backing) contains a program-change event for program 25
   * And all note-on events in track 1 have pitches in [40, 76].
   */
  @Test
  void given_melodyAndBackingTrack_when_exported_then_twoTrackSMFWithCorrectProgramAndPitchRange()
      throws Exception {
    Sentence melody = cMajorSentence();
    SentimentProfile profile = SentimentProfile.fromLabel("HAPPY");
    BackingTrack backing = BackingTrackGenerator.generate(melody, profile);

    File tmpFile = Files.createTempFile("motifgen-e2e-", ".mid").toFile();
    tmpFile.deleteOnExit();
    MidiExporter.export(melody, backing, tmpFile, DEFAULT_TEMPO);

    Sequence seq = MidiSystem.getSequence(tmpFile);

    // The exported file must be a Type-1 SMF
    assertEquals(Sequence.PPQ, seq.getDivisionType(),
        "SMF division type must be PPQ");
    assertEquals(2, seq.getTracks().length,
        "Exported MIDI must contain exactly 2 tracks (Type-1 SMF)");

    Track backingTrack = seq.getTracks()[1];

    // Track 1 must have a program-change event selecting GM program 25.
    // In MIDI, PROGRAM_CHANGE data byte 0 is the program number (0-indexed),
    // but the MidiExporter writes backing.program() - 1 = 24 (0-indexed) on
    // MIDI channel 1 (0-indexed), which equals GM program 25.
    boolean foundProgramChange = false;
    boolean allNotesInRange = true;

    for (int i = 0; i < backingTrack.size(); i++) {
      javax.sound.midi.MidiEvent event = backingTrack.get(i);
      javax.sound.midi.MidiMessage msg = event.getMessage();
      if (msg instanceof ShortMessage sm) {
        int cmd = sm.getCommand();
        if (cmd == ShortMessage.PROGRAM_CHANGE && sm.getChannel() == BackingTrack.BACKING_CHANNEL) {
          // SM getData1() returns the 0-indexed program; GM program 25 = data1 24
          assertEquals(BackingTrack.GUITAR_PROGRAM - 1, sm.getData1(),
              "Program-change data byte must select GM program 25 (data1=24)");
          foundProgramChange = true;
        }
        if (cmd == ShortMessage.NOTE_ON && sm.getData2() > 0
            && sm.getChannel() == BackingTrack.BACKING_CHANNEL) {
          int pitch = sm.getData1();
          if (pitch < 40 || pitch > 76) {
            allNotesInRange = false;
          }
        }
      }
    }

    assertTrue(foundProgramChange,
        "Track 1 must contain a program-change event on MIDI channel 2 (index 1)");
    assertTrue(allNotesInRange,
        "All note-on events in track 1 must have pitches in [40, 76]");
  }

  // ---------------------------------------------------------------------------
  // Scenario 2: Five harmony approaches produce candidates
  // ---------------------------------------------------------------------------

  /**
   * Scenario 2.
   * Given any of the 5 HarmonyApproach values
   * When BackingTrackGenerator is invoked
   * Then BackingTrackGenerator produces a non-empty BackingTrack.
   */
  @ParameterizedTest(name = "HarmonyApproach.{0} produces a non-empty BackingTrack")
  @EnumSource(HarmonyApproach.class)
  void given_harmonyApproach_when_backingTrackGenerated_then_nonEmptyBackingTrackProduced(
      HarmonyApproach approach) {

    Sentence melody = cMajorSentence();
    SentimentProfile profile = SentimentProfile.fromVA(0.6, 0.6);

    // Exercise the specific approach by using the selector's internal helper:
    // We invoke the full generator (which evaluates all 30 combinations) and
    // separately verify that the named approach itself generates chord slots.
    List<com.motifgen.guitar.backing.ChordSlot> slots = approach.generateChords(
        melody.getAllNotes(),
        KeySignature.major(0), // C major — matches cMajorSentence()
        profile);

    // The approach must return at least one chord slot for a non-empty melody
    assertFalse(slots.isEmpty(),
        "HarmonyApproach." + approach + " must produce at least one chord slot");

    // The full generator must also return a non-null, program-correct result
    BackingTrack backing = BackingTrackGenerator.generate(melody, profile);
    assertNotNull(backing, "BackingTrackGenerator must return a non-null BackingTrack");
    assertEquals(BackingTrack.GUITAR_PROGRAM, backing.program(),
        "BackingTrack program must be 25 (Acoustic Guitar)");
  }

  // ---------------------------------------------------------------------------
  // Scenario 3: Rhythm density adapts to arousal
  // ---------------------------------------------------------------------------

  /**
   * Scenario 3.
   * Given a high-arousal sentiment and a low-arousal sentiment
   * When strum patterns are generated via the full pipeline
   * Then the high-arousal pattern has more active strum events per bar than
   *   the low-arousal pattern.
   */
  @Test
  void given_highVsLowArousal_when_strumPatternsGenerated_then_highArousalIsDenser() {
    Sentence melody = cMajorSentence();

    // High arousal (0.9) → EIGHTH or SIXTEENTH subdivision → DRIVING or FUNK archetype
    SentimentProfile highArousal = SentimentProfile.fromVA(0.6, 0.9);
    // Low arousal (0.1) → WHOLE subdivision → BALLAD archetype
    SentimentProfile lowArousal  = SentimentProfile.fromVA(0.6, 0.1);

    RhythmDensityPlan highPlan = RhythmDensityPlanner.plan(highArousal, "A", melody);
    RhythmDensityPlan lowPlan  = RhythmDensityPlanner.plan(lowArousal,  "A", melody);

    boolean[] highPattern = StrumPattern.forSentiment(
        highArousal, VoicingType.OPEN, DEFAULT_TEMPO, highPlan);
    boolean[] lowPattern  = StrumPattern.forSentiment(
        lowArousal, VoicingType.OPEN, DEFAULT_TEMPO, lowPlan);

    int highActive = activeStrums(highPattern);
    int lowActive  = activeStrums(lowPattern);

    assertTrue(highActive > lowActive,
        "High-arousal strum pattern (active=%d) must be denser than low-arousal (active=%d)"
            .formatted(highActive, lowActive));
  }

  // ---------------------------------------------------------------------------
  // Scenario 4: Strumming pattern responds to sentiment
  // ---------------------------------------------------------------------------

  /**
   * Scenario 4.
   * Given HAPPY sentiment and MELANCHOLY sentiment
   * When strum patterns are generated
   * Then the HAPPY pattern differs from the MELANCHOLY pattern (brighter/denser).
   */
  @Test
  void given_happyVsMelancholySentiment_when_strumPatternGenerated_then_patternsAreDifferent() {
    Sentence melody = cMajorSentence();

    // HAPPY (valence=0.75, arousal=0.70) → high energy, brighter pattern
    // GLOOMY (valence=0.20, arousal=0.20) → low energy, sparse ballad pattern
    SentimentProfile happy  = SentimentProfile.fromLabel("HAPPY");
    SentimentProfile gloomy = SentimentProfile.fromLabel("GLOOMY");

    RhythmDensityPlan happyPlan  = RhythmDensityPlanner.plan(happy,  "A", melody);
    RhythmDensityPlan gloomyPlan = RhythmDensityPlanner.plan(gloomy, "A", melody);

    boolean[] happyPattern  = StrumPattern.forSentiment(
        happy,  VoicingType.OPEN, DEFAULT_TEMPO, happyPlan);
    boolean[] gloomyPattern = StrumPattern.forSentiment(
        gloomy, VoicingType.OPEN, DEFAULT_TEMPO, gloomyPlan);

    int happyActive  = activeStrums(happyPattern);
    int gloomyActive = activeStrums(gloomyPattern);

    // HAPPY must produce a denser (brighter) strum pattern than GLOOMY
    assertTrue(happyActive > gloomyActive,
        "HAPPY strum pattern (active=%d) must be denser/brighter than GLOOMY (active=%d)"
            .formatted(happyActive, gloomyActive));
  }

  // ---------------------------------------------------------------------------
  // Scenario 6: Top candidate selected automatically
  // ---------------------------------------------------------------------------

  /**
   * Scenario 6.
   * Given any valid Sentence and SentimentProfile
   * When BackingTrackSelector is invoked
   * Then it always returns a non-null, non-empty BackingTrack.
   */
  @Test
  void given_validSentenceAndProfile_when_selectorRuns_then_alwaysReturnsNonNullBackingTrack() {
    // Exercise several representative profiles to guard against edge cases
    List<SentimentProfile> profiles = List.of(
        SentimentProfile.fromLabel("HAPPY"),
        SentimentProfile.fromLabel("SAD"),
        SentimentProfile.fromLabel("ANGRY"),
        SentimentProfile.fromLabel("RELAXED"),
        SentimentProfile.fromVA(0.0, 0.0),
        SentimentProfile.fromVA(1.0, 1.0),
        SentimentProfile.fromVA(0.5, 0.5)
    );

    Sentence melody = cMajorSentence();

    for (SentimentProfile profile : profiles) {
      BackingTrack result = BackingTrackSelector.select(melody, profile);
      assertNotNull(result,
          "BackingTrackSelector must never return null (profile: " + profile.closestLabel() + ")");
      assertEquals(BackingTrack.GUITAR_PROGRAM, result.program(),
          "BackingTrack program must always be 25");
    }
  }

  // ---------------------------------------------------------------------------
  // Scenario 7: BackingConsonanceScorer always returns a value in [0, 100]
  // ---------------------------------------------------------------------------

  /**
   * Scenario 7.
   * Given any valid melody and backed chord voicings
   * When BackingConsonanceScorer.score() is called
   * Then the returned value is always in [0, 100].
   */
  @Test
  void given_voicedChordsAndMelody_when_consonanceScorerRuns_then_scoreIsInValidRange() {
    Sentence melody = cMajorSentence();
    List<Note> melodyNotes = melody.getAllNotes();

    // Test all 5 harmony approaches to exercise a variety of chord structures
    for (HarmonyApproach approach : HarmonyApproach.values()) {
      List<com.motifgen.guitar.backing.ChordSlot> slots = approach.generateChords(
          melodyNotes,
          KeySignature.major(0), // C major — matches cMajorSentence()
          SentimentProfile.fromVA(0.5, 0.5));

      List<com.motifgen.guitar.backing.VoicedChord> voiced =
          GuitarChordVoicer.voice(slots, VoicingType.OPEN, TICKS_PER_BEAT);

      double score = BackingConsonanceScorer.score(voiced, melodyNotes, TICKS_PER_BEAT);

      assertTrue(score >= 0.0 && score <= 100.0,
          "BackingConsonanceScorer must return [0,100] for approach "
              + approach + "; got " + score);
    }
  }

  // ---------------------------------------------------------------------------
  // Scenario 8: BackingCatchinessScorer always returns a value in [0, 100]
  // ---------------------------------------------------------------------------

  /**
   * Scenario 8.
   * Given any valid melody sentence, voiced chords, and strum pattern
   * When BackingCatchinessScorer.score() is called
   * Then the returned value is always in [0, 100].
   */
  @Test
  void given_sentenceAndVoicedChords_when_catchinessScorerRuns_then_scoreIsInValidRange() {
    Sentence melody = cMajorSentence();
    List<Note> melodyNotes = melody.getAllNotes();
    SentimentProfile profile = SentimentProfile.fromVA(0.5, 0.5);

    for (HarmonyApproach approach : HarmonyApproach.values()) {
      List<com.motifgen.guitar.backing.ChordSlot> slots = approach.generateChords(
          melodyNotes,
          KeySignature.major(0), // C major — matches cMajorSentence()
          profile);

      List<com.motifgen.guitar.backing.VoicedChord> voiced =
          GuitarChordVoicer.voice(slots, VoicingType.OPEN, TICKS_PER_BEAT);

      RhythmDensityPlan plan = RhythmDensityPlanner.plan(profile, "A", melody);
      boolean[] strumPat = StrumPattern.forSentiment(profile, VoicingType.OPEN, DEFAULT_TEMPO, plan);

      double score = BackingCatchinessScorer.score(melody, voiced, strumPat, TICKS_PER_BEAT);

      assertTrue(score >= 0.0 && score <= 100.0,
          "BackingCatchinessScorer must return [0,100] for approach "
              + approach + "; got " + score);
    }
  }
}
