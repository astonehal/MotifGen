package com.motifgen.guitar.backing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Priority-based greedy repair pass that removes lower-priority drum events to
 * eliminate {@link DrumPlayabilityViolation}s.
 *
 * <p>Repair strategy:
 * <ul>
 *   <li><b>Right-hand conflict</b>: remove the event with the lower priority index
 *       (higher index = lower priority).</li>
 *   <li><b>Limb collision</b>: remove the second (later) event of the colliding pair,
 *       unless it has strictly higher priority than the first, in which case the first
 *       is removed instead.</li>
 * </ul>
 *
 * <p>The repair loop runs until no violations remain or the event list is exhausted.
 */
public final class DrumPlayabilityRepair {

  /**
   * Repair priority by GM note number — lower index = higher priority.
   * Notes not listed here are treated as lowest priority (Integer.MAX_VALUE).
   */
  private static final List<int[]> PRIORITY_GROUPS = List.of(
      new int[]{36, 35},  // kick
      new int[]{38, 40},  // snare
      new int[]{37},      // snare ghost / rimshot
      new int[]{42},      // hihat_closed
      new int[]{51},      // ride
      new int[]{50},      // tom_high
      new int[]{45},      // tom_mid
      new int[]{43},      // tom_low
      new int[]{47},      // tom_floor (mid_tom_2 / low_tom in GM)
      new int[]{46},      // hihat_open
      new int[]{49, 57},  // crash
      new int[]{44}       // hihat_pedal
  );

  private static final Map<Integer, Integer> PRIORITY_INDEX;

  static {
    PRIORITY_INDEX = new HashMap<>();
    for (int groupIdx = 0; groupIdx < PRIORITY_GROUPS.size(); groupIdx++) {
      for (int note : PRIORITY_GROUPS.get(groupIdx)) {
        PRIORITY_INDEX.put(note, groupIdx);
      }
    }
  }

  private DrumPlayabilityRepair() {}

  /**
   * Repairs the given drum events and returns a new, violation-free list.
   *
   * @param events   source events (need not be sorted)
   * @param tempoBpm tempo in beats per minute (passed to validator)
   * @return new list with violations resolved; preserves quantized timing
   */
  public static List<DrumEvent> repair(List<DrumEvent> events, int tempoBpm) {
    List<DrumEvent> working = new ArrayList<>(events);

    for (int iteration = 0; iteration < working.size() + 1; iteration++) {
      List<DrumPlayabilityViolation> violations =
          DrumPlayabilityValidator.validate(working, tempoBpm);
      if (violations.isEmpty()) break;

      DrumPlayabilityViolation first = violations.get(0);
      working = removeOffendingEvent(working, first);
    }
    return working;
  }

  private static List<DrumEvent> removeOffendingEvent(
      List<DrumEvent> events, DrumPlayabilityViolation violation) {

    List<DrumEvent> notes = violation.drumNotes();
    if (notes.size() < 2) return events;

    DrumEvent a = notes.get(0);
    DrumEvent b = notes.get(1);
    DrumEvent toRemove = lowerPriority(a, b);

    List<DrumEvent> result = new ArrayList<>(events);
    // Remove only the first occurrence of the lower-priority event.
    for (int i = 0; i < result.size(); i++) {
      DrumEvent e = result.get(i);
      if (e.gmNote() == toRemove.gmNote() && e.startTick() == toRemove.startTick()) {
        result.remove(i);
        break;
      }
    }
    return result;
  }

  /** Returns the event with the lower priority (higher priority index). */
  private static DrumEvent lowerPriority(DrumEvent a, DrumEvent b) {
    int pa = PRIORITY_INDEX.getOrDefault(a.gmNote(), Integer.MAX_VALUE);
    int pb = PRIORITY_INDEX.getOrDefault(b.gmNote(), Integer.MAX_VALUE);
    // Higher index = lower priority = the one to remove.
    // On a tie, remove the later event (b).
    return pa > pb ? a : b;
  }
}
