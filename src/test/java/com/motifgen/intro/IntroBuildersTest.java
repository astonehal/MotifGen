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

  private IntroContext riffCtx;    // driving, high arousal (mid: 0.6) → riff mode, barCount=3
  private IntroContext chordCtx;   // ballad, low arousal → chord mode, barCount=4
  private IntroContext highCtx;    // high arousal (0.9) → barCount=2
  private IntroContext fourBarCtx; // mid arousal (0.6) → barCount=3 still, use low (0.2) for 4

  @BeforeEach
  void setUp() {
    // arousal 0.6 → barCount=3, riffScore=3 for driving
    riffCtx    = IntroContext.of(SentimentProfile.fromVA(0.7, 0.6), C_MAJOR, "driving", PPQ, BPB);
    // arousal 0.3 → barCount=4, riffScore=1 for ballad
    chordCtx   = IntroContext.of(SentimentProfile.fromVA(0.6, 0.3), C_MAJOR, "ballad",  PPQ, BPB);
    // arousal 0.9 → barCount=2
    highCtx    = IntroContext.of(SentimentProfile.fromVA(0.7, 0.9), C_MAJOR, "driving", PPQ, BPB);
    // arousal 0.2 → barCount=4 (used for tests needing 4 bars)
    fourBarCtx = IntroContext.of(SentimentProfile.fromVA(0.7, 0.2), C_MAJOR, "driving", PPQ, BPB);
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
    // arousal 0.6 → mid tier: root on beat 1, fifth on beat 3; barCount=3
    IntroContext ctx = IntroContext.of(SentimentProfile.fromVA(0.6, 0.6), C_MAJOR, "driving",
        PPQ, BPB);
    assertEquals(3, ctx.barCount());
    List<ChanneledNote> events = new IntroBassBuilder().build(ctx, 1);
    // By the last bar we should have at least 2 distinct pitches (root + fifth).
    long lastBarStart = (long) (ctx.barCount() - 1) * BAR_TICKS;
    long distinctPitches = events.stream()
        .filter(cn -> cn.note().startTick() >= lastBarStart)
        .mapToInt(cn -> cn.note().pitch())
        .distinct().count();
    assertTrue(distinctPitches >= 2, "Mid-arousal bass should include root and fifth by last bar");
  }

  @Test
  void bassHighArousal_eighthNoteGroove() {
    // highCtx has arousal=0.9 → barCount=2; last bar is bar 2 (index 1)
    assertEquals(2, highCtx.barCount());
    List<ChanneledNote> events = new IntroBassBuilder().build(highCtx, 1);
    long lastBarStart = (long) (highCtx.barCount() - 1) * BAR_TICKS;
    long lastBarCount = events.stream()
        .filter(cn -> cn.note().startTick() >= lastBarStart
            && cn.note().startTick() < lastBarStart + BAR_TICKS)
        .count();
    assertEquals(8, lastBarCount,
        "High-arousal bass last bar should have 8 eighth-note events");
  }

  @Test
  void bassEscalates_notCountIncreasesOrStays() {
    // highCtx barCount=2; check bars 1→2 are non-decreasing
    assertEquals(2, highCtx.barCount());
    List<ChanneledNote> events = new IntroBassBuilder().build(highCtx, 1);
    int barCount = highCtx.barCount();
    long[] counts = new long[barCount];
    for (int bar = 0; bar < barCount; bar++) {
      final long start = (long) bar * BAR_TICKS;
      final long end   = start + BAR_TICKS;
      counts[bar] = events.stream()
          .filter(cn -> cn.note().startTick() >= start && cn.note().startTick() < end)
          .count();
    }
    for (int bar = 1; bar < barCount; bar++) {
      assertTrue(counts[bar] >= counts[bar - 1],
          "Bass note count should be non-decreasing (bar " + (bar + 1) + ")");
    }
  }

  // -----------------------------------------------------------------------
  // Drums — density ramp + launch fill
  // -----------------------------------------------------------------------

  @Test
  void drums_eventCountIncreasesPerBar() {
    // fourBarCtx: arousal=0.2 → barCount=4; full density ramp over 4 bars
    assertEquals(4, fourBarCtx.barCount());
    List<DrumEvent> events = new IntroDrumBuilder().build(fourBarCtx, 1);
    int barCount = fourBarCtx.barCount();
    int[] counts = new int[barCount];
    for (DrumEvent ev : events) {
      int bar = (int) (ev.startTick() / BAR_TICKS);
      if (bar >= 0 && bar < barCount) counts[bar]++;
    }
    // Bars 1 to (barCount-1) must be non-decreasing; last bar is fill so excluded.
    for (int bar = 1; bar < barCount - 1; bar++) {
      assertTrue(counts[bar] >= counts[bar - 1],
          "Drum density should increase through bars 1-" + (barCount - 1)
              + " (bar " + (bar + 1) + ")");
    }
  }

  @Test
  void drums_bar4ContainsSnareHits() {
    // fourBarCtx → barCount=4; last bar is index 3
    assertEquals(4, fourBarCtx.barCount());
    List<DrumEvent> events = new IntroDrumBuilder().build(fourBarCtx, 1);
    long lastBarStart = (long) (fourBarCtx.barCount() - 1) * BAR_TICKS;
    boolean hasSnare = events.stream()
        .anyMatch(ev -> ev.startTick() >= lastBarStart && ev.gmNote() == DrumPattern.SNARE);
    assertTrue(hasSnare, "Last bar should contain snare fill hits");
  }

  @Test
  void drums_bar4SecondHalfContainsFourSnares() {
    // fourBarCtx → barCount=4; check the launch-fill bar's second half
    assertEquals(4, fourBarCtx.barCount());
    List<DrumEvent> events = new IntroDrumBuilder().build(fourBarCtx, 1);
    long lastBarStart = (long) (fourBarCtx.barCount() - 1) * BAR_TICKS;
    long fillStart = lastBarStart + BAR_TICKS / 2;

    // The twoBeatsGroove template produces exactly 4 snare hits in the second half;
    // the oneBeatsGroove template produces 12 — assert at least 4.
    long fillSnares = events.stream()
        .filter(ev -> ev.startTick() >= fillStart && ev.gmNote() == DrumPattern.SNARE)
        .count();
    assertTrue(fillSnares >= 4,
        "Last bar second half should have at least 4 snare fill hits, got " + fillSnares);
  }

  @Test
  void drums_bar4HasCrashAtStart() {
    // fourBarCtx → barCount=4; crash at beat 1 of last bar
    assertEquals(4, fourBarCtx.barCount());
    List<DrumEvent> events = new IntroDrumBuilder().build(fourBarCtx, 1);
    long lastBarStart = (long) (fourBarCtx.barCount() - 1) * BAR_TICKS;
    boolean hasCrash = events.stream()
        .anyMatch(ev -> ev.startTick() == lastBarStart && ev.gmNote() == DrumPattern.CRASH);
    assertTrue(hasCrash, "Last bar beat 1 should have a crash cymbal");
  }

  @Test
  void drums_noEventsBeforeEntryBar() {
    List<DrumEvent> events = new IntroDrumBuilder().build(fourBarCtx, 2);
    events.forEach(ev ->
        assertTrue(ev.startTick() >= BAR_TICKS, "No drum events before entryBar 2"));
  }
}
