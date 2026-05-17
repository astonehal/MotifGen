package com.motifgen.intro;

import com.motifgen.guitar.backing.ChanneledNote;
import com.motifgen.guitar.backing.DrumEvent;

import java.util.List;
import java.util.Map;

/**
 * Scores an {@link IntroTrack} candidate in [0.0, 100.0].
 *
 * <p>Weighted sum of three sub-scores:
 * <ol>
 *   <li><b>Density ramp correctness</b> (40 %): drum event count increases bar-by-bar.</li>
 *   <li><b>Entry timing match</b> (30 %): each instrument enters on or before its planned bar.</li>
 *   <li><b>Bass escalation</b> (30 %): bass note count increases bar-by-bar.</li>
 * </ol>
 *
 * <p>Bar count is variable (2, 3, or 4) and is taken from {@link IntroContext#barCount()}.
 * The ramp-score denominator is guarded with {@code Math.max(1, barCount - 1)} to avoid
 * division by zero when {@code barCount == 1}.
 */
public final class IntroScorer {

  private static final double WEIGHT_DENSITY = 0.40;
  private static final double WEIGHT_ENTRY   = 0.30;
  private static final double WEIGHT_BASS    = 0.30;

  /** Minimum acceptable score for a generated intro. */
  private static final double MINIMUM_SCORE = 30.0;

  /**
   * Scores the supplied track against the planned entry bars.
   *
   * @param track    intro track candidate
   * @param entryMap planned entry bars (from {@link IntroEntryPlanner#plan(IntroContext)})
   * @param ctx      intro context
   * @return score in [0.0, 100.0]
   */
  public double score(IntroTrack track, Map<String, Integer> entryMap, IntroContext ctx) {
    double density = scoreDensityRamp(track.drumEvents(), ctx);
    double entry   = scoreEntryTiming(track, entryMap, ctx);
    double bass    = scoreBassEscalation(track.bassEvents(), ctx);

    return (density * WEIGHT_DENSITY + entry * WEIGHT_ENTRY + bass * WEIGHT_BASS) * 100.0;
  }

  /**
   * Returns {@code true} if the given score meets the minimum threshold of 30.0.
   *
   * @param score score to test
   * @return {@code true} iff {@code score >= 30.0}
   */
  public boolean meetsMinimum(double score) {
    return score >= MINIMUM_SCORE;
  }

  // ---------- sub-scorers ----------

  /**
   * Returns 1.0 if drum event count is non-decreasing across all bars; linearly
   * interpolated otherwise.
   */
  private double scoreDensityRamp(List<DrumEvent> drumEvents, IntroContext ctx) {
    int[] counts = eventsPerBarDrum(drumEvents, ctx);
    return rampScore(counts);
  }

  /**
   * Returns 1.0 if each instrument's first note appears at or before its planned entry bar.
   */
  private double scoreEntryTiming(IntroTrack track, Map<String, Integer> entryMap,
      IntroContext ctx) {
    int ppq = ctx.ticksPerBeat();
    long barTicks = (long) ctx.beatsPerBar() * ppq;
    int barCount = ctx.barCount();

    int checks = 0;
    int passed = 0;

    for (Map.Entry<String, Integer> e : entryMap.entrySet()) {
      String inst = e.getKey();
      int plannedBar = e.getValue();
      checks++;

      long firstTick = firstTick(track, inst);
      if (firstTick < 0) {
        // No events — passes if planned bar exceeds the intro length
        if (plannedBar > barCount) passed++;
        continue;
      }
      int actualBar = (int) (firstTick / barTicks) + 1; // 1-indexed
      if (actualBar <= plannedBar) {
        passed++;
      }
    }
    return checks == 0 ? 1.0 : (double) passed / checks;
  }

  /** Returns 1.0 if bass note count is non-decreasing across bars. */
  private double scoreBassEscalation(List<ChanneledNote> bassEvents, IntroContext ctx) {
    int[] counts = eventsPerBarBass(bassEvents.stream()
        .map(cn -> cn.note().startTick())
        .toList(), ctx);
    return rampScore(counts);
  }

  // ---------- helpers ----------

  private int[] eventsPerBarDrum(List<DrumEvent> events, IntroContext ctx) {
    int ppq = ctx.ticksPerBeat();
    long barTicks = (long) ctx.beatsPerBar() * ppq;
    int barCount = ctx.barCount();
    int[] counts = new int[barCount];
    for (DrumEvent ev : events) {
      int bar = (int) (ev.startTick() / barTicks);
      if (bar >= 0 && bar < barCount) counts[bar]++;
    }
    return counts;
  }

  private int[] eventsPerBarBass(List<Long> startTicks, IntroContext ctx) {
    int ppq = ctx.ticksPerBeat();
    long barTicks = (long) ctx.beatsPerBar() * ppq;
    int barCount = ctx.barCount();
    int[] counts = new int[barCount];
    for (long tick : startTicks) {
      int bar = (int) (tick / barTicks);
      if (bar >= 0 && bar < barCount) counts[bar]++;
    }
    return counts;
  }

  /**
   * 1.0 if non-decreasing; reduced proportionally for each descent.
   * Safe against single-element arrays (returns 1.0).
   */
  private double rampScore(int[] counts) {
    if (counts.length < 2) return 1.0;
    int violations = 0;
    for (int i = 1; i < counts.length; i++) {
      if (counts[i] < counts[i - 1]) violations++;
    }
    return 1.0 - (double) violations / Math.max(1, counts.length - 1);
  }

  /** Returns the first startTick for an instrument, or -1 if empty. */
  private long firstTick(IntroTrack track, String instrument) {
    return switch (instrument) {
      case IntroEntryPlanner.GUITAR -> track.guitarEvents().stream()
          .mapToLong(cn -> cn.note().startTick()).min().orElse(-1L);
      case IntroEntryPlanner.BASS -> track.bassEvents().stream()
          .mapToLong(cn -> cn.note().startTick()).min().orElse(-1L);
      case IntroEntryPlanner.DRUMS -> track.drumEvents().stream()
          .mapToLong(DrumEvent::startTick).min().orElse(-1L);
      default -> -1L;
    };
  }
}
