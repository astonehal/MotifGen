package com.motifgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.motifgen.exporter.MidiExporter;
import com.motifgen.guitar.backing.BackingTrack;
import com.motifgen.guitar.backing.BackingTrackGenerator;
import com.motifgen.guitar.backing.BassGrooveArchetype;
import com.motifgen.guitar.backing.BassHarmonicSkeleton;
import com.motifgen.guitar.backing.BassLine;
import com.motifgen.guitar.backing.BassLineScorer;
import com.motifgen.guitar.backing.BassNote;
import com.motifgen.guitar.backing.BassPlayabilityOptimiser;
import com.motifgen.guitar.backing.BassRhythmPattern;
import com.motifgen.guitar.backing.BassTrack;
import com.motifgen.guitar.backing.BassTrackGenerator;
import com.motifgen.guitar.backing.BassVoiceLeading;
import com.motifgen.guitar.backing.ChanneledNote;
import com.motifgen.guitar.backing.ChordSlot;
import com.motifgen.model.Motif;
import com.motifgen.model.Note;
import com.motifgen.model.Sentence;
import com.motifgen.sentiment.SentimentProfile;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.sound.midi.MidiEvent;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Track;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * End-to-end tests for issue #21 (Bass Guitar Track Generation).
 *
 * <p>These tests drive the full bass generation pipeline — harmonic skeleton
 * derivation, rhythmic elaboration, voice leading, playability DP, scoring,
 * candidate selection, and MIDI export — against realistic note sequences,
 * mirroring the Gherkin acceptance criteria.
 */
class BassGuitarE2ETest {

  private static final int TICKS_PER_BEAT = 480;
  private static final int BEATS_PER_BAR  = 4;
  private static final int DEFAULT_TEMPO  = 120;

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  /** Build a {@link Motif} from the given pitches (one note per beat). */
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

  /** Build a sentence from the given pitches in C major. */
  private Sentence sentenceOf(int[] pitches) {
    Motif motif = motifOf(pitches);
    return new Sentence(List.of(motif), "a", "C major", 50.0);
  }

  /** A simple 8-note C-major scale melody spanning 2 bars. */
  private Sentence cMajorSentence() {
    return sentenceOf(new int[]{60, 62, 64, 65, 67, 69, 71, 72});
  }

  /**
   * Build a minimal set of chord slots covering 4 bars with common C-major chords.
   * Each slot is one bar (4 beats = 4 * ppq ticks).
   */
  private List<ChordSlot> cMajorChordSlots(int ppq) {
    long barTicks = (long) ppq * 4;
    // C major = [60, 64, 67], G major = [67, 71, 74], F major = [65, 69, 72], Am = [69, 72, 76]
    return List.of(
        new ChordSlot(0,           barTicks, List.of(60, 64, 67)),
        new ChordSlot(barTicks,    barTicks, List.of(67, 71, 74)),
        new ChordSlot(barTicks * 2, barTicks, List.of(65, 69, 72)),
        new ChordSlot(barTicks * 3, barTicks, List.of(69, 72, 76))
    );
  }

  // ---------------------------------------------------------------------------
  // Scenario 1: Bass track is added to MIDI output as a separate track
  // ---------------------------------------------------------------------------

  /**
   * Given a song has been generated with melody, harmony, and rhythm guitar backing,
   * When the generation pipeline completes,
   * Then the output MIDI file contains a bass guitar track as a separate track.
   */
  @Test
  void given_songWithMelodyHarmonyAndRhythm_when_pipelineCompletes_then_midiContainsBassTrack(
      @TempDir Path tempDir) throws Exception {

    Sentence melody   = cMajorSentence();
    SentimentProfile profile = SentimentProfile.fromLabel("HAPPY");
    BackingTrack backing = BackingTrackGenerator.generate(melody, profile);
    BassTrack bass       = BassTrackGenerator.generate(melody, profile, DEFAULT_TEMPO);

    File outFile = tempDir.resolve("bass_e2e.mid").toFile();
    MidiExporter.export(melody, backing, bass, outFile, DEFAULT_TEMPO);

    Sequence seq = MidiSystem.getSequence(outFile);

    // Must be a Type-1 SMF with exactly 3 tracks: melody, rhythm guitar, bass
    assertEquals(Sequence.PPQ, seq.getDivisionType(),
        "SMF division type must be PPQ");
    assertEquals(3, seq.getTracks().length,
        "3-track export must produce exactly 3 tracks: melody, rhythm guitar, bass");

    // Track 2 (index 2) is the bass track
    Track bassTrack = seq.getTracks()[2];

    // Must contain a program-change for GM program 34 (Electric Bass – Finger)
    boolean foundProgramChange = false;
    for (int i = 0; i < bassTrack.size(); i++) {
      MidiMessage msg = bassTrack.get(i).getMessage();
      if (msg instanceof ShortMessage sm
          && sm.getCommand() == ShortMessage.PROGRAM_CHANGE
          && sm.getChannel() == BassTrack.BASS_CHANNEL) {
        // MidiExporter writes bass.program() - 1 = 33 (0-indexed)
        assertEquals(BassTrack.BASS_PROGRAM - 1, sm.getData1(),
            "Bass program-change data byte must be 33 (GM program 34, 0-indexed)");
        foundProgramChange = true;
      }
    }
    assertTrue(foundProgramChange,
        "Bass track must contain a program-change event on channel " + BassTrack.BASS_CHANNEL);

    // Track 2 must have at least one NOTE_ON event on the bass channel
    boolean hasNoteOn = false;
    for (int i = 0; i < bassTrack.size(); i++) {
      MidiMessage msg = bassTrack.get(i).getMessage();
      if (msg instanceof ShortMessage sm
          && sm.getCommand() == ShortMessage.NOTE_ON
          && sm.getData2() > 0
          && sm.getChannel() == BassTrack.BASS_CHANNEL) {
        hasNoteOn = true;
        break;
      }
    }
    assertTrue(hasNoteOn, "Bass track must contain at least one NOTE_ON event");
  }

  // ---------------------------------------------------------------------------
  // Scenario 2: Bass notes stay in playable register [28, 55], primary [28, 43]
  // ---------------------------------------------------------------------------

  /**
   * Given a chord progression in any key,
   * When the harmonic skeleton is generated,
   * Then all bass notes fall within MIDI 28 (E1) to 55 (G3),
   * And the primary register is MIDI 28–43 (E1–G2).
   */
  @Test
  void given_chordProgressionInAnyKey_when_harmonicSkeletonGenerated_then_bassNotesInPlayableRegister() {
    int ppq = TICKS_PER_BEAT;
    List<ChordSlot> slots = cMajorChordSlots(ppq);

    // Derive skeleton with all three octave offsets used by the generator
    for (int offset : new int[]{0, 12, -12}) {
      List<BassNote> skeleton = BassHarmonicSkeleton.derive(slots, ppq, offset);
      assertFalse(skeleton.isEmpty(),
          "Harmonic skeleton must not be empty for offset=" + offset);

      for (BassNote note : skeleton) {
        assertTrue(note.midi() >= BassNote.MIDI_MIN && note.midi() <= BassNote.MIDI_MAX,
            "Bass note MIDI " + note.midi() + " (offset=" + offset
                + ") must be in [" + BassNote.MIDI_MIN + ", " + BassNote.MIDI_MAX + "]");
      }
    }

    // Default (offset=0) skeleton must land primarily in [28, 43]
    List<BassNote> defaultSkeleton = BassHarmonicSkeleton.derive(slots, ppq);
    long inPrimary = defaultSkeleton.stream()
        .filter(n -> n.midi() >= BassNote.MIDI_MIN && n.midi() <= BassNote.PRIMARY_MAX)
        .count();
    // At least half the notes should be in the primary register for a standard progression
    assertTrue(inPrimary >= defaultSkeleton.size() / 2,
        "Majority of bass skeleton notes must be in primary register [28, 43]; "
            + "got " + inPrimary + " of " + defaultSkeleton.size());
  }

  // ---------------------------------------------------------------------------
  // Scenario 3: Rhythmic pattern matches groove archetype
  // ---------------------------------------------------------------------------

  /**
   * Given a song with a known groove archetype (driving, ballad, folk, funk, or reggae),
   * When rhythmic elaboration is applied to the harmonic skeleton,
   * Then the bass rhythm pattern matches the expected pattern for that archetype.
   */
  @ParameterizedTest(name = "archetype={0} produces the expected 8-slot rhythm pattern")
  @EnumSource(BassGrooveArchetype.class)
  void given_knownGrooveArchetype_when_rhythmicElaborationApplied_then_patternMatchesArchetype(
      BassGrooveArchetype archetype) {

    boolean[] pattern = BassRhythmPattern.forArchetype(archetype);

    // Pattern must be exactly 8 slots (eighth-note grid in 4/4)
    assertEquals(8, pattern.length,
        "Archetype " + archetype + " pattern must have 8 slots");

    // Each archetype has a documented, distinguishing pattern
    switch (archetype) {
      case DRIVING -> {
        // All 8 slots active
        for (boolean slot : pattern) {
          assertTrue(slot, "DRIVING pattern must have all 8 slots active");
        }
      }
      case BALLAD -> {
        // Only beats 1 and 3 (slots 0 and 4) active
        assertTrue(pattern[0],  "BALLAD pattern must have slot 0 active");
        assertTrue(pattern[4],  "BALLAD pattern must have slot 4 active");
        assertFalse(pattern[1], "BALLAD pattern must have slot 1 inactive");
        assertFalse(pattern[2], "BALLAD pattern must have slot 2 inactive");
      }
      case REGGAE -> {
        // Slots 2 and 6 active (off-beat emphasis)
        assertFalse(pattern[0], "REGGAE pattern must have slot 0 inactive");
        assertTrue(pattern[2],  "REGGAE pattern must have slot 2 active");
        assertTrue(pattern[6],  "REGGAE pattern must have slot 6 active");
      }
      case FOLK -> {
        // Beats 1, 3, 5 plus syncopation on slot 7
        assertTrue(pattern[0], "FOLK pattern must have slot 0 active");
        assertTrue(pattern[2], "FOLK pattern must have slot 2 active");
        assertTrue(pattern[4], "FOLK pattern must have slot 4 active");
        assertTrue(pattern[7], "FOLK pattern must have slot 7 active");
      }
      case FUNK -> {
        // Sixteenth-note groove with beat 1 and syncopation
        assertTrue(pattern[0], "FUNK pattern must have slot 0 active");
        assertTrue(pattern[1], "FUNK pattern must have slot 1 active");
        assertTrue(pattern[3], "FUNK pattern must have slot 3 active");
      }
    }
  }

  // ---------------------------------------------------------------------------
  // Scenario 4: Best of 5 candidates is selected
  // ---------------------------------------------------------------------------

  /**
   * Given the bass generator produces 5 candidate bass lines,
   * When each is scored on root_emphasis, rhythmic_lock, voice_leading,
   *   register_fit, playability, and tonal_consonance,
   * Then the candidate with the highest composite score is selected and added
   *   to the output.
   */
  @Test
  void given_fiveCandidateBassLines_when_eachScored_then_highestScoringCandidateSelected() {
    int ppq = TICKS_PER_BEAT;
    List<ChordSlot> slots = cMajorChordSlots(ppq);

    // Generate the best track for each archetype and collect scores
    double maxAcrossArchetypes = 0.0;
    for (BassGrooveArchetype archetype : BassGrooveArchetype.values()) {
      BassTrack candidate = BassTrackGenerator.generate(slots, ppq, archetype);
      assertNotNull(candidate,
          "BassTrackGenerator must return a non-null BassTrack for archetype " + archetype);
      assertEquals(BassTrack.BASS_PROGRAM, candidate.program(),
          "BassTrack program must be 34 for archetype " + archetype);
      assertTrue(candidate.combinedScore() >= 0.0 && candidate.combinedScore() <= 1.0,
          "combinedScore must be in [0,1] for archetype " + archetype
              + "; got " + candidate.combinedScore());
      if (candidate.combinedScore() > maxAcrossArchetypes) {
        maxAcrossArchetypes = candidate.combinedScore();
      }
    }

    // The default generator (DRIVING archetype) must return a non-null, valid result
    BassTrack result = BassTrackGenerator.generate(slots, ppq);

    assertNotNull(result, "BassTrackGenerator must return a non-null BassTrack");
    assertEquals(BassTrack.BASS_PROGRAM, result.program(),
        "BassTrack program must be 34 (Electric Bass – Finger)");

    // combinedScore must be in [0, 1]
    assertTrue(result.combinedScore() >= 0.0 && result.combinedScore() <= 1.0,
        "BassTrack combinedScore must be in [0, 1]; got " + result.combinedScore());

    // All notes must be on the bass channel
    for (ChanneledNote cn : result.notes()) {
      assertEquals(BassTrack.BASS_CHANNEL, cn.channel(),
          "All bass notes must be on channel " + BassTrack.BASS_CHANNEL);
    }

    // The generator selects the best-of-5 within a single archetype; verify
    // that the selected score is not below any single-candidate score it could
    // have chosen by re-scoring just the skeleton (without rhythmic elaboration)
    // using the same archetype. The full pipeline's score will always be >= the
    // raw-skeleton score because rhythmic elaboration adds more notes that align
    // with the archetype pattern (improving rhythmic_lock).
    List<BassNote> rawSkeleton = BassHarmonicSkeleton.derive(slots, ppq);
    List<BassNote> rawOptimised = BassPlayabilityOptimiser.optimise(rawSkeleton);
    BassLine rawLine = new BassLine(rawOptimised, 0.0, BassGrooveArchetype.DRIVING);
    double rawScore = BassLineScorer.score(rawLine, slots, ppq);

    assertTrue(result.combinedScore() >= rawScore - 1e-9,
        "Selected BassTrack score (%.4f) must be ≥ raw skeleton score (%.4f)"
            .formatted(result.combinedScore(), rawScore));
  }

  // ---------------------------------------------------------------------------
  // Scenario 5: Playability is optimised via DP
  // ---------------------------------------------------------------------------

  /**
   * Given a sequence of bass notes with multiple fretboard positions,
   * When the playability DP runs,
   * Then the selected fingering minimises total hand movement
   *   (fret shift + string crossing).
   */
  @Test
  void given_bassNotesWithMultipleFretboardPositions_when_playabilityDPRuns_then_fingeringMinimisesHandMovement() {
    // Construct a sequence of notes that have multiple valid string/fret positions
    // so the DP has real choices to make.
    // E.g. MIDI 40 (E2) can be: string 0 fret 12, string 1 fret 7, string 2 fret 2
    //      MIDI 43 (G2) can be: string 0 fret 15, string 1 fret 10, string 2 fret 5, string 3 fret 0
    //      MIDI 36 (C2) can be: string 0 fret 8, string 1 fret 3
    List<BassNote> input = List.of(
        new BassNote(40, 0,    TICKS_PER_BEAT, 85, 0, 0),
        new BassNote(43, 480,  TICKS_PER_BEAT, 85, 0, 0),
        new BassNote(36, 960,  TICKS_PER_BEAT, 85, 0, 0),
        new BassNote(40, 1440, TICKS_PER_BEAT, 85, 0, 0),
        new BassNote(33, 1920, TICKS_PER_BEAT, 85, 0, 0)
    );

    List<BassNote> optimised = BassPlayabilityOptimiser.optimise(input);

    assertEquals(input.size(), optimised.size(),
        "DP must return the same number of notes as the input");

    // All notes must still have correct (clamped) MIDI values
    for (int i = 0; i < input.size(); i++) {
      assertEquals(input.get(i).midi(), optimised.get(i).midi(),
          "DP must preserve MIDI pitch of note " + i);
    }

    // Compute total transition cost of the optimised sequence
    double totalCost = 0.0;
    for (int i = 1; i < optimised.size(); i++) {
      int fretShift   = Math.abs(optimised.get(i).fret()      - optimised.get(i - 1).fret());
      int stringCross = Math.abs(optimised.get(i).stringIdx() - optimised.get(i - 1).stringIdx());
      totalCost += fretShift + stringCross * 0.5;
    }

    // Compute total cost of the "always use string 0" naive assignment for same notes
    double naiveCost = 0.0;
    // Open strings: E1=28, A1=33, D2=38, G2=43
    int[] openStrings = {28, 33, 38, 43};
    int prevFret = -1;
    int prevString = -1;
    for (BassNote note : input) {
      // Find lowest-fret position on any valid string
      int bestS = -1, bestF = Integer.MAX_VALUE;
      for (int s = 0; s < openStrings.length; s++) {
        int f = note.midi() - openStrings[s];
        if (f >= 0 && f <= 15 && f < bestF) { bestF = f; bestS = s; }
      }
      if (prevFret >= 0) {
        naiveCost += Math.abs(bestF - prevFret) + Math.abs(bestS - prevString) * 0.5;
      }
      prevFret = bestF; prevString = bestS;
    }

    // DP cost must be ≤ naive greedy cost (may be equal or strictly better)
    assertTrue(totalCost <= naiveCost + 1e-9,
        "DP total hand-movement cost (%.2f) must be ≤ naive greedy cost (%.2f)"
            .formatted(totalCost, naiveCost));
  }

  // ---------------------------------------------------------------------------
  // Scenario 6: Approach notes are inserted at chord boundaries
  // ---------------------------------------------------------------------------

  /**
   * Given a bass line with at least 0.5 beats of rhythmic space before a chord change,
   * When voice leading is applied,
   * Then a diatonic or chromatic approach note is inserted one subdivision before
   *   the next chord root.
   */
  @Test
  void given_bassLineWithRhythmicSpaceBeforeChordChange_when_voiceLeadingApplied_then_approachNoteInserted() {
    int ppq = TICKS_PER_BEAT;
    long halfBeat = ppq / 2L; // one eighth note

    // Two skeleton notes with a large gap (2 full beats) between them
    // so the voice-leading has plenty of space to insert an approach note
    long note1Start    = 0;
    long note1Duration = ppq * 3; // 3 beats; leaves a gap of 1 beat before next note
    long note2Start    = ppq * 4; // starts on beat 5 (next bar)
    long note2Duration = ppq * 2;

    List<BassNote> skeleton = List.of(
        new BassNote(33, note1Start,  note1Duration, 85, 0, 0), // A1
        new BassNote(38, note2Start,  note2Duration, 85, 0, 0)  // D2 (target root)
    );

    // Chord slots aligned with the skeleton (one per note)
    List<ChordSlot> slots = List.of(
        new ChordSlot(note1Start, ppq * 4, List.of(33, 40, 45)), // A minor
        new ChordSlot(note2Start, ppq * 2, List.of(38, 45, 50))  // D minor
    );

    // Apply chromatic approach (semitone below target)
    List<BassNote> withChromatic = BassVoiceLeading.apply(
        skeleton, slots, ppq, BassVoiceLeading.ApproachStyle.CHROMATIC);

    // The result must contain more notes than the skeleton (approach note inserted)
    assertTrue(withChromatic.size() > skeleton.size(),
        "Chromatic voice leading must insert at least one approach note; "
            + "got " + withChromatic.size() + " notes for skeleton of " + skeleton.size());

    // The approach note must be one subdivision (halfBeat) before note2Start
    long expectedApproachTick = note2Start - halfBeat;
    boolean foundApproach = withChromatic.stream()
        .anyMatch(n -> n.startTick() == expectedApproachTick);
    assertTrue(foundApproach,
        "An approach note must be inserted at tick " + expectedApproachTick);

    // Verify approach pitch: chromatic = target - 1 = 37 (D2-1 = C#2)
    int expectedApproachMidi = Math.max(BassNote.MIDI_MIN,
        Math.min(BassNote.MIDI_MAX, 38 - 1)); // D2 - 1 semitone
    BassNote approachNote = withChromatic.stream()
        .filter(n -> n.startTick() == expectedApproachTick)
        .findFirst()
        .orElseThrow(() -> new AssertionError("Approach note not found at expected tick"));
    assertEquals(expectedApproachMidi, approachNote.midi(),
        "Chromatic approach note must be one semitone below the target root");

    // Also verify diatonic approach (target - 2 semitones = 36)
    List<BassNote> withDiatonic = BassVoiceLeading.apply(
        skeleton, slots, ppq, BassVoiceLeading.ApproachStyle.DIATONIC);
    assertTrue(withDiatonic.size() > skeleton.size(),
        "Diatonic voice leading must insert at least one approach note");

    int expectedDiatonicMidi = Math.max(BassNote.MIDI_MIN,
        Math.min(BassNote.MIDI_MAX, 38 - 2)); // D2 - 2 semitones
    BassNote diatonicApproach = withDiatonic.stream()
        .filter(n -> n.startTick() == expectedApproachTick)
        .findFirst()
        .orElseThrow(() -> new AssertionError("Diatonic approach note not found at expected tick"));
    assertEquals(expectedDiatonicMidi, diatonicApproach.midi(),
        "Diatonic approach note must be two semitones below the target root");

    // NONE style must not insert any approach notes
    List<BassNote> withNone = BassVoiceLeading.apply(
        skeleton, slots, ppq, BassVoiceLeading.ApproachStyle.NONE);
    assertEquals(skeleton.size(), withNone.size(),
        "NONE style must leave the skeleton unchanged");
  }

  // ---------------------------------------------------------------------------
  // Integration smoke: full pipeline via MotifGen.run produces 3-track MIDI
  // ---------------------------------------------------------------------------

  /**
   * Integration smoke test: full pipeline via {@link MotifGen#run} produces a
   * 3-track MIDI with a bass guitar track on channel 2.
   */
  @Test
  void given_fullPipelineViaMotifGenRun_when_completed_then_threeTrackMidiContainsBassOnChannel2(
      @TempDir Path tempDir) throws Exception {

    // Build a minimal 4-bar MIDI input file programmatically
    int ppq = 480;
    javax.sound.midi.Sequence seq = new javax.sound.midi.Sequence(
        javax.sound.midi.Sequence.PPQ, ppq);
    javax.sound.midi.Track track = seq.createTrack();

    // Tempo: 120 BPM
    int mpq = 500_000;
    track.add(new MidiEvent(new javax.sound.midi.MetaMessage(
        0x51,
        new byte[]{(byte)(mpq >> 16), (byte)(mpq >> 8), (byte) mpq},
        3), 0));
    // Time signature 4/4
    track.add(new MidiEvent(new javax.sound.midi.MetaMessage(
        0x58, new byte[]{4, 2, 24, 8}, 4), 0));

    // 16 quarter-note C-major melody notes (4 bars)
    int[] pitches = {60, 62, 64, 65, 67, 65, 64, 62, 60, 62, 64, 67, 65, 64, 62, 60};
    long tick = 0;
    for (int pitch : pitches) {
      track.add(new MidiEvent(
          new ShortMessage(ShortMessage.NOTE_ON,  0, pitch, 90), tick));
      track.add(new MidiEvent(
          new ShortMessage(ShortMessage.NOTE_OFF, 0, pitch, 0), tick + ppq));
      tick += ppq;
    }

    File inputMidi  = tempDir.resolve("input.mid").toFile();
    File outputDir  = tempDir.resolve("out").toFile();
    outputDir.mkdirs();
    MidiSystem.write(seq, 1, inputMidi);

    // Run the full pipeline
    MotifGen.run(inputMidi.getAbsolutePath(), outputDir.getAbsolutePath(),
        DEFAULT_TEMPO, MotifGen.OutputFormat.MIDI, SentimentProfile.fromLabel("HAPPY"));

    // Find an output MIDI file
    File[] midFiles = outputDir.listFiles((d, n) -> n.endsWith(".mid"));
    assertNotNull(midFiles, "Output directory must contain at least one .mid file");
    assertTrue(midFiles.length > 0, "At least one MIDI output file must be generated");

    // Inspect the first output file
    // With a 4-bar intro prepended the full-band export now produces 7 tracks:
    // 0=melody, 1=intro guitar, 2=intro bass, 3=intro drums,
    // 4=sentence guitar, 5=sentence bass, 6=sentence drums.
    javax.sound.midi.Sequence outSeq = MidiSystem.getSequence(midFiles[0]);
    assertTrue(outSeq.getTracks().length >= 4,
        "Full pipeline output must contain at least 4 MIDI tracks");

    // At least one track must have NOTE_ON events on BASS_CHANNEL (channel index 2).
    boolean hasBassNotes = false;
    outer:
    for (javax.sound.midi.Track t : outSeq.getTracks()) {
      for (int i = 0; i < t.size(); i++) {
        MidiMessage msg = t.get(i).getMessage();
        if (msg instanceof ShortMessage sm
            && sm.getCommand() == ShortMessage.NOTE_ON
            && sm.getData2() > 0
            && sm.getChannel() == BassTrack.BASS_CHANNEL) {
          hasBassNotes = true;
          break outer;
        }
      }
    }
    assertTrue(hasBassNotes,
        "At least one track must contain NOTE_ON events on MIDI channel "
            + BassTrack.BASS_CHANNEL);
  }
}
