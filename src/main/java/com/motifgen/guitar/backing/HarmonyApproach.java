package com.motifgen.guitar.backing;

import com.motifgen.model.Note;
import com.motifgen.sentiment.SentimentProfile;
import com.motifgen.theory.KeySignature;

import java.util.ArrayList;
import java.util.List;

/**
 * The five harmony approaches available to the backing track generator.
 *
 * <p>Each variant implements
 * {@link #generateChords(List, KeySignature, SentimentProfile)} and returns a
 * list of {@link ChordSlot} objects covering the melodic timeline.
 */
public enum HarmonyApproach {

  /**
   * Diatonic triads (I ii iii IV V vi) assigned so their tones best contain the
   * strong-beat melody note at each phrase boundary; ranked by voice-leading
   * distance from the previous chord.
   */
  FUNCTIONAL_DIATONIC {
    @Override
    public List<ChordSlot> generateChords(
        List<Note> melody, KeySignature key, SentimentProfile sentiment, long declaredTotalTicks) {
      if (melody.isEmpty()) return List.of();

      long slotSize = computeSlotSize(declaredTotalTicks, 4);

      // Build the 6 diatonic triads (I ii iii IV V vi) in the key
      int[] scale = key.scaleDegrees();
      int[][] triads = buildDiatonicTriads(scale);

      List<ChordSlot> slots = new ArrayList<>();
      List<Integer> prevChord = null;
      for (int i = 0; i < 4; i++) {
        long start = i * slotSize;
        long end = Math.min(start + slotSize, declaredTotalTicks);
        if (start >= declaredTotalTicks) break;

        // Find the strong-beat melody note for this slot
        final long slotStart = start;
        final long slotEnd = end;
        Note strongBeat = findStrongBeatNote(melody, slotStart, slotEnd);
        int targetPc = strongBeat != null ? strongBeat.pitchClass() : key.root();

        // Pick the triad that best contains the target pitch class, with
        // tie-break by voice-leading distance from previous chord
        int[] best = chooseBestTriad(triads, targetPc, prevChord);
        List<Integer> pitches = triadToPitches(best);
        slots.add(new ChordSlot(start, slotSize, pitches));
        prevChord = pitches;
      }
      return slots;
    }
  },

  /**
   * Builds triads on all 7 scale degrees of the melody's mode; picks the one
   * with highest consonance with the melody note for each slot.
   */
  MODAL_COLOUR {
    @Override
    public List<ChordSlot> generateChords(
        List<Note> melody, KeySignature key, SentimentProfile sentiment, long declaredTotalTicks) {
      if (melody.isEmpty()) return List.of();

      long slotSize = computeSlotSize(declaredTotalTicks, 4);
      int[] scale = key.scaleDegrees();
      int[][] triads = buildDiatonicTriads(scale);

      List<ChordSlot> slots = new ArrayList<>();
      for (int i = 0; i < 4; i++) {
        long start = i * slotSize;
        if (start >= declaredTotalTicks) break;

        final long slotStart = start;
        final long slotEnd = Math.min(start + slotSize, declaredTotalTicks);
        Note strongBeat = findStrongBeatNote(melody, slotStart, slotEnd);
        int targetPc = strongBeat != null ? strongBeat.pitchClass() : key.root();

        // Highest consonance = most overlap between triad tones and target pc
        int[] best = triads[0];
        int bestScore = -1;
        for (int[] triad : triads) {
          int score = consonanceScore(triad, targetPc);
          if (score > bestScore) {
            bestScore = score;
            best = triad;
          }
        }
        slots.add(new ChordSlot(start, slotSize, triadToPitches(best)));
      }
      return slots;
    }
  },

  /**
   * Tonic chord repeated for all slots; quality = major/minor based on the key.
   */
  STATIC_PEDAL {
    @Override
    public List<ChordSlot> generateChords(
        List<Note> melody, KeySignature key, SentimentProfile sentiment, long declaredTotalTicks) {
      if (melody.isEmpty()) return List.of();

      long slotSize = computeSlotSize(declaredTotalTicks, 4);

      // Tonic triad: root, third (major=4 or minor=3 semitones), fifth
      int root = key.root();
      int third = key.minor() ? root + 3 : root + 4;
      int fifth = root + 7;
      List<Integer> tonic = List.of(root, third % 12, fifth % 12);

      List<ChordSlot> slots = new ArrayList<>();
      for (int i = 0; i < 4; i++) {
        long start = i * slotSize;
        if (start >= declaredTotalTicks) break;
        slots.add(new ChordSlot(start, slotSize, tonic));
      }
      return slots;
    }
  },

  /**
   * ii7-V7-I7 progression with shell voicings (root + third + seventh).
   */
  JAZZ_SHELL {
    @Override
    public List<ChordSlot> generateChords(
        List<Note> melody, KeySignature key, SentimentProfile sentiment, long declaredTotalTicks) {
      if (melody.isEmpty()) return List.of();

      long slotSize = computeSlotSize(declaredTotalTicks, 4);

      int root = key.root();
      // ii7: root+2, +(minor3rd), +(7th from ii root) = root+2, root+5, root+12
      // V7: root+7, root+11, root+5 (shell: root, major3, minor7)
      // I7 (or Imaj7): root, root+4, root+11 (major7)
      List<List<Integer>> progression = List.of(
          List.of((root + 2) % 12, (root + 5) % 12, (root + 0) % 12), // ii7 shell
          List.of((root + 7) % 12, (root + 11) % 12, (root + 5) % 12), // V7 shell
          List.of(root % 12, (root + 4) % 12, (root + 11) % 12),        // Imaj7 shell
          List.of(root % 12, (root + 4) % 12, (root + 11) % 12)         // Imaj7 repeated
      );

      List<ChordSlot> slots = new ArrayList<>();
      for (int i = 0; i < 4; i++) {
        long start = i * slotSize;
        if (start >= declaredTotalTicks) break;
        slots.add(new ChordSlot(start, slotSize, progression.get(i)));
      }
      return slots;
    }
  },

  /**
   * Matches the pitch-class set of each melody segment to the closest diatonic
   * triad by overlap count.
   */
  IMPLIED_HARMONY {
    @Override
    public List<ChordSlot> generateChords(
        List<Note> melody, KeySignature key, SentimentProfile sentiment, long declaredTotalTicks) {
      if (melody.isEmpty()) return List.of();

      long slotSize = computeSlotSize(declaredTotalTicks, 4);
      int[] scale = key.scaleDegrees();
      int[][] triads = buildDiatonicTriads(scale);

      List<ChordSlot> slots = new ArrayList<>();
      for (int i = 0; i < 4; i++) {
        long start = i * slotSize;
        if (start >= declaredTotalTicks) break;
        final long slotStart = start;
        final long slotEnd = Math.min(start + slotSize, declaredTotalTicks);

        // Collect pitch classes in this segment
        List<Integer> segmentPcs = melody.stream()
            .filter(n -> !n.isRest()
                && n.startTick() >= slotStart
                && n.startTick() < slotEnd)
            .map(Note::pitchClass)
            .distinct()
            .toList();

        // Find the triad with most overlap
        int[] best = triads[0];
        int bestOverlap = -1;
        for (int[] triad : triads) {
          int overlap = 0;
          for (int pc : segmentPcs) {
            for (int t : triad) {
              if (t == pc) overlap++;
            }
          }
          if (overlap > bestOverlap) {
            bestOverlap = overlap;
            best = triad;
          }
        }
        slots.add(new ChordSlot(start, slotSize, triadToPitches(best)));
      }
      return slots;
    }
  };

  // -----------------------------------------------------------------------
  // Abstract method
  // -----------------------------------------------------------------------

  /**
   * Generates chord slots covering the melodic timeline using the declared sentence duration.
   *
   * @param melody              all melody notes in time order
   * @param key                 key signature of the piece
   * @param sentiment           sentiment profile (may influence chord quality)
   * @param declaredTotalTicks  declared sentence duration (phrase count × bars × ticks-per-bar);
   *                            use this instead of max(endTick) to keep slots on bar boundaries
   * @return list of chord slots, never null
   */
  public abstract List<ChordSlot> generateChords(
      List<Note> melody, KeySignature key, SentimentProfile sentiment, long declaredTotalTicks);

  /**
   * Backward-compatible 3-argument overload. Derives total ticks from the note list; prefer
   * the 4-argument form when the declared sentence duration is available.
   */
  public final List<ChordSlot> generateChords(
      List<Note> melody, KeySignature key, SentimentProfile sentiment) {
    long totalTicks = melody.stream().mapToLong(Note::endTick).max().orElse(0);
    return generateChords(melody, key, sentiment, totalTicks);
  }

  // -----------------------------------------------------------------------
  // Shared helpers (package-private for testability)
  // -----------------------------------------------------------------------

  /** Divides the total melody tick span evenly into {@code count} slots. */
  static long computeSlotSize(long totalTicks, int count) {
    return Math.max(1, totalTicks / count);
  }

  /**
   * Builds diatonic triads from the 7 scale degrees: each triad is
   * [root_pc, third_pc, fifth_pc] (all mod 12).
   */
  static int[][] buildDiatonicTriads(int[] scaleDegrees) {
    int[][] triads = new int[7][3];
    for (int i = 0; i < 7; i++) {
      triads[i][0] = scaleDegrees[i];
      triads[i][1] = scaleDegrees[(i + 2) % 7];
      triads[i][2] = scaleDegrees[(i + 4) % 7];
    }
    return triads;
  }

  /** Converts a pitch-class triad to a List of pitch-class integers. */
  static List<Integer> triadToPitches(int[] triad) {
    return List.of(triad[0], triad[1], triad[2]);
  }

  /**
   * Finds the note closest to a strong beat (beat 0 of each bar, i.e. tick
   * positions divisible by 4× ppq) within the given tick range.
   * Falls back to the earliest note in the range.
   */
  static Note findStrongBeatNote(List<Note> melody, long start, long end) {
    Note first = null;
    for (Note n : melody) {
      if (n.isRest()) continue;
      if (n.startTick() >= start && n.startTick() < end) {
        if (first == null) first = n;
        // Treat any note as a candidate; just return the first non-rest
      }
    }
    return first;
  }

  /**
   * Counts how many of the triad's pitch classes equal the target pitch class,
   * yielding a consonance score for MODAL_COLOUR selection.
   */
  static int consonanceScore(int[] triad, int targetPc) {
    int score = 0;
    for (int pc : triad) {
      if (pc == targetPc) score += 2;       // direct match
      else if ((Math.abs(pc - targetPc) % 12) == 5
            || (Math.abs(pc - targetPc) % 12) == 7) score++; // 4th or 5th above
    }
    return score;
  }

  /**
   * Chooses the best triad from the candidates by:
   * <ol>
   *   <li>Number of tones matching the target pitch class (higher = better)</li>
   *   <li>Tie-break: minimum voice-leading distance from previous chord</li>
   * </ol>
   */
  static int[] chooseBestTriad(int[][] triads, int targetPc, List<Integer> prevChord) {
    int[] best = triads[0];
    int bestContainment = -1;
    int bestVoiceLeading = Integer.MAX_VALUE;

    for (int[] triad : triads) {
      int containment = consonanceScore(triad, targetPc);
      int vl = prevChord == null ? 0 : voiceLeadingDistance(triad, prevChord);

      if (containment > bestContainment
          || (containment == bestContainment && vl < bestVoiceLeading)) {
        bestContainment = containment;
        bestVoiceLeading = vl;
        best = triad;
      }
    }
    return best;
  }

  /** Sum of minimum semitone distances between each new tone and the previous chord. */
  private static int voiceLeadingDistance(int[] triad, List<Integer> prevChord) {
    int total = 0;
    for (int pc : triad) {
      int minDist = prevChord.stream()
          .mapToInt(p -> Math.min(Math.abs(pc - p), 12 - Math.abs(pc - p)))
          .min()
          .orElse(0);
      total += minDist;
    }
    return total;
  }
}
