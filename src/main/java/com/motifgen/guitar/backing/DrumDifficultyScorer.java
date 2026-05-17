package com.motifgen.guitar.backing;

import java.util.List;
import java.util.Set;

/**
 * Stateless 4-factor difficulty scorer for drum tracks.
 *
 * <p>Composite score = 0.35 × independence + 0.25 × density + 0.20 × kick + 0.20 × tempo.
 * All sub-scores and the composite are in [0.0, 1.0].
 */
public final class DrumDifficultyScorer {

  private static final double WEIGHT_INDEPENDENCE = 0.35;
  private static final double WEIGHT_DENSITY      = 0.25;
  private static final double WEIGHT_KICK         = 0.20;
  private static final double WEIGHT_TEMPO        = 0.20;

  /** Tempo at which the tempo penalty reaches 1.0. */
  private static final double MAX_SCORED_TEMPO = 240.0;
  /** Tempo below which the tempo penalty is 0.0. */
  private static final double MIN_SCORED_TEMPO = 60.0;

  // GM note sets used for independence scoring.
  private static final Set<Integer> KICK_NOTES  = Set.of(35, 36);
  private static final Set<Integer> SNARE_NOTES = Set.of(37, 38, 40);
  private static final Set<Integer> HIHAT_NOTES = Set.of(42, 44, 46);
  private static final Set<Integer> RIDE_NOTES  = Set.of(51, 53, 59);
  private static final Set<Integer> CYMBAL_NOTES = Set.of(49, 52, 57);
  private static final Set<Integer> TOM_NOTES   = Set.of(41, 43, 45, 47, 48, 50);

  private DrumDifficultyScorer() {}

  /**
   * Scores the given drum events against the supplied tempo.
   *
   * @param events   drum events (any order)
   * @param tempoBpm tempo in beats per minute
   * @return composite difficulty score and level
   */
  public static DrumDifficulty.DifficultyScore score(List<DrumEvent> events, int tempoBpm) {
    if (events.isEmpty()) {
      return new DrumDifficulty.DifficultyScore(
          0.0, DrumDifficulty.BEGINNER, 0.0, 0.0, 0.0, 0.0);
    }

    double independence = scoreIndependence(events);
    double density      = scoreDensity(events);
    double kick         = scoreKickComplexity(events);
    double tempo        = scoreTempoFactor(tempoBpm);

    double composite = WEIGHT_INDEPENDENCE * independence
        + WEIGHT_DENSITY * density
        + WEIGHT_KICK * kick
        + WEIGHT_TEMPO * tempo;

    composite = Math.max(0.0, Math.min(1.0, composite));
    return new DrumDifficulty.DifficultyScore(
        composite, DrumDifficulty.fromScore(composite),
        independence, density, kick, tempo);
  }

  // -------------------------------------------------------------------------
  // Sub-scorers
  // -------------------------------------------------------------------------

  /**
   * Independence demand: fraction of beats where 3 or more distinct limbs are
   * active simultaneously (kick, snare/tom, and cymbal at same tick window).
   */
  private static double scoreIndependence(List<DrumEvent> events) {
    long totalTicks = spanTicks(events);
    if (totalTicks <= 0) return 0.0;

    // Collect unique ticks.
    Set<Long> ticks = new java.util.LinkedHashSet<>();
    for (DrumEvent e : events) ticks.add(e.startTick());

    long simultaneousCount = 0;
    for (long tick : ticks) {
      boolean hasKick   = false;
      boolean hasSnare  = false;
      boolean hasCymbal = false;
      for (DrumEvent e : events) {
        if (e.startTick() != tick) continue;
        if (KICK_NOTES.contains(e.gmNote()))  hasKick = true;
        if (SNARE_NOTES.contains(e.gmNote()) || TOM_NOTES.contains(e.gmNote())) hasSnare = true;
        if (HIHAT_NOTES.contains(e.gmNote()) || RIDE_NOTES.contains(e.gmNote())
            || CYMBAL_NOTES.contains(e.gmNote())) hasCymbal = true;
      }
      if (hasKick && hasSnare && hasCymbal) simultaneousCount++;
    }
    // Normalise: 4 tri-limb hits per bar ≈ expert.
    long bars = Math.max(1, totalTicks / (4 * 480));
    double rate = (double) simultaneousCount / (bars * 4);
    return Math.min(1.0, rate);
  }

  /**
   * Subdivision density: ratio of active 16th-note slots to total available slots.
   */
  private static double scoreDensity(List<DrumEvent> events) {
    long totalTicks = spanTicks(events);
    if (totalTicks <= 0) return 0.0;

    long sixteenth = 120L; // 480 / 4
    Set<Long> occupiedSlots = new java.util.HashSet<>();
    for (DrumEvent e : events) {
      occupiedSlots.add(e.startTick() / sixteenth);
    }
    long totalSlots = Math.max(1, totalTicks / sixteenth);
    return Math.min(1.0, (double) occupiedSlots.size() / totalSlots);
  }

  /**
   * Kick complexity: fraction of kick hits that fall off the quarter-note grid
   * (i.e. syncopated kicks).
   */
  private static double scoreKickComplexity(List<DrumEvent> events) {
    long quarterNote = 480L;
    long tolerance   = 30L;
    long kicks = 0;
    long syncopated = 0;
    for (DrumEvent e : events) {
      if (!KICK_NOTES.contains(e.gmNote())) continue;
      kicks++;
      long mod = e.startTick() % quarterNote;
      boolean onBeat = mod <= tolerance || mod >= (quarterNote - tolerance);
      if (!onBeat) syncopated++;
    }
    return kicks == 0 ? 0.0 : Math.min(1.0, (double) syncopated / kicks);
  }

  /**
   * Tempo penalty: linearly maps [60, 240] BPM → [0.0, 1.0].
   */
  private static double scoreTempoFactor(int tempoBpm) {
    double clamped = Math.max(MIN_SCORED_TEMPO, Math.min(MAX_SCORED_TEMPO, tempoBpm));
    return (clamped - MIN_SCORED_TEMPO) / (MAX_SCORED_TEMPO - MIN_SCORED_TEMPO);
  }

  private static long spanTicks(List<DrumEvent> events) {
    long min = Long.MAX_VALUE;
    long max = Long.MIN_VALUE;
    for (DrumEvent e : events) {
      if (e.startTick() < min) min = e.startTick();
      if (e.startTick() > max) max = e.startTick();
    }
    return (min == Long.MAX_VALUE) ? 0L : (max - min + 1);
  }
}
