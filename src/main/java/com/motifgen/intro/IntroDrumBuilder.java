package com.motifgen.intro;

import com.motifgen.guitar.backing.DrumEvent;
import com.motifgen.guitar.backing.DrumGrooveArchetype;
import com.motifgen.guitar.backing.DrumPattern;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Builds the drum part for a variable-length intro (2, 3, or 4 bars).
 *
 * <p>Groove density increases each bar:
 * <ul>
 *   <li>Bar 1: kick pattern (archetype-specific) only.</li>
 *   <li>Bar 2: kick + hi-hats (pattern from template).</li>
 *   <li>Bar 3: kick + snare (archetype-specific beats) + hi-hats.</li>
 *   <li>Final bar (launch fill): first portion = full groove; remainder = snare fill.</li>
 * </ul>
 *
 * <p>Kick and snare beat placement is controlled by {@link IntroContext#drumArchetype()}:
 * <ul>
 *   <li>DRIVING / POWER: kick on beats 1 &amp; 3, snare on beats 2 &amp; 4.</li>
 *   <li>FUNK: kick on beat 1 and beat 2.5 (eighth after beat 2), snare on beats 2 &amp; 4.</li>
 *   <li>FOLK / BALLAD: kick on beat 1 only, snare on beat 3.</li>
 *   <li>default: kick on every beat.</li>
 * </ul>
 *
 * <p>Hi-hat pattern in groove bars is driven by {@link IntroTemplatePool.DrumSubTemplate#grooveType()}:
 * <ul>
 *   <li>{@code "EIGHTH"}: closed hi-hat on every eighth note.</li>
 *   <li>{@code "QUARTER"}: closed hi-hat on every quarter note.</li>
 *   <li>{@code "SIXTEENTH"}: closed hi-hat on every sixteenth note.</li>
 *   <li>{@code "OFF_BEAT"}: open hi-hat on the "and" of each beat.</li>
 * </ul>
 *
 * <p>The launch-fill bar split is driven by {@link IntroTemplatePool.DrumSubTemplate#twoBeatsGroove()}:
 * <ul>
 *   <li>{@code true}: 2 beats groove + 2 beats snare fill.</li>
 *   <li>{@code false}: 1 beat groove + 3 beats sixteenth snare fill.</li>
 * </ul>
 */
public final class IntroDrumBuilder implements IntroInstrumentBuilder<DrumEvent> {

  private static final int KICK_VELOCITY  = 100;
  private static final int SNARE_VELOCITY = 90;
  private static final int HIHAT_VELOCITY = 75;
  private static final int FILL_VELOCITY  = 110;

  @Override
  public List<DrumEvent> build(IntroContext ctx, int entryBar) {
    int introBars = ctx.barCount();
    if (entryBar > introBars) {
      return List.of();
    }
    List<DrumEvent> events = new ArrayList<>();
    int ppq = ctx.ticksPerBeat();
    long barTicks = (long) ctx.beatsPerBar() * ppq;
    long sixteenth = ppq / 4L;

    IntroTemplatePool.DrumSubTemplate template =
        IntroTemplatePool.drawDrum(ctx, new Random());

    for (int bar = 0; bar < introBars; bar++) {
      if (bar + 1 < entryBar) {
        continue;
      }
      long barStart = bar * barTicks;
      boolean isLastBar = (bar == introBars - 1);

      if (isLastBar) {
        addLaunchFillBar(events, barStart, ppq, barTicks, sixteenth, template);
      } else {
        addGrooveBar(events, bar, entryBar, barStart, ppq, sixteenth, ctx.drumArchetype(), template);
      }
    }
    return List.copyOf(events);
  }

  // ---------- bar builders ----------

  private void addGrooveBar(List<DrumEvent> events, int bar, int entryBar,
      long barStart, int ppq, long sixteenth,
      DrumGrooveArchetype archetype, IntroTemplatePool.DrumSubTemplate template) {
    int activeIdx = bar - (entryBar - 1);
    // tier 0 → kick only; tier 1 → kick + hihat; tier 2 → kick + snare + hihat
    int tier = Math.min(2, activeIdx);

    addKickPattern(events, barStart, ppq, sixteenth, archetype);

    if (tier >= 1) {
      addHiHatPattern(events, barStart, ppq, sixteenth, template.grooveType());
    }

    if (tier >= 2) {
      addSnarePattern(events, barStart, ppq, sixteenth, archetype);
    }
  }

  private void addKickPattern(List<DrumEvent> events, long barStart, int ppq,
      long sixteenth, DrumGrooveArchetype archetype) {
    switch (archetype) {
      case FOLK, BALLAD -> {
        events.add(hit(DrumPattern.KICK, barStart, sixteenth, KICK_VELOCITY));
      }
      case FUNK -> {
        events.add(hit(DrumPattern.KICK, barStart, sixteenth, KICK_VELOCITY));
        events.add(hit(DrumPattern.KICK, barStart + 2L * ppq + ppq / 2L, sixteenth, KICK_VELOCITY - 5));
      }
      default -> {
        // DRIVING, POWER, REGGAE: kick on beats 1 and 3
        events.add(hit(DrumPattern.KICK, barStart, sixteenth, KICK_VELOCITY));
        events.add(hit(DrumPattern.KICK, barStart + 2L * ppq, sixteenth, KICK_VELOCITY));
      }
    }
  }

  private void addSnarePattern(List<DrumEvent> events, long barStart, int ppq,
      long sixteenth, DrumGrooveArchetype archetype) {
    switch (archetype) {
      case FOLK, BALLAD -> {
        events.add(hit(DrumPattern.SNARE, barStart + 2L * ppq, sixteenth, SNARE_VELOCITY));
      }
      default -> {
        events.add(hit(DrumPattern.SNARE, barStart + ppq, sixteenth, SNARE_VELOCITY));
        events.add(hit(DrumPattern.SNARE, barStart + 3L * ppq, sixteenth, SNARE_VELOCITY));
      }
    }
  }

  /**
   * Adds hi-hat events for one groove bar, using the pattern specified by grooveType.
   */
  private void addHiHatPattern(List<DrumEvent> events, long barStart, int ppq,
      long sixteenth, String grooveType) {
    switch (grooveType) {
      case "SIXTEENTH" -> {
        for (int s = 0; s < 16; s++) {
          events.add(hit(DrumPattern.CLOSED_HIHAT, barStart + s * sixteenth, sixteenth,
              HIHAT_VELOCITY - (s % 2 == 0 ? 0 : 8))); // slight accent on even sixteenths
        }
      }
      case "QUARTER" -> {
        for (int b = 0; b < 4; b++) {
          events.add(hit(DrumPattern.CLOSED_HIHAT, barStart + (long) b * ppq, sixteenth,
              HIHAT_VELOCITY));
        }
      }
      case "OFF_BEAT" -> {
        // Open hi-hat on the "and" of each beat
        for (int b = 0; b < 4; b++) {
          long start = barStart + (long) b * ppq + ppq / 2L;
          events.add(hit(DrumPattern.OPEN_HIHAT, start, sixteenth, HIHAT_VELOCITY));
        }
      }
      default -> { // "EIGHTH"
        for (int e = 0; e < 8; e++) {
          events.add(hit(DrumPattern.CLOSED_HIHAT, barStart + e * (ppq / 2L), sixteenth,
              HIHAT_VELOCITY));
        }
      }
    }
  }

  /**
   * Launch-fill bar: groove portion + snare fill. The split is controlled by
   * {@link IntroTemplatePool.DrumSubTemplate#twoBeatsGroove()}.
   */
  private void addLaunchFillBar(List<DrumEvent> events, long barStart, int ppq,
      long barTicks, long sixteenth, IntroTemplatePool.DrumSubTemplate template) {
    events.add(hit(DrumPattern.CRASH, barStart, sixteenth, FILL_VELOCITY));

    if (template.twoBeatsGroove()) {
      // 2 beats groove (beats 1 & 2) + 2 beats fill (beats 3 & 4)
      long halfBar = barTicks / 2L;

      events.add(hit(DrumPattern.KICK, barStart, sixteenth, KICK_VELOCITY));
      events.add(hit(DrumPattern.KICK, barStart + (long) ppq, sixteenth, KICK_VELOCITY));
      events.add(hit(DrumPattern.SNARE, barStart + (long) ppq, sixteenth, SNARE_VELOCITY));
      for (int e = 0; e < 4; e++) {
        events.add(hit(DrumPattern.CLOSED_HIHAT, barStart + e * (ppq / 2L), sixteenth,
            HIHAT_VELOCITY));
      }
      // 4-note snare fill on sixteenth notes in second half
      for (int i = 0; i < 4; i++) {
        long start = barStart + halfBar + i * sixteenth;
        int vel = Math.min(127, FILL_VELOCITY + i * 3);
        events.add(hit(DrumPattern.SNARE, start, sixteenth, vel));
      }
    } else {
      // 1 beat groove (beat 1) + 3 beats of sixteenth snare fill
      events.add(hit(DrumPattern.KICK, barStart, sixteenth, KICK_VELOCITY));
      events.add(hit(DrumPattern.CLOSED_HIHAT, barStart, sixteenth, HIHAT_VELOCITY));

      for (int i = 0; i < 12; i++) {
        long start = barStart + (long) ppq + i * sixteenth;
        if (start >= barStart + barTicks) break;
        int vel = Math.min(127, SNARE_VELOCITY + i * 2);
        events.add(hit(DrumPattern.SNARE, start, sixteenth, vel));
      }
    }
  }

  // ---------- helpers ----------

  private static DrumEvent hit(int gmNote, long start, long dur, int velocity) {
    return new DrumEvent(gmNote, start, dur, Math.max(1, Math.min(127, velocity)));
  }
}
