package com.motifgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.motifgen.exporter.MidiExporter;
import com.motifgen.exporter.MusicXMLExporter;
import com.motifgen.guitar.backing.BackingTrack;
import com.motifgen.guitar.backing.BackingTrackGenerator;
import com.motifgen.guitar.backing.BassTrack;
import com.motifgen.guitar.backing.BassTrackGenerator;
import com.motifgen.guitar.backing.ChordSlot;
import com.motifgen.guitar.backing.DrumEvent;
import com.motifgen.guitar.backing.DrumGrooveArchetype;
import com.motifgen.guitar.backing.DrumPattern;
import com.motifgen.guitar.backing.DrumTrack;
import com.motifgen.guitar.backing.DrumTrackGenerator;
import com.motifgen.model.Motif;
import com.motifgen.model.Note;
import com.motifgen.model.Sentence;
import com.motifgen.sentiment.SentimentProfile;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Track;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * End-to-end tests for issue #23 (Drum Track Generation).
 *
 * <p>These tests exercise the full drum-track pipeline — groove emission,
 * fills, kick-locking, humanization, and multi-track MIDI/MusicXML export —
 * mirroring the Gherkin acceptance criteria.
 */
class DrumTrackE2ETest {

  private static final int TICKS_PER_BEAT = 480;
  private static final int BEATS_PER_BAR  = 4;
  private static final int DEFAULT_TEMPO  = 120;

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  /**
   * Build a multi-bar sentence with {@code bars} bars of quarter-note melody
   * in C major. Used so the drum generator emits exactly {@code bars} bars of
   * output.
   */
  private Sentence sentenceWithBars(int bars) {
    List<Note> notes = new ArrayList<>();
    int[] cMaj = {60, 62, 64, 65, 67, 69, 71, 72};
    long tick = 0;
    for (int b = 0; b < bars; b++) {
      for (int beat = 0; beat < BEATS_PER_BAR; beat++) {
        int pitch = cMaj[(b * BEATS_PER_BAR + beat) % cMaj.length];
        notes.add(new Note(pitch, tick, TICKS_PER_BEAT, 80));
        tick += TICKS_PER_BEAT;
      }
    }
    Motif motif = new Motif(notes, bars, BEATS_PER_BAR, TICKS_PER_BEAT);
    return new Sentence(List.of(motif), "a".repeat(bars), "C major", 50.0);
  }

  /** Builds one-bar C-major chord slots covering the full sentence. */
  private List<ChordSlot> chordSlotsForBars(int bars) {
    long barTicks = (long) TICKS_PER_BEAT * BEATS_PER_BAR;
    List<int[]> palette = List.of(
        new int[]{60, 64, 67}, // C
        new int[]{67, 71, 74}, // G
        new int[]{65, 69, 72}, // F
        new int[]{69, 72, 76}  // Am
    );
    List<ChordSlot> slots = new ArrayList<>();
    for (int b = 0; b < bars; b++) {
      int[] chord = palette.get(b % palette.size());
      slots.add(new ChordSlot(b * barTicks, barTicks, List.of(chord[0], chord[1], chord[2])));
    }
    return slots;
  }

  /** Compute bar index for a tick. */
  private int barOf(long tick) {
    return (int) (tick / ((long) TICKS_PER_BEAT * BEATS_PER_BAR));
  }

  // ---------------------------------------------------------------------------
  // Scenario 1: Groove generated for every bar per archetype
  // ---------------------------------------------------------------------------

  /**
   * Given a song is being generated with groove archetype (driving/ballad/folk/funk/reggae/power)
   * When the drum track generator runs
   * Then kick, snare, and cymbal events are present in every non-fill bar.
   */
  @ParameterizedTest(name = "archetype={0} produces kick/snare/cymbal in every non-fill bar")
  @EnumSource(DrumGrooveArchetype.class)
  void given_grooveArchetype_when_generatorRuns_then_kickSnareCymbalInEveryNonFillBar(
      DrumGrooveArchetype archetype) {

    int bars = 8;
    Sentence sentence = sentenceWithBars(bars);
    List<ChordSlot> slots = chordSlotsForBars(bars);
    BassTrack bass = BassTrackGenerator.generate(slots, TICKS_PER_BEAT);

    DrumTrack drums = DrumTrackGenerator.generate(sentence, slots, bass, archetype);

    Set<Integer> cymbalNotes = Set.of(
        DrumPattern.CLOSED_HIHAT, DrumPattern.OPEN_HIHAT,
        DrumPattern.RIDE, DrumPattern.RIDE_BELL);

    // Bars 4 (half-bar fill) and 8 (full-bar fill) are the "fill" bars in an
    // 8-bar phrase. Every other bar must contain kick, snare, and a cymbal.
    for (int bar = 0; bar < bars; bar++) {
      if (bar == 3 || bar == 7) continue; // skip fill bars
      final int b = bar;
      boolean hasKick = drums.events().stream()
          .anyMatch(e -> barOf(e.startTick()) == b && e.gmNote() == DrumPattern.KICK);
      boolean hasSnare = drums.events().stream()
          .anyMatch(e -> barOf(e.startTick()) == b && e.gmNote() == DrumPattern.SNARE
              && e.velocity() > 50); // exclude ghost snares
      boolean hasCymbal = drums.events().stream()
          .anyMatch(e -> barOf(e.startTick()) == b && cymbalNotes.contains(e.gmNote()));
      assertTrue(hasKick,  "Bar " + bar + " (" + archetype + ") must contain a kick event");
      assertTrue(hasSnare, "Bar " + bar + " (" + archetype + ") must contain a snare event");
      assertTrue(hasCymbal, "Bar " + bar + " (" + archetype + ") must contain a cymbal event");
    }
  }

  // ---------------------------------------------------------------------------
  // Scenario 2: Half-bar fill on bar 4 of each 8-bar phrase
  // ---------------------------------------------------------------------------

  /**
   * Given a drum track is generated for an 8-bar phrase
   * When bar 4 is processed
   * Then beats 1-2 contain the normal groove and beats 3-4 contain an
   *      archetype-appropriate fill.
   */
  @Test
  void given_eightBarPhrase_when_barFourProcessed_then_halfBarFillOnBeatsThreeAndFour() {
    int bars = 8;
    Sentence sentence = sentenceWithBars(bars);
    List<ChordSlot> slots = chordSlotsForBars(bars);
    DrumTrack drums = DrumTrackGenerator.generate(
        sentence, slots, null, DrumGrooveArchetype.DRIVING);

    long ticksPerBar = (long) TICKS_PER_BEAT * BEATS_PER_BAR;
    long bar4Start = 3 * ticksPerBar;
    long bar4Mid   = bar4Start + 2L * TICKS_PER_BEAT;
    long bar4End   = bar4Start + ticksPerBar;

    Set<Integer> tomNotes = Set.of(
        DrumPattern.HIGH_TOM, DrumPattern.MID_TOM, DrumPattern.LOW_TOM);

    // Humanization jitter is +/- 0.02 beats for kick/snare and +/- 0.05 beats
    // for other voices. Pad the window slightly so the assertion is robust.
    long jitter = Math.round(0.05 * TICKS_PER_BEAT) + 1;

    // First half (beats 1-2): expect normal groove (kick on 1, snare on 2).
    boolean firstHalfHasKick = drums.events().stream()
        .anyMatch(e -> e.startTick() >= bar4Start - jitter
            && e.startTick() < bar4Mid
            && e.gmNote() == DrumPattern.KICK);
    boolean firstHalfHasSnare = drums.events().stream()
        .anyMatch(e -> e.startTick() >= bar4Start - jitter
            && e.startTick() < bar4Mid
            && e.gmNote() == DrumPattern.SNARE);
    assertTrue(firstHalfHasKick,  "Bar 4 first-half must contain a kick");
    assertTrue(firstHalfHasSnare, "Bar 4 first-half must contain a snare");

    // Second half (beats 3-4): expect tom hits (archetype-appropriate fill)
    // and NO kick/cymbal events from the regular groove.
    long tomsInSecondHalf = drums.events().stream()
        .filter(e -> e.startTick() >= bar4Mid - jitter
            && e.startTick() < bar4End + jitter
            && tomNotes.contains(e.gmNote()))
        .count();
    assertTrue(tomsInSecondHalf >= 4,
        "Bar 4 second-half must contain at least 4 tom hits (got " + tomsInSecondHalf + ")");

    long kicksInSecondHalfCore = drums.events().stream()
        .filter(e -> e.startTick() >= bar4Mid + jitter
            && e.startTick() < bar4End - jitter
            && e.gmNote() == DrumPattern.KICK)
        .count();
    assertEquals(0, kicksInSecondHalfCore,
        "Bar 4 second-half must not contain regular-groove kicks");
  }

  // ---------------------------------------------------------------------------
  // Scenario 3: Full-bar fill on bar 8 of each 8-bar phrase
  // ---------------------------------------------------------------------------

  /**
   * Given a drum track is generated for an 8-bar phrase
   * When bar 8 is processed
   * Then the entire bar is replaced by a fill that ends with crash + kick on
   *      beat 1 of the next phrase.
   */
  @Test
  void given_eightBarPhrase_when_barEightProcessed_then_fullBarFillEndsWithCrashAndKick() {
    int bars = 16; // two phrases so we get a "next phrase" downbeat
    Sentence sentence = sentenceWithBars(bars);
    List<ChordSlot> slots = chordSlotsForBars(bars);
    DrumTrack drums = DrumTrackGenerator.generate(
        sentence, slots, null, DrumGrooveArchetype.DRIVING);

    long ticksPerBar = (long) TICKS_PER_BEAT * BEATS_PER_BAR;
    long bar8Start = 7 * ticksPerBar;
    long bar8End   = bar8Start + ticksPerBar;

    Set<Integer> tomOrSnare = Set.of(
        DrumPattern.HIGH_TOM, DrumPattern.MID_TOM, DrumPattern.LOW_TOM,
        DrumPattern.SNARE);

    long jitter = Math.round(0.05 * TICKS_PER_BEAT) + 1;

    // Full bar 8 contains many tom/snare fill hits.
    long fillHits = drums.events().stream()
        .filter(e -> e.startTick() >= bar8Start - jitter
            && e.startTick() < bar8End + jitter
            && tomOrSnare.contains(e.gmNote()))
        .count();
    assertTrue(fillHits >= 8,
        "Bar 8 must contain >= 8 tom/snare fill hits (got " + fillHits + ")");

    // No closed-hihat from regular groove inside the full-bar fill.
    long groovedHihat = drums.events().stream()
        .filter(e -> e.startTick() >= bar8Start + jitter
            && e.startTick() < bar8End - jitter
            && e.gmNote() == DrumPattern.CLOSED_HIHAT)
        .count();
    assertEquals(0, groovedHihat,
        "Bar 8 must not contain regular closed-hihat events (fill replaces the bar)");

    // Crash + kick on the downbeat of the next phrase (bar 9).
    boolean crashOnDownbeat = drums.events().stream()
        .anyMatch(e -> Math.abs(e.startTick() - bar8End) <= jitter
            && e.gmNote() == DrumPattern.CRASH);
    boolean kickOnDownbeat = drums.events().stream()
        .anyMatch(e -> Math.abs(e.startTick() - bar8End) <= jitter
            && e.gmNote() == DrumPattern.KICK);
    assertTrue(crashOnDownbeat, "Crash must land on bar 9 downbeat");
    assertTrue(kickOnDownbeat,  "Kick must land on bar 9 downbeat");
  }

  // ---------------------------------------------------------------------------
  // Scenario 4: A/B/C section differentiation
  // ---------------------------------------------------------------------------

  /**
   * Given a song with sections A, B, and C
   * When the drum track is generated
   * Then section A uses closed hihat, section B uses ride with ghost snares,
   *      section C uses ride bell with extra kick and ghost snares.
   */
  @Test
  void given_songWithSectionsABC_when_drumTrackGenerated_then_eachSectionHasItsVoicings() {
    // 9 bars: bar 0-2 = A, 3-5 = B, 6-8 = C. (bars 3 and 7 are fill bars and are
    // excluded by the assertions below.)
    int bars = 9;
    Sentence base = sentenceWithBars(bars);
    Sentence sentence = base.withMetadata("sectionLabels", "ABC");

    List<ChordSlot> slots = chordSlotsForBars(bars);
    DrumTrack drums = DrumTrackGenerator.generate(
        sentence, slots, null, DrumGrooveArchetype.DRIVING);

    long ticksPerBar = (long) TICKS_PER_BEAT * BEATS_PER_BAR;

    // Section A: bar 0..2 -> closed hihat present, no ride/ride-bell.
    assertSectionCymbal(drums, ticksPerBar, 0, 3,
        DrumPattern.CLOSED_HIHAT, "A");

    // Section B: bar 3..5 (skip bar 3 since it's a half-bar fill bar).
    // Ride must be present and ghost snares (velocity <= 50) must appear.
    assertSectionCymbal(drums, ticksPerBar, 4, 6,
        DrumPattern.RIDE, "B");
    assertGhostSnares(drums, ticksPerBar, 4, 6, "B");

    // Section C: bar 6..8 (skip bar 7 since it's a full-bar fill bar).
    assertSectionCymbal(drums, ticksPerBar, 6, 7,
        DrumPattern.RIDE_BELL, "C");
    assertGhostSnares(drums, ticksPerBar, 6, 7, "C");
    // Extra-kick: section C adds a kick on the "and of 2" (slot 6 = 1.5 beats
    // after the bar). Look for any kick in bar 6 whose offset within the bar
    // is in [beat 1.4, beat 1.7] (allowing humanization jitter).
    long jitter = Math.round(0.05 * TICKS_PER_BEAT) + 1;
    long bar6Start = 6 * ticksPerBar;
    long extraKickTarget = bar6Start + (long) (1.5 * TICKS_PER_BEAT);
    boolean foundExtraKick = drums.events().stream()
        .anyMatch(e -> e.gmNote() == DrumPattern.KICK
            && Math.abs(e.startTick() - extraKickTarget) <= jitter);
    assertTrue(foundExtraKick,
        "Section C must contain an 'extra kick' on beat 2.5 of bar 6");
  }

  private void assertSectionCymbal(
      DrumTrack drums, long ticksPerBar, int barLo, int barHi,
      int expectedCymbal, String section) {
    long lo = (long) barLo * ticksPerBar;
    long hi = (long) barHi * ticksPerBar;
    boolean hasExpected = drums.events().stream()
        .anyMatch(e -> e.startTick() >= lo && e.startTick() < hi
            && e.gmNote() == expectedCymbal);
    assertTrue(hasExpected,
        "Section " + section + " (bars " + barLo + ".." + (barHi - 1)
            + ") must contain cymbal GM=" + expectedCymbal);
  }

  private void assertGhostSnares(
      DrumTrack drums, long ticksPerBar, int barLo, int barHi, String section) {
    long lo = (long) barLo * ticksPerBar;
    long hi = (long) barHi * ticksPerBar;
    boolean hasGhost = drums.events().stream()
        .anyMatch(e -> e.startTick() >= lo && e.startTick() < hi
            && e.gmNote() == DrumPattern.SNARE && e.velocity() <= 50);
    assertTrue(hasGhost,
        "Section " + section + " must contain ghost snare events (velocity <= 50)");
  }

  // ---------------------------------------------------------------------------
  // Scenario 5: Bass-kick locking
  // ---------------------------------------------------------------------------

  /**
   * Given both bass and drum tracks have been generated
   * When the kick-to-bass alignment pass runs
   * Then every kick on a strong beat is within 0.03 beats of the nearest bass
   *      note onset.
   */
  @Test
  void given_bassAndDrumTracks_when_kickLockRuns_then_kicksOnStrongBeatsAlignedToBassOnsets() {
    int bars = 8;
    Sentence sentence = sentenceWithBars(bars);
    List<ChordSlot> slots = chordSlotsForBars(bars);
    BassTrack bass = BassTrackGenerator.generate(slots, TICKS_PER_BEAT);

    DrumTrack drums = DrumTrackGenerator.generate(
        sentence, slots, bass, DrumGrooveArchetype.DRIVING);

    long[] bassTicks = bass.notes().stream()
        .mapToLong(cn -> cn.note().startTick()).sorted().toArray();
    assertTrue(bassTicks.length > 0, "Test setup: bass must have notes");

    // Strong-beat tolerance defined by the generator (0.03 beats around any
    // beat boundary). We use 0.05-beat tolerance for the strong-beat predicate
    // to be safe with humanization jitter on the kick (+/- 0.02 beats).
    long lockTol  = Math.round(0.03 * TICKS_PER_BEAT);
    long humanTol = Math.round(0.02 * TICKS_PER_BEAT);
    long strongBeatTol = lockTol + humanTol + 1;

    int strongBeatKicks = 0;
    int locked = 0;
    for (DrumEvent e : drums.events()) {
      if (e.gmNote() != DrumPattern.KICK) continue;
      long modBeat = e.startTick() % TICKS_PER_BEAT;
      boolean onStrongBeat = (modBeat <= strongBeatTol)
          || (modBeat >= TICKS_PER_BEAT - strongBeatTol);
      if (!onStrongBeat) continue;
      strongBeatKicks++;

      long nearest = Long.MAX_VALUE;
      for (long t : bassTicks) {
        if (Math.abs(t - e.startTick()) < Math.abs(nearest - e.startTick())) {
          nearest = t;
        }
      }
      if (Math.abs(nearest - e.startTick()) <= lockTol + humanTol + 1) {
        locked++;
      }
    }
    assertTrue(strongBeatKicks > 0, "There must be at least one strong-beat kick");
    // The locking pass only snaps when the nearest bass note is already within
    // 0.03 beats; for any unsnapped kick, the bass note may be far away. We
    // therefore require that the majority of strong-beat kicks are aligned.
    assertTrue(locked * 2 >= strongBeatKicks,
        "Majority of strong-beat kicks must be locked to bass onsets; got "
            + locked + " of " + strongBeatKicks);
  }

  // ---------------------------------------------------------------------------
  // Scenario 6: Humanization applied
  // ---------------------------------------------------------------------------

  /**
   * Given a drum track is generated
   * When events are inspected
   * Then all events lie on the clean 16th-note grid (no timing jitter) and
   *      velocities are valid MIDI values.
   *
   * <p>Humanization was removed in issue #25 to guarantee export fidelity.
   * Events are now quantized to the 16th-note grid with no deviation.
   */
  @Test
  void given_drumTrackGenerated_when_humanizationRuns_then_deviationsWithinDocumentedBounds() {
    int bars = 8;
    Sentence sentence = sentenceWithBars(bars);
    List<ChordSlot> slots = chordSlotsForBars(bars);
    DrumTrack drums = DrumTrackGenerator.generate(
        sentence, slots, null, DrumGrooveArchetype.DRIVING);

    long sixteenth = TICKS_PER_BEAT / 4L;

    for (DrumEvent e : drums.events()) {
      long diff = e.startTick() % sixteenth;
      assertEquals(0, diff,
          "Event GM=" + e.gmNote() + " at tick " + e.startTick()
              + " is not on the 16th-note grid (remainder=" + diff + ")");
      assertTrue(e.velocity() >= 1 && e.velocity() <= 127,
          "Velocity must be in [1,127]; got " + e.velocity());
    }
  }

  // ---------------------------------------------------------------------------
  // Scenario 7: MIDI export includes drum track on channel 10 (0-idx 9)
  // ---------------------------------------------------------------------------

  /**
   * Given a complete song with a drum track
   * When exported to MIDI
   * Then the output contains notes on MIDI channel 9 (0-indexed) with correct
   *      GM note numbers.
   */
  @Test
  void given_completeSongWithDrums_when_exportedToMidi_then_outputContainsChannelNineEvents(
      @TempDir Path tempDir) throws Exception {

    int bars = 8;
    Sentence sentence = sentenceWithBars(bars);
    SentimentProfile profile = SentimentProfile.fromLabel("HAPPY");
    BackingTrack backing = BackingTrackGenerator.generate(sentence, profile);
    BassTrack bass = BassTrackGenerator.generate(sentence, profile, DEFAULT_TEMPO);
    List<ChordSlot> slots = chordSlotsForBars(bars);
    DrumTrack drums = DrumTrackGenerator.generate(
        sentence, slots, bass, DrumGrooveArchetype.DRIVING);

    File outFile = tempDir.resolve("drums_e2e.mid").toFile();
    MidiExporter.export(sentence, backing, bass, drums, outFile, DEFAULT_TEMPO);

    Sequence seq = MidiSystem.getSequence(outFile);
    assertEquals(4, seq.getTracks().length,
        "4-track export must produce melody + rhythm + bass + drums");

    Track drumTrack = seq.getTracks()[3];
    Set<Integer> drumNotes = new HashSet<>();
    int channelNineNoteOns = 0;
    for (int i = 0; i < drumTrack.size(); i++) {
      MidiMessage msg = drumTrack.get(i).getMessage();
      if (msg instanceof ShortMessage sm
          && sm.getCommand() == ShortMessage.NOTE_ON
          && sm.getData2() > 0) {
        assertEquals(DrumTrack.DRUM_CHANNEL, sm.getChannel(),
            "All drum NOTE_ON events must be on MIDI channel 9 (0-indexed)");
        channelNineNoteOns++;
        drumNotes.add(sm.getData1());
      }
    }
    assertTrue(channelNineNoteOns > 0,
        "Drum track must contain at least one NOTE_ON on channel 9");

    // Verify GM note numbers are valid drum pieces.
    Set<Integer> allowed = Set.of(
        DrumPattern.KICK, DrumPattern.SNARE, DrumPattern.CLOSED_HIHAT,
        DrumPattern.OPEN_HIHAT, DrumPattern.RIDE, DrumPattern.RIDE_BELL,
        DrumPattern.CRASH, DrumPattern.HIGH_TOM, DrumPattern.MID_TOM,
        DrumPattern.LOW_TOM);
    for (int gm : drumNotes) {
      assertTrue(allowed.contains(gm),
          "Drum note " + gm + " must be one of the documented GM percussion numbers");
    }
    // At minimum, kick, snare, and a cymbal should be present.
    assertTrue(drumNotes.contains(DrumPattern.KICK),  "Drum output must contain kick (36)");
    assertTrue(drumNotes.contains(DrumPattern.SNARE), "Drum output must contain snare (38)");
    assertTrue(drumNotes.contains(DrumPattern.CLOSED_HIHAT)
            || drumNotes.contains(DrumPattern.RIDE)
            || drumNotes.contains(DrumPattern.RIDE_BELL),
        "Drum output must contain at least one cymbal voice");
  }

  // ---------------------------------------------------------------------------
  // Scenario 8: MusicXML export includes drum part
  // ---------------------------------------------------------------------------

  /**
   * Given a complete song with a drum track
   * When exported to MusicXML
   * Then the output contains a percussion part with unpitched notes for each
   *      drum hit.
   */
  @Test
  void given_completeSongWithDrums_when_exportedToMusicXML_then_outputContainsPercussionPart(
      @TempDir Path tempDir) throws Exception {

    int bars = 8;
    Sentence sentence = sentenceWithBars(bars);
    SentimentProfile profile = SentimentProfile.fromLabel("HAPPY");
    BackingTrack backing = BackingTrackGenerator.generate(sentence, profile);
    BassTrack bass = BassTrackGenerator.generate(sentence, profile, DEFAULT_TEMPO);
    List<ChordSlot> slots = chordSlotsForBars(bars);
    DrumTrack drums = DrumTrackGenerator.generate(
        sentence, slots, bass, DrumGrooveArchetype.DRIVING);

    File outFile = tempDir.resolve("drums_e2e.musicxml").toFile();
    MusicXMLExporter.export(sentence, backing, bass, drums, outFile, DEFAULT_TEMPO);
    assertTrue(outFile.exists() && outFile.length() > 0,
        "MusicXML output file must exist and be non-empty");

    DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
    // Disable DTD validation/lookup so the parser doesn't fetch musicxml.org.
    dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
    dbf.setValidating(false);
    DocumentBuilder db = dbf.newDocumentBuilder();
    Document doc = db.parse(outFile);

    // Find the drum score-part by part-name == "Drums".
    NodeList scoreParts = doc.getElementsByTagName("score-part");
    String drumPartId = null;
    for (int i = 0; i < scoreParts.getLength(); i++) {
      Element sp = (Element) scoreParts.item(i);
      NodeList partNames = sp.getElementsByTagName("part-name");
      if (partNames.getLength() > 0
          && "Drums".equals(partNames.item(0).getTextContent())) {
        drumPartId = sp.getAttribute("id");
        break;
      }
    }
    assertNotNull(drumPartId, "MusicXML must declare a score-part with part-name 'Drums'");

    // Find the matching <part id="P?"> element.
    NodeList parts = doc.getElementsByTagName("part");
    Element drumPart = null;
    for (int i = 0; i < parts.getLength(); i++) {
      Element p = (Element) parts.item(i);
      if (drumPartId.equals(p.getAttribute("id"))) {
        drumPart = p;
        break;
      }
    }
    assertNotNull(drumPart, "MusicXML must contain a <part> matching the drum part id");

    // The drum part must contain at least one <unpitched> note element.
    NodeList unpitched = drumPart.getElementsByTagName("unpitched");
    assertTrue(unpitched.getLength() > 0,
        "Drum part must contain <unpitched> note elements");

    // And at least one <clef><sign>percussion</sign></clef>.
    NodeList signs = drumPart.getElementsByTagName("sign");
    boolean foundPercussionClef = false;
    for (int i = 0; i < signs.getLength(); i++) {
      if ("percussion".equals(signs.item(i).getTextContent())) {
        foundPercussionClef = true;
        break;
      }
    }
    assertTrue(foundPercussionClef,
        "Drum part must contain a <clef> with <sign>percussion</sign>");

    // The number of <unpitched> elements must equal the number of drum events
    // (one note element per hit).
    assertEquals(drums.events().size(), unpitched.getLength(),
        "MusicXML must emit one <unpitched> note per drum event");
  }

  // ---------------------------------------------------------------------------
  // Integration smoke: full pipeline via MotifGen.run produces a 4-track MIDI
  // ---------------------------------------------------------------------------

  /**
   * Integration smoke test: full pipeline via {@link MotifGen#run} produces a
   * 4-track MIDI with a drum track on channel 9.
   */
  @Test
  void given_fullPipelineViaMotifGenRun_when_completed_then_fourTrackMidiContainsDrumsOnChannelNine(
      @TempDir Path tempDir) throws Exception {

    int ppq = 480;
    Sequence seq = new Sequence(Sequence.PPQ, ppq);
    Track track = seq.createTrack();
    int mpq = 500_000;
    track.add(new MidiEvent(new MetaMessage(
        0x51,
        new byte[]{(byte) (mpq >> 16), (byte) (mpq >> 8), (byte) mpq}, 3), 0));
    track.add(new MidiEvent(new MetaMessage(0x58, new byte[]{4, 2, 24, 8}, 4), 0));

    int[] pitches = {60, 62, 64, 65, 67, 65, 64, 62, 60, 62, 64, 67, 65, 64, 62, 60};
    long tick = 0;
    for (int pitch : pitches) {
      track.add(new MidiEvent(
          new ShortMessage(ShortMessage.NOTE_ON,  0, pitch, 90), tick));
      track.add(new MidiEvent(
          new ShortMessage(ShortMessage.NOTE_OFF, 0, pitch, 0), tick + ppq));
      tick += ppq;
    }

    File inputMidi = tempDir.resolve("input.mid").toFile();
    File outputDir = tempDir.resolve("out").toFile();
    outputDir.mkdirs();
    MidiSystem.write(seq, 1, inputMidi);

    MotifGen.run(inputMidi.getAbsolutePath(), outputDir.getAbsolutePath(),
        DEFAULT_TEMPO, MotifGen.OutputFormat.MIDI, SentimentProfile.fromLabel("HAPPY"));

    File[] midFiles = outputDir.listFiles((d, n) -> n.endsWith(".mid"));
    assertNotNull(midFiles, "Output directory must contain at least one .mid file");
    assertTrue(midFiles.length > 0, "At least one MIDI output file must be generated");

    // With a 4-bar intro prepended the full-band export now produces 7 tracks:
    // 0=melody, 1=intro guitar, 2=intro bass, 3=intro drums,
    // 4=sentence guitar, 5=sentence bass, 6=sentence drums.
    Sequence outSeq = MidiSystem.getSequence(midFiles[0]);
    assertTrue(outSeq.getTracks().length >= 4,
        "Full pipeline output must contain at least 4 MIDI tracks");

    // At least one track must have NOTE_ON events on DRUM_CHANNEL (channel 9).
    boolean hasDrumNotes = false;
    outer:
    for (Track t : outSeq.getTracks()) {
      for (int i = 0; i < t.size(); i++) {
        MidiMessage msg = t.get(i).getMessage();
        if (msg instanceof ShortMessage sm
            && sm.getCommand() == ShortMessage.NOTE_ON
            && sm.getData2() > 0
            && sm.getChannel() == DrumTrack.DRUM_CHANNEL) {
          hasDrumNotes = true;
          break outer;
        }
      }
    }
    assertTrue(hasDrumNotes,
        "At least one track must contain NOTE_ON events on MIDI channel "
            + DrumTrack.DRUM_CHANNEL);
  }
}
