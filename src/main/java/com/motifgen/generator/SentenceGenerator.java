package com.motifgen.generator;

import com.motifgen.generator.catchy.AnnealingRefiner;
import com.motifgen.generator.catchy.BeamSearcher;
import com.motifgen.generator.catchy.ClimaxPlacer;
import com.motifgen.generator.catchy.SectionGoal;
import com.motifgen.generator.catchy.StructuralPlan;
import com.motifgen.generator.catchy.StructuralPlanner;
import com.motifgen.model.Motif;
import com.motifgen.model.Note;
import com.motifgen.model.Sentence;
import com.motifgen.scoring.SentenceScorer;
import com.motifgen.theory.KeyDetector;
import com.motifgen.theory.KeySignature;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Generates 16-bar musical sentences from a 4-bar motif using a score-guided
 * pipeline: structural plan → per-phrase beam search → climax placement →
 * simulated annealing refinement.
 *
 * <p>For each related key and each supported template, the pipeline is run
 * across a small number of random seeds and the highest-scoring run is kept.
 * The returned list is sorted best-first so the CLI can pick a top pair.
 */
public class SentenceGenerator {

  private static final int BEAM_WIDTH = 16;
  private static final int REFINEMENT_ITERATIONS = 30;
  private static final int SEEDS_PER_COMBO = 3;
  private static final String[] TEMPLATES = {"AABA", "ABAB", "ABAC", "ABCA"};

  private final long rootSeed;
  private final StructuralPlanner planner = new StructuralPlanner();
  private final ClimaxPlacer climaxPlacer = new ClimaxPlacer();
  private final SentenceScorer scorer = new SentenceScorer();

  public SentenceGenerator(long seed) {
    this.rootSeed = seed;
  }

  public SentenceGenerator() {
    this(System.nanoTime());
  }

  public List<Sentence> generate(Motif motif) {
    KeySignature detectedKey = KeyDetector.bestKey(motif);
    List<KeySignature> keys = detectedKey.relatedKeys();

    System.out.println("  Detected key: " + detectedKey.name());
    System.out.println("  Exploring keys: " + keys.stream().map(KeySignature::name).toList());

    List<Sentence> candidates = new ArrayList<>();
    long comboSeed = rootSeed;

    for (KeySignature key : keys) {
      int transposition = key.root() - detectedKey.root();
      Motif baseMotif = transposition == 0 ? motif : motif.transpose(transposition);
      for (String template : TEMPLATES) {
        Sentence best = null;
        for (int s = 0; s < SEEDS_PER_COMBO; s++) {
          long runSeed = comboSeed + s * 31L;
          Sentence produced = scorer.score(runPipeline(baseMotif, key, template, runSeed));
          if (best == null || produced.getScore() > best.getScore()) {
            best = produced;
          }
        }
        candidates.add(best);
        comboSeed += 1009L;
      }
    }

    candidates.sort(Comparator.comparingDouble(Sentence::getScore).reversed());
    return candidates;
  }

  private Sentence runPipeline(Motif motif, KeySignature key, String template, long seed) {
    StructuralPlan plan = planner.plan(motif, template, key);

    BeamSearcher searcher = new BeamSearcher(seed, BEAM_WIDTH);
    List<Motif> phrases = new ArrayList<>();
    int sections = plan.sectionCount();
    for (int i = 0; i < sections; i++) {
      char sectionChar = template.charAt(i);
      SectionGoal goal = goalFor(sectionChar, i == sections - 1);
      Motif phrase = searcher.search(motif, goal, List.copyOf(phrases), key,
          plan.notesPerPhrase(), motif.getTicksPerBeat(), motif.getBeatsPerBar());
      phrase = phrase.withBars(plan.phraseBars());
      phrases.add(phrase);
    }

    Motif climaxed = applyClimax(phrases, plan, key);
    List<Motif> shapedPhrases = splitByPhrase(climaxed, phrases);

    Sentence assembled = new Sentence(shapedPhrases, structureStringFor(template),
        key.name(), 0);

    AnnealingRefiner refiner = new AnnealingRefiner(seed ^ 0xA11CE, REFINEMENT_ITERATIONS);
    return refiner.refine(assembled, motif, key);
  }

  private Motif applyClimax(List<Motif> phrases, StructuralPlan plan, KeySignature key) {
    List<Note> all = new ArrayList<>();
    long offset = 0;
    for (Motif phrase : phrases) {
      for (Note n : phrase.getNotes()) {
        all.add(n.withStartTick(n.startTick() + offset));
      }
      offset += phrase.totalTicks();
    }
    Motif combined = new Motif(all, plan.totalBars(),
        phrases.get(0).getBeatsPerBar(), phrases.get(0).getTicksPerBeat());
    return climaxPlacer.enforceClimax(combined, plan.climaxPosition(), key);
  }

  private List<Motif> splitByPhrase(Motif combined, List<Motif> originalPhrases) {
    List<Motif> split = new ArrayList<>();
    int cursor = 0;
    List<Note> all = combined.getNotes();
    for (Motif original : originalPhrases) {
      int count = original.getNotes().size();
      List<Note> slice = new ArrayList<>();
      long baseOffset = (long) split.size() * original.totalTicks();
      for (int i = 0; i < count && cursor < all.size(); i++) {
        Note n = all.get(cursor++);
        slice.add(n.withStartTick(n.startTick() - baseOffset));
      }
      split.add(new Motif(slice, original.getBars(),
          original.getBeatsPerBar(), original.getTicksPerBeat()));
    }
    return split;
  }

  private static SectionGoal goalFor(char section, boolean isFinal) {
    if (isFinal) return SectionGoal.RESOLVE_TO_TONIC;
    return section == 'A' ? SectionGoal.REINFORCE_MOTIF : SectionGoal.PROVIDE_CONTRAST;
  }

  private static String structureStringFor(String template) {
    StringBuilder sb = new StringBuilder();
    int[] seen = new int[26];
    for (int i = 0; i < template.length(); i++) {
      if (i > 0) sb.append(' ');
      char c = template.charAt(i);
      char lower = Character.toLowerCase(c);
      sb.append(lower);
      int count = seen[c - 'A']++;
      for (int j = 0; j < count; j++) sb.append('\'');
    }
    return sb.toString();
  }
}
