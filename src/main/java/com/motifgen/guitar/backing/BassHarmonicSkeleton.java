package com.motifgen.guitar.backing;

import java.util.ArrayList;
import java.util.List;

/**
 * Derives root-position bass notes from a chord-slot progression.
 *
 * <p>For each {@link ChordSlot} the lowest pitch in the chord's pitch list is
 * identified, reduced to the primary bass register [28, 43] (E1–G2), and
 * optionally shifted by an octave offset before clamping to [28, 55].
 */
public final class BassHarmonicSkeleton {

  private BassHarmonicSkeleton() {}

  /**
   * Derives bass notes with no octave offset (default register).
   *
   * @param slots ordered chord slots
   * @param ppq   ticks per quarter note
   * @return bass notes (one per chord slot, sorted by start tick)
   */
  public static List<BassNote> derive(List<ChordSlot> slots, int ppq) {
    return derive(slots, ppq, 0);
  }

  /**
   * Derives bass notes with an optional octave offset.
   *
   * <p>The offset is applied before clamping, allowing callers to explore
   * different register regions while still guaranteeing [28, 55] output.
   *
   * @param slots        ordered chord slots
   * @param ppq          ticks per quarter note
   * @param octaveOffset semitone shift applied before clamping (+12, 0, -12)
   * @return bass notes (one per chord slot, sorted by start tick)
   */
  public static List<BassNote> derive(List<ChordSlot> slots, int ppq, int octaveOffset) {
    List<BassNote> result = new ArrayList<>();
    for (ChordSlot slot : slots) {
      if (slot.pitches().isEmpty()) continue;

      // Find the chord root (lowest pitch class) and put it in primary register
      int rootPc = slot.pitches().stream()
          .mapToInt(p -> p % 12)
          .min()
          .orElse(0);

      // Build the root in primary range [28, 43]: start from E1 (28) and step up
      int bassMidi = 24 + rootPc; // C0=24, so C1=36, but we need to find right octave
      // Adjust to put rootPc in octave 1–2 (MIDI 24–47)
      while (bassMidi < BassNote.MIDI_MIN) bassMidi += 12;
      while (bassMidi > BassNote.PRIMARY_MAX) bassMidi -= 12;

      // Apply octave offset then clamp
      bassMidi = Math.max(BassNote.MIDI_MIN, Math.min(BassNote.MIDI_MAX, bassMidi + octaveOffset));

      result.add(new BassNote(bassMidi, slot.startTick(), slot.durationTicks(), 85, 0, 0));
    }
    return result;
  }
}
