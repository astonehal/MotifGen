package com.motifgen.generator.catchy;

import com.motifgen.model.Motif;
import com.motifgen.model.Note;
import com.motifgen.model.Sentence;
import com.motifgen.scoring.SentenceScorer;
import com.motifgen.theory.KeySignature;
import java.util.ArrayList;
import java.util.List;

/**
 * Adapts a motif's content duration to a phrase's target duration before any
 * role-specific transformation is applied. Two paths:
 *
 * <ul>
 *   <li><b>Extension</b> (motif content shorter than target): tile the phrase
 *       with successive diatonic transpositions of the motif. The
 *       {@link #match(Motif, long, KeySignature, long) match} method picks the
 *       highest-scoring tile pattern from a small candidate set.</li>
 *   <li><b>Reduction</b> (motif content longer than target): apply proportional
 *       diminution; if scaling would push any duration below
 *       {@link #MIN_DURATION_TICKS}, fall back to keeping every Nth note (with
 *       the first and last sounding notes always preserved) and then scale.</li>
 * </ul>
 */
public final class MotifLengthMatcher {

  /** 16th note at 480 ticks per beat. */
  public static final long MIN_DURATION_TICKS = 120L;

  private static final int[][] A_CANDIDATE_PATTERNS = {
      {0, 0, 0, 0},
      {0, 1, 2, 3},
      {0, 1, -1, 2},
      {0, 0, 1, 0},
      {0, -1, 1, -2}
  };

  private final MotifTransformer transformer = new MotifTransformer();
  private final SentenceScorer scorer = new SentenceScorer();

  public record ContentSpan(long startTick, long endTick, long durationTicks) {}

  public ContentSpan span(Motif motif) {
    Long start = null;
    long end = 0L;
    for (Note n : motif.getNotes()) {
      if (n.isRest()) continue;
      if (start == null) start = n.startTick();
      end = Math.max(end, n.endTick());
    }
    if (start == null) return new ContentSpan(0L, 0L, 0L);
    return new ContentSpan(start, end, end - start);
  }

  public Motif match(Motif motif, long phraseTicks, KeySignature key, long seed) {
    long content = span(motif).durationTicks();
    if (content == phraseTicks || content == 0L) return motif;
    if (content > phraseTicks) return reduce(motif, phraseTicks);

    Motif best = null;
    double bestScore = Double.NEGATIVE_INFINITY;
    for (int[] pattern : A_CANDIDATE_PATTERNS) {
      Motif candidate = extend(motif, phraseTicks, key, pattern);
      Sentence mock = new Sentence(
          List.of(candidate, candidate, candidate, candidate),
          "a a a a", key.name(), 0.0);
      double score = scorer.score(mock).getScore();
      if (score > bestScore) {
        bestScore = score;
        best = candidate;
      }
    }
    return best;
  }

  Motif extend(Motif tile0, long phraseTicks, KeySignature key, int[] steps) {
    if (steps == null || steps.length == 0) {
      throw new IllegalArgumentException("steps must be non-empty");
    }
    long tileTicks = span(tile0).durationTicks();
    if (tileTicks <= 0L) return tile0;

    List<Note> out = new ArrayList<>();
    long cursor = 0L;
    int tileIdx = 0;
    while (cursor < phraseTicks) {
      Motif tile = transformer.diatonicTranspose(tile0,
          steps[tileIdx % steps.length], key);
      for (Note n : tile.getNotes()) {
        long start = cursor + n.startTick();
        if (start >= phraseTicks) break;
        long end = Math.min(start + n.durationTicks(), phraseTicks);
        out.add(new Note(n.pitch(), start, end - start, n.velocity()));
      }
      cursor += tileTicks;
      tileIdx++;
    }
    if (!out.isEmpty()) {
      Note last = out.get(out.size() - 1);
      long maxEnd = Math.min(phraseTicks, last.startTick() + last.durationTicks());
      if (maxEnd != last.endTick()) {
        out.set(out.size() - 1, new Note(last.pitch(), last.startTick(),
            maxEnd - last.startTick(), last.velocity()));
      }
      // If the last note ends short of the boundary by less than one tick,
      // stretch it to land exactly on the boundary.
      Note tail = out.get(out.size() - 1);
      if (tail.endTick() < phraseTicks) {
        out.set(out.size() - 1, new Note(tail.pitch(), tail.startTick(),
            phraseTicks - tail.startTick(), tail.velocity()));
      }
    }
    int bars = (int) Math.max(1L,
        phraseTicks / ((long) tile0.getBeatsPerBar() * tile0.getTicksPerBeat()));
    return new Motif(out, bars, tile0.getBeatsPerBar(), tile0.getTicksPerBeat());
  }

  Motif reduce(Motif motif, long phraseTicks) {
    long content = span(motif).durationTicks();
    if (content <= phraseTicks) return motif;

    List<Note> sounding = new ArrayList<>();
    for (Note n : motif.getNotes()) {
      if (!n.isRest()) sounding.add(n);
    }
    if (sounding.isEmpty()) return motif;

    int subsample = chooseSubsample(sounding, content, phraseTicks);
    List<Note> kept = subsample(sounding, subsample);
    long keptSpan = kept.get(kept.size() - 1).endTick() - kept.get(0).startTick();
    if (keptSpan <= 0L) return motif;
    double scale = (double) phraseTicks / (double) keptSpan;
    long firstStart = kept.get(0).startTick();

    List<Note> scaled = new ArrayList<>();
    for (Note n : kept) {
      long newStart = Math.round((n.startTick() - firstStart) * scale);
      long newDur = Math.max(MIN_DURATION_TICKS, Math.round(n.durationTicks() * scale));
      scaled.add(new Note(n.pitch(), newStart, newDur, n.velocity()));
    }
    int bars = (int) Math.max(1L,
        phraseTicks / ((long) motif.getBeatsPerBar() * motif.getTicksPerBeat()));
    return new Motif(scaled, bars, motif.getBeatsPerBar(), motif.getTicksPerBeat());
  }

  private static int chooseSubsample(List<Note> sounding, long content, long target) {
    if (sounding.isEmpty()) return 1;
    long minDur = sounding.stream().mapToLong(Note::durationTicks).min().orElse(0L);
    if (minDur <= 0L) return 1;
    double scale = (double) target / (double) content;
    if (minDur * scale >= MIN_DURATION_TICKS) return 1;
    int n = 2;
    while (n <= sounding.size()) {
      double effectiveScale = (double) target / ((double) content / n);
      if (minDur * effectiveScale >= MIN_DURATION_TICKS) return n;
      n++;
    }
    return n;
  }

  private static List<Note> subsample(List<Note> sounding, int n) {
    if (n <= 1) return sounding;
    List<Note> out = new ArrayList<>();
    out.add(sounding.get(0));
    for (int i = n; i < sounding.size() - 1; i += n) {
      out.add(sounding.get(i));
    }
    Note last = sounding.get(sounding.size() - 1);
    if (out.get(out.size() - 1) != last) out.add(last);
    return out;
  }
}
