package com.motifgen.guitar.backing;

import java.util.List;

/**
 * Output of the {@link RhythmDensityPlanner}: the chosen subdivision level,
 * how many chord changes occur per bar, and which beat positions (0-indexed)
 * carry a rhythmic accent.
 *
 * @param subdivision   target rhythmic granularity
 * @param changesPerBar number of chord changes per bar (1, 2, or 4)
 * @param accentBeats   0-indexed beat positions that receive an accent within one bar
 */
public record RhythmDensityPlan(
    Subdivision subdivision,
    int changesPerBar,
    List<Integer> accentBeats) {}
