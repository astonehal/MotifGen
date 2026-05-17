package com.motifgen.guitar.backing;

import com.motifgen.model.Sentence;
import com.motifgen.sentiment.SentimentProfile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Facade that generates a {@link DrumTrack} from a sentence + chord progression.
 *
 * <h3>Seven-pass pipeline</h3>
 * <ol>
 *   <li><b>Groove</b> — emit kick/snare/cymbal events per archetype 16-slot grid,
 *       applying A/B/C section voicings drawn from
 *       {@code sentence.getMetadata("sectionLabels")}.</li>
 *   <li><b>Fills</b> — replace bar 4 (half-bar) and bar 8 (full-bar) of every
 *       8-bar phrase with archetype-appropriate fills, ending each full-bar
 *       fill with crash+kick on the downbeat of the next phrase.</li>
 *   <li><b>Kick-lock</b> — snap kicks within ±0.03 beats of the nearest bass
 *       note onset on each strong beat to lock the rhythm section.</li>
 *   <li><b>Validate</b> — detect limb collisions and right-hand conflicts.</li>
 *   <li><b>Repair</b> — remove lower-priority conflicting events.</li>
 *   <li><b>Score</b> — compute composite difficulty.</li>
 * </ol>
 *
 * <p>Humanization (timing and velocity jitter) is intentionally absent; all
 * events are placed on the clean 16th-note grid for export fidelity.
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

  private static final double KICK_LOCK_BEATS = 0.03;

  /** Maximum simplification iterations in {@link #finaliseTrack}. */
  private static final int MAX_SIMPLIFY_ITERATIONS = 5;

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

    events.sort((a, b) -> Long.compare(a.startTick(), b.startTick()));

    // Pass 4: validate.
    // Tempo is not passed into generate(); use a neutral 120 BPM for validation.
    int tempoBpm = 120;
    List<DrumPlayabilityViolation> violations =
        DrumPlayabilityValidator.validate(events, tempoBpm);

    // Pass 5: repair.
    if (!violations.isEmpty()) {
      events = DrumPlayabilityRepair.repair(events, tempoBpm);
      violations = DrumPlayabilityValidator.validate(events, tempoBpm);
    }

    // Pass 6: score.
    DrumDifficulty.DifficultyScore difficulty =
        DrumDifficultyScorer.score(events, tempoBpm);

    return new DrumTrack(
        Collections.unmodifiableList(events),
        List.copyOf(violations),
        difficulty);
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

  /**
   * Simplifies {@code track} until its difficulty is at or below {@code target},
   * applying up to {@link #MAX_SIMPLIFY_ITERATIONS} simplification passes.
   *
   * <p>Simplification order:
   * <ol>
   *   <li>Remove ghost snares (velocity ≤ 40 on snare note 38/40)</li>
   *   <li>Remove off-beat hihat subdivisions (hihat on non-eighth-note slots)</li>
   *   <li>Remove syncopated kicks (kick not on a quarter-note boundary)</li>
   *   <li>Remove off-beat toms</li>
   *   <li>Remove open hihats</li>
   * </ol>
   *
   * @param track    source drum track
   * @param tempoBpm tempo in beats per minute (used for scoring and validation)
   * @param target   desired maximum difficulty level
   * @return simplified, repaired, re-scored {@link DrumTrack}
   */
  public static DrumTrack finaliseTrack(DrumTrack track, int tempoBpm, DrumDifficulty target) {
    List<DrumEvent> events = new ArrayList<>(track.events());

    for (int i = 0; i < MAX_SIMPLIFY_ITERATIONS; i++) {
      List<DrumPlayabilityViolation> violations =
          DrumPlayabilityValidator.validate(events, tempoBpm);
      if (!violations.isEmpty()) {
        events = DrumPlayabilityRepair.repair(events, tempoBpm);
      }

      DrumDifficulty.DifficultyScore current = DrumDifficultyScorer.score(events, tempoBpm);
      if (current.numericScore() <= target.maxScore()) break;

      events = applySimplificationStep(events, i, tempoBpm);
    }

    // Final repair + score.
    events = DrumPlayabilityRepair.repair(events, tempoBpm);
    List<DrumPlayabilityViolation> finalViolations =
        DrumPlayabilityValidator.validate(events, tempoBpm);
    DrumDifficulty.DifficultyScore finalScore = DrumDifficultyScorer.score(events, tempoBpm);

    return new DrumTrack(
        Collections.unmodifiableList(events),
        List.copyOf(finalViolations),
        finalScore);
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
      for (int slot = 1; slot < SIXTEENTHS_PER_BAR; slot += 4) {
        long tick = barStart + slot * sixteenth;
        if (!snare[slot]) {
          out.add(new DrumEvent(DrumPattern.SNARE, tick, sixteenth, GHOST_SNARE_VEL));
        }
      }
    }
    if (section.extraKick()) {
      long tick = barStart + 6 * sixteenth;
      out.add(new DrumEvent(DrumPattern.KICK, tick, sixteenth,
          scaleVelocity(BASE_KICK_VEL, section)));
    }
    if (section == DrumSection.B) {
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

      if (phraseBar == 3 && e.startTick() >= midBar && e.startTick() < barEnd) {
        continue;
      }
      if (phraseBar == 7 && e.startTick() >= barStart && e.startTick() < barEnd) {
        continue;
      }
      out.add(e);
    }

    for (int bar = 0; bar < totalBars; bar++) {
      int phraseBar = bar % PHRASE_BARS;
      long barStart = bar * ticksPerBar;
      long midBar = barStart + 2L * ppq;
      long barEnd = barStart + ticksPerBar;

      if (phraseBar == 3) {
        addHalfBarFill(out, archetype, midBar, sixteenth);
      } else if (phraseBar == 7) {
        addFullBarFill(out, archetype, barStart, sixteenth);
        out.add(new DrumEvent(DrumPattern.CRASH, barEnd, ppq, CRASH_VEL));
        out.add(new DrumEvent(DrumPattern.KICK, barEnd, sixteenth, BASE_KICK_VEL));
      }
    }
    return out;
  }

  private static void addHalfBarFill(
      List<DrumEvent> out, DrumGrooveArchetype archetype, long startTick, long sixteenth) {
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
      long modBeat = e.startTick() % ticksPerBeat;
      boolean onStrongBeat = (modBeat <= toleranceTicks)
          || (modBeat >= ticksPerBeat - toleranceTicks);
      if (!onStrongBeat) {
        out.add(e);
        continue;
      }
      long nearest = nearestBassTick(bassTicks, e.startTick());
      if (Math.abs(nearest - e.startTick()) <= toleranceTicks) {
        // Snap to the nearest bass tick, then re-quantize to the 16th grid.
        long sixteenth = ppq / 4L;
        long quantized = Math.round((double) nearest / sixteenth) * sixteenth;
        out.add(new DrumEvent(e.gmNote(), quantized, e.durationTicks(), e.velocity()));
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
  // Simplification steps (used by finaliseTrack)
  // -------------------------------------------------------------------------

  private static List<DrumEvent> applySimplificationStep(
      List<DrumEvent> events, int step, int tempoBpm) {
    return switch (step) {
      case 0 -> removeGhostSnares(events);
      case 1 -> removeOffBeatHihats(events, tempoBpm);
      case 2 -> removeSyncopatedKicks(events, tempoBpm);
      case 3 -> removeOffBeatToms(events, tempoBpm);
      case 4 -> removeOpenHihats(events);
      default -> events;
    };
  }

  private static List<DrumEvent> removeGhostSnares(List<DrumEvent> events) {
    List<DrumEvent> result = new ArrayList<>();
    for (DrumEvent e : events) {
      boolean isGhostSnare = (e.gmNote() == DrumPattern.SNARE || e.gmNote() == 40)
          && e.velocity() <= 40;
      if (!isGhostSnare) result.add(e);
    }
    return result;
  }

  private static List<DrumEvent> removeOffBeatHihats(List<DrumEvent> events, int tempoBpm) {
    long ppq = 480L;
    long eighth = ppq / 2;
    List<DrumEvent> result = new ArrayList<>();
    for (DrumEvent e : events) {
      boolean isHihat = e.gmNote() == DrumPattern.CLOSED_HIHAT;
      if (isHihat && e.startTick() % eighth != 0) continue;
      result.add(e);
    }
    return result;
  }

  private static List<DrumEvent> removeSyncopatedKicks(List<DrumEvent> events, int tempoBpm) {
    long ppq = 480L;
    long tolerance = 30L;
    List<DrumEvent> result = new ArrayList<>();
    for (DrumEvent e : events) {
      if (e.gmNote() != DrumPattern.KICK) {
        result.add(e);
        continue;
      }
      long mod = e.startTick() % ppq;
      boolean onBeat = mod <= tolerance || mod >= (ppq - tolerance);
      if (onBeat) result.add(e);
    }
    return result;
  }

  private static List<DrumEvent> removeOffBeatToms(List<DrumEvent> events, int tempoBpm) {
    long ppq = 480L;
    long eighth = ppq / 2;
    java.util.Set<Integer> tomNotes = java.util.Set.of(
        DrumPattern.HIGH_TOM, DrumPattern.MID_TOM, DrumPattern.LOW_TOM);
    List<DrumEvent> result = new ArrayList<>();
    for (DrumEvent e : events) {
      if (tomNotes.contains(e.gmNote()) && e.startTick() % eighth != 0) continue;
      result.add(e);
    }
    return result;
  }

  private static List<DrumEvent> removeOpenHihats(List<DrumEvent> events) {
    List<DrumEvent> result = new ArrayList<>();
    for (DrumEvent e : events) {
      if (e.gmNote() != DrumPattern.OPEN_HIHAT) result.add(e);
    }
    return result;
  }

  // -------------------------------------------------------------------------
  // Helpers
  // -------------------------------------------------------------------------

  private static DrumSection sectionForBar(int bar, String labels, int totalBars) {
    if (labels == null || labels.isEmpty()) return DrumSection.A;
    int phraseLen = Math.max(1, totalBars / labels.length());
    int idx = Math.min(labels.length() - 1, bar / phraseLen);
    return DrumSection.fromLabel(labels.charAt(idx));
  }

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
