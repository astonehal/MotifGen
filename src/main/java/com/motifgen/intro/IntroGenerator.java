package com.motifgen.intro;

import com.motifgen.guitar.backing.ChanneledNote;
import com.motifgen.guitar.backing.DrumEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Facade that generates 3 intro candidates, scores each with {@link IntroScorer}, and returns the
 * best {@link IntroTrack}.
 *
 * <p>Each candidate uses the same {@link IntroEntryPlanner} plan but a different random seed
 * variation (passed via slightly mutated arousal ±0.05) so the three candidates differ in
 * density and timing choices without requiring a separate RNG dependency.
 */
public final class IntroGenerator {

  private static final int NUM_CANDIDATES = 3;
  /** Arousal delta applied to the second and third candidate contexts. */
  private static final double CANDIDATE_DELTA = 0.05;

  private final IntroGuitarBuilder guitarBuilder = new IntroGuitarBuilder();
  private final IntroBassBuilder bassBuilder = new IntroBassBuilder();
  private final IntroDrumBuilder drumBuilder = new IntroDrumBuilder();
  private final IntroScorer scorer = new IntroScorer();

  /**
   * Generates 3 intro candidates and returns the highest-scoring one.
   *
   * @param ctx base intro context
   * @return best {@link IntroTrack} by {@link IntroScorer} score
   */
  public IntroTrack generate(IntroContext ctx) {
    Map<String, Integer> entryMap = IntroEntryPlanner.plan(ctx);

    List<IntroTrack> candidates = new ArrayList<>(NUM_CANDIDATES);

    for (int i = 0; i < NUM_CANDIDATES; i++) {
      IntroContext candidateCtx = varyContext(ctx, i);
      Map<String, Integer> candidateEntry = IntroEntryPlanner.plan(candidateCtx);

      List<ChanneledNote> guitar = guitarBuilder.build(candidateCtx,
          candidateEntry.getOrDefault(IntroEntryPlanner.GUITAR, 1));
      List<ChanneledNote> bass = bassBuilder.build(candidateCtx,
          candidateEntry.getOrDefault(IntroEntryPlanner.BASS, 1));
      List<DrumEvent> drums = drumBuilder.build(candidateCtx,
          candidateEntry.getOrDefault(IntroEntryPlanner.DRUMS, 1));

      double raw = scorer.score(
          new IntroTrack(guitar, bass, drums, 0.0, ctx.offsetTicks()),
          entryMap, ctx);

      candidates.add(new IntroTrack(guitar, bass, drums, raw, ctx.offsetTicks()));
    }

    return candidates.stream()
        .max((a, b) -> Double.compare(a.score(), b.score()))
        .orElseThrow(() -> new IllegalStateException("No intro candidates generated"));
  }

  // ---------- private helpers ----------

  /**
   * Returns a context whose arousal is slightly varied for candidate {@code index} so that each
   * candidate differs without changing the overall musical character.
   */
  private IntroContext varyContext(IntroContext base, int index) {
    if (index == 0) return base;
    double delta = (index == 1) ? CANDIDATE_DELTA : -CANDIDATE_DELTA;
    double newArousal = clamp01(base.sentiment().arousal() + delta);
    com.motifgen.sentiment.SentimentProfile variedSentiment =
        com.motifgen.sentiment.SentimentProfile.fromVA(base.sentiment().valence(), newArousal);
    return IntroContext.of(variedSentiment, base.key(), base.archetype(),
        base.ticksPerBeat(), base.beatsPerBar());
  }

  private static double clamp01(double v) {
    return Math.max(0.0, Math.min(1.0, v));
  }
}
