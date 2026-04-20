package com.motifgen.scoring;

import com.motifgen.model.Motif;
import com.motifgen.model.Note;
import com.motifgen.model.Sentence;

import java.util.*;

/**
 * Scores sentence candidates based on catchiness factors:
 * - Repetition: how much melodic/rhythmic material recurs
 * - Contour Predictability: Huron's arch shape and gap-fill principle
 * - Pitch Range Compactness: staying within a singable range
 * - Rhythmic Simplicity: simple, regular rhythmic patterns
 * - Internal Conventionality: conforming to common melodic intervals
 * - Hook Prominence: presence of a dominant short repeated motif
 *
 * Produces a score from 0-100.
 */
public class SentenceScorer {

  private static final double WEIGHT_REPETITION = 0.25;
  private static final double WEIGHT_CONTOUR = 0.15;
  private static final double WEIGHT_COMPACTNESS = 0.15;
  private static final double WEIGHT_RHYTHM = 0.15;
  private static final double WEIGHT_CONVENTIONALITY = 0.15;
  private static final double WEIGHT_HOOK = 0.15;

  public record ScoreBreakdown(
      double repetition,
      double contourPredictability,
      double pitchRangeCompactness,
      double rhythmicSimplicity,
      double internalConventionality,
      double hookProminence,
      double total
  ) {}

  /**
   * Score a sentence and return it with the computed score (0-100).
   */
  public Sentence score(Sentence sentence) {
    ScoreBreakdown breakdown = breakdown(sentence);
    return sentence.withScore(breakdown.total());
  }

  /**
   * Score all candidates and return them sorted best-first.
   */
  public List<Sentence> scoreAndRank(List<Sentence> candidates) {
    return candidates.stream()
        .map(this::score)
        .sorted(Comparator.comparingDouble(Sentence::getScore).reversed())
        .toList();
  }

  /**
   * Get a detailed score breakdown.
   */
  public ScoreBreakdown breakdown(Sentence sentence) {
    List<Note> notes = sentence.getAllNotes();
    List<Note> pitched = notes.stream().filter(n -> !n.isRest()).toList();
    if (pitched.isEmpty()) {
      return new ScoreBreakdown(0, 0, 0, 0, 0, 0, 0);
    }

    double repetition = scoreRepetition(sentence.getPhrases(), pitched);
    double contour = scoreContourPredictability(pitched);
    double compactness = scorePitchRangeCompactness(pitched);
    double rhythm = scoreRhythmicSimplicity(notes, sentence);
    double conventionality = scoreInternalConventionality(pitched);
    double hook = scoreHookProminence(pitched);

    double total = (repetition * WEIGHT_REPETITION
        + contour * WEIGHT_CONTOUR
        + compactness * WEIGHT_COMPACTNESS
        + rhythm * WEIGHT_RHYTHM
        + conventionality * WEIGHT_CONVENTIONALITY
        + hook * WEIGHT_HOOK) * 100;

    return new ScoreBreakdown(repetition, contour, compactness, rhythm,
        conventionality, hook, total);
  }

  /**
   * Returns a descriptive label for a given score.
   */
  public static String bandLabel(double score) {
    if (score >= 90) return "Rare - suspiciously sticky";
    if (score >= 75) return "Hook driven. Earworm.";
    if (score >= 55) return "Solid pop/folk territory - memorable on repeated listens";
    if (score >= 30) return "Coherent but unremarkable";
    return "Atonal, meandering - Hard to remember";
  }

  // ---------- Factor 1: Repetition (0.25) ----------

  /**
   * Measures how much melodic and rhythmic material recurs.
   * Compares phrases pairwise by pitch-class intervals and rhythm patterns,
   * plus detects repeated n-grams across the full melody.
   */
  private double scoreRepetition(List<Motif> phrases, List<Note> pitched) {
    // Phrase-level similarity (pairwise comparison)
    double phraseSimilarity = scorePhraseRepetition(phrases);

    // N-gram repetition across the full melody
    double ngramRepetition = scoreNgramRepetition(pitched);

    return clamp(0.6 * phraseSimilarity + 0.4 * ngramRepetition);
  }

  private double scorePhraseRepetition(List<Motif> phrases) {
    if (phrases.size() < 2) return 0;

    List<List<Integer>> phraseIntervals = new ArrayList<>();
    for (Motif phrase : phrases) {
      List<Note> pNotes = phrase.getNotes().stream()
          .filter(n -> !n.isRest()).toList();
      phraseIntervals.add(extractIntervals(pNotes));
    }

    double totalSim = 0;
    int pairs = 0;
    for (int i = 0; i < phraseIntervals.size(); i++) {
      for (int j = i + 1; j < phraseIntervals.size(); j++) {
        totalSim += listSimilarity(phraseIntervals.get(i), phraseIntervals.get(j));
        pairs++;
      }
    }

    return pairs > 0 ? totalSim / pairs : 0;
  }

  private List<Integer> extractIntervals(List<Note> pitched) {
    List<Integer> intervals = new ArrayList<>();
    for (int i = 1; i < pitched.size(); i++) {
      intervals.add(pitched.get(i).pitch() - pitched.get(i - 1).pitch());
    }
    return intervals;
  }

  private double listSimilarity(List<Integer> a, List<Integer> b) {
    if (a.isEmpty() && b.isEmpty()) return 1.0;
    if (a.isEmpty() || b.isEmpty()) return 0.0;

    int maxLen = Math.max(a.size(), b.size());
    int minLen = Math.min(a.size(), b.size());
    int matches = 0;

    for (int i = 0; i < minLen; i++) {
      if (a.get(i).equals(b.get(i))) {
        matches++;
      } else if (Math.abs(a.get(i) - b.get(i)) <= 1) {
        matches++; // near-match counts as partial
      }
    }

    return (double) matches / maxLen;
  }

  private double scoreNgramRepetition(List<Note> pitched) {
    if (pitched.size() < 4) return 0;

    // Extract pitch-class sequences
    List<Integer> pitchClasses = pitched.stream()
        .map(Note::pitchClass).toList();

    int bestCoverage = 0;

    // Try n-gram sizes 2, 3, 4
    for (int n = 2; n <= 4 && n <= pitchClasses.size(); n++) {
      Map<List<Integer>, Integer> ngramCounts = new HashMap<>();
      for (int i = 0; i <= pitchClasses.size() - n; i++) {
        List<Integer> ngram = pitchClasses.subList(i, i + n);
        ngramCounts.merge(ngram, 1, Integer::sum);
      }

      int maxCount = ngramCounts.values().stream()
          .mapToInt(Integer::intValue).max().orElse(0);
      int coverage = maxCount * n;
      bestCoverage = Math.max(bestCoverage, coverage);
    }

    return clamp((double) bestCoverage / pitched.size());
  }

  // ---------- Factor 2: Contour Predictability (0.15) ----------

  /**
   * Huron's arch shape (rise then fall) + gap-fill principle
   * (large leaps followed by stepwise reversal).
   */
  private double scoreContourPredictability(List<Note> pitched) {
    double arch = scoreArchShape(pitched);
    double gapFill = scoreGapFill(pitched);
    return clamp(0.5 * arch + 0.5 * gapFill);
  }

  private double scoreArchShape(List<Note> pitched) {
    if (pitched.size() < 3) return 0.5;

    // Compute normalized position and pitch for correlation with arch
    int minPitch = pitched.stream().mapToInt(Note::pitch).min().orElse(0);
    int maxPitch = pitched.stream().mapToInt(Note::pitch).max().orElse(127);
    int pitchRange = maxPitch - minPitch;
    if (pitchRange == 0) return 0.5;

    double sumProduct = 0;
    double sumArchSq = 0;
    double sumPitchSq = 0;

    for (int i = 0; i < pitched.size(); i++) {
      double t = (double) i / (pitched.size() - 1); // 0 to 1
      // Ideal arch: peaks at 0.5
      double idealArch = 4 * t * (1 - t); // parabola peaking at 1.0 when t=0.5
      double normalizedPitch = (double) (pitched.get(i).pitch() - minPitch) / pitchRange;

      // Center both
      double archCentered = idealArch - 0.667; // mean of 4t(1-t) over [0,1] = 2/3
      double pitchCentered = normalizedPitch - 0.5;

      sumProduct += archCentered * pitchCentered;
      sumArchSq += archCentered * archCentered;
      sumPitchSq += pitchCentered * pitchCentered;
    }

    if (sumArchSq == 0 || sumPitchSq == 0) return 0.5;

    double correlation = sumProduct / Math.sqrt(sumArchSq * sumPitchSq);
    // Correlation ranges -1 to 1; map to 0-1
    return clamp((correlation + 1) / 2);
  }

  private double scoreGapFill(List<Note> pitched) {
    if (pitched.size() < 3) return 0.5;

    int gaps = 0;
    int filled = 0;
    int stepwise = 0;
    int totalIntervals = pitched.size() - 1;

    for (int i = 1; i < pitched.size(); i++) {
      int interval = pitched.get(i).pitch() - pitched.get(i - 1).pitch();

      if (Math.abs(interval) <= 2) {
        stepwise++;
      }

      if (Math.abs(interval) > 4 && i < pitched.size() - 1) { // large leap
        gaps++;
        // Check if next note(s) move stepwise in opposite direction
        int nextInterval = pitched.get(i + 1).pitch() - pitched.get(i).pitch();
        boolean oppositeDirection = (interval > 0 && nextInterval < 0)
            || (interval < 0 && nextInterval > 0);
        boolean isStepwise = Math.abs(nextInterval) <= 2;
        if (oppositeDirection && isStepwise) {
          filled++;
        }
      }
    }

    // Combine gap-fill ratio with overall stepwise motion ratio
    double gapFillScore = gaps == 0 ? 0.8 : (double) filled / gaps;
    double stepwiseRatio = (double) stepwise / totalIntervals;

    return clamp(0.5 * gapFillScore + 0.5 * stepwiseRatio);
  }

  // ---------- Factor 3: Pitch Range Compactness (0.15) ----------

  /**
   * Rewards melodies within a singable, compact range.
   * Sweet spot: 7-19 semitones (a fifth to an octave+fifth).
   * Gentle decay beyond that — generated sentences often span 2+ octaves
   * due to transformations like inversion and climax building.
   */
  private double scorePitchRangeCompactness(List<Note> pitched) {
    int min = pitched.stream().mapToInt(Note::pitch).min().orElse(60);
    int max = pitched.stream().mapToInt(Note::pitch).max().orElse(72);
    int range = max - min;

    if (range < 5) {
      // Too narrow — penalize
      return 0.3 + (range / 5.0) * 0.3;
    }
    if (range <= 19) {
      // Sweet spot: compact singable range (up to octave+fifth)
      return 1.0;
    }
    if (range <= 36) {
      // Gradual decay from 1.0 at 19 to 0.1 at 36 (3 octaves)
      return 1.0 - 0.9 * (double) (range - 19) / 17.0;
    }
    return 0.1;
  }

  // ---------- Factor 4: Rhythmic Simplicity (0.15) ----------

  /**
   * Favors simple, regular rhythmic patterns.
   * Combines on-beat ratio with inverse duration entropy.
   */
  private double scoreRhythmicSimplicity(List<Note> notes, Sentence sentence) {
    List<Note> sounding = notes.stream().filter(n -> !n.isRest()).toList();
    if (sounding.isEmpty()) return 0;

    int ticksPerBeat = getTicksPerBeat(sentence);
    double onBeat = scoreOnBeatRatio(sounding, ticksPerBeat);
    double simpleRhythm = scoreInverseDurationEntropy(sounding);

    return clamp(0.5 * onBeat + 0.5 * simpleRhythm);
  }

  private int getTicksPerBeat(Sentence sentence) {
    if (!sentence.getPhrases().isEmpty()) {
      return sentence.getPhrases().getFirst().getTicksPerBeat();
    }
    return 480;
  }

  private double scoreOnBeatRatio(List<Note> sounding, int ticksPerBeat) {
    if (ticksPerBeat == 0) return 0.5;

    long onBeat = sounding.stream()
        .filter(n -> n.startTick() % ticksPerBeat == 0)
        .count();

    return (double) onBeat / sounding.size();
  }

  private double scoreInverseDurationEntropy(List<Note> sounding) {
    Map<Long, Integer> durationCounts = new HashMap<>();
    for (Note n : sounding) {
      // Quantize to nearest 16th note equivalent
      long quantized = (n.durationTicks() / 60) * 60;
      durationCounts.merge(quantized, 1, Integer::sum);
    }

    if (durationCounts.size() <= 1) return 1.0; // single duration = maximally simple

    double total = durationCounts.values().stream().mapToInt(Integer::intValue).sum();
    double entropy = 0;
    for (int count : durationCounts.values()) {
      double p = count / total;
      if (p > 0) entropy -= p * Math.log(p);
    }

    double maxEntropy = Math.log(durationCounts.size());
    double normalized = maxEntropy > 0 ? entropy / maxEntropy : 0;

    return 1.0 - normalized; // invert: low entropy = high simplicity
  }

  // ---------- Factor 5: Internal Conventionality (0.15) ----------

  /**
   * Measures how well the melody conforms to common interval patterns.
   * Compares interval distribution against an ideal "catchy" distribution.
   */
  private double scoreInternalConventionality(List<Note> pitched) {
    if (pitched.size() < 2) return 0.5;

    // Build interval histogram (absolute intervals, in semitones)
    double[] bins = new double[5]; // [unison, second, third, fourth/fifth, larger]
    int totalIntervals = pitched.size() - 1;

    for (int i = 1; i < pitched.size(); i++) {
      int interval = Math.abs(pitched.get(i).pitch() - pitched.get(i - 1).pitch());
      if (interval == 0) bins[0]++;
      else if (interval <= 2) bins[1]++;
      else if (interval <= 4) bins[2]++;
      else if (interval <= 7) bins[3]++;
      else bins[4]++;
    }

    // Normalize to proportions
    for (int i = 0; i < bins.length; i++) {
      bins[i] /= totalIntervals;
    }

    // Ideal catchy distribution:
    // unisons ~15%, seconds ~55%, thirds ~15%, 4ths/5ths ~10%, larger ~5%
    double[] ideal = {0.15, 0.55, 0.15, 0.10, 0.05};

    // Cosine similarity
    double dotProduct = 0, normA = 0, normB = 0;
    for (int i = 0; i < bins.length; i++) {
      dotProduct += bins[i] * ideal[i];
      normA += bins[i] * bins[i];
      normB += ideal[i] * ideal[i];
    }

    if (normA == 0) return 0;
    return clamp(dotProduct / (Math.sqrt(normA) * Math.sqrt(normB)));
  }

  // ---------- Factor 6: Hook Prominence (0.15) ----------

  /**
   * Measures dominance of the most-repeated short (2-4 note) pitch+rhythm pattern.
   */
  private double scoreHookProminence(List<Note> pitched) {
    if (pitched.size() < 4) return 0;

    int bestCoverage = 0;

    for (int n = 2; n <= 4 && n <= pitched.size(); n++) {
      Map<String, Integer> patternCounts = new HashMap<>();
      for (int i = 0; i <= pitched.size() - n; i++) {
        StringBuilder key = new StringBuilder();
        for (int j = 0; j < n; j++) {
          Note note = pitched.get(i + j);
          int pc = note.pitchClass();
          // Quantize duration to nearest 16th
          long qDur = (note.durationTicks() / 120) * 120;
          if (j > 0) key.append(",");
          key.append(pc).append(":").append(qDur);
        }
        patternCounts.merge(key.toString(), 1, Integer::sum);
      }

      int maxCount = patternCounts.values().stream()
          .mapToInt(Integer::intValue).max().orElse(0);
      int coverage = maxCount * n;
      bestCoverage = Math.max(bestCoverage, coverage);
    }

    return clamp((double) bestCoverage / pitched.size());
  }

  // ---------- Utility ----------

  private static double clamp(double value) {
    return Math.max(0, Math.min(1.0, value));
  }
}
