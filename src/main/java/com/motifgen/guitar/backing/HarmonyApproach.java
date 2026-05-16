package com.motifgen.guitar.backing;

import com.motifgen.model.Note;
import com.motifgen.sentiment.SentimentProfile;
import com.motifgen.theory.KeySignature;

import java.util.ArrayList;
import java.util.List;

/**
 * The five harmony approaches available to the backing track generator.
 *
 * <p>Each variant uses a well-known Roman-numeral chord progression
 * (adapted for major or minor keys) so that the backing track sounds
 * harmonically coherent and resolves correctly at phrase endings.
 *
 * <p>Scale-degree indices follow Java 0-based convention:
 * 0=I/i, 1=II/ii, 2=III/iii, 3=IV/iv, 4=V/v, 5=VI/vi, 6=VII/vii
 */
public enum HarmonyApproach {

  /**
   * Classical tonal harmony: selects from I, ii, IV, V using the strong-beat
   * melody note to pick the chord containing it; forces V→I at every phrase cadence.
   */
  FUNCTIONAL_DIATONIC {
    private static final int[] MAJ_PALETTE = {0, 1, 3, 4};  // I ii IV V
    private static final int[] MIN_PALETTE = {0, 3, 4, 5};  // i iv v VI

    @Override
    public List<ChordSlot> generateChords(
        List<Note> melody, KeySignature key, SentimentProfile sentiment,
        long declaredTotalTicks, int numSlots) {
      return generateMelodyAware(
          key.minor() ? MIN_PALETTE : MAJ_PALETTE,
          melody, key, declaredTotalTicks, numSlots);
    }
  },

  /**
   * Pop/rock palette: I, IV, V, vi — selects chord containing the strong-beat
   * melody note; cadences with V→I at phrase boundaries.
   */
  MODAL_COLOUR {
    private static final int[] MAJ_PALETTE = {0, 3, 4, 5};  // I IV V vi
    private static final int[] MIN_PALETTE = {0, 3, 5, 6};  // i iv VI VII

    @Override
    public List<ChordSlot> generateChords(
        List<Note> melody, KeySignature key, SentimentProfile sentiment,
        long declaredTotalTicks, int numSlots) {
      return generateMelodyAware(
          key.minor() ? MIN_PALETTE : MAJ_PALETTE,
          melody, key, declaredTotalTicks, numSlots);
    }
  },

  /**
   * Warm/steady palette: I, iii, IV, vi — adds the mediant for a richer colour;
   * melody-driven chord selection with phrase cadences.
   */
  STATIC_PEDAL {
    private static final int[] MAJ_PALETTE = {0, 2, 3, 5};  // I iii IV vi
    private static final int[] MIN_PALETTE = {0, 2, 5, 6};  // i III VI VII

    @Override
    public List<ChordSlot> generateChords(
        List<Note> melody, KeySignature key, SentimentProfile sentiment,
        long declaredTotalTicks, int numSlots) {
      return generateMelodyAware(
          key.minor() ? MIN_PALETTE : MAJ_PALETTE,
          melody, key, declaredTotalTicks, numSlots);
    }
  },

  /**
   * Jazz palette: I, ii, V, vi — emphasises the ii–V relationship;
   * melody-driven with ii→V→I cadential phrases.
   */
  JAZZ_SHELL {
    private static final int[] MAJ_PALETTE = {0, 1, 4, 5};  // I ii V vi
    private static final int[] MIN_PALETTE = {0, 1, 4, 5};  // i ii° v VI

    @Override
    public List<ChordSlot> generateChords(
        List<Note> melody, KeySignature key, SentimentProfile sentiment,
        long declaredTotalTicks, int numSlots) {
      return generateMelodyAware(
          key.minor() ? MIN_PALETTE : MAJ_PALETTE,
          melody, key, declaredTotalTicks, numSlots);
    }
  },

  /**
   * Blues/implied palette: all seven diatonic degrees — maximally flexible;
   * selects the chord whose tones best match the melody at each bar.
   */
  IMPLIED_HARMONY {
    private static final int[] ALL_PALETTE = {0, 1, 2, 3, 4, 5, 6};

    @Override
    public List<ChordSlot> generateChords(
        List<Note> melody, KeySignature key, SentimentProfile sentiment,
        long declaredTotalTicks, int numSlots) {
      return generateMelodyAware(ALL_PALETTE, melody, key, declaredTotalTicks, numSlots);
    }
  };

  // -----------------------------------------------------------------------
  // Abstract method
  // -----------------------------------------------------------------------

  /**
   * Generates chord slots covering the melodic timeline.
   *
   * @param melody              all melody notes in time order
   * @param key                 key signature of the piece
   * @param sentiment           sentiment profile (may influence chord quality)
   * @param declaredTotalTicks  declared sentence duration (phrases × bars × ticks-per-bar)
   * @param numSlots            number of chord slots to generate (controls harmonic rhythm)
   * @return list of chord slots, never null
   */
  public abstract List<ChordSlot> generateChords(
      List<Note> melody, KeySignature key, SentimentProfile sentiment,
      long declaredTotalTicks, int numSlots);

  /**
   * Backward-compatible 4-argument overload. Uses 4 slots.
   */
  public final List<ChordSlot> generateChords(
      List<Note> melody, KeySignature key, SentimentProfile sentiment, long declaredTotalTicks) {
    return generateChords(melody, key, sentiment, declaredTotalTicks, 4);
  }

  /**
   * Backward-compatible 3-argument overload. Derives total ticks from the note list.
   */
  public final List<ChordSlot> generateChords(
      List<Note> melody, KeySignature key, SentimentProfile sentiment) {
    long totalTicks = melody.stream().mapToLong(Note::endTick).max().orElse(0);
    return generateChords(melody, key, sentiment, totalTicks, 4);
  }

  // -----------------------------------------------------------------------
  // Shared helpers (package-private for testability)
  // -----------------------------------------------------------------------

  /**
   * Generates chord slots from a Roman-numeral pattern (0-based scale-degree indices).
   * The pattern cycles to fill {@code numSlots}.
   */
  static List<ChordSlot> generateFromPattern(
      int[] scaleDegreePattern, KeySignature key,
      long declaredTotalTicks, int numSlots) {

    int[] scale = key.scaleDegrees();
    int[][] triads = buildDiatonicTriads(scale);
    long slotSize = computeSlotSize(declaredTotalTicks, numSlots);

    List<ChordSlot> slots = new ArrayList<>();
    for (int i = 0; i < numSlots; i++) {
      long start = i * slotSize;
      if (start >= declaredTotalTicks) break;
      int degree = scaleDegreePattern[i % scaleDegreePattern.length];
      slots.add(new ChordSlot(start, slotSize, triadToPitches(triads[degree])));
    }
    return slots;
  }

  /** Divides the total tick span evenly into {@code count} slots. */
  static long computeSlotSize(long totalTicks, int count) {
    return Math.max(1, totalTicks / count);
  }

  /**
   * Builds diatonic triads from the 7 scale degrees: each triad is
   * [root_pc, third_pc, fifth_pc] (pitch classes, all mod 12).
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
   * Melody-aware chord generator: for each slot selects the diatonic chord
   * (from {@code palette}) whose tones best match the strong-beat melody note.
   *
   * <p>Cadence rule: the second-to-last slot of every 4-bar phrase is forced
   * to the dominant (degree 4 = V/v), and the final slot to the tonic (degree 0 = I/i).
   * This ensures every phrase resolves, regardless of the melody at those positions.
   */
  static List<ChordSlot> generateMelodyAware(
      int[] palette, List<Note> melody, KeySignature key,
      long declaredTotalTicks, int numSlots) {

    int[] scale = key.scaleDegrees();
    int[][] triads = buildDiatonicTriads(scale);
    long slotSize = computeSlotSize(declaredTotalTicks, numSlots);
    int slotsPerPhrase = 4;

    List<ChordSlot> slots = new ArrayList<>();
    int prevDegree = 0;

    for (int i = 0; i < numSlots; i++) {
      long start = i * slotSize;
      if (start >= declaredTotalTicks) break;

      int posInPhrase = i % slotsPerPhrase;
      int degree;

      Note strongBeat = findStrongBeatNote(melody, start, start + slotSize);
      int targetPc = strongBeat != null ? ((strongBeat.pitch() % 12) + 12) % 12 : -1;

      if (posInPhrase == slotsPerPhrase - 1) {
        // Phrase final: tonic when melody fits, otherwise best melody-aware degree.
        if (targetPc < 0 || chordContainsPc(triads[0], targetPc)) {
          degree = 0;
        } else {
          degree = bestContainingDegree(triads, palette, targetPc, prevDegree);
        }
      } else if (posInPhrase == slotsPerPhrase - 2) {
        // Pre-cadence: dominant when melody fits, otherwise best melody-aware degree.
        if (targetPc < 0 || chordContainsPc(triads[4], targetPc)) {
          degree = 4;
        } else {
          degree = bestContainingDegree(triads, palette, targetPc, prevDegree);
        }
      } else {
        if (targetPc >= 0) {
          degree = bestContainingDegree(triads, palette, targetPc, prevDegree);
        } else {
          degree = prevDegree;
        }
      }

      slots.add(new ChordSlot(start, slotSize, triadToPitches(triads[degree])));
      prevDegree = degree;
    }
    return slots;
  }

  /**
   * Returns the degree from {@code palette} whose triad contains {@code targetPc}.
   * Among containing chords, prefers the one closest to {@code prevDegree} for
   * smooth voice-leading. Falls back to maximum consonance score if none contain it.
   */
  static int bestContainingDegree(int[][] triads, int[] palette, int targetPc, int prevDegree) {
    int bestContaining = -1;
    int bestDist = Integer.MAX_VALUE;
    for (int d : palette) {
      for (int pc : triads[d]) {
        if (pc == targetPc) {
          int dist = Math.abs(d - prevDegree);
          if (dist < bestDist) {
            bestDist = dist;
            bestContaining = d;
          }
          break;
        }
      }
    }
    if (bestContaining >= 0) return bestContaining;

    // No chord contains the melody note exactly — pick by consonance score
    int best = palette[0];
    int bestScore = -1;
    for (int d : palette) {
      int score = consonanceScore(triads[d], targetPc);
      if (score > bestScore) {
        bestScore = score;
        best = d;
      }
    }
    return best;
  }

  // Kept for tests that call the old helper methods
  static Note findStrongBeatNote(List<Note> melody, long start, long end) {
    for (Note n : melody) {
      if (n.isRest()) continue;
      if (n.startTick() >= start && n.startTick() < end) return n;
    }
    return null;
  }

  static boolean chordContainsPc(int[] triad, int pc) {
    for (int p : triad) {
      if (p == pc) return true;
    }
    return false;
  }

  static int consonanceScore(int[] triad, int targetPc) {
    int score = 0;
    for (int pc : triad) {
      if (pc == targetPc) score += 2;
      else if ((Math.abs(pc - targetPc) % 12) == 5
            || (Math.abs(pc - targetPc) % 12) == 7) score++;
    }
    return score;
  }

  static int[] chooseBestTriad(int[][] triads, int targetPc, List<Integer> prevChord) {
    int[] best = triads[0];
    int bestContainment = -1;
    for (int[] triad : triads) {
      int containment = consonanceScore(triad, targetPc);
      if (containment > bestContainment) {
        bestContainment = containment;
        best = triad;
      }
    }
    return best;
  }
}
