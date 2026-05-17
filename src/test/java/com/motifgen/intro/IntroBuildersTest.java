package com.motifgen.intro;

import com.motifgen.guitar.backing.ChanneledNote;
import com.motifgen.guitar.backing.DrumEvent;
import com.motifgen.guitar.backing.DrumPattern;
import com.motifgen.sentiment.SentimentProfile;
import com.motifgen.theory.KeySignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link IntroGuitarBuilder}, {@link IntroBassBuilder}, and {@link IntroDrumBuilder}.
 *
 * <p>Criteria covered:
 * <ul>
 *   <li>Guitar riff mode: bar 2 is rhythmic variation of bar 1.</li>
 *   <li>Guitar chord mode: density increases bar-by-bar.</li>
 *   <li>Drum build: event count is non-decreasing; bar 4 contains snare fill.</li>
 *   <li>Bass builds from root-only to richer groove.</li>
 *   <li>No events before entryBar.</li>
 * </ul>
 */
class IntroBuildersTest {

  private static final int PPQ = 480;
  private static final int BPB = 4;
  private static final KeySignature C_MAJOR = KeySignature.major(0);
  private static final long BAR_TICKS = (long) BPB * PPQ;

  private IntroContext riffCtx;   // driving, high arousal → riff mode
  private IntroContext chordCtx;  // ballad, low riff score → chord mode
  private IntroContext highCtx;   // high arousal

  @BeforeEach
  void setUp() {
    riffCtx  = IntroContext.of(SentimentProfile.fromVA(0.7, 0.8), C_MAJOR, "driving", PPQ, BPB);
    chordCtx = IntroContext.of(SentimentProfile.fromVA(0.6, 0.3), C_MAJOR, "ballad",  PPQ, BPB);
    highCtx  = IntroContext.of(SentimentProfile.fromVA(0.7, 0.9), C_MAJOR, "driving", PPQ, BPB);
  }

  // -----------------------------------------------------------------------
  // Guitar — riff mode (riffScore >= 3)
  // -----------------------------------------------------------------------

  @Test
  void guitarRiffMode_producesEventsInBars1And2() {
    List<ChanneledNote> events = new IntroGuitarBuilder().build(riffCtx, 1);
    assertFalse(events.isEmpty(), "Riff mode should produce events");

    long bar2Start = BAR_TICKS;
    long bar2End   = bar2Start + BAR_TICKS;
    boolean hasBar1 = events.stream().anyMatch(cn -> cn.note().startTick() < bar2Start);
    boolean hasBar2 = events.stream().anyMatch(
        cn -> cn.note().startTick() >= bar2Start && cn.note().startTick() < bar2End);

    assertTrue(hasBar1, "Riff mode should have events in bar 1");
    assertTrue(hasBar2, "Riff mode should have a variation in bar 2");
  }

  @Test
  void guitarRiffMode_bar2EventsShifted() {
    List<ChanneledNote> events = new IntroGuitarBuilder().build(riffCtx, 1);
    // Bar 2 events should start at BAR_TICKS + eighth-note offset (not at BAR_TICKS + 0).
    long bar2FirstTick = events.stream()
        .filter(cn -> cn.note().startTick() >= BAR_TICKS && cn.note().startTick() < 2 * BAR_TICKS)
        .mapToLong(cn -> cn.note().startTick()).min().orElseThrow();

    // bar2 first tick = BAR_TICKS + eighth-note offset
    long expectedBar2First = BAR_TICKS + PPQ / 2L;
    assertEquals(expectedBar2First, bar2FirstTick,
        "Bar 2 riff variation should be offset by an eighth note");
  }

  // -----------------------------------------------------------------------
  // Guitar — chord mode (riffScore < 2)
  // -----------------------------------------------------------------------

  @Test
  void guitarChordMode_densityIncreasesPerBar() {
    List<ChanneledNote> events = new IntroGuitarBuilder().build(chordCtx, 1);
    assertFalse(events.isEmpty(), "Chord mode should produce events");

    // Count chord onsets per bar (unique start ticks, since each strum produces 2 notes).
    long[] strumsPerBar = new long[4];
    for (int bar = 0; bar < 4; bar++) {
      final long start = bar * BAR_TICKS;
      final long end   = start + BAR_TICKS;
      // Each strum lands two notes at same tick; count distinct ticks.
      strumsPerBar[bar] = events.stream()
          .map(cn -> cn.note().startTick())
          .filter(t -> t >= start && t < end)
          .distinct()
          .count();
    }
    // Density must be non-decreasing.
    for (int bar = 1; bar < 4; bar++) {
      assertTrue(strumsPerBar[bar] >= strumsPerBar[bar - 1],
          "Chord density should be non-decreasing (bar " + (bar + 1) + ")");
    }
  }

  // -----------------------------------------------------------------------
  // Guitar — respects entryBar
  // -----------------------------------------------------------------------

  @Test
  void guitarRespectsEntryBar() {
    List<ChanneledNote> events = new IntroGuitarBuilder().build(chordCtx, 3);
    long bar3Start = 2 * BAR_TICKS;
    events.forEach(cn ->
        assertTrue(cn.note().startTick() >= bar3Start,
            "No guitar events before entryBar 3"));
  }

  // -----------------------------------------------------------------------
  // Bass — density tiers
  // -----------------------------------------------------------------------

  @Test
  void bassLowArousal_wholeNoteRootOnly() {
    // arousal 0.3 < 0.4 → whole-note root
    IntroContext ctx = IntroContext.of(SentimentProfile.fromVA(0.6, 0.3), C_MAJOR, "ballad",
        PPQ, BPB);
    List<ChanneledNote> events = new IntroBassBuilder().build(ctx, 1);
    // Bar 1 should have exactly 1 note (whole-note root).
    long bar1Count = events.stream()
        .filter(cn -> cn.note().startTick() < BAR_TICKS).count();
    assertEquals(1, bar1Count, "Low arousal bar 1 should have a single whole-note root");
  }

  @Test
  void bassMidArousal_rootAndFifth() {
    // arousal 0.6 → mid tier: root on beat 1, fifth on beat 3
    IntroContext ctx = IntroContext.of(SentimentProfile.fromVA(0.6, 0.6), C_MAJOR, "driving",
        PPQ, BPB);
    List<ChanneledNote> events = new IntroBassBuilder().build(ctx, 1);
    // By bar 4 we should have at least 2 distinct pitches (root + fifth).
    long bar4Start = 3 * BAR_TICKS;
    long distinctPitches = events.stream()
        .filter(cn -> cn.note().startTick() >= bar4Start)
        .mapToInt(cn -> cn.note().pitch())
        .distinct().count();
    assertTrue(distinctPitches >= 2, "Mid-arousal bass should include root and fifth by bar 4");
  }

  @Test
  void bassHighArousal_eighthNoteGroove() {
    List<ChanneledNote> events = new IntroBassBuilder().build(highCtx, 1);
    // Last bar (bar 4) should have 8 notes (eighth-note groove).
    long bar4Start = 3 * BAR_TICKS;
    long bar4Count = events.stream()
        .filter(cn -> cn.note().startTick() >= bar4Start).count();
    assertEquals(8, bar4Count, "High-arousal bass bar 4 should have 8 eighth-note events");
  }

  @Test
  void bassEscalates_notCountIncreasesOrStays() {
    List<ChanneledNote> events = new IntroBassBuilder().build(highCtx, 1);
    long[] counts = new long[4];
    for (int bar = 0; bar < 4; bar++) {
      final long start = bar * BAR_TICKS;
      final long end   = start + BAR_TICKS;
      counts[bar] = events.stream()
          .filter(cn -> cn.note().startTick() >= start && cn.note().startTick() < end)
          .count();
    }
    for (int bar = 1; bar < 4; bar++) {
      assertTrue(counts[bar] >= counts[bar - 1],
          "Bass note count should be non-decreasing (bar " + (bar + 1) + ")");
    }
  }

  // -----------------------------------------------------------------------
  // Drums — density ramp + launch fill
  // -----------------------------------------------------------------------

  @Test
  void drums_eventCountIncreasesPerBar() {
    List<DrumEvent> events = new IntroDrumBuilder().build(riffCtx, 1);
    int[] counts = new int[4];
    for (DrumEvent ev : events) {
      int bar = (int) (ev.startTick() / BAR_TICKS);
      if (bar >= 0 && bar < 4) counts[bar]++;
    }
    // Bars 1–3 must be non-decreasing; bar 4 may differ due to fill structure.
    for (int bar = 1; bar < 3; bar++) {
      assertTrue(counts[bar] >= counts[bar - 1],
          "Drum density should increase through bars 1-3 (bar " + (bar + 1) + ")");
    }
  }

  @Test
  void drums_bar4ContainsSnareHits() {
    List<DrumEvent> events = new IntroDrumBuilder().build(riffCtx, 1);
    long bar4Start = 3 * BAR_TICKS;
    boolean hasSnare = events.stream()
        .anyMatch(ev -> ev.startTick() >= bar4Start && ev.gmNote() == DrumPattern.SNARE);
    assertTrue(hasSnare, "Bar 4 should contain snare fill hits");
  }

  @Test
  void drums_bar4SecondHalfContainsFourSnares() {
    List<DrumEvent> events = new IntroDrumBuilder().build(riffCtx, 1);
    long bar4Start = 3 * BAR_TICKS;
    long halfBar   = BAR_TICKS / 2;
    long fillStart = bar4Start + halfBar;

    long fillSnares = events.stream()
        .filter(ev -> ev.startTick() >= fillStart && ev.gmNote() == DrumPattern.SNARE)
        .count();
    assertEquals(4, fillSnares, "Bar 4 second half should have exactly 4 snare fill hits");
  }

  @Test
  void drums_bar4HasCrashAtStart() {
    List<DrumEvent> events = new IntroDrumBuilder().build(riffCtx, 1);
    long bar4Start = 3 * BAR_TICKS;
    boolean hasCrash = events.stream()
        .anyMatch(ev -> ev.startTick() == bar4Start && ev.gmNote() == DrumPattern.CRASH);
    assertTrue(hasCrash, "Bar 4 beat 1 should have a crash cymbal");
  }

  @Test
  void drums_noEventsBeforeEntryBar() {
    List<DrumEvent> events = new IntroDrumBuilder().build(riffCtx, 2);
    events.forEach(ev ->
        assertTrue(ev.startTick() >= BAR_TICKS, "No drum events before entryBar 2"));
  }
}
