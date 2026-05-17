package com.motifgen.guitar.backing;

import com.motifgen.model.Motif;
import com.motifgen.model.Note;
import com.motifgen.model.Sentence;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TDD tests for GitHub issue #23 - Drum Track.
 *
 * <p>Covers the 8 acceptance-criteria scenarios:
 * <ol>
 *   <li>Groove generated for every bar per archetype</li>
 *   <li>Half-bar fill on bar 4 of each 8-bar phrase</li>
 *   <li>Full-bar fill on bar 8 of each 8-bar phrase</li>
 *   <li>A/B/C section differentiation</li>
 *   <li>Bass-kick locking</li>
 *   <li>Humanization applied</li>
 *   <li>MIDI export includes drum track on channel 10</li>
 *   <li>MusicXML export includes drum part</li>
 * </ol>
 */
class DrumTrackTest {

  private static final int PPQ = 480;
  private static final int BPB = 4;

  private Sentence sentence;
  private List<ChordSlot> chordSlots;

  @BeforeEach
  void setUp() {
    // 16-bar sentence (4 phrases x 4 bars) — but we want 8-bar phrase tracking.
    // Build a single 8-bar motif so totalBars() == 8 and phrase length matches.
    List<Note> notes = new ArrayList<>();
    for (int bar = 0; bar < 8; bar++) {
      notes.add(new Note(60, (long) bar * BPB * PPQ, PPQ, 80));
    }
    Motif motif = new Motif(notes, 8, BPB, PPQ);
    sentence = new Sentence(List.of(motif), "a a' b a''", "C major", 75.0);

    chordSlots = new ArrayList<>();
    for (int bar = 0; bar < 8; bar++) {
      chordSlots.add(new ChordSlot(
          (long) bar * BPB * PPQ, BPB * PPQ, List.of(60, 64, 67)));
    }
  }

  // -------------------------------------------------------------------------
  // DrumGrooveArchetype
  // -------------------------------------------------------------------------

  @Test
  void drumGrooveArchetypeHasSixValues() {
    assertEquals(6, DrumGrooveArchetype.values().length);
  }

  @Test
  void drumGrooveArchetypeContainsExpectedNames() {
    assertDoesNotThrow(() -> DrumGrooveArchetype.valueOf("DRIVING"));
    assertDoesNotThrow(() -> DrumGrooveArchetype.valueOf("BALLAD"));
    assertDoesNotThrow(() -> DrumGrooveArchetype.valueOf("FOLK"));
    assertDoesNotThrow(() -> DrumGrooveArchetype.valueOf("FUNK"));
    assertDoesNotThrow(() -> DrumGrooveArchetype.valueOf("REGGAE"));
    assertDoesNotThrow(() -> DrumGrooveArchetype.valueOf("POWER"));
  }

  @Test
  void drumGrooveArchetypeFromBassArchetypeMapsAll() {
    for (BassGrooveArchetype bass : BassGrooveArchetype.values()) {
      assertNotNull(DrumGrooveArchetype.fromBassArchetype(bass));
    }
  }

  // -------------------------------------------------------------------------
  // DrumPattern
  // -------------------------------------------------------------------------

  @ParameterizedTest
  @EnumSource(DrumGrooveArchetype.class)
  void drumPatternKickGridIs16Slots(DrumGrooveArchetype a) {
    assertEquals(16, DrumPattern.kickGrid(a).length);
    assertEquals(16, DrumPattern.snareGrid(a).length);
    assertEquals(16, DrumPattern.cymbalGrid(a).length);
  }

  @Test
  void drumPatternGmNoteConstantsHaveExpectedValues() {
    assertEquals(36, DrumPattern.KICK);
    assertEquals(38, DrumPattern.SNARE);
    assertEquals(42, DrumPattern.CLOSED_HIHAT);
    assertEquals(46, DrumPattern.OPEN_HIHAT);
    assertEquals(51, DrumPattern.RIDE);
    assertEquals(53, DrumPattern.RIDE_BELL);
    assertEquals(49, DrumPattern.CRASH);
  }

  // -------------------------------------------------------------------------
  // DrumEvent + DrumTrack
  // -------------------------------------------------------------------------

  @Test
  void drumEventHoldsFields() {
    DrumEvent ev = new DrumEvent(36, 0L, PPQ, 100);
    assertEquals(36, ev.gmNote());
    assertEquals(0L, ev.startTick());
    assertEquals(PPQ, ev.durationTicks());
    assertEquals(100, ev.velocity());
  }

  @Test
  void drumTrackHasChannel9() {
    assertEquals(9, DrumTrack.DRUM_CHANNEL);
  }

  @Test
  void drumTrackRecordHoldsEvents() {
    DrumTrack t = new DrumTrack(List.of(new DrumEvent(36, 0, PPQ, 100)));
    assertEquals(1, t.events().size());
  }

  // -------------------------------------------------------------------------
  // DrumSection
  // -------------------------------------------------------------------------

  @Test
  void drumSectionAUsesClosedHihat() {
    assertEquals(DrumPattern.CLOSED_HIHAT, DrumSection.A.cymbalNote());
    assertFalse(DrumSection.A.ghostSnares());
    assertFalse(DrumSection.A.extraKick());
  }

  @Test
  void drumSectionBUsesRideWithGhostSnares() {
    assertEquals(DrumPattern.RIDE, DrumSection.B.cymbalNote());
    assertTrue(DrumSection.B.ghostSnares());
  }

  @Test
  void drumSectionCUsesRideBellWithExtraKick() {
    assertEquals(DrumPattern.RIDE_BELL, DrumSection.C.cymbalNote());
    assertTrue(DrumSection.C.ghostSnares());
    assertTrue(DrumSection.C.extraKick());
  }

  // -------------------------------------------------------------------------
  // Scenario 1: Groove generated for every non-fill bar per archetype
  // -------------------------------------------------------------------------

  @ParameterizedTest
  @EnumSource(DrumGrooveArchetype.class)
  void groovePresentInEveryNonFillBar(DrumGrooveArchetype archetype) {
    DrumTrack track = DrumTrackGenerator.generate(sentence, chordSlots, null, archetype);
    long ticksPerBar = (long) BPB * PPQ;
    int totalBars = sentence.totalBars();

    for (int bar = 0; bar < totalBars; bar++) {
      int phraseBar = bar % 8;
      // Bar 7 (phraseBar==7) is the full-bar fill — skip
      if (phraseBar == 7) continue;
      long barStart = bar * ticksPerBar;
      long barEnd   = barStart + ticksPerBar;

      boolean hasKick   = hasEventOfType(track, DrumPattern.KICK, barStart, barEnd);
      boolean hasSnare  = hasEventOfType(track, DrumPattern.SNARE, barStart, barEnd);
      boolean hasCymbal = hasCymbal(track, barStart, barEnd);
      assertTrue(hasKick,   "Bar " + bar + " (" + archetype + ") missing kick");
      assertTrue(hasSnare,  "Bar " + bar + " (" + archetype + ") missing snare");
      assertTrue(hasCymbal, "Bar " + bar + " (" + archetype + ") missing cymbal");
    }
  }

  // -------------------------------------------------------------------------
  // Scenario 2: Half-bar fill on bar 4 of each 8-bar phrase
  // -------------------------------------------------------------------------

  @Test
  void halfBarFillOnBar4OfPhrase() {
    DrumTrack track = DrumTrackGenerator.generate(
        sentence, chordSlots, null, DrumGrooveArchetype.DRIVING);
    long ticksPerBar = (long) BPB * PPQ;
    int phraseBar4Index = 3; // 0-indexed; "bar 4"
    long barStart = phraseBar4Index * ticksPerBar;
    long midBar   = barStart + 2L * PPQ;
    long barEnd   = barStart + ticksPerBar;

    // Beats 1-2 must contain normal groove (kick or snare present)
    boolean grooveFirst = hasEventOfType(track, DrumPattern.KICK, barStart, midBar)
        || hasEventOfType(track, DrumPattern.SNARE, barStart, midBar);
    assertTrue(grooveFirst, "Bar 4: beats 1-2 should contain normal groove");

    // Beats 3-4 must contain at least one tom hit (fill marker)
    boolean fillHasTom = false;
    for (DrumEvent e : track.events()) {
      if (e.startTick() >= midBar && e.startTick() < barEnd
          && isTom(e.gmNote())) {
        fillHasTom = true;
        break;
      }
    }
    assertTrue(fillHasTom, "Bar 4: beats 3-4 should contain fill tom hits");
  }

  // -------------------------------------------------------------------------
  // Scenario 3: Full-bar fill on bar 8 ends with crash+kick on next phrase
  // -------------------------------------------------------------------------

  @Test
  void fullBarFillOnBar8WithCrashAndKickOnNextPhrase() {
    // Use a longer sentence — but with only 8 bars total, the "next phrase
    // downbeat" must exist; the generator should still emit a crash/kick at
    // the *end* of the bar-8 region (i.e., at tick == 8 * ticksPerBar) even if
    // that lies at the boundary.
    DrumTrack track = DrumTrackGenerator.generate(
        sentence, chordSlots, null, DrumGrooveArchetype.DRIVING);
    long ticksPerBar = (long) BPB * PPQ;
    long bar7Start = 7L * ticksPerBar;
    long bar7End   = bar7Start + ticksPerBar;

    // Bar 8 (index 7) must contain tom or snare fill events across the bar
    long fillEvents = track.events().stream()
        .filter(e -> e.startTick() >= bar7Start && e.startTick() < bar7End)
        .filter(e -> isTom(e.gmNote()) || e.gmNote() == DrumPattern.SNARE)
        .count();
    assertTrue(fillEvents >= 2, "Bar 8 should contain fill events");

    // A crash+kick should land at the start of the next phrase (== bar7End)
    boolean crashHit = track.events().stream()
        .anyMatch(e -> e.gmNote() == DrumPattern.CRASH
            && Math.abs(e.startTick() - bar7End) <= PPQ / 16L);
    boolean kickAtNext = track.events().stream()
        .anyMatch(e -> e.gmNote() == DrumPattern.KICK
            && Math.abs(e.startTick() - bar7End) <= PPQ / 16L);
    assertTrue(crashHit, "Bar 8 fill should end with crash on next phrase");
    assertTrue(kickAtNext, "Bar 8 fill should end with kick on next phrase");
  }

  // -------------------------------------------------------------------------
  // Scenario 4: A/B/C section differentiation
  // -------------------------------------------------------------------------

  @Test
  void sectionADiffersFromSectionB() {
    // Use a 16-bar sentence and stamp sectionLabels metadata "AB"
    Sentence s16 = build16BarSentence();
    Sentence labelled = s16.withMetadata("sectionLabels", "AB");
    List<ChordSlot> slots = chordSlotsFor(16);

    DrumTrack track = DrumTrackGenerator.generate(
        labelled, slots, null, DrumGrooveArchetype.DRIVING);

    long ticksPerBar = (long) BPB * PPQ;
    // Section A spans bars 0-7 (8 bars), section B spans bars 8-15
    long aStart = 0, aEnd = 8 * ticksPerBar;
    long bStart = 8 * ticksPerBar, bEnd = 16 * ticksPerBar;

    long hihatInA = countByNote(track, DrumPattern.CLOSED_HIHAT, aStart, aEnd);
    long rideInB  = countByNote(track, DrumPattern.RIDE, bStart, bEnd);

    assertTrue(hihatInA > 0, "Section A should use closed hihat");
    assertTrue(rideInB > 0,  "Section B should use ride");
  }

  @Test
  void sectionCUsesRideBellAndExtraKick() {
    // 8-bar sentence labelled "C" — single section across the whole thing
    Sentence labelled = sentence.withMetadata("sectionLabels", "C");
    DrumTrack track = DrumTrackGenerator.generate(
        labelled, chordSlots, null, DrumGrooveArchetype.DRIVING);
    long anyRideBell = track.events().stream()
        .filter(e -> e.gmNote() == DrumPattern.RIDE_BELL).count();
    assertTrue(anyRideBell > 0, "Section C should use ride bell");
  }

  // -------------------------------------------------------------------------
  // Scenario 5: Bass-kick locking
  // -------------------------------------------------------------------------

  @Test
  void kickLocksToBassOnStrongBeats() {
    BassTrack bass = BassTrackGenerator.generate(chordSlots, PPQ);
    DrumTrack track = DrumTrackGenerator.generate(
        sentence, chordSlots, bass, DrumGrooveArchetype.DRIVING);

    long tolerance = Math.round(0.03 * PPQ);
    long ticksPerBeat = PPQ;
    long ticksPerBar = (long) BPB * PPQ;

    // For each bar, find the first kick on beat 1 and find the closest bass note
    for (int bar = 0; bar < sentence.totalBars(); bar++) {
      if (bar % 8 == 7) continue; // skip fill bar
      long barStart = bar * ticksPerBar;
      long beat1End = barStart + ticksPerBeat / 2;

      DrumEvent kick = track.events().stream()
          .filter(e -> e.gmNote() == DrumPattern.KICK)
          .filter(e -> e.startTick() >= barStart - tolerance
              && e.startTick() <= beat1End)
          .findFirst().orElse(null);
      if (kick == null) continue;

      long kickTick = kick.startTick();
      long nearestBass = bass.notes().stream()
          .mapToLong(cn -> cn.note().startTick())
          .reduce(Long.MAX_VALUE,
              (a, b) -> Math.abs(b - kickTick) < Math.abs(a - kickTick) ? b : a);
      if (nearestBass == Long.MAX_VALUE) continue;
      assertTrue(Math.abs(kickTick - nearestBass) <= tolerance,
          "Bar " + bar + ": kick at " + kickTick
              + " not locked to bass at " + nearestBass);
    }
  }

  // -------------------------------------------------------------------------
  // Scenario 6: Humanization applied
  // -------------------------------------------------------------------------

  @Test
  void humanizationKeepsKickSnareTimingWithinTolerance() {
    DrumTrack track = DrumTrackGenerator.generate(
        sentence, chordSlots, null, DrumGrooveArchetype.DRIVING);

    long kickSnareTol = Math.round(0.02 * PPQ);
    long otherTol     = Math.round(0.05 * PPQ);
    long sixteenth    = PPQ / 4;

    for (DrumEvent e : track.events()) {
      // The closest sixteenth-grid position
      long mod = e.startTick() % sixteenth;
      long jitter = Math.min(mod, sixteenth - mod);
      long allowed = (e.gmNote() == DrumPattern.KICK || e.gmNote() == DrumPattern.SNARE)
          ? kickSnareTol : otherTol;
      assertTrue(jitter <= allowed + 1,
          "Event " + e.gmNote() + " at " + e.startTick()
              + " jitter " + jitter + " > " + allowed);
    }
  }

  @Test
  void humanizationVelocityWithinTolerance() {
    DrumTrack track = DrumTrackGenerator.generate(
        sentence, chordSlots, null, DrumGrooveArchetype.DRIVING);
    for (DrumEvent e : track.events()) {
      assertTrue(e.velocity() >= 1 && e.velocity() <= 127,
          "Velocity " + e.velocity() + " out of [1, 127]");
    }
  }

  // -------------------------------------------------------------------------
  // Scenario 7: MIDI export includes drum track on channel 10 (index 9)
  // -------------------------------------------------------------------------

  @Test
  void midiExportDrumTrackContainsChannel9() throws Exception {
    BackingTrack backing = new BackingTrack(List.of(), 25, 0.0);
    BassTrack bass = new BassTrack(List.of(), 34, 0.0);
    DrumTrack drums = new DrumTrack(List.of(
        new DrumEvent(DrumPattern.KICK, 0, PPQ, 100),
        new DrumEvent(DrumPattern.SNARE, PPQ, PPQ, 90)
    ));
    java.io.File tmp = java.io.File.createTempFile("drum-midi", ".mid");
    tmp.deleteOnExit();
    com.motifgen.exporter.MidiExporter.export(sentence, backing, bass, drums, tmp, 120);

    javax.sound.midi.Sequence seq = javax.sound.midi.MidiSystem.getSequence(tmp);
    boolean foundDrumChannel = false;
    for (javax.sound.midi.Track t : seq.getTracks()) {
      for (int i = 0; i < t.size(); i++) {
        javax.sound.midi.MidiEvent ev = t.get(i);
        if (ev.getMessage() instanceof javax.sound.midi.ShortMessage sm) {
          if (sm.getCommand() == javax.sound.midi.ShortMessage.NOTE_ON
              && sm.getChannel() == 9) {
            foundDrumChannel = true;
            break;
          }
        }
      }
    }
    assertTrue(foundDrumChannel, "MIDI export must contain notes on channel 9");
  }

  // -------------------------------------------------------------------------
  // Scenario 8: MusicXML export includes drum part
  // -------------------------------------------------------------------------

  @Test
  void musicXmlExportContainsPercussionPart() throws Exception {
    BackingTrack backing = new BackingTrack(List.of(), 25, 0.0);
    BassTrack bass = new BassTrack(List.of(), 34, 0.0);
    DrumTrack drums = new DrumTrack(List.of(
        new DrumEvent(DrumPattern.KICK, 0, PPQ, 100),
        new DrumEvent(DrumPattern.SNARE, PPQ, PPQ, 90),
        new DrumEvent(DrumPattern.CLOSED_HIHAT, 2 * PPQ, PPQ, 80)
    ));
    java.io.File tmp = java.io.File.createTempFile("drum-xml", ".musicxml");
    tmp.deleteOnExit();
    com.motifgen.exporter.MusicXMLExporter.export(
        sentence, backing, bass, drums, tmp, 120);

    String xml = java.nio.file.Files.readString(tmp.toPath());
    assertTrue(xml.contains("Drums") || xml.contains("Percussion"),
        "MusicXML must contain a percussion / drum part");
    assertTrue(xml.contains("unpitched"),
        "MusicXML drum part should use <unpitched> notes");
  }

  // -------------------------------------------------------------------------
  // Helpers
  // -------------------------------------------------------------------------

  /** Bar membership with a small humanization tolerance (one sixteenth at PPQ=480). */
  private static final long BAR_TOLERANCE = PPQ / 4;

  private static boolean hasEventOfType(DrumTrack track, int gmNote, long start, long end) {
    return track.events().stream()
        .anyMatch(e -> e.gmNote() == gmNote
            && e.startTick() >= start - BAR_TOLERANCE
            && e.startTick() < end);
  }

  private static boolean hasCymbal(DrumTrack track, long start, long end) {
    return track.events().stream()
        .anyMatch(e -> isCymbal(e.gmNote())
            && e.startTick() >= start - BAR_TOLERANCE
            && e.startTick() < end);
  }

  private static boolean isCymbal(int gmNote) {
    return gmNote == DrumPattern.CLOSED_HIHAT || gmNote == DrumPattern.OPEN_HIHAT
        || gmNote == DrumPattern.RIDE || gmNote == DrumPattern.RIDE_BELL
        || gmNote == DrumPattern.CRASH;
  }

  private static boolean isTom(int gmNote) {
    return gmNote == DrumPattern.HIGH_TOM || gmNote == DrumPattern.MID_TOM
        || gmNote == DrumPattern.LOW_TOM;
  }

  private static long countByNote(DrumTrack track, int gmNote, long start, long end) {
    return track.events().stream()
        .filter(e -> e.gmNote() == gmNote && e.startTick() >= start
            && e.startTick() < end)
        .count();
  }

  private Sentence build16BarSentence() {
    List<Note> notes = new ArrayList<>();
    for (int bar = 0; bar < 16; bar++) {
      notes.add(new Note(60, (long) bar * BPB * PPQ, PPQ, 80));
    }
    Motif motif = new Motif(notes, 16, BPB, PPQ);
    return new Sentence(List.of(motif), "AB", "C major", 75.0);
  }

  private List<ChordSlot> chordSlotsFor(int bars) {
    List<ChordSlot> slots = new ArrayList<>();
    for (int bar = 0; bar < bars; bar++) {
      slots.add(new ChordSlot(
          (long) bar * BPB * PPQ, BPB * PPQ, List.of(60, 64, 67)));
    }
    return slots;
  }
}
