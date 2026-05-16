package com.motifgen.guitar.backing;

import com.motifgen.model.Note;
import com.motifgen.model.Sentence;
import com.motifgen.scoring.SentenceScorer;

import java.util.List;

/**
 * Scores the catchiness of a backing track relative to the melody.
 *
 * <p>Formula:
 * <pre>
 *   catchiness = 0.40 × base_melody
 *              + 0.25 × rhythmic_interlock
 *              + 0.20 × hook_reinforcement
 *              + 0.15 × harmonic_interest
 *
 *   rhythmic_interlock = 0.5 × interlock + 0.3 × pattern_repetition − 0.2 × busyness_penalty
 * </pre>
 *
 * <p>All component scores are in [0, 1] before the final × 100 normalisation.
 */
public final class BackingCatchinessScorer {

  private static final double W_BASE_MELODY      = 0.40;
  private static final double W_RHYTHMIC_INTERLOCK = 0.25;
  private static final double W_HOOK_REINFORCEMENT  = 0.20;
  private static final double W_HARMONIC_INTEREST   = 0.15;

  private static final double W_INTERLOCK         = 0.5;
  private static final double W_PATTERN_REPETITION = 0.3;
  private static final double W_BUSYNESS_PENALTY   = 0.2;

  /** Maximum distinct chord changes considered fully harmonically interesting. */
  private static final int MAX_DISTINCT_CHANGES = 8;

  private BackingCatchinessScorer() {}

  /**
   * Computes a normalised catchiness score (0–100).
   *
   * @param sentence   the melody sentence
   * @param voiced     voiced backing chords
   * @param strumPat   strum pattern boolean array (true = active strum slot)
   * @param ppq        ticks per quarter note
   * @return score in [0, 100]
   */
  public static double score(
      Sentence sentence, List<VoicedChord> voiced, boolean[] strumPat, int ppq) {

    List<Note> melodyNotes = sentence.getAllNotes();

    double baseMelody      = baseMelodyScore(sentence);
    double rhythmicInterlock = rhythmicInterlock(melodyNotes, strumPat, ppq);
    double hookReinforcement = hookReinforcement(melodyNotes, strumPat, ppq);
    double harmonicInterest  = harmonicInterest(voiced);

    double raw = W_BASE_MELODY * baseMelody
        + W_RHYTHMIC_INTERLOCK * rhythmicInterlock
        + W_HOOK_REINFORCEMENT * hookReinforcement
        + W_HARMONIC_INTEREST  * harmonicInterest;

    return Math.max(0.0, Math.min(100.0, raw * 100.0));
  }

  // -----------------------------------------------------------------------
  // Component scorers
  // -----------------------------------------------------------------------

  /** Delegates to SentenceScorer and normalises to [0, 1]. */
  private static double baseMelodyScore(Sentence sentence) {
    SentenceScorer scorer = new SentenceScorer();
    double total = scorer.breakdown(sentence).total(); // 0–100
    return total / 100.0;
  }

  /**
   * Rhythmic interlock: how well the backing strum offsets complement melody onsets.
   * <pre>
   *   0.5 × interlock + 0.3 × pattern_repetition − 0.2 × busyness_penalty
   * </pre>
   */
  private static double rhythmicInterlock(
      List<Note> melodyNotes, boolean[] strumPat, int ppq) {
    if (strumPat.length == 0 || melodyNotes.isEmpty()) return 0.5;

    double interlock = computeInterlock(melodyNotes, strumPat, ppq);
    double patternRepetition = computePatternRepetition(strumPat);
    double busyness = computeBusyness(strumPat);

    double raw = W_INTERLOCK * interlock
        + W_PATTERN_REPETITION * patternRepetition
        - W_BUSYNESS_PENALTY * busyness;
    return Math.max(0.0, Math.min(1.0, raw));
  }

  /**
   * Interlock: fraction of strum slots that do NOT coincide with melody onsets
   * (complementarity — strums fill the gaps).
   */
  private static double computeInterlock(
      List<Note> melodyNotes, boolean[] strumPat, int ppq) {
    int patLen = strumPat.length;
    if (patLen == 0) return 0.5;
    long slotTicks = (long) ppq / 2; // eighth-note slot size

    int activeStrums = 0;
    int complementaryStrums = 0;
    for (int i = 0; i < patLen; i++) {
      if (!strumPat[i]) continue;
      activeStrums++;
      long strumTick = i * slotTicks;
      boolean melodyOnset = melodyNotes.stream()
          .anyMatch(n -> !n.isRest() && Math.abs(n.startTick() - strumTick) < slotTicks / 2);
      if (!melodyOnset) complementaryStrums++;
    }
    return activeStrums == 0 ? 0.5 : (double) complementaryStrums / activeStrums;
  }

  /** Pattern repetition: how regular the strum pattern is (low entropy = high repetition). */
  private static double computePatternRepetition(boolean[] strumPat) {
    // Measure: if first half equals second half → perfect repetition
    int half = strumPat.length / 2;
    if (half == 0) return 0.5;
    int matches = 0;
    for (int i = 0; i < half; i++) {
      if (strumPat[i] == strumPat[i + half]) matches++;
    }
    return (double) matches / half;
  }

  /** Busyness penalty: proportion of active strum slots (more active = busier). */
  private static double computeBusyness(boolean[] strumPat) {
    if (strumPat.length == 0) return 0.0;
    int active = 0;
    for (boolean b : strumPat) if (b) active++;
    return (double) active / strumPat.length;
  }

  /**
   * Hook reinforcement: proportion of strum accents that align with melody notes
   * occurring at strong beat positions (beat 1 and beat 3 in 4/4).
   */
  private static double hookReinforcement(
      List<Note> melodyNotes, boolean[] strumPat, int ppq) {
    if (strumPat.length == 0) return 0.5;
    long slotTicks = (long) ppq / 2;
    // Strong beat slots: 0 and 4 in an 8-slot bar
    int[] strongSlots = {0, 4};
    int aligned = 0;
    int checked = 0;
    for (int slot : strongSlots) {
      if (slot >= strumPat.length) continue;
      checked++;
      if (!strumPat[slot]) continue;
      long strumTick = slot * slotTicks;
      boolean melodyPresent = melodyNotes.stream()
          .anyMatch(n -> !n.isRest() && Math.abs(n.startTick() - strumTick) < slotTicks);
      if (melodyPresent) aligned++;
    }
    return checked == 0 ? 0.5 : (double) aligned / checked;
  }

  /**
   * Harmonic interest: number of distinct chord changes normalised by
   * {@link #MAX_DISTINCT_CHANGES}.
   */
  private static double harmonicInterest(List<VoicedChord> voiced) {
    if (voiced.isEmpty()) return 0.0;
    long distinctChanges = voiced.stream()
        .map(VoicedChord::notes)
        .distinct()
        .count();
    return Math.min(1.0, (double) distinctChanges / MAX_DISTINCT_CHANGES);
  }
}
