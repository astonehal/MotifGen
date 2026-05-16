package com.motifgen.guitar.backing;

import java.util.List;

/**
 * Immutable record representing a candidate bass line before channel assignment.
 *
 * @param notes     ordered list of bass notes
 * @param score     composite score in [0, 1] produced by {@link BassLineScorer}
 * @param archetype the groove archetype used to generate this line
 */
public record BassLine(List<BassNote> notes, double score, BassGrooveArchetype archetype) {}
