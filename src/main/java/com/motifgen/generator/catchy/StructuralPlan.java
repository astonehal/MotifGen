package com.motifgen.generator.catchy;

/**
 * Immutable plan describing the macro shape of a 16-bar sentence before any
 * notes are chosen. Produced by {@link StructuralPlanner} and consumed by
 * downstream stages (beam search, climax placement, refinement).
 */
public record StructuralPlan(
    String template,
    int phraseBars,
    int totalBars,
    int notesPerPhrase,
    int climaxPosition,
    int tonalCenterPc) {

  public int sectionCount() {
    return template.length();
  }

  public int totalNotes() {
    return notesPerPhrase * sectionCount();
  }
}
