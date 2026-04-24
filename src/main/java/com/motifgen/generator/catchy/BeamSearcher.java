package com.motifgen.generator.catchy;

import com.motifgen.model.Motif;
import com.motifgen.model.Note;
import com.motifgen.theory.KeySignature;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * Constrained beam search that grows a phrase note-by-note. Each extension is
 * scored by {@link IncrementalScorer} plus a {@link SectionGoal} bonus and
 * the log of the proposal weight. Beam width defaults to 16; wider beams give
 * diminishing returns per the issue's tuning notes.
 */
public final class BeamSearcher {

  private final long seed;
  private final int beamWidth;
  private final IncrementalScorer partialScorer = new IncrementalScorer();

  public BeamSearcher(long seed, int beamWidth) {
    if (beamWidth <= 0) {
      throw new IllegalArgumentException("beamWidth must be positive, got " + beamWidth);
    }
    this.seed = seed;
    this.beamWidth = beamWidth;
  }

  public Motif search(Motif seedMotif, SectionGoal goal, List<Motif> priorContext,
      KeySignature key, int phraseLengthNotes, int ticksPerBeat, int beatsPerBar) {

    List<Note> seedNotes = seedMotif.getNotes();
    Note firstSeed = seedNotes.stream().filter(n -> !n.isRest()).findFirst()
        .orElse(new Note(key.root() + 60, 0, ticksPerBeat, 90));

    List<Beam> beams = new ArrayList<>();
    List<Note> start = new ArrayList<>();
    start.add(new Note(firstSeed.pitch(), 0, ticksPerBeat, firstSeed.velocity()));
    beams.add(new Beam(start, 0.0, new Random(seed)));

    while (beams.get(0).notes.size() < phraseLengthNotes) {
      List<Beam> candidates = new ArrayList<>();
      for (Beam beam : beams) {
        Note anchor = beam.notes.getLast();
        PhraseProposer proposer = new PhraseProposer(beam.rng.nextLong());
        for (PhraseProposer.Proposal proposal :
            proposer.propose(anchor.pitch(), ticksPerBeat, key)) {

          List<Note> extended = new ArrayList<>(beam.notes);
          long start2 = anchor.startTick() + anchor.durationTicks();
          extended.add(new Note(proposal.pitch(), start2, proposal.durationTicks(), 90));

          double partial = partialScorer.scorePartial(extended);
          double bonus = goal.bonus(extended, seedMotif, priorContext, key.root());
          double combined = partial + bonus + Math.log(proposal.weight());

          // If this beam is on its final note and the goal wants tonic
          // resolution, only let beams that land on the tonic pitch class through.
          if (extended.size() == phraseLengthNotes
              && goal == SectionGoal.RESOLVE_TO_TONIC
              && proposal.pitch() % 12 != key.root()) {
            continue;
          }

          Random child = new Random(beam.rng.nextLong());
          candidates.add(new Beam(extended, combined, child));
        }
      }

      // If tonic-resolution filtering eliminated everything on the final step,
      // fall back to unfiltered candidates so we always return a phrase.
      if (candidates.isEmpty()) {
        for (Beam beam : beams) {
          Note anchor = beam.notes.getLast();
          List<Note> extended = new ArrayList<>(beam.notes);
          long start2 = anchor.startTick() + anchor.durationTicks();
          int tonicPitch = nearestTonicPitch(anchor.pitch(), key.root());
          extended.add(new Note(tonicPitch, start2, ticksPerBeat, 90));
          candidates.add(new Beam(extended, beam.score, beam.rng));
        }
      }

      candidates.sort(Comparator.comparingDouble((Beam b) -> b.score).reversed());
      beams = candidates.subList(0, Math.min(beamWidth, candidates.size()));
    }

    Beam best = beams.get(0);
    int bars = Math.max(1, (int) Math.ceil((double) totalTicks(best.notes)
        / ((long) beatsPerBar * ticksPerBeat)));
    List<Note> padded = padToBars(best.notes, bars, beatsPerBar, ticksPerBeat);
    return new Motif(padded, bars, beatsPerBar, ticksPerBeat);
  }

  private static int nearestTonicPitch(int anchor, int tonicPc) {
    int octave = anchor / 12;
    int candidate = octave * 12 + tonicPc;
    if (Math.abs(candidate - anchor) > 6) {
      candidate = candidate > anchor ? candidate - 12 : candidate + 12;
    }
    return Math.max(0, Math.min(127, candidate));
  }

  private static long totalTicks(List<Note> notes) {
    if (notes.isEmpty()) return 0;
    Note last = notes.getLast();
    return last.startTick() + last.durationTicks();
  }

  private static List<Note> padToBars(List<Note> notes, int bars, int beatsPerBar,
      int ticksPerBeat) {
    long target = (long) bars * beatsPerBar * ticksPerBeat;
    long actual = totalTicks(notes);
    if (actual >= target || notes.isEmpty()) {
      return notes;
    }
    List<Note> padded = new ArrayList<>(notes);
    Note last = notes.getLast();
    padded.set(padded.size() - 1, new Note(last.pitch(), last.startTick(),
        last.durationTicks() + (target - actual), last.velocity()));
    return padded;
  }

  private record Beam(List<Note> notes, double score, Random rng) {}
}
