package com.motifgen.generator.catchy;

import com.motifgen.model.Motif;
import com.motifgen.model.Note;
import com.motifgen.theory.KeySignature;
import java.util.Set;

/**
 * Builds a {@link StructuralPlan} for a 16-bar sentence from a seed motif.
 *
 * <p>Four templates are supported: {@code AABA}, {@code ABAB}, {@code ABAC},
 * {@code ABCA}. The climax is placed at roughly 60% of the way through the
 * melody (hook-prominence convention), and the tonal center is taken from
 * the supplied key.
 */
public final class StructuralPlanner {

  private static final Set<String> SUPPORTED_TEMPLATES = Set.of("AABA", "ABAB", "ABAC", "ABCA");

  private static final int DEFAULT_TOTAL_BARS = 16;
  private static final double CLIMAX_RELATIVE_POSITION = 0.6;

  public StructuralPlan plan(Motif motif, String template, KeySignature key) {
    if (!SUPPORTED_TEMPLATES.contains(template)) {
      throw new IllegalArgumentException(
          "Unsupported template: " + template + " (expected one of " + SUPPORTED_TEMPLATES + ")");
    }

    int sectionCount = template.length();
    int phraseBars = DEFAULT_TOTAL_BARS / sectionCount;
    int notesPerPhrase = countSoundingNotes(motif);
    int totalNotes = notesPerPhrase * sectionCount;
    int climaxPosition = Math.max(0,
        Math.min(totalNotes - 1, (int) Math.round(totalNotes * CLIMAX_RELATIVE_POSITION)));

    return new StructuralPlan(template, phraseBars, DEFAULT_TOTAL_BARS,
        notesPerPhrase, climaxPosition, key.root());
  }

  private static int countSoundingNotes(Motif motif) {
    int n = 0;
    for (Note note : motif.getNotes()) {
      if (!note.isRest()) {
        n++;
      }
    }
    return Math.max(n, 1);
  }
}
