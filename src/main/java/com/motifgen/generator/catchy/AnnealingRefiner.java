package com.motifgen.generator.catchy;

import com.motifgen.model.Motif;
import com.motifgen.model.Note;
import com.motifgen.model.Sentence;
import com.motifgen.scoring.SentenceScorer;
import com.motifgen.sentiment.SentimentProfile;
import com.motifgen.theory.KeySignature;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Simulated-annealing refiner that nudges a generated sentence toward a higher
 * catchiness score. Each iteration:
 *
 * <ol>
 *   <li>Scores the current sentence and identifies the weakest factor.</li>
 *   <li>Applies a targeted mutation selected by the weakest factor.</li>
 *   <li>Accepts the new sentence if it improves the score, or probabilistically
 *       otherwise (Metropolis criterion with cooling temperature).</li>
 *   <li>Tracks the global best separately from the search state.</li>
 * </ol>
 */
public final class AnnealingRefiner {

  private static final double INITIAL_TEMPERATURE = 1.0;
  private static final double COOLING_RATE = 0.92;

  private final long seed;
  private final int maxIterations;
  private final SentenceScorer scorer = new SentenceScorer();

  public AnnealingRefiner(long seed, int maxIterations) {
    if (maxIterations < 0) {
      throw new IllegalArgumentException("maxIterations must be >= 0");
    }
    this.seed = seed;
    this.maxIterations = maxIterations;
  }

  public Sentence refine(Sentence initial, Motif seedMotif, KeySignature key) {
    return refine(initial, seedMotif, key, Collections.emptySet());
  }

  /**
   * Sentiment-aware overload. When {@code profile} has high arousal the rhythm
   * mutation palette is biased toward shorter note durations.
   */
  public Sentence refine(Sentence initial, Motif seedMotif, KeySignature key,
      SentimentProfile profile) {
    return refine(initial, seedMotif, key, Collections.emptySet(), profile);
  }

  public Sentence refine(Sentence initial, Motif seedMotif, KeySignature key,
      Set<Integer> immutableIndices) {
    return refine(initial, seedMotif, key, immutableIndices, null);
  }

  public Sentence refine(Sentence initial, Motif seedMotif, KeySignature key,
      Set<Integer> immutableIndices, SentimentProfile profile) {
    Random rng = new Random(seed);

    List<Integer> mutableIndices = new ArrayList<>();
    for (int i = 0; i < initial.getPhrases().size(); i++) {
      if (!immutableIndices.contains(i)) mutableIndices.add(i);
    }

    Sentence current = scorer.score(initial);
    Sentence best = current;
    if (mutableIndices.isEmpty()) {
      return best;
    }
    double temperature = INITIAL_TEMPERATURE;

    for (int i = 0; i < maxIterations; i++) {
      Weakest weakest = identifyWeakest(scorer.breakdown(current));
      Sentence candidate = scorer.score(mutate(current, weakest, key, rng, mutableIndices, profile));

      double delta = candidate.getScore() - current.getScore();
      if (delta > 0 || rng.nextDouble() < Math.exp(delta / Math.max(1e-6, temperature))) {
        current = candidate;
      }
      if (current.getScore() > best.getScore()) {
        best = current;
      }
      temperature *= COOLING_RATE;
    }

    return best;
  }

  private Sentence mutate(Sentence sentence, Weakest weakest, KeySignature key,
      Random rng, List<Integer> mutableIndices, SentimentProfile profile) {
    List<Motif> phrases = new ArrayList<>(sentence.getPhrases());
    int phraseIdx = mutableIndices.get(rng.nextInt(mutableIndices.size()));
    Motif phrase = phrases.get(phraseIdx);
    List<Note> notes = new ArrayList<>(phrase.getNotes());
    List<Integer> soundingIdx = new ArrayList<>();
    for (int i = 0; i < notes.size(); i++) {
      if (!notes.get(i).isRest()) soundingIdx.add(i);
    }
    if (soundingIdx.isEmpty()) return sentence;

    switch (weakest) {
      case REPETITION -> applyRepetitionMutation(phrases, phraseIdx, notes, soundingIdx, rng);
      case CONTOUR -> applyContourMutation(notes, soundingIdx, key, rng);
      case COMPACTNESS -> applyCompactnessMutation(notes, soundingIdx);
      case RHYTHM -> applyRhythmMutation(notes, soundingIdx, rng, profile);
      case CONVENTIONALITY -> applyConventionalityMutation(notes, soundingIdx, key, rng);
      case HOOK -> applyHookMutation(notes, soundingIdx, rng);
    }

    phrases.set(phraseIdx, new Motif(notes, phrase.getBars(),
        phrase.getBeatsPerBar(), phrase.getTicksPerBeat()));
    return new Sentence(phrases, sentence.getStructure(), sentence.getKeyName(), 0);
  }

  private void applyRepetitionMutation(List<Motif> phrases, int phraseIdx,
      List<Note> notes, List<Integer> soundingIdx, Random rng) {
    if (phrases.size() < 2 || soundingIdx.size() < 3) return;
    int sourceIdx = (phraseIdx + 1) % phrases.size();
    List<Note> source = phrases.get(sourceIdx).getNotes().stream()
        .filter(n -> !n.isRest()).toList();
    if (source.size() < 3) return;
    int fragStart = rng.nextInt(source.size() - 2);
    int targetStart = rng.nextInt(soundingIdx.size() - 2);
    for (int k = 0; k < 3; k++) {
      int dst = soundingIdx.get(targetStart + k);
      Note orig = notes.get(dst);
      notes.set(dst, new Note(source.get(fragStart + k).pitch(),
          orig.startTick(), orig.durationTicks(), orig.velocity()));
    }
  }

  private void applyContourMutation(List<Note> notes, List<Integer> soundingIdx,
      KeySignature key, Random rng) {
    if (soundingIdx.size() < 3) return;
    int pick = 1 + rng.nextInt(soundingIdx.size() - 2);
    int prevPitch = notes.get(soundingIdx.get(pick - 1)).pitch();
    int nextPitch = notes.get(soundingIdx.get(pick + 1)).pitch();
    int smoothed = nearestInKey((prevPitch + nextPitch) / 2, key);
    int idx = soundingIdx.get(pick);
    Note orig = notes.get(idx);
    notes.set(idx, new Note(smoothed, orig.startTick(),
        orig.durationTicks(), orig.velocity()));
  }

  private void applyCompactnessMutation(List<Note> notes, List<Integer> soundingIdx) {
    int mean = 0;
    for (int idx : soundingIdx) mean += notes.get(idx).pitch();
    mean /= soundingIdx.size();

    int worstIdx = soundingIdx.get(0);
    int worstDistance = 0;
    for (int idx : soundingIdx) {
      int d = Math.abs(notes.get(idx).pitch() - mean);
      if (d > worstDistance) {
        worstDistance = d;
        worstIdx = idx;
      }
    }
    Note target = notes.get(worstIdx);
    int shift = target.pitch() > mean ? -12 : 12;
    int newPitch = Math.max(0, Math.min(127, target.pitch() + shift));
    notes.set(worstIdx, new Note(newPitch, target.startTick(),
        target.durationTicks(), target.velocity()));
  }

  private void applyRhythmMutation(List<Note> notes, List<Integer> soundingIdx,
      Random rng, SentimentProfile profile) {
    long ticksPerBeat = inferTicksPerBeat(notes, soundingIdx);
    long quarter = Math.max(1L, ticksPerBeat);
    long eighth  = Math.max(1L, ticksPerBeat / 2L);
    long half    = ticksPerBeat * 2L;
    long[] palette = {eighth, quarter, half};

    Map<Long, Integer> counts = new HashMap<>();
    for (int idx : soundingIdx) {
      counts.merge(notes.get(idx).durationTicks(), 1, Integer::sum);
    }
    long modal = counts.entrySet().stream()
        .max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(quarter);

    // High arousal biases toward shorter durations: pick eighth note directly
    // when arousal >= 0.7 and the modal duration is not already eighth.
    long target;
    if (profile != null && profile.arousal() >= 0.7 && modal != eighth) {
      target = eighth;
    } else {
      target = palette[0];
      int targetCount = Integer.MAX_VALUE;
      for (long candidate : palette) {
        if (candidate == modal) continue;
        int c = counts.getOrDefault(candidate, 0);
        if (c < targetCount) {
          targetCount = c;
          target = candidate;
        }
      }
    }

    int pick = soundingIdx.get(rng.nextInt(soundingIdx.size()));
    Note n = notes.get(pick);
    if (n.durationTicks() != target) {
      notes.set(pick, new Note(n.pitch(), n.startTick(), target, n.velocity()));
    }
  }

  private static long inferTicksPerBeat(List<Note> notes, List<Integer> soundingIdx) {
    if (soundingIdx.isEmpty()) return 480L;
    long sum = 0L;
    for (int idx : soundingIdx) sum += notes.get(idx).durationTicks();
    long avg = Math.max(1L, sum / soundingIdx.size());
    long[] candidates = {120L, 240L, 480L, 960L};
    long best = 480L;
    long bestDiff = Long.MAX_VALUE;
    for (long c : candidates) {
      long diff = Math.abs(c - avg);
      if (diff < bestDiff) {
        bestDiff = diff;
        best = c;
      }
    }
    return best;
  }

  private void applyConventionalityMutation(List<Note> notes, List<Integer> soundingIdx,
      KeySignature key, Random rng) {
    if (soundingIdx.size() < 2) return;
    for (int k = 1; k < soundingIdx.size(); k++) {
      int prev = notes.get(soundingIdx.get(k - 1)).pitch();
      int here = notes.get(soundingIdx.get(k)).pitch();
      if (Math.abs(here - prev) > 7) {
        int stepped = prev + (here > prev ? 2 : -2);
        int inKey = nearestInKey(stepped, key);
        int idx = soundingIdx.get(k);
        Note orig = notes.get(idx);
        notes.set(idx, new Note(inKey, orig.startTick(),
            orig.durationTicks(), orig.velocity()));
        return;
      }
    }
  }

  private void applyHookMutation(List<Note> notes, List<Integer> soundingIdx, Random rng) {
    if (soundingIdx.size() < 4) return;
    Map<String, int[]> patternAt = new HashMap<>();
    Map<String, Integer> patternCount = new HashMap<>();
    for (int k = 0; k <= soundingIdx.size() - 2; k++) {
      int a = notes.get(soundingIdx.get(k)).pitchClass();
      int b = notes.get(soundingIdx.get(k + 1)).pitchClass();
      String key = a + "," + b;
      patternCount.merge(key, 1, Integer::sum);
      patternAt.putIfAbsent(key, new int[] {k, k + 1});
    }
    String topKey = patternCount.entrySet().stream()
        .max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
    if (topKey == null) return;
    int[] srcIdx = patternAt.get(topKey);
    int destStart = rng.nextInt(Math.max(1, soundingIdx.size() - 1));
    if (destStart + 1 >= soundingIdx.size()) return;

    Note srcA = notes.get(soundingIdx.get(srcIdx[0]));
    Note srcB = notes.get(soundingIdx.get(srcIdx[1]));
    int dstIdxA = soundingIdx.get(destStart);
    int dstIdxB = soundingIdx.get(destStart + 1);
    Note dstA = notes.get(dstIdxA);
    Note dstB = notes.get(dstIdxB);
    notes.set(dstIdxA, new Note(srcA.pitch(), dstA.startTick(),
        dstA.durationTicks(), dstA.velocity()));
    notes.set(dstIdxB, new Note(srcB.pitch(), dstB.startTick(),
        dstB.durationTicks(), dstB.velocity()));
  }

  private static int nearestInKey(int pitch, KeySignature key) {
    int clamped = Math.max(0, Math.min(127, pitch));
    for (int d = 0; d <= 6; d++) {
      int up = clamped + d;
      if (up <= 127 && key.containsPitchClass(((up % 12) + 12) % 12)) return up;
      int down = clamped - d;
      if (down >= 0 && key.containsPitchClass(((down % 12) + 12) % 12)) return down;
    }
    return clamped;
  }

  private static Weakest identifyWeakest(SentenceScorer.ScoreBreakdown bd) {
    double[] factors = {bd.repetition(), bd.contourPredictability(),
        bd.pitchRangeCompactness(), bd.rhythmicSimplicity(),
        bd.internalConventionality(), bd.hookProminence(),
        bd.rhythmicVariety()};
    Weakest[] names = {Weakest.REPETITION, Weakest.CONTOUR, Weakest.COMPACTNESS,
        Weakest.RHYTHM, Weakest.CONVENTIONALITY, Weakest.HOOK,
        Weakest.RHYTHM};
    int worst = 0;
    for (int i = 1; i < factors.length; i++) {
      if (factors[i] < factors[worst]) worst = i;
    }
    return names[worst];
  }

  private enum Weakest {
    REPETITION, CONTOUR, COMPACTNESS, RHYTHM, CONVENTIONALITY, HOOK
  }
}
