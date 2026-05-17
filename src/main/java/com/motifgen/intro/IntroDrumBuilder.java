package com.motifgen.intro;

import com.motifgen.guitar.backing.DrumEvent;
import com.motifgen.guitar.backing.DrumPattern;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the drum part for the 4-bar intro.
 *
 * <p>The groove density increases each bar:
 * <ul>
 *   <li>Bar 1: kick only (every beat).</li>
 *   <li>Bar 2: kick + closed hi-hat on eighth notes.</li>
 *   <li>Bar 3: kick + snare (beats 2 &amp; 4) + closed hi-hat.</li>
 *   <li>Bar 4 (launch fill): first half = full groove; second half = snare fill (four 16ths).</li>
 * </ul>
 */
public final class IntroDrumBuilder implements IntroInstrumentBuilder<DrumEvent> {

  private static final int INTRO_BARS = 4;
  private static final int KICK_VELOCITY = 100;
  private static final int SNARE_VELOCITY = 90;
  private static final int HIHAT_VELOCITY = 75;
  private static final int FILL_VELOCITY = 110;

  @Override
  public List<DrumEvent> build(IntroContext ctx, int entryBar) {
    if (entryBar > INTRO_BARS) {
      return List.of();
    }
    List<DrumEvent> events = new ArrayList<>();
    int ppq = ctx.ticksPerBeat();
    long barTicks = (long) ctx.beatsPerBar() * ppq;
    long sixteenth = ppq / 4L;

    for (int bar = 0; bar < INTRO_BARS; bar++) {
      if (bar + 1 < entryBar) {
        continue;
      }
      long barStart = bar * barTicks;
      boolean isLastBar = (bar == INTRO_BARS - 1);

      if (isLastBar) {
        addLaunchFillBar(events, barStart, ppq, barTicks, sixteenth);
      } else {
        addGrooveBar(events, bar, entryBar, barStart, ppq, sixteenth);
      }
    }
    return List.copyOf(events);
  }

  // ---------- bar builders ----------

  /** Adds a groove bar with density corresponding to its position in the intro. */
  private void addGrooveBar(List<DrumEvent> events, int bar, int entryBar,
      long barStart, int ppq, long sixteenth) {
    // Active bar index (0-based from first active bar).
    int activeIdx = bar - (entryBar - 1);
    // tier 0 → kick only; tier 1 → kick + hihat; tier 2 → kick + snare + hihat
    int tier = Math.min(2, activeIdx);

    // Kick on every beat.
    for (int beat = 0; beat < 4; beat++) {
      long start = barStart + (long) beat * ppq;
      events.add(hit(DrumPattern.KICK, start, sixteenth, KICK_VELOCITY));
    }

    if (tier >= 1) {
      // Closed hi-hat on every eighth note.
      for (int e = 0; e < 8; e++) {
        long start = barStart + e * (ppq / 2L);
        events.add(hit(DrumPattern.CLOSED_HIHAT, start, sixteenth, HIHAT_VELOCITY));
      }
    }

    if (tier >= 2) {
      // Snare on beats 2 and 4.
      events.add(hit(DrumPattern.SNARE, barStart + ppq, sixteenth, SNARE_VELOCITY));
      events.add(hit(DrumPattern.SNARE, barStart + 3L * ppq, sixteenth, SNARE_VELOCITY));
    }
  }

  /**
   * Bar 4 = first half full groove (kick + snare + hihat), second half = snare fill (four 16ths).
   */
  private void addLaunchFillBar(List<DrumEvent> events, long barStart, int ppq,
      long barTicks, long sixteenth) {
    long halfBar = barTicks / 2L;

    // First half: full groove (beats 1 and 2).
    for (int beat = 0; beat < 2; beat++) {
      long start = barStart + (long) beat * ppq;
      events.add(hit(DrumPattern.KICK, start, sixteenth, KICK_VELOCITY));
    }
    // Snare on beat 2 of first half.
    events.add(hit(DrumPattern.SNARE, barStart + ppq, sixteenth, SNARE_VELOCITY));
    // Hi-hat on eighth notes through first half.
    for (int e = 0; e < 4; e++) {
      events.add(hit(DrumPattern.CLOSED_HIHAT, barStart + e * (ppq / 2L), sixteenth,
          HIHAT_VELOCITY));
    }

    // Second half: 4-note snare fill on sixteenth notes (beats 3.00, 3.25, 3.50, 3.75).
    for (int i = 0; i < 4; i++) {
      long start = barStart + halfBar + i * sixteenth;
      int vel = Math.min(127, FILL_VELOCITY + i * 3);
      events.add(hit(DrumPattern.SNARE, start, sixteenth, vel));
    }
    // Crash on bar 4 beat 1 to mark the launch.
    events.add(hit(DrumPattern.CRASH, barStart, sixteenth, FILL_VELOCITY));
  }

  // ---------- helpers ----------

  private static DrumEvent hit(int gmNote, long start, long dur, int velocity) {
    return new DrumEvent(gmNote, start, dur, Math.max(1, Math.min(127, velocity)));
  }
}
