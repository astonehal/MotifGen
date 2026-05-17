package com.motifgen.guitar.backing;

import com.motifgen.model.Sentence;
import com.motifgen.sentiment.SentimentProfile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Facade that generates a {@link DrumTrack} from a sentence + chord progression.
 *
 * <h3>Four-pass pipeline</h3>
 * <ol>
 *   <li><b>Groove</b> — emit kick/snare/cymbal events per archetype 16-slot grid,
 *       applying A/B/C section voicings drawn from
 *       {@code sentence.getMetadata("sectionLabels")}.</li>
 *   <li><b>Fills</b> — replace bar 4 (half-bar) and bar 8 (full-bar) of every
 *       8-bar phrase with archetype-appropriate fills, ending each full-bar
 *       fill with crash+kick on the downbeat of the next phrase.</li>
 *   <li><b>Kick-lock</b> — snap kicks within ±0.03 beats of the nearest bass
 *       note onset on each strong beat to lock the rhythm section.</li>
 *   <li><b>Humanize</b> — apply seeded micro-timing and velocity jitter
 *       (tighter for kick/snare, looser for others).</li>
 * </ol>
 */
public final class DrumTrackGenerator {

  private static final int BEATS_PER_BAR = 4;
  private static final int SIXTEENTHS_PER_BAR = DrumPattern.SLOTS_PER_BAR;
  private static final int PHRASE_BARS = 8;

  private static final int BASE_KICK_VEL   = 100;
  private static final int BASE_SNARE_VEL  = 95;
  private static final int BASE_CYMBAL_VEL = 80;
  private static final int GHOST_SNARE_VEL = 35;
  private static final int CRASH_VEL       = 110;

  private static final double KICK_SNARE_JITTER_BEATS = 0.02;
  private static final double OTHER_JITTER_BEATS      = 0.05;
  private static final int    VELOCITY_JITTER         = 8;
  private static final double KICK_LOCK_BEATS         = 0.03;

  private DrumTrackGenerator() {}

  /**
   * Generates a drum track for the given sentence and chord progression.
   *
   * @param sentence  melody sentence (supplies bar count, section labels metadata)
   * @param slots     chord-slot progression (one slot per bar)
   * @param bass      bass track for kick-locking, may be {@code null}
   * @param archetype drum groove archetype
   * @return the resulting {@link DrumTrack}
   */
  public static DrumTrack generate(
      Sentence sentence,
      List<ChordSlot> slots,
      BassTrack bass,
      DrumGrooveArchetype archetype) {

    int ppq = sentence.getPhrases().isEmpty()
        ? 480
        : sentence.getPhrases().getFirst().getTicksPerBeat();
    int totalBars = Math.max(1, sentence.totalBars());

    String sectionLabels = sentence.getMetadataValue("sectionLabels");

    // Pass 1: groove (every bar).
    List<DrumEvent> events = new ArrayList<>();
    for (int bar = 0; bar < totalBars; bar++) {
      DrumSection section = sectionForBar(bar, sectionLabels, totalBars);
      addBarGroove(events, archetype, section, bar, ppq);
    }

    // Pass 2: replace bar 4 and bar 8 of each 8-bar phrase with fills.
    events = applyFills(events, archetype, totalBars, ppq);

    // Pass 3: kick-lock to bass.
    if (bass != null && !bass.notes().isEmpty()) {
      events = lockKicksToBass(events, bass, ppq);
    }

    // Pass 4: humanize.
    events = humanize(events, archetype, ppq, sentence.hashCode());

    events.sort((a, b) -> Long.compare(a.startTick(), b.startTick()));
    return new DrumTrack(Collections.unmodifiableList(events));
  }

  /**
   * Convenience overload that picks an archetype from a sentiment profile.
   *
   * @param sentence melody sentence
   * @param slots    chord-slot progression
   * @param bass     bass track for kick-locking, may be {@code null}
   * @param profile  sentiment profile (drives archetype selection)
   * @return the resulting {@link DrumTrack}
   */
  public static DrumTrack generate(
      Sentence sentence,
      List<ChordSlot> slots,
      BassTrack bass,
      SentimentProfile profile) {
    BassGrooveArchetype bassArch = (profile == null)
        ? BassGrooveArchetype.DRIVING
        : BassGrooveArchetype.fromStrumArchetype(pickStrumArchetype(profile));
    return generate(sentence, slots, bass, DrumGrooveArchetype.fromBassArchetype(bassArch));
  }

  // -------------------------------------------------------------------------
  // Pass 1: groove
  // -------------------------------------------------------------------------

  private static void addBarGroove(
      List<DrumEvent> out,
      DrumGrooveArchetype archetype,
      DrumSection section,
      int bar,
      int ppq) {

    boolean[] kick = DrumPattern.kickGrid(archetype);
    boolean[] snare = DrumPattern.snareGrid(archetype);
    boolean[] cymbal = DrumPattern.cymbalGrid(archetype);
    long ticksPerBar = (long) BEATS_PER_BAR * ppq;
    long sixteenth = ppq / 4L;
    long barStart = bar * ticksPerBar;

    for (int slot = 0; slot < SIXTEENTHS_PER_BAR; slot++) {
      long tick = barStart + slot * sixteenth;

      if (kick[slot]) {
        out.add(new DrumEvent(DrumPattern.KICK, tick, sixteenth,
            scaleVelocity(BASE_KICK_VEL, section)));
      }
      if (snare[slot]) {
        out.add(new DrumEvent(DrumPattern.SNARE, tick, sixteenth,
            scaleVelocity(BASE_SNARE_VEL, section)));
      }
      if (cymbal[slot]) {
        out.add(new DrumEvent(section.cymbalNote(), tick, sixteenth,
            scaleVelocity(BASE_CYMBAL_VEL, section)));
      }
    }

    // Section ornaments.
    if (section.ghostSnares()) {
      // Ghost snares on the "e" and "a" sixteenths of each beat (slots 1, 3, 5, ... 15).
      for (int slot = 1; slot < SIXTEENTHS_PER_BAR; slot += 4) {
        long tick = barStart + slot * sixteenth;
        if (!snare[slot]) {
          out.add(new DrumEvent(DrumPattern.SNARE, tick, sixteenth, GHOST_SNARE_VEL));
        }
      }
    }
    if (section.extraKick()) {
      // Extra kick on the "and of 2" (slot 6).
      long tick = barStart + 6 * sixteenth;
      out.add(new DrumEvent(DrumPattern.KICK, tick, sixteenth,
          scaleVelocity(BASE_KICK_VEL, section)));
    }
    if (section == DrumSection.B) {
      // Open hi-hat accent on the "and of 2" (slot 6).
      long tick = barStart + 6 * sixteenth;
      out.add(new DrumEvent(DrumPattern.OPEN_HIHAT, tick, sixteenth,
          scaleVelocity(BASE_CYMBAL_VEL, section)));
    }
  }

  private static int scaleVelocity(int base, DrumSection section) {
    int scaled = (int) Math.round(base * section.velocityMod());
    return Math.max(1, Math.min(127, scaled));
  }

  // -------------------------------------------------------------------------
  // Pass 2: fills
  // -------------------------------------------------------------------------

  private static List<DrumEvent> applyFills(
      List<DrumEvent> events, DrumGrooveArchetype archetype, int totalBars, int ppq) {

    long ticksPerBar = (long) BEATS_PER_BAR * ppq;
    long sixteenth = ppq / 4L;
    List<DrumEvent> out = new ArrayList<>(events.size());

    for (DrumEvent e : events) {
      int bar = (int) (e.startTick() / ticksPerBar);
      int phraseBar = bar % PHRASE_BARS;
      long barStart = bar * ticksPerBar;
      long midBar = barStart + 2L * ppq;
      long barEnd = barStart + ticksPerBar;

      // Bar 4 (index 3 in phrase): drop normal events in beats 3-4.
      if (phraseBar == 3 && e.startTick() >= midBar && e.startTick() < barEnd) {
        continue;
      }
      // Bar 8 (index 7 in phrase): drop entire bar.
      if (phraseBar == 7 && e.startTick() >= barStart && e.startTick() < barEnd) {
        continue;
      }
      out.add(e);
    }

    // Add half-bar fills (bar index % 8 == 3) and full-bar fills (index % 8 == 7).
    for (int bar = 0; bar < totalBars; bar++) {
      int phraseBar = bar % PHRASE_BARS;
      long barStart = bar * ticksPerBar;
      long midBar = barStart + 2L * ppq;
      long barEnd = barStart + ticksPerBar;

      if (phraseBar == 3) {
        addHalfBarFill(out, archetype, midBar, sixteenth);
      } else if (phraseBar == 7) {
        addFullBarFill(out, archetype, barStart, sixteenth);
        // Crash + kick on the downbeat of the next phrase.
        out.add(new DrumEvent(DrumPattern.CRASH, barEnd, ppq, CRASH_VEL));
        out.add(new DrumEvent(DrumPattern.KICK, barEnd, sixteenth, BASE_KICK_VEL));
      }
    }
    return out;
  }

  private static void addHalfBarFill(
      List<DrumEvent> out, DrumGrooveArchetype archetype, long startTick, long sixteenth) {
    // 8 sixteenth-note tom hits — high to low — for the "drum solo" sweep.
    int[] toms = pickFillToms(archetype);
    for (int i = 0; i < 8; i++) {
      int tom = toms[i % toms.length];
      long tick = startTick + i * sixteenth;
      out.add(new DrumEvent(tom, tick, sixteenth, BASE_SNARE_VEL));
    }
  }

  private static void addFullBarFill(
      List<DrumEvent> out, DrumGrooveArchetype archetype, long barStart, long sixteenth) {
    int[] toms = pickFillToms(archetype);
    for (int i = 0; i < SIXTEENTHS_PER_BAR; i++) {
      int hit = (i % 4 == 0) ? DrumPattern.SNARE : toms[i % toms.length];
      long tick = barStart + i * sixteenth;
      out.add(new DrumEvent(hit, tick, sixteenth, BASE_SNARE_VEL));
    }
  }

  private static int[] pickFillToms(DrumGrooveArchetype archetype) {
    return switch (archetype) {
      case FUNK, REGGAE -> new int[]{DrumPattern.HIGH_TOM, DrumPattern.MID_TOM};
      case BALLAD       -> new int[]{DrumPattern.MID_TOM, DrumPattern.LOW_TOM};
      default           -> new int[]{
          DrumPattern.HIGH_TOM, DrumPattern.HIGH_TOM,
          DrumPattern.MID_TOM,  DrumPattern.MID_TOM,
          DrumPattern.LOW_TOM,  DrumPattern.LOW_TOM};
    };
  }

  // -------------------------------------------------------------------------
  // Pass 3: kick-lock to bass
  // -------------------------------------------------------------------------

  private static List<DrumEvent> lockKicksToBass(
      List<DrumEvent> events, BassTrack bass, int ppq) {
    long toleranceTicks = Math.round(KICK_LOCK_BEATS * ppq);
    long[] bassTicks = bass.notes().stream()
        .mapToLong(cn -> cn.note().startTick())
        .sorted()
        .toArray();
    if (bassTicks.length == 0) return events;

    long ticksPerBeat = ppq;
    List<DrumEvent> out = new ArrayList<>(events.size());
    for (DrumEvent e : events) {
      if (e.gmNote() != DrumPattern.KICK) {
        out.add(e);
        continue;
      }
      // Strong-beat detection — kick falls within ±halfBeat of a beat boundary.
      long modBeat = e.startTick() % ticksPerBeat;
      boolean onStrongBeat = (modBeat <= toleranceTicks)
          || (modBeat >= ticksPerBeat - toleranceTicks);
      if (!onStrongBeat) {
        out.add(e);
        continue;
      }
      long nearest = nearestBassTick(bassTicks, e.startTick());
      if (Math.abs(nearest - e.startTick()) <= toleranceTicks) {
        out.add(new DrumEvent(e.gmNote(), nearest, e.durationTicks(), e.velocity()));
      } else {
        out.add(e);
      }
    }
    return out;
  }

  private static long nearestBassTick(long[] sortedTicks, long target) {
    int lo = 0;
    int hi = sortedTicks.length - 1;
    while (lo < hi) {
      int mid = (lo + hi) >>> 1;
      if (sortedTicks[mid] < target) {
        lo = mid + 1;
      } else {
        hi = mid;
      }
    }
    long candidate = sortedTicks[lo];
    if (lo > 0) {
      long prev = sortedTicks[lo - 1];
      if (Math.abs(prev - target) < Math.abs(candidate - target)) candidate = prev;
    }
    return candidate;
  }

  // -------------------------------------------------------------------------
  // Pass 4: humanize
  // -------------------------------------------------------------------------

  private static List<DrumEvent> humanize(
      List<DrumEvent> events, DrumGrooveArchetype archetype, int ppq, int seed) {
    Random rng = new Random(seed ^ archetype.ordinal());
    long kickSnareTol = Math.round(KICK_SNARE_JITTER_BEATS * ppq);
    long otherTol     = Math.round(OTHER_JITTER_BEATS * ppq);

    List<DrumEvent> out = new ArrayList<>(events.size());
    for (DrumEvent e : events) {
      boolean isKickSnare = e.gmNote() == DrumPattern.KICK
          || e.gmNote() == DrumPattern.SNARE;
      long tol = isKickSnare ? kickSnareTol : otherTol;
      long jitter = (tol == 0) ? 0L
          : (long) (rng.nextInt((int) (2 * tol + 1)) - tol);
      long newTick = Math.max(0, e.startTick() + jitter);

      int velJitter = rng.nextInt(2 * VELOCITY_JITTER + 1) - VELOCITY_JITTER;
      int newVel = Math.max(1, Math.min(127, e.velocity() + velJitter));

      out.add(new DrumEvent(e.gmNote(), newTick, e.durationTicks(), newVel));
    }
    return out;
  }

  // -------------------------------------------------------------------------
  // Helpers
  // -------------------------------------------------------------------------

  /**
   * Maps a bar index to a {@link DrumSection} using the sentence's
   * {@code sectionLabels} metadata. Each character in the label string maps
   * to a phrase; phrase length is {@code totalBars / labels.length}.
   */
  private static DrumSection sectionForBar(int bar, String labels, int totalBars) {
    if (labels == null || labels.isEmpty()) return DrumSection.A;
    int phraseLen = Math.max(1, totalBars / labels.length());
    int idx = Math.min(labels.length() - 1, bar / phraseLen);
    return DrumSection.fromLabel(labels.charAt(idx));
  }

  /**
   * Mirrors {@code BassTrackGenerator.pickStrumArchetype} for archetype
   * selection from a sentiment profile.
   */
  private static StrumPattern.Archetype pickStrumArchetype(SentimentProfile profile) {
    double arousal = profile.arousal();
    double valence = profile.valence();
    if (arousal > 0.75) {
      return valence > 0.5 ? StrumPattern.Archetype.DRIVING : StrumPattern.Archetype.FUNK;
    }
    if (arousal > 0.5) {
      return valence > 0.5 ? StrumPattern.Archetype.FOLK : StrumPattern.Archetype.REGGAE;
    }
    return StrumPattern.Archetype.BALLAD;
  }
}
