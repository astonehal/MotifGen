package com.motifgen.guitar.backing;

import com.motifgen.guitar.GuitarFingering;
import com.motifgen.model.Note;

import java.util.ArrayList;
import java.util.List;

/**
 * Voices {@link ChordSlot} objects into {@link VoicedChord} objects.
 *
 * <p>For each slot:
 * <ol>
 *   <li>Clamp each pitch to the guitar register MIDI 40–76 (E2–E5) by octave
 *       transposition.</li>
 *   <li>Deduplicate pitches within the register.</li>
 *   <li>Pass the resulting notes through {@link GuitarFingering#compute} to
 *       validate that fret positions exist (ensuring playability).</li>
 *   <li>Return a {@link VoicedChord} whose {@code notes} carry the clamped
 *       pitches, the slot duration, and a fixed velocity of 72.</li>
 * </ol>
 */
public final class GuitarChordVoicer {

  /** Lowest MIDI pitch in the guitar backing register (E2). */
  public static final int MIN_PITCH = 40;

  /** Highest MIDI pitch in the guitar backing register (E5). */
  public static final int MAX_PITCH = 76;

  private static final int DEFAULT_VELOCITY = 72;

  private GuitarChordVoicer() {}

  /**
   * Voices a list of chord slots.
   *
   * @param slots    raw chord slots from a {@link HarmonyApproach}
   * @param voicing  voicing type (affects pitch selection)
   * @param ppq      ticks per quarter note (passed to GuitarFingering)
   * @return list of voiced chords in slot order
   */
  public static List<VoicedChord> voice(List<ChordSlot> slots, VoicingType voicing, int ppq) {
    List<VoicedChord> result = new ArrayList<>(slots.size());
    for (ChordSlot slot : slots) {
      result.add(voiceSlot(slot, voicing, ppq));
    }
    return result;
  }

  // -----------------------------------------------------------------------
  // Private helpers
  // -----------------------------------------------------------------------

  private static VoicedChord voiceSlot(ChordSlot slot, VoicingType voicing, int ppq) {
    List<Integer> clamped = clampToRegister(slot.pitches(), voicing);

    // Build Note objects for GuitarFingering validation
    List<Note> chordNotes = new ArrayList<>();
    for (int i = 0; i < clamped.size(); i++) {
      chordNotes.add(new Note(clamped.get(i), slot.startTick() + (long) i, slot.durationTicks(),
          DEFAULT_VELOCITY));
    }

    // Validate via GuitarFingering DP (ensures playable fret positions exist)
    if (!chordNotes.isEmpty()) {
      GuitarFingering.compute(chordNotes, ppq);
    }

    return new VoicedChord(slot.startTick(), chordNotes);
  }

  /** Clamps each pitch into MIDI 40–76 by octave transposition, then deduplicates. */
  static List<Integer> clampToRegister(List<Integer> pitches, VoicingType voicing) {
    List<Integer> result = new ArrayList<>();
    for (int pitch : pitches) {
      int clamped = clampByOctave(pitch);
      if (!result.contains(clamped)) {
        result.add(clamped);
      }
    }
    // POWER voicing: keep only root and fifth (first two distinct pitches)
    if (voicing == VoicingType.POWER && result.size() > 2) {
      result = result.subList(0, 2);
    }
    // SHELL voicing: keep root, third, seventh (first, second, fourth if present)
    if (voicing == VoicingType.SHELL && result.size() > 3) {
      result = List.of(result.get(0), result.get(1), result.get(result.size() - 1));
    }
    return result;
  }

  private static int clampByOctave(int pitch) {
    while (pitch < MIN_PITCH) pitch += 12;
    while (pitch > MAX_PITCH) pitch -= 12;
    return pitch;
  }
}
