package com.motifgen.guitar.backing;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Stateless validator that inspects a list of {@link DrumEvent}s and returns
 * all {@link DrumPlayabilityViolation}s.
 *
 * <p>Two violation categories are detected:
 * <ul>
 *   <li>{@link DrumPlayabilityViolation.Type#LIMB_COLLISION} — the same limb is required
 *       to strike twice within its minimum inter-onset gap.</li>
 *   <li>{@link DrumPlayabilityViolation.Type#RIGHT_HAND_CONFLICT} — a hihat (closed/open)
 *       and a ride cymbal appear within 10 ticks of each other.</li>
 * </ul>
 */
public final class DrumPlayabilityValidator {

  // Minimum inter-onset gaps in ticks at or below 160 BPM.
  private static final long MIN_GAP_RIGHT_FOOT  = 240L;
  private static final long MIN_GAP_LEFT_FOOT   = 480L;
  private static final long MIN_GAP_HAND_NORMAL = 60L;
  private static final long MIN_GAP_HAND_FAST   = 120L;

  /** BPM threshold above which hand minimum gap increases. */
  private static final int FAST_TEMPO_THRESHOLD = 160;

  /** Tolerance in ticks for right-hand conflict detection. */
  private static final long RIGHT_HAND_CONFLICT_TOLERANCE = 10L;

  /** GM note numbers that belong to the hihat group (closed or open) for conflict detection. */
  private static final java.util.Set<Integer> HIHAT_NOTES =
      java.util.Set.of(42, 46);

  /** GM note numbers that belong to the ride group for conflict detection. */
  private static final java.util.Set<Integer> RIDE_NOTES =
      java.util.Set.of(51, 53, 59);

  /**
   * Limb assignment for each GM drum note number.
   * Notes not listed here are ignored in collision detection.
   */
  private static final Map<Integer, DrumPlayabilityViolation.Limb> LIMB_MAP;

  static {
    LIMB_MAP = new HashMap<>();

    // RIGHT_FOOT (kick)
    LIMB_MAP.put(35, DrumPlayabilityViolation.Limb.RIGHT_FOOT);
    LIMB_MAP.put(36, DrumPlayabilityViolation.Limb.RIGHT_FOOT);

    // LEFT_FOOT (hi-hat pedal)
    LIMB_MAP.put(44, DrumPlayabilityViolation.Limb.LEFT_FOOT);
    LIMB_MAP.put(26, DrumPlayabilityViolation.Limb.LEFT_FOOT);

    // LEFT_HAND (snare, rimshot, low/mid toms)
    LIMB_MAP.put(38, DrumPlayabilityViolation.Limb.LEFT_HAND);
    LIMB_MAP.put(40, DrumPlayabilityViolation.Limb.LEFT_HAND);
    LIMB_MAP.put(37, DrumPlayabilityViolation.Limb.LEFT_HAND);
    LIMB_MAP.put(41, DrumPlayabilityViolation.Limb.LEFT_HAND);
    LIMB_MAP.put(43, DrumPlayabilityViolation.Limb.LEFT_HAND);
    LIMB_MAP.put(45, DrumPlayabilityViolation.Limb.LEFT_HAND);
    LIMB_MAP.put(47, DrumPlayabilityViolation.Limb.LEFT_HAND);

    // RIGHT_HAND (cymbals, hi-tom, hi-tom-2)
    LIMB_MAP.put(42, DrumPlayabilityViolation.Limb.RIGHT_HAND);
    LIMB_MAP.put(46, DrumPlayabilityViolation.Limb.RIGHT_HAND);
    LIMB_MAP.put(49, DrumPlayabilityViolation.Limb.RIGHT_HAND);
    LIMB_MAP.put(57, DrumPlayabilityViolation.Limb.RIGHT_HAND);
    LIMB_MAP.put(51, DrumPlayabilityViolation.Limb.RIGHT_HAND);
    LIMB_MAP.put(59, DrumPlayabilityViolation.Limb.RIGHT_HAND);
    LIMB_MAP.put(50, DrumPlayabilityViolation.Limb.RIGHT_HAND);
    LIMB_MAP.put(48, DrumPlayabilityViolation.Limb.RIGHT_HAND);
    LIMB_MAP.put(52, DrumPlayabilityViolation.Limb.RIGHT_HAND);
    LIMB_MAP.put(53, DrumPlayabilityViolation.Limb.RIGHT_HAND);
  }

  private DrumPlayabilityValidator() {}

  /**
   * Validates the given drum events and returns all violations found.
   *
   * @param events   drum events (need not be sorted)
   * @param tempoBpm tempo in beats per minute (affects hand minimum gap above 160 BPM)
   * @return immutable list of violations; empty if none
   */
  public static List<DrumPlayabilityViolation> validate(List<DrumEvent> events, int tempoBpm) {
    List<DrumEvent> sorted = events.stream()
        .sorted(Comparator.comparingLong(DrumEvent::startTick))
        .toList();

    long handMinGap = tempoBpm > FAST_TEMPO_THRESHOLD ? MIN_GAP_HAND_FAST : MIN_GAP_HAND_NORMAL;

    List<DrumPlayabilityViolation> violations = new ArrayList<>();
    violations.addAll(detectLimbCollisions(sorted, handMinGap));
    violations.addAll(detectRightHandConflicts(sorted));
    return List.copyOf(violations);
  }

  // -------------------------------------------------------------------------
  // Limb collision detection
  // -------------------------------------------------------------------------

  private static List<DrumPlayabilityViolation> detectLimbCollisions(
      List<DrumEvent> sorted, long handMinGap) {

    List<DrumPlayabilityViolation> result = new ArrayList<>();
    // Track the last-seen tick per limb.
    Map<DrumPlayabilityViolation.Limb, Long> lastTick = new HashMap<>();
    Map<DrumPlayabilityViolation.Limb, DrumEvent> lastEvent = new HashMap<>();

    for (DrumEvent e : sorted) {
      DrumPlayabilityViolation.Limb limb = LIMB_MAP.get(e.gmNote());
      if (limb == null) continue;

      long minGap = minGapForLimb(limb, handMinGap);
      Long prev = lastTick.get(limb);
      if (prev != null) {
        long gap = e.startTick() - prev;
        // gap == 0 means simultaneous (chord) — physically possible, not a collision.
        if (gap > 0 && gap < minGap) {
          result.add(new DrumPlayabilityViolation(
              DrumPlayabilityViolation.Type.LIMB_COLLISION,
              limb,
              e.startTick(),
              List.of(lastEvent.get(limb), e)));
        }
      }
      // Always advance the last tick so we only flag the earliest violation
      // and don't cascade-flag every subsequent event.
      if (prev == null || e.startTick() > prev) {
        lastTick.put(limb, e.startTick());
        lastEvent.put(limb, e);
      }
    }
    return result;
  }

  private static long minGapForLimb(DrumPlayabilityViolation.Limb limb, long handMinGap) {
    return switch (limb) {
      case RIGHT_FOOT -> MIN_GAP_RIGHT_FOOT;
      case LEFT_FOOT  -> MIN_GAP_LEFT_FOOT;
      case RIGHT_HAND, LEFT_HAND -> handMinGap;
    };
  }

  // -------------------------------------------------------------------------
  // Right-hand conflict detection
  // -------------------------------------------------------------------------

  private static List<DrumPlayabilityViolation> detectRightHandConflicts(
      List<DrumEvent> sorted) {

    List<DrumPlayabilityViolation> result = new ArrayList<>();
    for (int i = 0; i < sorted.size(); i++) {
      DrumEvent a = sorted.get(i);
      if (!HIHAT_NOTES.contains(a.gmNote()) && !RIDE_NOTES.contains(a.gmNote())) continue;

      for (int j = i + 1; j < sorted.size(); j++) {
        DrumEvent b = sorted.get(j);
        if (b.startTick() - a.startTick() > RIGHT_HAND_CONFLICT_TOLERANCE) break;

        boolean aIsHihat = HIHAT_NOTES.contains(a.gmNote());
        boolean bIsHihat = HIHAT_NOTES.contains(b.gmNote());
        boolean aIsRide  = RIDE_NOTES.contains(a.gmNote());
        boolean bIsRide  = RIDE_NOTES.contains(b.gmNote());

        if ((aIsHihat && bIsRide) || (aIsRide && bIsHihat)) {
          result.add(new DrumPlayabilityViolation(
              DrumPlayabilityViolation.Type.RIGHT_HAND_CONFLICT,
              DrumPlayabilityViolation.Limb.RIGHT_HAND,
              a.startTick(),
              List.of(a, b)));
        }
      }
    }
    return result;
  }
}
