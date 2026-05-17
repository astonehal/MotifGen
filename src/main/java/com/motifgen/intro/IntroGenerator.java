package com.motifgen.intro;

import com.motifgen.guitar.backing.ChanneledNote;
import com.motifgen.guitar.backing.DrumEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Facade that generates up to 8 intro candidates, scores each with {@link IntroScorer}, and
 * returns the best {@link IntroTrack} that meets the minimum score threshold (30.0).
 *
 * <p>If no candidate reaches the threshold after 8 attempts, the highest-scoring candidate is
 * returned unconditionally.
 *
 * <p>Each attempt draws fresh templates from {@link IntroTemplatePool} using the shared RNG,
 * so successive attempts genuinely differ in entry order, guitar pattern, and drum sub-pattern.
 */
public final class IntroGenerator {

  private static final int MAX_ATTEMPTS = 8;

  private final IntroGuitarBuilder guitarBuilder = new IntroGuitarBuilder();
  private final IntroBassBuilder bassBuilder = new IntroBassBuilder();
  private final IntroDrumBuilder drumBuilder = new IntroDrumBuilder();
  private final IntroScorer scorer = new IntroScorer();
  private final Random rng = new Random();

  /**
   * Generates up to {@value MAX_ATTEMPTS} intro candidates and returns the best one.
   *
   * <p>Preference is given to the highest-scoring candidate that meets the 30.0 minimum.
   * If none qualifies, the overall highest scorer is returned.
   *
   * @param ctx base intro context
   * @return best {@link IntroTrack} by {@link IntroScorer} score
   */
  public IntroTrack generate(IntroContext ctx) {
    List<IntroTrack> candidates = new ArrayList<>(MAX_ATTEMPTS);

    for (int i = 0; i < MAX_ATTEMPTS; i++) {
      Map<String, Integer> entryMap = IntroEntryPlanner.plan(ctx);

      List<ChanneledNote> guitar = guitarBuilder.build(ctx,
          entryMap.getOrDefault(IntroEntryPlanner.GUITAR, 1));
      List<ChanneledNote> bass = bassBuilder.build(ctx,
          entryMap.getOrDefault(IntroEntryPlanner.BASS, 1));
      List<DrumEvent> drums = drumBuilder.build(ctx,
          entryMap.getOrDefault(IntroEntryPlanner.DRUMS, 1));

      double raw = scorer.score(
          new IntroTrack(guitar, bass, drums, 0.0, ctx.offsetTicks()),
          entryMap, ctx);

      candidates.add(new IntroTrack(guitar, bass, drums, raw, ctx.offsetTicks()));
    }

    // Prefer the best candidate that meets the minimum threshold.
    return candidates.stream()
        .filter(t -> scorer.meetsMinimum(t.score()))
        .max((a, b) -> Double.compare(a.score(), b.score()))
        .orElseGet(() ->
            candidates.stream()
                .max((a, b) -> Double.compare(a.score(), b.score()))
                .orElseThrow(() -> new IllegalStateException("No intro candidates generated")));
  }
}
