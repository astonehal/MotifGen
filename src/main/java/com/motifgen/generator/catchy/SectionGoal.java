package com.motifgen.generator.catchy;

import com.motifgen.model.Note;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Strategy describing what a phrase-building pass should optimise for.
 * Each goal returns a bonus in roughly {@code [-0.5, 0.5]} that is added
 * to the partial catchiness score inside {@link BeamSearcher}.
 */
public enum SectionGoal {

  /** Reinforce the seed motif: favour pitch-class overlap with the motif. */
  REINFORCE_MOTIF {
    @Override
    public double bonus(List<Note> partial, com.motifgen.model.Motif motif,
        List<com.motifgen.model.Motif> priorContext, int tonalCenterPc) {
      Set<Integer> motifPcs = new HashSet<>();
      for (Note n : motif.getNotes()) {
        if (!n.isRest()) motifPcs.add(n.pitchClass());
      }
      if (partial.isEmpty() || motifPcs.isEmpty()) return 0.0;
      int hits = 0;
      int total = 0;
      for (Note n : partial) {
        if (n.isRest()) continue;
        if (motifPcs.contains(n.pitchClass())) hits++;
        total++;
      }
      return total == 0 ? 0.0 : 0.5 * (double) hits / total;
    }
  },

  /** Contrast with prior material: penalise pitch-class overlap with prior. */
  PROVIDE_CONTRAST {
    @Override
    public double bonus(List<Note> partial, com.motifgen.model.Motif motif,
        List<com.motifgen.model.Motif> priorContext, int tonalCenterPc) {
      Set<Integer> priorPcs = new HashSet<>();
      for (com.motifgen.model.Motif phrase : priorContext) {
        for (Note n : phrase.getNotes()) {
          if (!n.isRest()) priorPcs.add(n.pitchClass());
        }
      }
      if (partial.isEmpty() || priorPcs.isEmpty()) return 0.0;
      int hits = 0;
      int total = 0;
      for (Note n : partial) {
        if (n.isRest()) continue;
        if (priorPcs.contains(n.pitchClass())) hits++;
        total++;
      }
      return total == 0 ? 0.0 : 0.25 - 0.5 * (double) hits / total;
    }
  },

  /** Final phrase: favour resolution onto the tonal center. */
  RESOLVE_TO_TONIC {
    @Override
    public double bonus(List<Note> partial, com.motifgen.model.Motif motif,
        List<com.motifgen.model.Motif> priorContext, int tonalCenterPc) {
      if (partial.isEmpty()) return 0.0;
      Note last = partial.getLast();
      if (last.isRest()) return 0.0;
      int distance = Math.min(
          ((last.pitchClass() - tonalCenterPc) + 12) % 12,
          ((tonalCenterPc - last.pitchClass()) + 12) % 12);
      return 0.5 - 0.1 * distance;
    }
  };

  public abstract double bonus(List<Note> partial, com.motifgen.model.Motif motif,
      List<com.motifgen.model.Motif> priorContext, int tonalCenterPc);
}
