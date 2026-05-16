package com.motifgen.generator;

import com.motifgen.generator.catchy.AnnealingRefiner;
import com.motifgen.generator.catchy.ClimaxPlacer;
import com.motifgen.generator.catchy.MotifLengthMatcher;
import com.motifgen.generator.catchy.PhraseSeeder;
import com.motifgen.generator.catchy.StructuralPlan;
import com.motifgen.generator.catchy.StructuralPlanner;
import com.motifgen.guitar.PlayabilityGate;
import com.motifgen.model.Motif;
import com.motifgen.model.Note;
import com.motifgen.model.Sentence;
import com.motifgen.scoring.SentenceScorer;
import com.motifgen.sentiment.KeyAffinity;
import com.motifgen.sentiment.SentimentProfile;
import com.motifgen.theory.KeyDetector;
import com.motifgen.theory.KeySignature;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Generates 16-bar musical sentences from a 4-bar motif by seeding each phrase
 * from the input motif (via {@link PhraseSeeder}) and refining the result with
 * simulated annealing. {@code 'A'} sections are kept as exact copies of the
 * motif; {@code 'B'} and {@code 'C'} sections are transformed (transpose,
 * invert, retrograde) and then nudged by the annealer toward higher
 * catchiness scores.
 *
 * <p>For each related key and each supported template, the pipeline is run
 * across a small number of random seeds and the highest-scoring run is kept.
 * The returned list is sorted best-first.
 */
public class SentenceGenerator {

  private static final int REFINEMENT_ITERATIONS = 30;
  private static final int SEEDS_PER_COMBO = 3;
  private static final String[] TEMPLATES = {"AABA", "ABAB", "ABAC", "ABCA"};

  private final long rootSeed;
  private final StructuralPlanner planner = new StructuralPlanner();
  private final ClimaxPlacer climaxPlacer = new ClimaxPlacer();
  private final SentenceScorer scorer = new SentenceScorer();
  private final MotifLengthMatcher lengthMatcher = new MotifLengthMatcher();
  /** Optional post-refinement gate; {@code null} means no playability filtering. */
  private final PlayabilityGate playabilityGate;

  public SentenceGenerator(long seed) {
    this(seed, null);
  }

  /**
   * Creates a generator with an explicit {@link PlayabilityGate}.
   * After the candidate list is scored and sorted, each candidate is evaluated by the gate.
   * Candidates that pass are promoted to the front of the returned list; if every candidate
   * fails the gate the best-scored one is kept as a fallback so callers always receive a result.
   *
   * @param seed deterministic seed for the generation pipeline
   * @param gate playability gate, or {@code null} to disable guitar playability filtering
   */
  public SentenceGenerator(long seed, PlayabilityGate gate) {
    this.rootSeed = seed;
    this.playabilityGate = gate;
  }

  public SentenceGenerator() {
    this(System.nanoTime(), null);
  }

  public List<Sentence> generate(Motif motif) {
    return generate(motif, null);
  }

  /**
   * Sentiment-aware generation. The related-key fleet is weighted by
   * {@link KeyAffinity}, and each pipeline run uses the sentiment profile
   * to bias phrase seeding, climax placement, and structural template
   * selection.
   *
   * @param motif   seed motif
   * @param profile sentiment profile, or {@code null} for no sentiment influence
   * @return sorted (best-first) list of 20 sentence candidates
   */
  public List<Sentence> generate(Motif motif, SentimentProfile profile) {
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
          Sentence produced = scorer.score(runPipeline(baseMotif, key, template, runSeed, profile));
          if (best == null || produced.getScore() > best.getScore()) {
            best = produced;
          }
        }
        candidates.add(best);
        comboSeed += 1009L;
      }
    }

    candidates.sort(Comparator.comparingDouble(Sentence::getScore).reversed());

    if (playabilityGate == null) {
      return candidates;
    }

    // Run each candidate through the gate; keep those that pass.
    int ticksPerBeat = motif.getTicksPerBeat();
    List<Sentence> playable = new ArrayList<>();
    for (Sentence candidate : candidates) {
      PlayabilityGate.GateResult result = playabilityGate.evaluate(candidate, ticksPerBeat);
      if (result.passed()) {
        playable.add(result.sentence()); // labelled sentence
      }
    }

    // Fall back to the best-scored candidate when every candidate fails the gate.
    if (playable.isEmpty()) {
      System.out.println("  [PlayabilityGate] All candidates failed — returning best anyway.");
      return candidates;
    }

    System.out.println("  [PlayabilityGate] " + playable.size() + "/" + candidates.size()
        + " candidates passed.");
    return playable;
  }

  private Sentence runPipeline(Motif motif, KeySignature key, String template, long seed,
      SentimentProfile profile) {
    StructuralPlan plan = profile != null
        ? planner.plan(motif, template, key, profile)
        : planner.plan(motif, template, key);
    long phraseTicks = (long) plan.phraseBars() * motif.getBeatsPerBar()
        * motif.getTicksPerBeat();
    Motif lengthMatched = lengthMatcher.match(motif, phraseTicks, key, seed);

    PhraseSeeder seeder = profile != null
        ? new PhraseSeeder(seed, profile)
        : new PhraseSeeder(seed);
    List<Motif> phrases = new ArrayList<>();
    Set<Integer> immutableIndices = new HashSet<>();
    int sections = plan.sectionCount();
    for (int i = 0; i < sections; i++) {
      char role = template.charAt(i);
      boolean isFinal = i == sections - 1;
      PhraseSeeder.SeededPhrase seeded = seeder.seed(role, isFinal,
          lengthMatched, key);
      Motif phrase = seeded.phrase().withBars(plan.phraseBars());
      phrases.add(phrase);
      if (seeded.immutable()) immutableIndices.add(i);
    }

    int climaxPhraseIdx = phraseIndexForClimax(plan.climaxPosition(), phrases);
    if (climaxPhraseIdx >= 0 && !immutableIndices.contains(climaxPhraseIdx)) {
      Motif climaxed = profile != null
          ? applyClimax(phrases, plan, key, profile)
          : applyClimax(phrases, plan, key);
      phrases = splitByPhrase(climaxed, phrases);
    }

    Sentence assembled = new Sentence(phrases, structureStringFor(template),
        key.name(), 0);

    AnnealingRefiner refiner = new AnnealingRefiner(seed ^ 0xA11CE, REFINEMENT_ITERATIONS);
    Sentence refined = profile != null
        ? refiner.refine(assembled, motif, key, immutableIndices, profile)
        : refiner.refine(assembled, motif, key, immutableIndices);
    int finalPhraseIdx = refined.getPhrases().size() - 1;
    if (!immutableIndices.contains(finalPhraseIdx)) {
      refined = forceFinalNoteToTonic(refined, key, finalPhraseIdx);
    }
    return refined;
  }

  private static Sentence forceFinalNoteToTonic(Sentence sentence, KeySignature key,
      int phraseIdx) {
    List<Motif> phrases = new ArrayList<>(sentence.getPhrases());
    Motif phrase = phrases.get(phraseIdx);
    List<Note> notes = new ArrayList<>(phrase.getNotes());

    int lastIdx = -1;
    for (int i = notes.size() - 1; i >= 0; i--) {
      if (!notes.get(i).isRest()) {
        lastIdx = i;
        break;
      }
    }
    if (lastIdx < 0) return sentence;

    Note last = notes.get(lastIdx);
    int currentPc = ((last.pitch() % 12) + 12) % 12;
    if (currentPc == key.root()) return sentence;

    int octaveStart = (last.pitch() / 12) * 12;
    int candidate = octaveStart + key.root();
    int up = candidate >= last.pitch() ? candidate : candidate + 12;
    int down = candidate <= last.pitch() ? candidate : candidate - 12;
    int chosen = Math.abs(up - last.pitch()) <= Math.abs(last.pitch() - down) ? up : down;
    chosen = Math.max(0, Math.min(127, chosen));

    notes.set(lastIdx, new Note(chosen, last.startTick(),
        last.durationTicks(), last.velocity()));
    phrases.set(phraseIdx, new Motif(notes, phrase.getBars(),
        phrase.getBeatsPerBar(), phrase.getTicksPerBeat()));
    return new Sentence(phrases, sentence.getStructure(), sentence.getKeyName(),
        sentence.getScore());
  }

  private static int phraseIndexForClimax(int climaxPosition, List<Motif> phrases) {
    int running = 0;
    for (int i = 0; i < phrases.size(); i++) {
      int sounding = (int) phrases.get(i).getNotes().stream()
          .filter(n -> !n.isRest()).count();
      if (climaxPosition < running + sounding) return i;
      running += sounding;
    }
    return -1;
  }

  private Motif applyClimax(List<Motif> phrases, StructuralPlan plan, KeySignature key) {
    return applyClimax(phrases, plan, key, null);
  }

  private Motif applyClimax(List<Motif> phrases, StructuralPlan plan, KeySignature key,
      SentimentProfile profile) {
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
    if (profile != null) {
      return climaxPlacer.enforceClimax(combined, plan.climaxPosition(), key, profile);
    }
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
