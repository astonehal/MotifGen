package com.motifgen.guitar.backing;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Inserts approach notes at chord boundaries when rhythmic space permits.
 *
 * <p>An approach note is inserted one subdivision (eighth note = ppq/2 ticks)
 * before the next chord root when there are at least 0.5 beats of space
 * (i.e. the preceding note ends at least ppq/2 ticks before the chord change).
 *
 * <p>Three styles are supported:
 * <ul>
 *   <li>{@link ApproachStyle#NONE} — no approach notes</li>
 *   <li>{@link ApproachStyle#CHROMATIC} — semitone below the target root</li>
 *   <li>{@link ApproachStyle#DIATONIC} — whole step below the target root
 *       (falls back to chromatic if the diatonic step is still outside [28,55])</li>
 * </ul>
 */
public final class BassVoiceLeading {

  /** Strategy for choosing approach-note pitch relative to the target chord root. */
  public enum ApproachStyle {
    NONE,
    CHROMATIC,
    DIATONIC
  }

  private BassVoiceLeading() {}

  /**
   * Applies voice-leading approach notes to a skeleton bass line.
   *
   * @param skeleton   input bass notes (sorted by start tick)
   * @param slots      chord slots (used to identify chord boundaries)
   * @param ppq        ticks per quarter note
   * @param style      approach-note insertion strategy
   * @return new list of bass notes, possibly with approach notes inserted
   */
  public static List<BassNote> apply(
      List<BassNote> skeleton,
      List<ChordSlot> slots,
      int ppq,
      ApproachStyle style) {

    if (style == ApproachStyle.NONE || skeleton.isEmpty()) {
      return new ArrayList<>(skeleton);
    }

    long halfBeat = ppq / 2L; // 0.5 beats = one eighth-note

    // Sort skeleton by start tick (defensive copy)
    List<BassNote> sorted = skeleton.stream()
        .sorted(Comparator.comparingLong(BassNote::startTick))
        .collect(java.util.stream.Collectors.toCollection(ArrayList::new));

    List<BassNote> result = new ArrayList<>();

    for (int i = 0; i < sorted.size(); i++) {
      BassNote current = sorted.get(i);

      if (i + 1 < sorted.size()) {
        BassNote next = sorted.get(i + 1);
        long gap = next.startTick() - (current.startTick() + current.durationTicks());

        // Only insert if there is at least 0.5 beats of space
        if (gap >= halfBeat) {
          // Shorten current note to make room for approach note
          long newDur = current.durationTicks() + gap - halfBeat;
          BassNote shortened = new BassNote(current.midi(), current.startTick(),
              newDur, current.velocity(), current.stringIdx(), current.fret());
          result.add(shortened);

          // Approach note pitch
          int approachMidi = approachPitch(next.midi(), style);
          long approachStart = next.startTick() - halfBeat;
          result.add(new BassNote(approachMidi, approachStart, halfBeat,
              current.velocity() - 5, 0, 0));
          continue;
        }
      }

      result.add(current);
    }

    return result;
  }

  // -------------------------------------------------------------------------
  // Private helpers
  // -------------------------------------------------------------------------

  private static int approachPitch(int targetMidi, ApproachStyle style) {
    int approach = switch (style) {
      case CHROMATIC -> targetMidi - 1;
      case DIATONIC  -> targetMidi - 2;
      case NONE      -> targetMidi;
    };
    // Clamp to [28, 55]
    return Math.max(BassNote.MIDI_MIN, Math.min(BassNote.MIDI_MAX, approach));
  }
}
