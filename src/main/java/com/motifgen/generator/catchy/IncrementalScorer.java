package com.motifgen.generator.catchy;

import com.motifgen.model.Note;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Cheap partial catchiness scorer used inside beam search. Evaluates a
 * partial phrase on three inexpensive factors:
 *
 * <ul>
 *   <li>Pitch range compactness — singability window</li>
 *   <li>Stepwise motion ratio — surrogate for conventionality</li>
 *   <li>N-gram repetition — surrogate for hook prominence</li>
 * </ul>
 *
 * <p>Returns a value in {@code [0, 1]}. Not a replacement for the full
 * {@code SentenceScorer}, which is still authoritative for final ranking
 * and for refinement.
 */
public final class IncrementalScorer {

  private static final double COMPACTNESS_WEIGHT = 0.35;
  private static final double STEPWISE_WEIGHT = 0.30;
  private static final double NGRAM_WEIGHT = 0.35;

  public double scorePartial(List<Note> partial) {
    if (partial == null || partial.isEmpty()) {
      return 0.0;
    }

    double compactness = compactness(partial);
    double stepwise = stepwiseRatio(partial);
    double ngram = ngramRepetition(partial);

    double score = compactness * COMPACTNESS_WEIGHT
        + stepwise * STEPWISE_WEIGHT
        + ngram * NGRAM_WEIGHT;

    return Math.max(0.0, Math.min(1.0, score));
  }

  private double compactness(List<Note> notes) {
    int min = Integer.MAX_VALUE;
    int max = Integer.MIN_VALUE;
    for (Note n : notes) {
      if (n.isRest()) continue;
      min = Math.min(min, n.pitch());
      max = Math.max(max, n.pitch());
    }
    if (min > max) return 0.0;

    int range = max - min;
    if (range <= 12) return 1.0;
    if (range <= 19) return 1.0 - 0.2 * (range - 12) / 7.0;
    if (range <= 36) return 0.8 - 0.7 * (range - 19) / 17.0;
    return 0.1;
  }

  private double stepwiseRatio(List<Note> notes) {
    if (notes.size() < 2) return 0.5;
    int stepwise = 0;
    int total = 0;
    for (int i = 1; i < notes.size(); i++) {
      if (notes.get(i).isRest() || notes.get(i - 1).isRest()) continue;
      int interval = Math.abs(notes.get(i).pitch() - notes.get(i - 1).pitch());
      if (interval <= 2) stepwise++;
      total++;
    }
    return total == 0 ? 0.5 : (double) stepwise / total;
  }

  private double ngramRepetition(List<Note> notes) {
    if (notes.size() < 3) return 0.0;
    int best = 0;
    for (int n = 2; n <= 3 && n <= notes.size(); n++) {
      Map<String, Integer> counts = new HashMap<>();
      for (int i = 0; i <= notes.size() - n; i++) {
        StringBuilder key = new StringBuilder();
        for (int j = 0; j < n; j++) {
          if (j > 0) key.append(',');
          key.append(notes.get(i + j).pitchClass());
        }
        counts.merge(key.toString(), 1, Integer::sum);
      }
      int max = counts.values().stream().mapToInt(Integer::intValue).max().orElse(0);
      best = Math.max(best, max * n);
    }
    return Math.min(1.0, (double) best / notes.size());
  }
}
