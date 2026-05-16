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
 *   <li>Place the root (first pitch class in the slot) in the low-bass register
 *       (MIDI 40–55, E2–G3) so the chord sounds grounded.</li>
 *   <li>Stack each remaining chord tone strictly above the previous note,
 *       yielding an ascending, root-position voicing within MIDI 40–76.</li>
 *   <li>Apply voicing-type filters (POWER, SHELL) after stacking.</li>
 *   <li>Pass the result through {@link GuitarFingering#compute} to validate
 *       that fret positions exist (ensuring playability).</li>
 * </ol>
 */
public final class GuitarChordVoicer {

  /** Lowest MIDI pitch in the guitar backing register (E2). */
  public static final int MIN_PITCH = 40;

  /** Highest MIDI pitch in the guitar backing register (E5). */
  public static final int MAX_PITCH = 76;

  /**
   * Lowest root pitch for chord voicings (C3). Roots start here rather than at
   * MIN_PITCH so chords sit in the C3–B4 range and stay close to the melody.
   */
  private static final int ROOT_FLOOR = 48;   // C3

  /**
   * Ceiling for chord root notes. Roots above this are dropped an octave so
   * the chord has room to spread upward within the register.
   */
  private static final int ROOT_CEILING = 64; // E4

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
    List<Integer> pitched = buildAscendingVoicing(slot.pitches(), voicing);

    // Build Note objects for GuitarFingering validation
    List<Note> chordNotes = new ArrayList<>();
    for (int i = 0; i < pitched.size(); i++) {
      chordNotes.add(new Note(
          pitched.get(i),
          slot.startTick() + (long) i,
          slot.durationTicks(),
          DEFAULT_VELOCITY));
    }

    if (!chordNotes.isEmpty()) {
      GuitarFingering.compute(chordNotes, ppq);
    }

    return new VoicedChord(slot.startTick(), chordNotes);
  }

  /**
   * Builds a standard guitar chord voicing from a list of pitch classes.
   *
   * <p>Uses real guitar chord shapes rather than mathematical stacking:
   * <ul>
   *   <li>BARRE → E-shape (6 strings) when root is low enough, A-shape (5) otherwise</li>
   *   <li>OPEN  → A-shape (5 strings) or D-shape (4 strings) based on root height</li>
   *   <li>TRIAD/JAZZ → D-shape (4 strings)</li>
   *   <li>SHELL → compact 3-string open voicing (root + 3rd + 5th-above-octave)</li>
   *   <li>POWER → root + fifth + octave (3 strings)</li>
   * </ul>
   *
   * <p>Shape intervals follow standard barre/open chord patterns:
   * E-shape [0,7,12,third,19,24], A-shape [0,7,12,third,19], D-shape [0,7,12,third]
   * where {@code third} = 15 (minor) or 16 (major) above the root.
   */
  static List<Integer> buildAscendingVoicing(List<Integer> pitchClasses, VoicingType voicing) {
    if (pitchClasses.isEmpty()) return List.of();

    // Determine chord quality (major/minor) from the third pitch class
    int rootPc = pitchClasses.get(0);
    int thirdInterval = pitchClasses.size() >= 2
        ? (pitchClasses.get(1) - rootPc + 12) % 12
        : 4; // default major
    int thirdLow  = thirdInterval;      // 3 (minor) or 4 (major) above root
    int thirdHigh = 12 + thirdLow;     // same, one octave up: 15 (minor) or 16 (major)

    // Place root in guitar bass register (C3–B3, MIDI 48–59)
    int rootPitch = rootPc;
    while (rootPitch < ROOT_FLOOR) rootPitch += 12;
    while (rootPitch > MAX_PITCH)  rootPitch -= 12;
    while (rootPitch > ROOT_CEILING && rootPitch - 12 >= ROOT_FLOOR) rootPitch -= 12;

    // Select standard guitar chord shape intervals relative to root
    int[] intervals = selectShapeIntervals(voicing, rootPitch, thirdLow, thirdHigh);

    // Apply intervals; drop notes that fall outside the guitar register
    List<Integer> result = new ArrayList<>();
    for (int interval : intervals) {
      int pitch = rootPitch + interval;
      if (pitch >= MIN_PITCH && pitch <= MAX_PITCH) {
        result.add(pitch);
      }
    }
    return result.isEmpty() ? List.of(rootPitch) : result;
  }

  /**
   * Returns the interval array (semitones above root) for a standard guitar chord shape.
   *
   * <p>Shape selection for BARRE and OPEN is based on how high the highest string would
   * reach: E-shape adds 24 semitones above root (2 octaves), A-shape adds 19, D-shape
   * adds {@code thirdHigh} (15 or 16). If the higher shape would exceed MAX_PITCH the
   * next smaller shape is used automatically.
   */
  private static int[] selectShapeIntervals(
      VoicingType voicing, int rootPitch, int thirdLow, int thirdHigh) {
    return switch (voicing) {
      case POWER -> new int[]{0, 7, 12};
      case BARRE -> {
        // E-shape (6 strings) if root+24 fits; A-shape (5) if root+19 fits; D-shape (4) otherwise
        if (rootPitch + 24 <= MAX_PITCH) yield new int[]{0, 7, 12, thirdHigh, 19, 24};
        if (rootPitch + 19 <= MAX_PITCH) yield new int[]{0, 7, 12, thirdHigh, 19};
        yield new int[]{0, 7, 12, thirdHigh};
      }
      case OPEN -> {
        // A-shape (5 strings) if root+19 fits; D-shape (4 strings) otherwise
        if (rootPitch + 19 <= MAX_PITCH) yield new int[]{0, 7, 12, thirdHigh, 19};
        yield new int[]{0, 7, 12, thirdHigh};
      }
      case SHELL -> new int[]{0, thirdLow, 19}; // root + 3rd (1st octave) + 5th (2nd octave)
      default    -> new int[]{0, 7, 12, thirdHigh}; // TRIAD, JAZZ: D-shape (4 strings)
    };
  }

  // Kept for test backward compatibility — delegates to buildAscendingVoicing
  static List<Integer> clampToRegister(List<Integer> pitches, VoicingType voicing) {
    return buildAscendingVoicing(pitches, voicing);
  }
}
